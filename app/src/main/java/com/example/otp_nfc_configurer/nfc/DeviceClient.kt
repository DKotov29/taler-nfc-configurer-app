package com.example.otp_nfc_configurer.nfc

import com.example.otp_nfc_configurer.model.DeviceInfo
import com.example.otp_nfc_configurer.model.GlobalConfig
import com.example.otp_nfc_configurer.model.SlotConfig
import com.example.otp_nfc_configurer.model.SlotSummary
import com.example.otp_nfc_configurer.protocol.Protocol
import com.example.otp_nfc_configurer.protocol.ProtocolCodec

/** High-level protocol operations executed over one [NfcTransport] session. */
class DeviceClient(private val transport: NfcTransport) {

    val serial: ByteArray get() = transport.serial

    /** Discovery: prefers the exposed xotp-conf link, falls back to GET_DEVICE_INFO. */
    fun readDeviceInfo(): DeviceInfo {
        transport.readDiscoveryUri()
            ?.takeIf { it.startsWith(Protocol.URI_SCHEME) }
            ?.let { return ProtocolCodec.parseDeviceInfoUri(it) }
        return ProtocolCodec.decodeDeviceInfo(execute(ProtocolCodec.getDeviceInfo()))
    }

    fun readGlobal(): GlobalConfig = ProtocolCodec.decodeGlobal(execute(ProtocolCodec.getGlobal()))

    fun readSlotNames(startSlot: Int, endSlot: Int): List<SlotSummary> {
        val request = ProtocolCodec.getSlotMembers(startSlot, endSlot, Protocol.Param.SLOT_NAME)
        return ProtocolCodec.decodeSlotNames(execute(request), endSlot - startSlot + 1)
    }

    fun readSlot(slot: Int): SlotConfig = ProtocolCodec.decodeSlot(execute(ProtocolCodec.getSlot(slot)))

    /** Sends a prepared SET request and verifies the device confirmed it. */
    fun write(request: ByteArray) {
        execute(request)
    }

    private fun execute(request: ByteArray): ByteArray =
        ProtocolCodec.parseResponse(transport.transceive(request))
}
