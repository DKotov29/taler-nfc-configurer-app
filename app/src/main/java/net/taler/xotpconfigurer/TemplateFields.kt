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

import androidx.annotation.StringRes
import net.taler.xotpconfigurer.data.SavedConfig
import net.taler.xotpconfigurer.data.SavedConfigKind
import net.taler.xotpconfigurer.model.FieldInput
import net.taler.xotpconfigurer.model.GlobalConfig
import net.taler.xotpconfigurer.model.GlobalField
import net.taler.xotpconfigurer.model.InputKind
import net.taler.xotpconfigurer.model.SlotConfig
import net.taler.xotpconfigurer.model.SlotField
import net.taler.xotpconfigurer.model.UiText
import net.taler.xotpconfigurer.model.uiText
import net.taler.xotpconfigurer.protocol.Algorithm

/**
 * Fields stored by templates of this kind, in display order. Never the secret, and never the device ID,
 * which identifies a single device.
 */
val SavedConfigKind.fields: List<EditTarget>
    get() = when (this) {
        SavedConfigKind.SLOT -> SlotField.readable.map { EditTarget.Slot(it) }
        SavedConfigKind.GLOBAL -> listOf(GlobalField.MERCHANT_BACKEND, GlobalField.DEVICE_DESCRIPTION).map { EditTarget.Global(it) }
    }

/** Key of the field in [SavedConfig.values]. */
val EditTarget.key: String
    get() = when (this) {
        is EditTarget.Slot -> field.name
        is EditTarget.Global -> field.name
    }

@get:StringRes
val EditTarget.label: Int
    get() = when (this) {
        is EditTarget.Slot -> field.label
        is EditTarget.Global -> field.label
    }

val EditTarget.hint: UiText
    get() = when (this) {
        is EditTarget.Slot -> field.hint
        is EditTarget.Global -> field.hint
    }

val EditTarget.inputKind: InputKind
    get() = when (this) {
        is EditTarget.Slot -> field.kind
        is EditTarget.Global -> InputKind.TEXT
    }

/** Cuts [input] to what the field can hold. */
fun EditTarget.limit(input: String): String = when (this) {
    is EditTarget.Slot -> field.limit(input)
    is EditTarget.Global -> field.limit(input)
}

/** Validates [input] and returns it the way the device would store it (e.g. trimmed, currency upper case). */
fun EditTarget.normalize(input: String): FieldInput<String> = when (this) {
    is EditTarget.Slot -> when (val result = field.apply(TEMPLATE_SLOT, input)) {
        is FieldInput.Valid -> FieldInput.Valid(field.raw(result.value))
        is FieldInput.Invalid -> result
    }
    is EditTarget.Global -> when (val result = field.apply(TEMPLATE_GLOBAL, input)) {
        is FieldInput.Valid -> FieldInput.Valid(field.raw(result.value))
        is FieldInput.Invalid -> result
    }
}

/** Human readable form of a stored [raw] value; invalid values are shown as they are. */
fun EditTarget.display(raw: String?): UiText {
    if (raw.isNullOrEmpty()) return UiText.Raw("—")
    if (this !is EditTarget.Slot) return UiText.Raw(raw)
    if (field == SlotField.ALGORITHM) return UiText.Raw(raw.toIntOrNull()?.let(Algorithm::label) ?: raw)
    return when (val result = field.apply(TEMPLATE_SLOT, raw)) {
        is FieldInput.Valid -> field.display(result.value)
        is FieldInput.Invalid -> UiText.Raw(raw)
    }
}

/** Error of a new template name, or null if [name] can be used. */
fun templateNameError(name: String, taken: Collection<String>): UiText? = when {
    name.isBlank() -> uiText(R.string.error_not_empty)
    taken.any { it.equals(name.trim(), ignoreCase = true) } -> uiText(R.string.template_name_taken)
    else -> null
}

private val TEMPLATE_SLOT = SlotConfig(
    interval = 30, digits = 8, algorithm = Algorithm.XTOTP_SHA1.code,
    slotName = "", currency = "", fraction = 100, merchantTemplate = ""
)

private val TEMPLATE_GLOBAL = GlobalConfig(deviceId = "", merchantBackend = "", deviceDescription = "", activeSlotIndex = 0)
