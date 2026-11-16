/*
 * SPDX-FileCopyrightText: 2026 Taler Systems S.A.
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of GNU Taler
 * (C) 2026 Taler Systems S.A.
 *
 * GNU Taler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GNU Taler is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Taler.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.taler.xotpconfigurer

import android.content.SharedPreferences
import net.taler.xotpconfigurer.data.SavedConfig
import net.taler.xotpconfigurer.data.SavedConfigKind
import net.taler.xotpconfigurer.data.SavedConfigRepository
import net.taler.xotpconfigurer.model.GlobalField
import net.taler.xotpconfigurer.data.ThemePreferences
import net.taler.xotpconfigurer.debug.DebugTapMode
import net.taler.xotpconfigurer.model.uiText
import net.taler.xotpconfigurer.nfc.NfcRecord
import net.taler.xotpconfigurer.nfc.NfcTransport
import net.taler.xotpconfigurer.nfc.ReaderState
import net.taler.xotpconfigurer.nfc.TagHandle
import net.taler.xotpconfigurer.nfc.TagReader
import net.taler.xotpconfigurer.nfc.TagResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** Scheduling of NFC operations in [NfcViewModel] against a scripted [TagReader]. */
@OptIn(ExperimentalCoroutinesApi::class)
class NfcViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var reader: FakeTagReader
    private lateinit var viewModel: NfcViewModel
    private lateinit var templates: SavedConfigRepository

    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        reader = FakeTagReader()
        templates = SavedConfigRepository(FakePreferences())
        viewModel = NfcViewModel(
            savedConfigRepository = templates,
            themePreferences = ThemePreferences(FakePreferences()),
            reader = reader
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        advanceUntilIdle()
        body()
    }

    @Test
    fun tapOnDiscoverScreen_discoversDevice() = test {
        reader.tap()
        advanceUntilIdle()

        assertEquals(Screen.Overview, state.screen)
        assertEquals(FakeTagReader.SERIAL, state.session?.serial)
        assertEquals(5, state.session?.info?.slotCount)
        assertEquals(1, reader.runs.size)
    }

    @Test
    fun actionStillArmedAfterRun_waitsForNewTap() = test {
        reader.tap()
        advanceUntilIdle()
        // The fake device does not answer GET_GLOBAL: the page load failed and stays armed.
        assertEquals(LoadState.FAILED, state.session?.pageLoad)
        assertEquals(TapAction.LoadSlotNames(0), state.armedAction)
        assertEquals(1, reader.runs.size)

        reader.tap()
        advanceUntilIdle()
        assertEquals(2, reader.runs.size)
    }

    @Test
    fun newlyArmedAction_reusesTagStillInField() = test {
        viewModel.onOpenDebug()
        reader.tap()
        advanceUntilIdle()
        assertEquals(0, reader.runs.size) // Nothing armed yet

        viewModel.onDebugArm(DebugTapMode.READ)
        advanceUntilIdle()
        assertEquals(1, reader.runs.size)
        assertNull(state.debug.armed)

        viewModel.onDebugArm(DebugTapMode.READ)
        advanceUntilIdle()
        assertEquals(2, reader.runs.size)
        assertEquals(2, state.debug.log.size)
    }

    @Test
    fun readerNotActive_doesNotTouchTag() = test {
        reader.state.value = ReaderState.Paused
        reader.tap()
        advanceUntilIdle()
        assertEquals(0, reader.runs.size)

        reader.state.value = ReaderState.Active
        advanceUntilIdle()
        assertEquals(1, reader.runs.size)
    }

    @Test
    fun launchedByDevice_startsOverWithDiscovery() = test {
        viewModel.onOpenDebug()
        viewModel.onDebugArm(DebugTapMode.READ)
        // App in the background: the launching tap arrives before reader mode is on again.
        reader.state.value = ReaderState.Paused
        viewModel.onLaunchedByDevice()
        reader.tap()
        advanceUntilIdle()
        assertEquals(Screen.Discover, state.screen)
        assertNull(state.debug.armed)
        assertEquals(0, reader.runs.size)

        reader.state.value = ReaderState.Active
        advanceUntilIdle()
        assertEquals(Screen.Overview, state.screen)
        assertEquals(1, reader.runs.size)
    }

    @Test
    fun readerState_isShownAsNfcStatus() = test {
        reader.state.value = ReaderState.AdapterOff
        advanceUntilIdle()
        assertEquals(NfcStatus.DISABLED, state.nfcStatus)

        reader.state.value = ReaderState.Active
        advanceUntilIdle()
        assertEquals(NfcStatus.AVAILABLE, state.nfcStatus)
    }

    @Test
    fun tagGoneBeforeExchange_waitsSilentlyForNextTap() = test {
        reader.nextResult = TagResult.TagGone(duringExchange = false)
        reader.tap()
        advanceUntilIdle()
        assertEquals(Screen.Discover, state.screen)
        assertNull(state.message)

        reader.tap()
        advanceUntilIdle()
        assertEquals(Screen.Overview, state.screen)
    }

    @Test
    fun tagGoneDuringExchange_tellsUser() = test {
        reader.nextResult = TagResult.TagGone(duringExchange = true)
        reader.tap()
        advanceUntilIdle()
        assertEquals(Screen.Discover, state.screen)
        assertEquals(uiText(R.string.error_tag_lost), state.message)
    }

    @Test
    fun failure_isReportedForAction() = test {
        reader.nextResult = TagResult.Failed(IOException("boom"))
        reader.tap()
        advanceUntilIdle()
        assertEquals(Screen.Discover, state.screen)
        assertEquals(R.string.msg_discover_failed, (state.message as? net.taler.xotpconfigurer.model.UiText.Res)?.id)
        assertEquals(1, reader.runs.size) // Not repeated on the same tag
    }

    @Test
    fun templateDetails_editsAndRenamesOnSave() = test {
        val original = SavedConfig("Shop", SavedConfigKind.GLOBAL, mapOf(GlobalField.MERCHANT_BACKEND.name to "https://a"))
        templates.save(original)
        templates.save(SavedConfig("Other", SavedConfigKind.GLOBAL, emptyMap()))
        advanceUntilIdle()
        viewModel.onOpenSavedConfigs()
        viewModel.onOpenTemplate(original)

        val backend = EditTarget.Global(GlobalField.MERCHANT_BACKEND)
        viewModel.onEditField(backend)
        viewModel.onSaveEdit(" https://b ")
        viewModel.onRenameTemplate("other") // Taken: ignored
        viewModel.onRenameTemplate("Shop 2")
        assertEquals(2, state.templateEditor?.changeCount)
        assertEquals(original, templates.configs.value.first { it.name == "Shop" }) // Not saved yet

        viewModel.onSaveTemplateChanges()
        advanceUntilIdle()
        assertEquals(
            listOf("Other", "Shop 2"),
            templates.configs.value.map { it.name }
        )
        assertEquals("https://b", templates.configs.value.last().values[GlobalField.MERCHANT_BACKEND.name])
        assertEquals(0, state.templateEditor?.changeCount)

        viewModel.onBack()
        assertEquals(Screen.SavedConfigs, state.screen)
        assertNull(state.templateEditor)
    }
}

