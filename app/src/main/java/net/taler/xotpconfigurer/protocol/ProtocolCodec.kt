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

package net.taler.xotpconfigurer.protocol

import net.taler.xotpconfigurer.model.DeviceInfo
import net.taler.xotpconfigurer.model.GlobalConfig
import net.taler.xotpconfigurer.model.SlotConfig
import net.taler.xotpconfigurer.model.SlotSummary
import java.io.ByteArrayOutputStream

/** Builds request packets and decodes response payloads. Pure Kotlin, no Android dependencies. */
object ProtocolCodec {

    // ---------- Requests ----------

    fun getDeviceInfo(): ByteArray = packet(Protocol.OpCode.GET_DEVICE_INFO)

    fun getSlot(slot: Int, param: Int = Protocol.Param.ALL): ByteArray =
        packet(Protocol.OpCode.GET_SLOT, slot, param)

    fun getGlobal(param: Int = Protocol.Param.ALL): ByteArray =
        packet(Protocol.OpCode.GET_GLOBAL, param)

    fun getSlotMembers(startSlot: Int, endSlot: Int, param: Int): ByteArray =
        packet(Protocol.OpCode.GET_SLOT_MEMBERS, startSlot, endSlot, param)

    fun setSlot(slot: Int, param: Int, payload: ByteArray): ByteArray =
        packet(Protocol.OpCode.SET_SLOT, slot, param, payload = payload)

    fun setGlobal(param: Int, payload: ByteArray): ByteArray {
        require(param != Protocol.Param.ALL) { "Bulk SET_GLOBAL is not allowed" }
        return packet(Protocol.OpCode.SET_GLOBAL, param, payload = payload)
    }

    fun header(opCode: Int): Int = (Protocol.VERSION shl 4) or (opCode and 0x0F)

    fun checksum(bytes: ByteArray, length: Int = bytes.size): Int {
        var sum = 0
        for (i in 0 until length) sum = sum xor (bytes[i].toInt() and 0xFF)
        return sum
    }

    private fun packet(opCode: Int, vararg fields: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(header(opCode))
        fields.forEach { out.write(it and 0xFF) }
        out.write(payload)
        val body = out.toByteArray()
        return body + checksum(body).toByte()
    }

    // ---------- Responses ----------

    /** Validates checksum and status; returns the response payload. */
    fun parseResponse(response: ByteArray): ByteArray {
        if (response.size < 2) throw ProtocolException.Malformed("Response too short (${response.size} B)")
        val last = response.size - 1
        if (checksum(response, last) != (response[last].toInt() and 0xFF)) {
            throw ProtocolException.ChecksumMismatch()
        }
        val status = response[0].toInt() and 0xFF
        if (status != Protocol.Status.OK) throw ProtocolException.DeviceError(status)
        return response.copyOfRange(1, last)
    }

    /** True if [bytes] is a well-formed response packet (status byte + checksum), regardless of status. */
    fun isResponse(bytes: ByteArray): Boolean {
        if (bytes.size < 2) return false
        val status = bytes[0].toInt() and 0xFF
        val knownStatus = status == Protocol.Status.OK ||
                (status and 0xF0) == Protocol.Status.VERSION_MISMATCH_MASK ||
                status in Protocol.Status.INVALID_LENGTH..Protocol.Status.INVALID_VALUE
        return knownStatus && checksum(bytes, bytes.size - 1) == (bytes.last().toInt() and 0xFF)
    }

    fun parseDeviceInfoUri(uri: String): DeviceInfo {
        if (!uri.startsWith(Protocol.URI_SCHEME)) throw ProtocolException.Malformed("Not an xotp-conf link: $uri")
        val parts = uri.removePrefix(Protocol.URI_SCHEME).trimEnd('/', '\u0000').split('/')
        if (parts.size < 3) throw ProtocolException.Malformed("Incomplete discovery link: $uri")
        val slotCount = parts[parts.size - 2].toIntOrNull()
        val active = parts[parts.size - 1].toIntOrNull()
        if (slotCount == null || active == null) throw ProtocolException.Malformed("Invalid discovery link: $uri")
        return DeviceInfo(
            deviceId = parts.dropLast(2).joinToString("/"),
            slotCount = slotCount,
            activeSlotIndex = active
        )
    }

