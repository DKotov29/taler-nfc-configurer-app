package com.example.otp_nfc_configurer.protocol

/**
 * Constants of the stateless xOTP configuration protocol (thesis, section "Protocol Architecture and Framing").
 * Firmware-derived values reference their C counterpart in taler-xotp_fw.
 */
object Protocol {
    const val VERSION = 1

    const val URI_SCHEME = "xotp-conf://"

    /** 4-bit operation codes, GET = 0b00xx, SET = 0b01xx. */
    object OpCode {
        const val GET_DEVICE_INFO = 0b0000
        const val GET_SLOT = 0b0001
        const val GET_GLOBAL = 0b0010
        const val GET_SLOT_MEMBERS = 0b0011
        const val SET_SLOT = 0b0101
        const val SET_GLOBAL = 0b0110
    }

    object Status {
        const val OK = 0x55
        const val VERSION_MISMATCH_MASK = 0xE0
        const val INVALID_LENGTH = 0xF1
        const val CHECKSUM = 0xF2
        const val UNSUPPORTED_CMD = 0xF3
        const val INVALID_SLOT = 0xF4
        const val INVALID_PARAM = 0xF5
        const val INVALID_VALUE = 0xF6
    }

    /** Parameter identifiers; 0x00 addresses all members of the group. */
    object Param {
        const val ALL = 0x00

        const val SECRET = 0x01
        const val INTERVAL = 0x02
        const val DIGITS = 0x03
        const val ALGORITHM = 0x04
        const val SLOT_NAME = 0x05
        const val CURRENCY = 0x06
        const val FRACTION = 0x07
        const val MERCHANT_TEMPLATE = 0x08

        const val DEVICE_ID = 0x10
        const val MERCHANT_BACKEND = 0x11
        const val DEVICE_DESCRIPTION = 0x12
        const val ACTIVE_SLOT_INDEX = 0x13
    }

    /** Buffer sizes in bytes, including the terminating 0x00 for strings. */
    object Size {
        const val SECRET = 20            // TALER_SECRET_LEN
        const val SLOT_NAME = 12         // XTOTP_SLOT_NAME_SIZE
        const val CURRENCY = 12          // TALER_CURRENCY_LEN
        const val MERCHANT_TEMPLATE = 32 // XTOTP_MERCHANT_TEMPLATE_SIZE
        const val DEVICE_ID = 12         // XTOTP_DEVICE_ID_SIZE
        const val MERCHANT_BACKEND = 96  // XTOTP_MERCHANT_BACKEND_SIZE
        const val DEVICE_DESCRIPTION = 64 // XTOTP_DEVICE_DESC_SIZE
    }

    /** Value ranges accepted by the application. */
    object Limits {
        const val MIN_DIGITS = 4
        const val MAX_DIGITS = 8 // MAX_PASSCODE_LENGTH_DIGITS
        const val MIN_INTERVAL = 15
        const val MAX_INTERVAL = 120
        const val MAX_FRACTION = 100_000_000L // TALER_AMOUNT_FRAC_BASE
    }

    /** GET_SLOT_MEMBERS slot-name markers. */
    const val MEMBER_SEPARATOR = '\n'
    const val NAMELESS_CONFIGURED = "\r"
}

/** crypto_algorithms from xtotpCryptoHandler.h. */
enum class Algorithm(val code: Int, val label: String, val isExtended: Boolean) {
    TOTP_SHA1(0x01, "ALG_TOTP_SHA1", isExtended = false),
    XTOTP_SHA1(0x11, "ALG_XTOTP_SHA1", isExtended = true);

    companion object {
        fun fromCode(code: Int): Algorithm? = entries.firstOrNull { it.code == code }

        fun label(code: Int): String = fromCode(code)?.label ?: "Unknown (0x%02X)".format(code)
    }
}
