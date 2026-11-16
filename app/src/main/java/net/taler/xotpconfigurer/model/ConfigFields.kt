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

package net.taler.xotpconfigurer.model

import androidx.annotation.StringRes
import net.taler.xotpconfigurer.R
import net.taler.xotpconfigurer.protocol.Algorithm
import net.taler.xotpconfigurer.protocol.Protocol

/** Result of parsing user input for a field. */
sealed interface FieldInput<out T> {
    data class Valid<T>(val value: T) : FieldInput<T>
    data class Invalid(val reason: UiText) : FieldInput<Nothing>
}

enum class InputKind { TEXT, NUMBER, ALGORITHM, SECRET }

/** Editable slot parameters in the order shown on the Slot Details screen. */
enum class SlotField(val param: Int, @StringRes val label: Int, val kind: InputKind, val hint: UiText) {
    SLOT_NAME(Protocol.Param.SLOT_NAME, R.string.field_slot_name, InputKind.TEXT,
        uiText(R.string.hint_max_bytes, Protocol.Size.SLOT_NAME - 1)),
    ALGORITHM(Protocol.Param.ALGORITHM, R.string.field_algorithm, InputKind.ALGORITHM, UiText.Raw("")),
    DIGITS(Protocol.Param.DIGITS, R.string.field_digits, InputKind.NUMBER,
        uiText(R.string.hint_digits, Protocol.Limits.MIN_DIGITS, Protocol.Limits.MAX_DIGITS)),
    INTERVAL(Protocol.Param.INTERVAL, R.string.field_interval, InputKind.NUMBER,
        uiText(R.string.hint_interval, Protocol.Limits.MIN_INTERVAL, Protocol.Limits.MAX_INTERVAL)),
    CURRENCY(Protocol.Param.CURRENCY, R.string.field_currency, InputKind.TEXT,
        uiText(R.string.hint_currency, Protocol.Size.CURRENCY - 1)),
    FRACTION(Protocol.Param.FRACTION, R.string.field_fraction, InputKind.NUMBER,
        uiText(R.string.hint_fraction, Protocol.Limits.MAX_FRACTION)),
    MERCHANT_TEMPLATE(Protocol.Param.MERCHANT_TEMPLATE, R.string.field_merchant_template, InputKind.TEXT,
        uiText(R.string.hint_max_bytes, Protocol.Size.MERCHANT_TEMPLATE - 1)),
    SECRET(Protocol.Param.SECRET, R.string.field_secret, InputKind.SECRET,
        uiText(R.string.hint_secret, Protocol.Size.SECRET));

    fun display(config: SlotConfig): UiText = when (this) {
        SLOT_NAME -> UiText.Raw(config.slotName.ifEmpty { "—" })
        ALGORITHM -> UiText.Raw(Algorithm.label(config.algorithm))
        DIGITS -> uiPlural(R.plurals.value_digits, config.digits)
        INTERVAL -> uiPlural(R.plurals.value_seconds, config.interval)
        CURRENCY -> UiText.Raw(config.currency.ifEmpty { "—" })
        FRACTION -> UiText.Raw(config.fraction.toString())
        MERCHANT_TEMPLATE -> UiText.Raw(config.merchantTemplate.ifEmpty { "—" })
        SECRET -> uiText(R.string.value_secret_protected)
    }

    /** Raw editable text of the field. */
    fun raw(config: SlotConfig): String = when (this) {
        SLOT_NAME -> config.slotName
        ALGORITHM -> config.algorithm.toString()
        DIGITS -> config.digits.toString()
        INTERVAL -> config.interval.toString()
        CURRENCY -> config.currency
        FRACTION -> config.fraction.toString()
        MERCHANT_TEMPLATE -> config.merchantTemplate
        SECRET -> ""
    }

    /** Valid values of numeric fields with a stepper, null otherwise. */
    val range: IntRange?
        get() = when (this) {
            DIGITS -> Protocol.Limits.MIN_DIGITS..Protocol.Limits.MAX_DIGITS
            INTERVAL -> Protocol.Limits.MIN_INTERVAL..Protocol.Limits.MAX_INTERVAL
            else -> null
        }

