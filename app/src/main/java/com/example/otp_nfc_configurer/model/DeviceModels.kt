package com.example.otp_nfc_configurer.model

/** Parsed discovery link xotp-conf://<device-id>/<slot-count>/<active-slot-index>. */
data class DeviceInfo(
    val deviceId: String,
    val slotCount: Int,
    val activeSlotIndex: Int
)

data class GlobalConfig(
    val deviceId: String,
    val merchantBackend: String,
    val deviceDescription: String,
    val activeSlotIndex: Int
)

/** Readable slot parameters; the secret is write-only and never part of this model. */
data class SlotConfig(
    val interval: Int,
    val digits: Int,
    val algorithm: Int,
    val slotName: String,
    val currency: String,
    val fraction: Long,
    val merchantTemplate: String
)

sealed interface SlotSummary {
    data object Empty : SlotSummary
    data class Configured(val name: String?) : SlotSummary
}