/** [TagReader] whose taps and results are driven by the test. */
private class FakeTagReader : TagReader {
    override val state = MutableStateFlow<ReaderState>(ReaderState.Active)
    override val tag = MutableStateFlow<TagHandle?>(null)

    /** Returned by the next [withTag] instead of running the block. */
    var nextResult: TagResult<Nothing>? = null
    val runs = mutableListOf<TagHandle>()
    private var generation = 0L

    fun tap() {
        tag.value = TagHandle(SERIAL, ++generation)
    }

    override suspend fun <T> withTag(handle: TagHandle, block: (NfcTransport) -> T): TagResult<T> {
        runs += handle
        nextResult?.let { result ->
            nextResult = null
            if (result is TagResult.TagGone && tag.value == handle) tag.value = null
            return result
        }
        return TagResult.Success(block(FakeTransport()))
    }

    companion object {
        const val SERIAL = "E0:04:01:50:12:34:56:78"
    }
}

/** Device in configuration mode that exposes its discovery link but answers no request. */
private class FakeTransport : NfcTransport {
    override val serial = byteArrayOf(0xE0.toByte(), 0x04, 0x01, 0x50, 0x12, 0x34, 0x56, 0x78)
    override fun readDiscoveryUri() = "xotp-conf://DEV-01/5/0"
    override fun transceive(request: ByteArray): ByteArray = throw IOException("no answer")
    override fun readCurrentRecord(): NfcRecord? = null
    override fun close() = Unit
}

/** In-memory [SharedPreferences]. */
private class FakePreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values
    override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = defValues
    override fun getInt(key: String, defValue: Int) = values[key] as Int? ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue
    override fun contains(key: String) = key in values
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { values[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = this
        override fun putInt(key: String, value: Int) = apply { values[key] = value }
        override fun putLong(key: String, value: Long) = apply { values[key] = value }
        override fun putFloat(key: String, value: Float) = apply { values[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }
        override fun remove(key: String) = apply { values.remove(key) }
        override fun clear() = apply { values.clear() }
        override fun commit() = true
        override fun apply() = Unit
    }
}
