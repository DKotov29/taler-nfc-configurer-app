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

package com.example.otp_nfc_configurer.protocol

/** Kind of value a parameter carries on the wire. */
enum class ValueKind { U8, ALGORITHM, U32, FIXED_STRING, VAR_STRING, SECRET, STRUCT }

data class ParamSpec(val id: Int, val name: String, val kind: ValueKind, val size: Int = 0) {
    val label: String get() = "0x%02X %s".format(id, name)
}

object ParamSpecs {
    val slot = listOf(
        ParamSpec(Protocol.Param.ALL, "ALL", ValueKind.STRUCT),
        ParamSpec(Protocol.Param.SECRET, "secret", ValueKind.SECRET, Protocol.Size.SECRET),
        ParamSpec(Protocol.Param.INTERVAL, "interval", ValueKind.U8),
        ParamSpec(Protocol.Param.DIGITS, "digits", ValueKind.U8),
        ParamSpec(Protocol.Param.ALGORITHM, "algorithm", ValueKind.ALGORITHM),
        ParamSpec(Protocol.Param.SLOT_NAME, "slotName", ValueKind.VAR_STRING, Protocol.Size.SLOT_NAME),
        ParamSpec(Protocol.Param.CURRENCY, "currency", ValueKind.FIXED_STRING, Protocol.Size.CURRENCY),
        ParamSpec(Protocol.Param.FRACTION, "fraction", ValueKind.U32),
        ParamSpec(Protocol.Param.MERCHANT_TEMPLATE, "merchantTemplate", ValueKind.VAR_STRING, Protocol.Size.MERCHANT_TEMPLATE)
    )

    val global = listOf(
        ParamSpec(Protocol.Param.ALL, "ALL", ValueKind.STRUCT),
        ParamSpec(Protocol.Param.DEVICE_ID, "deviceID", ValueKind.FIXED_STRING, Protocol.Size.DEVICE_ID),
        ParamSpec(Protocol.Param.MERCHANT_BACKEND, "merchantBackend", ValueKind.VAR_STRING, Protocol.Size.MERCHANT_BACKEND),
        ParamSpec(Protocol.Param.DEVICE_DESCRIPTION, "deviceDescription", ValueKind.VAR_STRING, Protocol.Size.DEVICE_DESCRIPTION),
        ParamSpec(Protocol.Param.ACTIVE_SLOT_INDEX, "activeSlotIndex", ValueKind.U8)
    )

    /** Order of the full slot structure; reads omit the secret. */
    val fullSlotWrite = slot.drop(1)
    val fullSlotRead = slot.drop(2)
    val fullGlobal = global.drop(1)

    fun slot(id: Int): ParamSpec? = slot.firstOrNull { it.id == id }
    fun global(id: Int): ParamSpec? = global.firstOrNull { it.id == id }
}

/** One annotated field of a packet. */
data class PacketField(val name: String, val bytes: ByteArray, val meaning: String)

/** Field-level breakdown of request and response packets for the debug console. Pure Kotlin. */
object PacketInspector {

    val opCodes = listOf(
        Protocol.OpCode.GET_DEVICE_INFO to "GET_DEVICE_INFO",
        Protocol.OpCode.GET_SLOT to "GET_SLOT",
        Protocol.OpCode.GET_GLOBAL to "GET_GLOBAL",
        Protocol.OpCode.GET_SLOT_MEMBERS to "GET_SLOT_MEMBERS",
        Protocol.OpCode.SET_SLOT to "SET_SLOT",
        Protocol.OpCode.SET_GLOBAL to "SET_GLOBAL"
    )

    fun opName(op: Int): String = opCodes.firstOrNull { it.first == op }?.second ?: "UNKNOWN"

    fun statusName(status: Int): String = when {
        status == Protocol.Status.OK -> "STATUS_OK"
        (status and 0xF0) == Protocol.Status.VERSION_MISMATCH_MASK -> "ERR_VERSION_MISMATCH"
        status == Protocol.Status.INVALID_LENGTH -> "ERR_INVALID_LENGTH"
        status == Protocol.Status.CHECKSUM -> "ERR_CHECKSUM"
        status == Protocol.Status.UNSUPPORTED_CMD -> "ERR_UNSUPPORTED_CMD"
        status == Protocol.Status.INVALID_SLOT -> "ERR_INVALID_SLOT"
        status == Protocol.Status.INVALID_PARAM -> "ERR_INVALID_PARAM"
        status == Protocol.Status.INVALID_VALUE -> "ERR_INVALID_VALUE"
        else -> "UNKNOWN_STATUS"
    }

    // ---------- Formatting ----------

    fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }

    fun binary(byte: Byte): String =
        Integer.toBinaryString(byte.toInt() and 0xFF).padStart(8, '0').let { "${it.take(4)} ${it.drop(4)}" }

    fun ascii(byte: Byte): Char = (byte.toInt() and 0xFF).let { if (it in 0x20..0x7E) it.toChar() else '·' }

    /** Parses "13 00 04", "0x13,0x00" or "130004"; returns null on invalid input. */
    fun parseHex(text: String): ByteArray? {
        val clean = text.replace("0x", "", ignoreCase = true).filter { !it.isWhitespace() && it != ',' && it != ':' }
        if (clean.length % 2 != 0 || clean.any { it.digitToIntOrNull(16) == null }) return null
        return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    /** Parses decimal or 0x-prefixed hexadecimal numbers. */
    fun parseNumber(text: String): Long? {
        val t = text.trim()
        return if (t.startsWith("0x", ignoreCase = true)) t.drop(2).toLongOrNull(16) else t.toLongOrNull()
    }

    // ---------- Requests ----------

    fun requestFields(request: ByteArray): List<PacketField> {
        if (request.isEmpty()) return emptyList()
        val fields = mutableListOf<PacketField>()
        val header = request[0].toInt() and 0xFF
        val version = header shr 4
        val op = header and 0x0F
        fields += PacketField(
            "Header", request.copyOfRange(0, 1),
            "version $version (${binary(request[0]).take(4)}) | opcode ${opName(op)} (${binary(request[0]).takeLast(4)})"
        )
        if (request.size < 2) return fields
        val body = Cursor(request.copyOfRange(1, request.size - 1))
        try {
            when (op) {
                Protocol.OpCode.GET_DEVICE_INFO -> Unit
                Protocol.OpCode.GET_SLOT -> {
                    fields += body.u8Field("Slot ID") { "slot $it" }
                    fields += body.u8Field("Param ID") { paramLabel(ParamSpecs.slot(it), it) }
                }
                Protocol.OpCode.GET_GLOBAL ->
                    fields += body.u8Field("Param ID") { paramLabel(ParamSpecs.global(it), it) }
                Protocol.OpCode.GET_SLOT_MEMBERS -> {
                    fields += body.u8Field("Start slot") { "slot $it" }
                    fields += body.u8Field("End slot") { "slot $it" }
                    fields += body.u8Field("Param ID") { paramLabel(ParamSpecs.slot(it), it) }
                }
                Protocol.OpCode.SET_SLOT -> {
                    fields += body.u8Field("Slot ID") { "slot $it" }
                    val paramField = body.u8Field("Param ID") { paramLabel(ParamSpecs.slot(it), it) }
                    fields += paramField
                    val param = paramField.bytes[0].toInt() and 0xFF
                    fields += decodePayload(body, ParamSpecs.slot(param), ParamSpecs.fullSlotWrite)
                }
                Protocol.OpCode.SET_GLOBAL -> {
                    val paramField = body.u8Field("Param ID") { paramLabel(ParamSpecs.global(it), it) }
                    fields += paramField
                    val param = paramField.bytes[0].toInt() and 0xFF
                    fields += decodePayload(body, ParamSpecs.global(param).takeIf { param != Protocol.Param.ALL }, emptyList())
                }
            }
        } catch (e: IllegalStateException) {
            fields += PacketField("Error", ByteArray(0), e.message ?: "truncated")
        }
        body.rest()?.let { fields += PacketField("Unparsed", it, "${it.size} B not expected by this command") }
        fields += checksumField(request)
        return fields
    }

    // ---------- Responses ----------

    fun responseFields(request: ByteArray?, response: ByteArray): List<PacketField> {
        if (response.isEmpty()) return emptyList()
        val fields = mutableListOf<PacketField>()
        val status = response[0].toInt() and 0xFF
        fields += PacketField(
            "Status", response.copyOfRange(0, 1),
            "${statusName(status)}" + if (status != Protocol.Status.OK) " — ${ProtocolException.describe(status)}" else ""
        )
        if (response.size < 2) return fields
        val body = Cursor(response.copyOfRange(1, response.size - 1))
        if (status == Protocol.Status.OK && request != null && request.size >= 2) {
            try {
                fields += decodeResponsePayload(request, body)
            } catch (e: IllegalStateException) {
                fields += PacketField("Error", ByteArray(0), e.message ?: "truncated")
            }
        }
        body.rest()?.let { fields += PacketField("Payload", it, "\"${String(it, Charsets.UTF_8)}\"") }
        fields += checksumField(response)
        return fields
    }

    private fun decodeResponsePayload(request: ByteArray, body: Cursor): List<PacketField> {
        val op = request[0].toInt() and 0x0F
        val arg = { i: Int -> if (request.size > i + 1) request[i].toInt() and 0xFF else -1 }
        return when (op) {
            Protocol.OpCode.GET_DEVICE_INFO -> {
                val raw = body.rest() ?: return emptyList()
                val link = String(raw, Charsets.UTF_8)
                val meaning = runCatching { ProtocolCodec.parseDeviceInfoUri(link) }
                    .map { "\"$link\" → id=${it.deviceId}, slots=${it.slotCount}, active=${it.activeSlotIndex}" }
                    .getOrElse { "\"$link\" (${it.message})" }
                listOf(PacketField("Discovery link", raw, meaning))
            }
            Protocol.OpCode.GET_SLOT -> decodePayload(body, ParamSpecs.slot(arg(2)), ParamSpecs.fullSlotRead)
            Protocol.OpCode.GET_GLOBAL ->
                decodePayload(body, ParamSpecs.global(arg(1)), ParamSpecs.fullGlobal)
            Protocol.OpCode.GET_SLOT_MEMBERS -> {
                val raw = body.rest() ?: return emptyList()
                val fields = mutableListOf<PacketField>()
                var start = 0
                var slot = arg(1)
                for (i in raw.indices) {
                    if (raw[i] == '\n'.code.toByte()) {
                        val entry = raw.copyOfRange(start, i + 1)
                        val text = String(entry, 0, entry.size - 1, Charsets.UTF_8)
                        val meaning = when (text) {
                            "" -> "empty"
                            "\r" -> "configured, no value"
                            else -> "configured, \"$text\""
                        }
                        fields += PacketField("Slot $slot", entry, meaning)
                        slot++
                        start = i + 1
                    }
                }
                if (start < raw.size) fields += PacketField("Unterminated", raw.copyOfRange(start, raw.size), "missing \\n")
                fields
            }
            else -> emptyList()
        }
    }

    // ---------- Values ----------

    /** Decodes a single parameter value, or the full structure [struct] for Parameter ID 0x00. */
    private fun decodePayload(body: Cursor, spec: ParamSpec?, struct: List<ParamSpec>): List<PacketField> = when {
        spec == null -> emptyList()
        spec.kind == ValueKind.STRUCT -> struct.map { decodeValue(body, it) }
        else -> listOf(decodeValue(body, spec))
    }

    private fun decodeValue(body: Cursor, spec: ParamSpec): PacketField = when (spec.kind) {
        ValueKind.U8 -> body.u8Field(spec.name) { "$it" }
        ValueKind.ALGORITHM -> body.u8Field(spec.name) { "0x%02X %s".format(it, Algorithm.label(it)) }
        ValueKind.U32 -> body.take(4).let { b ->
            PacketField(spec.name, b, b.fold(0L) { acc, x -> (acc shl 8) or (x.toLong() and 0xFF) }.toString())
        }
        ValueKind.FIXED_STRING -> body.take(spec.size).let { b ->
            val end = b.indexOf(0.toByte()).let { if (it < 0) b.size else it }
            PacketField(spec.name, b, "\"${String(b, 0, end, Charsets.UTF_8)}\" (${spec.size} B fixed)")
        }
        ValueKind.VAR_STRING -> body.takeUntilZero().let { b ->
            val terminated = b.lastOrNull() == 0.toByte()
            val text = String(b, 0, if (terminated) b.size - 1 else b.size, Charsets.UTF_8)
            PacketField(spec.name, b, "\"$text\"" + if (terminated) " + \\0" else " (no \\0)")
        }
        ValueKind.SECRET -> {
            val length = body.take(1)[0].toInt() and 0xFF
            PacketField(spec.name, byteArrayOf(length.toByte()) + body.take(length), "length $length + $length B secret")
        }
        ValueKind.STRUCT -> PacketField(spec.name, ByteArray(0), "")
    }

    private fun paramLabel(spec: ParamSpec?, id: Int) = spec?.label ?: "0x%02X unknown".format(id)

    private fun checksumField(packet: ByteArray): PacketField {
        val expected = ProtocolCodec.checksum(packet, packet.size - 1)
        val actual = packet.last().toInt() and 0xFF
        return PacketField(
            "Checksum", byteArrayOf(packet.last()),
            if (expected == actual) "XOR valid" else "XOR INVALID, expected 0x%02X".format(expected)
        )
    }

    private class Cursor(private val bytes: ByteArray) {
        private var pos = 0

        fun take(count: Int): ByteArray {
            check(pos + count <= bytes.size) { "Truncated: need $count B at offset $pos" }
            return bytes.copyOfRange(pos, pos + count).also { pos += count }
        }

        fun takeUntilZero(): ByteArray {
            val end = (pos until bytes.size).firstOrNull { bytes[it] == 0.toByte() }?.plus(1) ?: bytes.size
            return bytes.copyOfRange(pos, end).also { pos = end }
        }

        fun u8Field(name: String, meaning: (Int) -> String): PacketField =
            take(1).let { PacketField(name, it, meaning(it[0].toInt() and 0xFF)) }

        fun rest(): ByteArray? = if (pos < bytes.size) bytes.copyOfRange(pos, bytes.size).also { pos = bytes.size } else null
    }
}
