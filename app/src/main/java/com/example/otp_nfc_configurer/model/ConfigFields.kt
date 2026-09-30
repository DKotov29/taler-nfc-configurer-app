package com.example.otp_nfc_configurer.model

import com.example.otp_nfc_configurer.protocol.Algorithm
import com.example.otp_nfc_configurer.protocol.Protocol

/** Result of parsing user input for a field. */
sealed interface FieldInput<out T> {
    data class Valid<T>(val value: T) : FieldInput<T>
    data class Invalid(val reason: String) : FieldInput<Nothing>
}

enum class InputKind { TEXT, NUMBER, ALGORITHM, SECRET }

/** Editable slot parameters in the order shown on the Slot Details screen. */
enum class SlotField(val param: Int, val label: String, val kind: InputKind, val hint: String) {
    SLOT_NAME(Protocol.Param.SLOT_NAME, "Slot Name", InputKind.TEXT, "Up to ${Protocol.Size.SLOT_NAME - 1} bytes."),
    ALGORITHM(Protocol.Param.ALGORITHM, "Algorithm", InputKind.ALGORITHM, ""),
    DIGITS(Protocol.Param.DIGITS, "Code Length", InputKind.NUMBER,
        "Allowed range: ${Protocol.Limits.MIN_DIGITS}–${Protocol.Limits.MAX_DIGITS} digits"),
    INTERVAL(Protocol.Param.INTERVAL, "Interval", InputKind.NUMBER,
        "Allowed range: ${Protocol.Limits.MIN_INTERVAL}–${Protocol.Limits.MAX_INTERVAL} seconds"),
    CURRENCY(Protocol.Param.CURRENCY, "Currency", InputKind.TEXT, "Up to ${Protocol.Size.CURRENCY - 1} bytes, e.g. KUDOS."),
    FRACTION(Protocol.Param.FRACTION, "Fraction", InputKind.NUMBER,
        "Power of ten up to ${Protocol.Limits.MAX_FRACTION}, e.g. 100 for cents."),
    MERCHANT_TEMPLATE(Protocol.Param.MERCHANT_TEMPLATE, "Merchant Template", InputKind.TEXT,
        "Up to ${Protocol.Size.MERCHANT_TEMPLATE - 1} bytes."),
    SECRET(Protocol.Param.SECRET, "Secret Key", InputKind.SECRET,
        "Base32 encoded, up to ${Protocol.Size.SECRET} bytes. Write-only.");

    fun display(config: SlotConfig): String = when (this) {
        SLOT_NAME -> config.slotName.ifEmpty { "—" }
        ALGORITHM -> Algorithm.label(config.algorithm)
        DIGITS -> "${config.digits} Digits"
        INTERVAL -> "${config.interval} Seconds"
        CURRENCY -> config.currency.ifEmpty { "—" }
        FRACTION -> config.fraction.toString()
        MERCHANT_TEMPLATE -> config.merchantTemplate.ifEmpty { "—" }
        SECRET -> "•••••••• (Protected)"
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
            ?: FieldInput.Invalid("Unknown algorithm")
        DIGITS -> validateRange(input, Protocol.Limits.MIN_DIGITS, Protocol.Limits.MAX_DIGITS) { config.copy(digits = it) }
        INTERVAL -> validateRange(input, Protocol.Limits.MIN_INTERVAL, Protocol.Limits.MAX_INTERVAL) { config.copy(interval = it) }
        FRACTION -> {
            val value = input.trim().toLongOrNull()
            if (value == null || !isPowerOfTen(value) || value > Protocol.Limits.MAX_FRACTION) {
                FieldInput.Invalid("Must be a power of ten between 1 and ${Protocol.Limits.MAX_FRACTION}")
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
            val bytes = Base32.decode(input) ?: return FieldInput.Invalid("Not a valid Base32 string")
            return when {
                bytes.isEmpty() -> FieldInput.Invalid("Secret must not be empty")
                bytes.size > Protocol.Size.SECRET -> FieldInput.Invalid("Secret exceeds ${Protocol.Size.SECRET} bytes")
                else -> FieldInput.Valid(bytes)
            }
        }
    }
}

enum class GlobalField(val param: Int, val label: String, val hint: String) {
    DEVICE_ID(Protocol.Param.DEVICE_ID, "Device ID", "Up to ${Protocol.Size.DEVICE_ID - 1} bytes."),
    MERCHANT_BACKEND(Protocol.Param.MERCHANT_BACKEND, "Merchant Backend", "Up to ${Protocol.Size.MERCHANT_BACKEND - 1} bytes."),
    DEVICE_DESCRIPTION(Protocol.Param.DEVICE_DESCRIPTION, "Device Description", "Up to ${Protocol.Size.DEVICE_DESCRIPTION - 1} bytes.");

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
        !allowEmpty && value.isEmpty() -> FieldInput.Invalid("Must not be empty")
        value.any { it == '\u0000' || it == '\n' || it == '\r' } -> FieldInput.Invalid("Contains invalid characters")
        value.toByteArray(Charsets.UTF_8).size >= size -> FieldInput.Invalid("At most ${size - 1} bytes allowed")
        else -> FieldInput.Valid(update(value))
    }
}

private fun <T> validateRange(input: String, min: Int, max: Int, update: (Int) -> T): FieldInput<T> {
    val value = input.trim().toIntOrNull()
    return if (value == null || value !in min..max) FieldInput.Invalid("Allowed range: $min–$max")
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