    fun decodeDeviceInfo(payload: ByteArray): DeviceInfo =
        parseDeviceInfoUri(String(payload, Charsets.UTF_8))

    fun decodeSlot(payload: ByteArray): SlotConfig = PayloadReader(payload).run {
        SlotConfig(
            interval = u8(),
            digits = u8(),
            algorithm = u8(),
            slotName = varString(),
            currency = fixedString(Protocol.Size.CURRENCY),
            fraction = u32(),
            merchantTemplate = varString()
        )
    }

    fun decodeGlobal(payload: ByteArray): GlobalConfig = PayloadReader(payload).run {
        GlobalConfig(
            deviceId = fixedString(Protocol.Size.DEVICE_ID),
            merchantBackend = varString(),
            deviceDescription = varString(),
            activeSlotIndex = u8()
        )
    }

    /** Decodes a GET_SLOT_MEMBERS slotName stream: "\n" separates, "" = empty slot, "\r" = configured without name. */
    fun decodeSlotNames(payload: ByteArray, expectedCount: Int): List<SlotSummary> {
        val entries = String(payload, Charsets.UTF_8).split(Protocol.MEMBER_SEPARATOR)
        // A trailing separator produces one extra empty entry.
        val relevant = if (entries.size == expectedCount + 1 && entries.last().isEmpty()) entries.dropLast(1) else entries
        if (relevant.size != expectedCount) {
            throw ProtocolException.Malformed("Expected $expectedCount slot names, got ${relevant.size}")
        }
        return relevant.map { entry ->
            when (entry) {
                "" -> SlotSummary.Empty
                Protocol.NAMELESS_CONFIGURED -> SlotSummary.Configured(name = null)
                else -> SlotSummary.Configured(name = entry.trimEnd('\r'))
            }
        }
    }

    // ---------- Field encoders ----------

    fun encodeU8(value: Int): ByteArray = byteArrayOf(value.toByte())

    /** uint32 in network byte order, as shown in the protocol examples (0x00000064 = 100). */
    fun encodeU32(value: Long): ByteArray = byteArrayOf(
        (value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte()
    )

    /** Fixed-length buffer, zero-padded up to [size]. */
    fun encodeFixedString(value: String, size: Int): ByteArray {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size < size) { "Value exceeds ${size - 1} bytes" }
        return bytes.copyOf(size)
    }