    /** Cuts [input] to what the field can hold, so longer values cannot be typed at all. */
    fun limit(input: String): String = when (this) {
        SLOT_NAME -> InputLimit.bytes(input, Protocol.Size.SLOT_NAME - 1)
        CURRENCY -> InputLimit.bytes(input, Protocol.Size.CURRENCY - 1)
        MERCHANT_TEMPLATE -> InputLimit.bytes(input, Protocol.Size.MERCHANT_TEMPLATE - 1)
        DIGITS -> InputLimit.digits(input, Protocol.Limits.MAX_DIGITS.toString().length)
        INTERVAL -> InputLimit.digits(input, Protocol.Limits.MAX_INTERVAL.toString().length)
        FRACTION -> InputLimit.digits(input, Protocol.Limits.MAX_FRACTION.toString().length)
        SECRET -> InputLimit.base32(input, Base32.encodedLength(Protocol.Size.SECRET))
        ALGORITHM -> input
    }

    /** Applies [input] to [config]. Not applicable to [SECRET], see [parseSecret]. */
    fun apply(config: SlotConfig, input: String): FieldInput<SlotConfig> = when (this) {
        SLOT_NAME -> validateString(input, Protocol.Size.SLOT_NAME) { config.copy(slotName = it) }
        CURRENCY -> validateString(input.uppercase(), Protocol.Size.CURRENCY, allowEmpty = false) { config.copy(currency = it) }
        MERCHANT_TEMPLATE -> validateString(input, Protocol.Size.MERCHANT_TEMPLATE) { config.copy(merchantTemplate = it) }
        ALGORITHM -> input.toIntOrNull()?.let(Algorithm::fromCode)
            ?.let { FieldInput.Valid(config.copy(algorithm = it.code)) }
            ?: FieldInput.Invalid(uiText(R.string.error_unknown_algorithm))
        DIGITS -> validateRange(input, Protocol.Limits.MIN_DIGITS, Protocol.Limits.MAX_DIGITS) { config.copy(digits = it) }
        INTERVAL -> validateRange(input, Protocol.Limits.MIN_INTERVAL, Protocol.Limits.MAX_INTERVAL) { config.copy(interval = it) }
        FRACTION -> {
            val value = input.trim().toLongOrNull()
            if (value == null || !isPowerOfTen(value) || value > Protocol.Limits.MAX_FRACTION) {
                FieldInput.Invalid(uiText(R.string.error_fraction, Protocol.Limits.MAX_FRACTION))
            } else {
                FieldInput.Valid(config.copy(fraction = value))
            }
        }
        SECRET -> throw IllegalArgumentException("Use parseSecret for the secret")
    }

    companion object {
        /** Fields that can be read back and compared against the device state. */
        val readable = entries.filter { it != SECRET }

        fun parseSecret(input: String): FieldInput<ByteArray> {
            if (Base32.significant(input).isEmpty()) return FieldInput.Invalid(uiText(R.string.error_secret_empty))
            val bytes = Base32.decode(input) ?: return FieldInput.Invalid(uiText(R.string.error_base32))
            return when {
                bytes.isEmpty() -> FieldInput.Invalid(uiText(R.string.error_base32))
                bytes.size > Protocol.Size.SECRET -> FieldInput.Invalid(uiText(R.string.error_secret_too_long, Protocol.Size.SECRET))
                else -> FieldInput.Valid(bytes)
            }
        }
    }
}

enum class GlobalField(val param: Int, @StringRes val label: Int, val hint: UiText) {
    DEVICE_ID(Protocol.Param.DEVICE_ID, R.string.field_device_id,
        uiText(R.string.hint_max_bytes, Protocol.Size.DEVICE_ID - 1)),
    MERCHANT_BACKEND(Protocol.Param.MERCHANT_BACKEND, R.string.field_merchant_backend,
        uiText(R.string.hint_max_bytes, Protocol.Size.MERCHANT_BACKEND - 1)),
    DEVICE_DESCRIPTION(Protocol.Param.DEVICE_DESCRIPTION, R.string.field_device_description,
        uiText(R.string.hint_max_bytes, Protocol.Size.DEVICE_DESCRIPTION - 1));

    fun raw(config: GlobalConfig): String = when (this) {
        DEVICE_ID -> config.deviceId
        MERCHANT_BACKEND -> config.merchantBackend
        DEVICE_DESCRIPTION -> config.deviceDescription
    }

    /** Cuts [input] to what the field can hold, so longer values cannot be typed at all. */
    fun limit(input: String): String = when (this) {
        DEVICE_ID -> InputLimit.bytes(input, Protocol.Size.DEVICE_ID - 1)
        MERCHANT_BACKEND -> InputLimit.bytes(input, Protocol.Size.MERCHANT_BACKEND - 1)
        DEVICE_DESCRIPTION -> InputLimit.bytes(input, Protocol.Size.DEVICE_DESCRIPTION - 1)
    }

