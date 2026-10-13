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

package com.example.otp_nfc_configurer.debug

import com.example.otp_nfc_configurer.model.Base32
import com.example.otp_nfc_configurer.nfc.NfcRecord
import com.example.otp_nfc_configurer.protocol.PacketInspector
import com.example.otp_nfc_configurer.protocol.ParamSpec
import com.example.otp_nfc_configurer.protocol.ParamSpecs
import com.example.otp_nfc_configurer.protocol.Protocol
import com.example.otp_nfc_configurer.protocol.ProtocolCodec
import com.example.otp_nfc_configurer.protocol.ValueKind

enum class PayloadMode { TYPED, HEX }

/** Command composed in the debug console. Numeric inputs are kept as text so invalid values can be shown. */
data class DebugDraft(
    val opCode: Int = Protocol.OpCode.GET_SLOT_MEMBERS,
    val slot: String = "0",
    val endSlot: String = "4",
    val param: Int = Protocol.Param.SLOT_NAME,
    val payloadMode: PayloadMode = PayloadMode.TYPED,
    val payload: String = "",
    val version: String = Protocol.VERSION.toString(),
    val autoChecksum: Boolean = true,
    val manualChecksum: String = "00",
    /** When set, this hex packet is sent as-is instead of the composed command. */
    val rawPacket: String? = null
) {
    val hasSlot: Boolean
        get() = opCode in listOf(Protocol.OpCode.GET_SLOT, Protocol.OpCode.GET_SLOT_MEMBERS, Protocol.OpCode.SET_SLOT)
    val hasEndSlot: Boolean get() = opCode == Protocol.OpCode.GET_SLOT_MEMBERS
    val hasParam: Boolean get() = opCode != Protocol.OpCode.GET_DEVICE_INFO
    val hasPayload: Boolean get() = opCode == Protocol.OpCode.SET_SLOT || opCode == Protocol.OpCode.SET_GLOBAL

    val paramOptions: List<ParamSpec>
        get() = if (opCode == Protocol.OpCode.GET_GLOBAL || opCode == Protocol.OpCode.SET_GLOBAL) ParamSpecs.global
        else ParamSpecs.slot

    val paramSpec: ParamSpec? get() = paramOptions.firstOrNull { it.id == param }
}

/** Result of composing a request: either the packet or the reason it cannot be built. */
sealed interface BuiltRequest {
    data class Packet(val bytes: ByteArray) : BuiltRequest
    data class Error(val message: String) : BuiltRequest
}

object DebugCommandBuilder {

    fun build(draft: DebugDraft): BuiltRequest {
        draft.rawPacket?.let { raw ->
            val bytes = PacketInspector.parseHex(raw) ?: return BuiltRequest.Error("Raw packet is not valid hex")
            return if (bytes.isEmpty()) BuiltRequest.Error("Raw packet is empty") else BuiltRequest.Packet(bytes)
        }
        val version = byteValue(draft.version, "Version", max = 0x0F) ?: return error("Version must be 0–15")
        val body = mutableListOf((version shl 4) or draft.opCode)
        if (draft.hasSlot) body += byteValue(draft.slot, "Slot") ?: return error("Slot must be 0–255")
        if (draft.hasEndSlot) body += byteValue(draft.endSlot, "End slot") ?: return error("End slot must be 0–255")
        if (draft.hasParam) body += draft.param

        var bytes = ByteArray(body.size) { body[it].toByte() }
        if (draft.hasPayload) {
            val payload = when (val result = encodePayload(draft)) {
                is BuiltRequest.Packet -> result.bytes
                is BuiltRequest.Error -> return result
            }
            bytes += payload
        }
        val checksum = if (draft.autoChecksum) ProtocolCodec.checksum(bytes)
        else PacketInspector.parseHex(draft.manualChecksum)?.singleOrNull()?.toInt()?.and(0xFF)
            ?: return error("Manual checksum must be one hex byte")
        return BuiltRequest.Packet(bytes + checksum.toByte())
    }

    /** Hint shown below the payload input. */
    fun payloadHint(draft: DebugDraft): String {
        if (draft.payloadMode == PayloadMode.HEX) return "Raw payload bytes, e.g. 1E or 43 48 46 00"
        return when (draft.paramSpec?.kind) {
            ValueKind.U8 -> "Number 0–255 (decimal or 0x..)"
            ValueKind.ALGORITHM -> "1 = ALG_TOTP_SHA1, 0x11 = ALG_XTOTP_SHA1"
            ValueKind.U32 -> "Number 0–4294967295, sent big-endian"
            ValueKind.FIXED_STRING -> "Text, zero-padded to ${draft.paramSpec?.size} B"
            ValueKind.VAR_STRING -> "Text, terminated with 0x00 (max ${(draft.paramSpec?.size ?: 1) - 1} B)"
            ValueKind.SECRET -> "Base32 secret, sent as length byte + bytes"
            ValueKind.STRUCT, null -> "Full structure: switch to hex mode"
        }
    }

    private fun encodePayload(draft: DebugDraft): BuiltRequest {
        if (draft.payloadMode == PayloadMode.HEX) {
            return PacketInspector.parseHex(draft.payload)?.let { BuiltRequest.Packet(it) }
                ?: error("Payload is not valid hex")
        }
        val spec = draft.paramSpec ?: return error("Unknown parameter")
        val input = draft.payload
        return try {
            val bytes = when (spec.kind) {
                ValueKind.U8, ValueKind.ALGORITHM ->
                    ProtocolCodec.encodeU8(byteValue(input, spec.name) ?: return error("${spec.name} must be 0–255"))
                ValueKind.U32 -> {
                    val value = PacketInspector.parseNumber(input)?.takeIf { it in 0..0xFFFFFFFFL }
                        ?: return error("${spec.name} must be 0–4294967295")
                    ProtocolCodec.encodeU32(value)
                }
                ValueKind.FIXED_STRING -> ProtocolCodec.encodeFixedString(input, spec.size)
                ValueKind.VAR_STRING -> ProtocolCodec.encodeVarString(input, spec.size)
                ValueKind.SECRET -> ProtocolCodec.encodeSecret(Base32.decode(input) ?: return error("Invalid Base32"))
                ValueKind.STRUCT -> return error("Typed input is not available for 0x00, use hex mode")
            }
            BuiltRequest.Packet(bytes)
        } catch (e: IllegalArgumentException) {
            error(e.message ?: "Invalid value")
        }
    }

    private fun byteValue(text: String, @Suppress("UNUSED_PARAMETER") name: String, max: Int = 0xFF): Int? =
        PacketInspector.parseNumber(text)?.takeIf { it in 0..max }?.toInt()

    private fun error(message: String) = BuiltRequest.Error(message)
}

enum class DebugTapMode { SEND, READ }

/** One logged NFC exchange of the debug console. */
data class DebugExchange(
    val id: Long,
    val timestamp: Long,
    val serial: String,
    val request: ByteArray?,
    val response: ByteArray?,
    val tagRecord: NfcRecord?,
    val error: String?,
    val durationMs: Long
)

data class DebugState(
    val draft: DebugDraft = DebugDraft(),
    val armed: DebugTapMode? = null,
    val log: List<DebugExchange> = emptyList()
)
