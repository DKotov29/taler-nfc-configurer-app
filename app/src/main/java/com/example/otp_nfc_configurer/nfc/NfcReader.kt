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

package com.example.otp_nfc_configurer.nfc

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.otp_nfc_configurer.toSerialString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Whether tags can be delivered right now. */
sealed interface ReaderState {
    /** The phone has no NFC hardware. */
    data object Unsupported : ReaderState
    /** NFC is switched off in the system settings. */
    data object AdapterOff : ReaderState
    /** The app is not in the foreground, reader mode is off. */
    data object Paused : ReaderState
    /** Reader mode is on: tags are delivered to the app. */
    data object Active : ReaderState
}

/**
 * Reference to the last discovered tag. [generation] grows with every discovery, so two handles of the same
 * physical tag are different and a handle that was invalidated is never used again.
 */
data class TagHandle(val serial: String, val generation: Long)

sealed interface TagResult<out T> {
    data class Success<T>(val value: T) : TagResult<T>

    /**
     * The tag is gone (left the field or its handle became invalid). [duringExchange] is true if at least one
     * request was answered before, i.e. the connection broke in the middle of an operation.
     */
    data class TagGone(val duringExchange: Boolean) : TagResult<Nothing>

    /** The tag is still there but the operation failed (protocol error, timeout, ...). */
    data class Failed(val error: Exception) : TagResult<Nothing>
}

/**
 * What the app logic needs to know about NFC. Android reports when a tag appears, but not when it is
 * removed: [tag] is the last tag seen and not known to be invalid, its presence is verified when it is used.
 */
interface TagReader {
    val state: StateFlow<ReaderState>

    /** Last discovered tag that may still be in the field, or null. */
    val tag: StateFlow<TagHandle?>

    /**
     * Runs [block] with a transport to the tag of [handle] (blocking I/O on a background thread, one operation
     * at a time). If the tag turns out to be gone, [tag] is cleared.
     */
    suspend fun <T> withTag(handle: TagHandle, block: (NfcTransport) -> T): TagResult<T>
}

/**
 * Single owner of the NFC reader mode. Observes the lifecycle of the activity it is added to: reader mode is
 * on exactly while the activity is resumed and NFC is enabled. Tag handles are invalidated whenever Android
 * invalidates them (reader mode or NFC switched off, new discovery).
 */
class NfcReader private constructor(
    private val context: Context,
    private val adapter: NfcAdapter?,
    private val openTransport: (Tag) -> NfcTransport = NdefTransport::open
) : TagReader, DefaultLifecycleObserver {

    private val _state = MutableStateFlow(
        when {
            adapter == null -> ReaderState.Unsupported
            !adapter.isEnabled -> ReaderState.AdapterOff
            else -> ReaderState.Paused
        }
    )
    override val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val _tag = MutableStateFlow<TagHandle?>(null)
    override val tag: StateFlow<TagHandle?> = _tag.asStateFlow()

    /** Guarded by `this`: the Android tag behind [_tag]. */
    private var current: Pair<TagHandle, Tag>? = null
    private var generation = 0L

    /** Main thread only. */
    private var resumedActivity: Activity? = null

    private val ioLock = Mutex()

    private val adapterStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF)) {
                NfcAdapter.STATE_ON -> resumedActivity?.let(::startReading)
                NfcAdapter.STATE_TURNING_OFF, NfcAdapter.STATE_OFF -> {
                    invalidateAll()
                    _state.value = ReaderState.AdapterOff
                }
            }
        }
    }

    /** New tag from reader mode (any thread) or from the intent that launched the app. */
    fun offer(tag: Tag) = synchronized(this) {
        val handle = TagHandle(serial = tag.id.toSerialString(), generation = ++generation)
        current = handle to tag
        _tag.value = handle
    }

    override fun onResume(owner: LifecycleOwner) {
        val activity = owner as? Activity ?: return
        resumedActivity = activity
        ContextCompat.registerReceiver(
            context, adapterStateReceiver,
            IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        startReading(activity)
    }

    override fun onPause(owner: LifecycleOwner) {
        val activity = resumedActivity ?: return
        resumedActivity = null
        context.unregisterReceiver(adapterStateReceiver)
        if (_state.value == ReaderState.Active) {
            adapter?.disableReaderMode(activity)
            _state.value = ReaderState.Paused
        }
        // Handles of disabled reader mode are out of date.
        invalidateAll()
    }

    private fun startReading(activity: Activity) {
        val adapter = adapter ?: return
        if (!adapter.isEnabled) {
            _state.value = ReaderState.AdapterOff
            return
        }
        val options = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, PRESENCE_CHECK_DELAY_MS) }
        adapter.enableReaderMode(activity, ::offer, READER_FLAGS, options)
        _state.value = ReaderState.Active
    }

    override suspend fun <T> withTag(handle: TagHandle, block: (NfcTransport) -> T): TagResult<T> =
        ioLock.withLock {
            withContext(Dispatchers.IO) {
                val tag = synchronized(this@NfcReader) { current?.takeIf { it.first == handle }?.second }
                if (tag == null || !NdefTransport.isPresent(tag)) {
                    invalidate(handle)
                    return@withContext TagResult.TagGone(duringExchange = false)
                }
                var exchanges = 0
                try {
                    openTransport(tag).use { transport ->
                        TagResult.Success(block(CountingTransport(transport) { exchanges++ }))
                    }
                } catch (e: Exception) {
                    // Android reports a removed tag as anything from IOException to SecurityException:
                    // only a fresh connection attempt tells whether it is still there.
                    if (NdefTransport.isPresent(tag)) {
                        TagResult.Failed(e)
                    } else {
                        invalidate(handle)
                        TagResult.TagGone(duringExchange = exchanges > 0)
                    }
                }
            }
        }

    private fun invalidate(handle: TagHandle) = synchronized(this) {
        if (current?.first == handle) {
            current = null
            _tag.value = null
        }
    }

    private fun invalidateAll() = synchronized(this) {
        current = null
        _tag.value = null
    }

    /** Counts answered requests to tell a stale tag from a connection lost in the middle of an operation. */
    private class CountingTransport(
        private val inner: NfcTransport,
        private val onExchange: () -> Unit
    ) : NfcTransport by inner {
        override fun transceive(request: ByteArray): ByteArray = inner.transceive(request).also { onExchange() }
    }

    companion object {
        private const val PRESENCE_CHECK_DELAY_MS = 250
        private const val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V

        @Volatile
        private var instance: NfcReader? = null

        /** Process-wide reader: there is one NFC adapter, shared by the activity and the view model. */
        fun get(context: Context): NfcReader = instance ?: synchronized(this) {
            instance ?: context.applicationContext.let { app ->
                NfcReader(app, NfcAdapter.getDefaultAdapter(app))
            }.also { instance = it }
        }
    }
}