    fun apply(config: GlobalConfig, input: String): FieldInput<GlobalConfig> = when (this) {
        DEVICE_ID -> validateString(input, Protocol.Size.DEVICE_ID, allowEmpty = false) { config.copy(deviceId = it) }
        MERCHANT_BACKEND -> validateString(input, Protocol.Size.MERCHANT_BACKEND) { config.copy(merchantBackend = it) }
        DEVICE_DESCRIPTION -> validateString(input, Protocol.Size.DEVICE_DESCRIPTION) { config.copy(deviceDescription = it) }
    }
}

private fun <T> validateString(
    input: String,
    size: Int,
    allowEmpty: Boolean = true,
    update: (String) -> T
): FieldInput<T> {
    val value = input.trim()
    return when {
        !allowEmpty && value.isEmpty() -> FieldInput.Invalid(uiText(R.string.error_not_empty))
        value.any { it == '\u0000' || it == '\n' || it == '\r' } -> FieldInput.Invalid(uiText(R.string.error_invalid_chars))
        value.toByteArray(Charsets.UTF_8).size >= size -> FieldInput.Invalid(uiText(R.string.error_max_bytes, size - 1))
        else -> FieldInput.Valid(update(value))
    }
}

private fun <T> validateRange(input: String, min: Int, max: Int, update: (Int) -> T): FieldInput<T> {
    val value = input.trim().toIntOrNull()
    return if (value == null || value !in min..max) FieldInput.Invalid(uiText(R.string.error_range, min, max))
    else FieldInput.Valid(update(value))
}

private fun isPowerOfTen(value: Long): Boolean {
    var v = value
    if (v < 1) return false
    while (v % 10 == 0L) v /= 10
    return v == 1L
}

object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** Characters without separators (whitespace, dashes) and padding. */
    fun significant(input: String): String = input.filterNot(::isIgnored)

    fun isIgnored(c: Char): Boolean = c.isWhitespace() || c == '-' || c == '='

    /** Number of Base32 characters (without padding) encoding [bytes] bytes. */
    fun encodedLength(bytes: Int): Int = (bytes * 8 + 4) / 5

    /**
     * RFC 4648 decoding; ignores whitespace, dashes, padding and case. Returns null on invalid input,
     * including lengths no byte sequence encodes to (1, 3 or 6 characters in the last group).
     */
    fun decode(input: String): ByteArray? {
        val clean = significant(input).uppercase()
        if (clean.length % 8 in INVALID_TAIL_LENGTHS) return null
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val index = ALPHABET.indexOf(c)
            if (index < 0) return null
            buffer = (buffer shl 5) or index
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    private val INVALID_TAIL_LENGTHS = setOf(1, 3, 6)
}

/** Truncation helpers that keep typed input within a field's capacity. */
object InputLimit {
    /** Longest prefix of [input] whose UTF-8 encoding fits into [maxBytes], never splitting a character. */
    fun bytes(input: String, maxBytes: Int): String {
        var used = 0
        var end = 0
        while (end < input.length) {
            val codePoint = input.codePointAt(end)
            val size = when {
                codePoint < 0x80 -> 1
                codePoint < 0x800 -> 2
                codePoint < 0x10000 -> 3
                else -> 4
            }
            if (used + size > maxBytes) break
            used += size
            end += Character.charCount(codePoint)
        }
        return input.substring(0, end)
    }

    /** Only digits, at most [maxLength] of them. */
    fun digits(input: String, maxLength: Int): String = input.filter { it in '0'..'9' }.take(maxLength)

    /** At most [maxChars] Base32 characters; separators and padding do not count. */
    fun base32(input: String, maxChars: Int): String {
        var count = 0
        val out = StringBuilder()
        for (c in input) {
            if (!Base32.isIgnored(c)) {
                if (count == maxChars) break
                count++
            }
            out.append(c)
        }
        return out.toString()
    }
}

/**
 * New text field value after the user changed [old] to [proposed]: accepted when it fits, otherwise cut
 * to the field's capacity if that still adds something (e.g. pasting), else the change is rejected.
 */
fun limitedInput(old: String, proposed: String, limit: (String) -> String): String {
    val limited = limit(proposed)
    return when {
        limited == proposed -> proposed
        proposed.length < old.length -> proposed // Deleting is always possible
        limited.length > old.length && limited.startsWith(old) -> limited
        else -> old
    }
}
