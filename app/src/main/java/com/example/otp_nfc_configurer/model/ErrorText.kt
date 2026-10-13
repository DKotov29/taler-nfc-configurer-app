package com.example.otp_nfc_configurer.model

import android.nfc.TagLostException
import com.example.otp_nfc_configurer.R
import com.example.otp_nfc_configurer.nfc.ResponseTimeoutException
import com.example.otp_nfc_configurer.nfc.UnsupportedTagException
import com.example.otp_nfc_configurer.protocol.Protocol
import com.example.otp_nfc_configurer.protocol.ProtocolException

/** User-facing reason of a failed NFC operation; low-level errors without translation keep their message. */
fun errorText(error: Exception): UiText = when (error) {
    is ProtocolException.DeviceError -> deviceStatusText(error.status)
    is ProtocolException.ChecksumMismatch -> uiText(R.string.error_checksum)
    is ProtocolException.Malformed -> uiText(R.string.error_malformed, error.message.orEmpty())
    is ResponseTimeoutException -> uiText(R.string.error_timeout)
    is UnsupportedTagException -> uiText(R.string.error_unsupported_tag)
    is TagLostException -> uiText(R.string.error_tag_lost)
    else -> UiText.Raw(error.message ?: error.javaClass.simpleName)
}

private fun deviceStatusText(status: Int): UiText = when {
    (status and 0xF0) == Protocol.Status.VERSION_MISMATCH_MASK ->
        uiText(R.string.status_version_mismatch, status and 0x0F, Protocol.VERSION)
    status == Protocol.Status.INVALID_LENGTH -> uiText(R.string.status_invalid_length)
    status == Protocol.Status.CHECKSUM -> uiText(R.string.status_checksum)
    status == Protocol.Status.UNSUPPORTED_CMD -> uiText(R.string.status_unsupported_cmd)
    status == Protocol.Status.INVALID_SLOT -> uiText(R.string.status_invalid_slot)
    status == Protocol.Status.INVALID_PARAM -> uiText(R.string.status_invalid_param)
    status == Protocol.Status.INVALID_VALUE -> uiText(R.string.status_invalid_value)
    else -> uiText(R.string.status_unknown, status)
}