    /** Variable-length string, terminated by a single 0x00 without trailing padding. */
    fun encodeVarString(value: String, size: Int): ByteArray {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size < size) { "Value exceeds ${size - 1} bytes" }
        return bytes + 0.toByte()
    }

    /** Secret with 1-byte length prefix. */
    fun encodeSecret(secret: ByteArray): ByteArray {
        require(secret.isNotEmpty() && secret.size <= Protocol.Size.SECRET) { "Secret must be 1–${Protocol.Size.SECRET} bytes" }
        return byteArrayOf(secret.size.toByte()) + secret
    }

    fun encodeSlotField(param: Int, config: SlotConfig): ByteArray = when (param) {
        Protocol.Param.INTERVAL -> encodeU8(config.interval)
        Protocol.Param.DIGITS -> encodeU8(config.digits)
        Protocol.Param.ALGORITHM -> encodeU8(config.algorithm)
        Protocol.Param.SLOT_NAME -> encodeVarString(config.slotName, Protocol.Size.SLOT_NAME)
        Protocol.Param.CURRENCY -> encodeFixedString(config.currency, Protocol.Size.CURRENCY)
        Protocol.Param.FRACTION -> encodeU32(config.fraction)
        Protocol.Param.MERCHANT_TEMPLATE -> encodeVarString(config.merchantTemplate, Protocol.Size.MERCHANT_TEMPLATE)
        else -> throw IllegalArgumentException("Not a readable slot parameter: $param")
    }

    /** Full slot structure for SET_SLOT with Parameter ID 0x00. */
    fun encodeFullSlot(config: SlotConfig, secret: ByteArray): ByteArray =
        encodeSecret(secret) +
                encodeU8(config.interval) +
                encodeU8(config.digits) +
                encodeU8(config.algorithm) +
                encodeVarString(config.slotName, Protocol.Size.SLOT_NAME) +
                encodeFixedString(config.currency, Protocol.Size.CURRENCY) +
                encodeU32(config.fraction) +
                encodeVarString(config.merchantTemplate, Protocol.Size.MERCHANT_TEMPLATE)

    fun encodeGlobalField(param: Int, config: GlobalConfig): ByteArray = when (param) {
        Protocol.Param.DEVICE_ID -> encodeFixedString(config.deviceId, Protocol.Size.DEVICE_ID)
        Protocol.Param.MERCHANT_BACKEND -> encodeVarString(config.merchantBackend, Protocol.Size.MERCHANT_BACKEND)
        Protocol.Param.DEVICE_DESCRIPTION -> encodeVarString(config.deviceDescription, Protocol.Size.DEVICE_DESCRIPTION)
        Protocol.Param.ACTIVE_SLOT_INDEX -> encodeU8(config.activeSlotIndex)
        else -> throw IllegalArgumentException("Not a global parameter: $param")
    }

    private class PayloadReader(private val bytes: ByteArray) {
        private var pos = 0

        private fun need(count: Int) {
            if (pos + count > bytes.size) throw ProtocolException.Malformed("Payload truncated at byte $pos")
        }

        fun u8(): Int {
            need(1)
            return bytes[pos++].toInt() and 0xFF
        }

        fun u32(): Long {
            need(4)
            var value = 0L
            repeat(4) { value = (value shl 8) or (bytes[pos++].toLong() and 0xFF) }
            return value
        }

        fun fixedString(size: Int): String {
            need(size)
            val raw = bytes.copyOfRange(pos, pos + size)
            pos += size
            val end = raw.indexOf(0.toByte()).let { if (it < 0) raw.size else it }
            return String(raw, 0, end, Charsets.UTF_8)
        }

        /** Reads up to the next 0x00; a missing terminator at the end of the payload is tolerated. */
        fun varString(): String {
            val start = pos
            while (pos < bytes.size && bytes[pos] != 0.toByte()) pos++
            val value = String(bytes, start, pos - start, Charsets.UTF_8)
            if (pos < bytes.size) pos++
            return value
        }
    }
}

sealed class ProtocolException(message: String) : Exception(message) {
    class Malformed(message: String) : ProtocolException(message)
    class ChecksumMismatch : ProtocolException("Response checksum mismatch")

    class DeviceError(val status: Int) : ProtocolException(describe(status))

    companion object {
        fun describe(status: Int): String = when {
            (status and 0xF0) == Protocol.Status.VERSION_MISMATCH_MASK ->
                "Protocol version mismatch (device supports v${status and 0x0F}, app uses v${Protocol.VERSION})"
            status == Protocol.Status.INVALID_LENGTH -> "Invalid payload length"
            status == Protocol.Status.CHECKSUM -> "Device rejected packet checksum"
            status == Protocol.Status.UNSUPPORTED_CMD -> "Command not supported by device"
            status == Protocol.Status.INVALID_SLOT -> "Slot does not exist"
            status == Protocol.Status.INVALID_PARAM -> "Unknown parameter"
            status == Protocol.Status.INVALID_VALUE -> "Device rejected the value"
            else -> "Unknown device status 0x%02X".format(status)
        }
    }
}
