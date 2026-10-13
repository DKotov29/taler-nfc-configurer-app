package com.example.otp_nfc_configurer.model

import androidx.annotation.StringRes
import com.example.otp_nfc_configurer.R
import com.example.otp_nfc_configurer.protocol.Algorithm
import com.example.otp_nfc_configurer.protocol.Protocol

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
            val bytes = Base32.decode(input) ?: return FieldInput.Invalid(uiText(R.string.error_base32))
            return when {
                bytes.isEmpty() -> FieldInput.Invalid(uiText(R.string.error_secret_empty))
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

    /** RFC 4648 decoding; ignores whitespace, dashes, padding and case. Returns null on invalid input. */
    fun decode(input: String): ByteArray? {
        val clean = input.uppercase().filterNot { it.isWhitespace() || it == '-' || it == '=' }
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
}
