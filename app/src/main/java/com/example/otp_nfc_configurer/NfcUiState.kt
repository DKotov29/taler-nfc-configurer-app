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

package com.example.otp_nfc_configurer

import com.example.otp_nfc_configurer.data.AppTheme
import com.example.otp_nfc_configurer.data.SavedConfig
import com.example.otp_nfc_configurer.data.SavedConfigKind
import com.example.otp_nfc_configurer.debug.DebugState
import com.example.otp_nfc_configurer.debug.DebugTapMode
import com.example.otp_nfc_configurer.model.DeviceInfo
import com.example.otp_nfc_configurer.model.FieldInput
import com.example.otp_nfc_configurer.model.GlobalConfig
import com.example.otp_nfc_configurer.model.GlobalField
import com.example.otp_nfc_configurer.model.SlotConfig
import com.example.otp_nfc_configurer.model.SlotField
import com.example.otp_nfc_configurer.model.SlotSummary
import com.example.otp_nfc_configurer.model.UiText

enum class NfcStatus { AVAILABLE, DISABLED, UNSUPPORTED }

/** Operation executed on the next NFC tap. */
sealed interface TapAction {
    data object Discover : TapAction
    data class LoadSlotNames(val page: Int) : TapAction
    data class LoadSlot(val index: Int) : TapAction
    data class ApplySlot(val index: Int) : TapAction
    data class SetupSlot(val index: Int) : TapAction
    data object ApplyGlobal : TapAction
    /** Debug console: send the composed packet as-is. */
    data object DebugSend : TapAction
    /** Debug console: only read the current tag content. */
    data object DebugRead : TapAction
}

sealed interface Screen {
    data object Discover : Screen
    data object Overview : Screen
    data class SlotDetails(val index: Int) : Screen
    data class SetupSlot(val index: Int) : Screen
    /** "Tap to load / apply" prompt that waits for the NFC tap executing [action]. */
    data class TapPrompt(val action: TapAction) : Screen
    data class Failure(val title: UiText, val message: UiText, val retry: TapAction) : Screen
    data class DeviceMismatch(val expectedSerial: String, val detectedSerial: String, val retry: TapAction) : Screen
    data object Settings : Screen
    data object SavedConfigs : Screen
    /** A template opened from [SavedConfigs], see [NfcUiState.templateEditor]. */
    data object TemplateDetails : Screen
    /** "Save Template" flow, see [NfcUiState.templateSave]. */
    data object SaveTemplate : Screen
    data object Debug : Screen
}

enum class LoadState { NOT_LOADED, LOADING, LOADED, FAILED }

data class DeviceSession(
    val serial: String,
    val info: DeviceInfo,
    val global: GlobalConfig? = null,
    val slots: Map<Int, SlotSummary> = emptyMap(),
    val page: Int = 0,
    val pageLoad: LoadState = LoadState.NOT_LOADED
) {
    val pageCount: Int get() = maxOf(1, (info.slotCount + PAGE_SIZE - 1) / PAGE_SIZE)
    val pageRange: IntRange get() = pageRange(page)

    fun pageRange(page: Int): IntRange =
        (page * PAGE_SIZE) until minOf((page + 1) * PAGE_SIZE, info.slotCount)

    fun isPageLoaded(page: Int): Boolean = pageRange(page).all { it in slots }

    companion object {
        const val PAGE_SIZE = 5
    }
}

data class GlobalEditor(val original: GlobalConfig, val draft: GlobalConfig) {
    val changedFields: List<GlobalField>
        get() = GlobalField.entries.filter { it.raw(original) != it.raw(draft) }
}

data class SlotEditor(
    val index: Int,
    val original: SlotConfig,
    val draft: SlotConfig,
    val newSecret: ByteArray? = null,
    val makeActive: Boolean = false
) {
    val changedFields: List<SlotField>
        get() = SlotField.readable.filter { it.raw(original) != it.raw(draft) } +
                listOfNotNull(SlotField.SECRET.takeIf { newSecret != null })

    val changeCount: Int get() = changedFields.size + if (makeActive) 1 else 0
}

