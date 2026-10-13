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