/** Raw form input of the "Setup Blank Slot" screen. */
data class SetupDraft(
    val index: Int,
    val values: Map<SlotField, String>,
    val errors: Map<SlotField, UiText> = emptyMap()
)

/** Template opened from the settings; changes are kept in [draft] until saved. */
data class TemplateEditor(val original: SavedConfig, val draft: SavedConfig) {
    val changedKeys: Set<String>
        get() = draft.kind.fields.map { it.key }.filter { original.values[it].orEmpty() != draft.values[it].orEmpty() }.toSet()

    val renamed: Boolean get() = original.name != draft.name

    val changeCount: Int get() = changedKeys.size + if (renamed) 1 else 0
}

enum class MergeSource { CURRENT, TEMPLATE, CUSTOM }

/** Which value of a differing field ends up in the template; [custom] is the user's own input. */
data class MergeChoice(
    val source: MergeSource = MergeSource.CURRENT,
    val custom: String = "",
    val error: UiText? = null
)

/**
 * "Save Template" flow: the [values] of the screen it was started from ([returnTo]) are saved as a new
 * template, or merged into the existing [target] field by field.
 */
data class TemplateSave(
    val kind: SavedConfigKind,
    val values: Map<String, String>,
    val returnTo: Screen,
    val target: SavedConfig? = null,
    val choices: Map<String, MergeChoice> = emptyMap()
) {
    /** Fields whose value differs between [values] and [target]. */
    val differing: List<EditTarget>
        get() = target?.let { t -> kind.fields.filter { values[it.key].orEmpty() != t.values[it.key].orEmpty() } }.orEmpty()

    /** Values to store in [target] according to [choices], and the errors of invalid custom values by key. */
    fun merge(): Pair<Map<String, String>, Map<String, UiText>> {
        val merged = kind.fields.associate { it.key to values[it.key].orEmpty() }.toMutableMap()
        val errors = mutableMapOf<String, UiText>()
        for (field in differing) {
            val choice = choices[field.key] ?: MergeChoice()
            when (choice.source) {
                MergeSource.CURRENT -> Unit
                MergeSource.TEMPLATE -> merged[field.key] = target?.values?.get(field.key).orEmpty()
                MergeSource.CUSTOM -> when (val result = field.normalize(choice.custom)) {
                    is FieldInput.Valid -> merged[field.key] = result.value
                    is FieldInput.Invalid -> errors[field.key] = result.reason
                }
            }
        }
        return merged to errors
    }
}

sealed interface EditTarget {
    data class Slot(val field: SlotField) : EditTarget
    data class Global(val field: GlobalField) : EditTarget
}

data class NfcUiState(
    val nfcStatus: NfcStatus = NfcStatus.AVAILABLE,
    val screen: Screen = Screen.Discover,
    val appTheme: AppTheme = AppTheme.SYSTEM,
    val isCommunicating: Boolean = false,
    val session: DeviceSession? = null,
    val globalEditor: GlobalEditor? = null,
    val slotEditor: SlotEditor? = null,
    val setupDraft: SetupDraft? = null,
    val editing: EditTarget? = null,
    val editError: UiText? = null,
    val savedConfigs: List<SavedConfig> = emptyList(),
    val templateEditor: TemplateEditor? = null,
    val templateSave: TemplateSave? = null,
    val debug: DebugState = DebugState(),
    val message: UiText? = null
) {
    /** Operation the next NFC tap triggers on the current screen, or null if taps are ignored. */
    val armedAction: TapAction?
        get() = when (val s = screen) {
            Screen.Discover -> TapAction.Discover
            is Screen.TapPrompt -> s.action
            Screen.Overview -> session?.takeIf { it.pageLoad != LoadState.LOADED }
                ?.let { TapAction.LoadSlotNames(it.page) }
            Screen.Debug -> when (debug.armed) {
                DebugTapMode.SEND -> TapAction.DebugSend
                DebugTapMode.READ -> TapAction.DebugRead
                null -> null
            }
            else -> null
        }
}
