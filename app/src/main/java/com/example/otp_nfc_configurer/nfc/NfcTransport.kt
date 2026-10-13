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

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NfcV
import android.nfc.tech.TagTechnology
import com.example.otp_nfc_configurer.protocol.ProtocolCodec
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException

/** First NDEF record currently stored on the tag, for diagnostics. */
data class NfcRecord(val tnf: Int, val type: String, val payload: ByteArray) {
    val tnfName: String
        get() = when (tnf) {
            NdefRecord.TNF_EMPTY.toInt() -> "Empty"
            NdefRecord.TNF_WELL_KNOWN.toInt() -> "Well-known"
            NdefRecord.TNF_MIME_MEDIA.toInt() -> "Media"
            NdefRecord.TNF_ABSOLUTE_URI.toInt() -> "Absolute URI"
            NdefRecord.TNF_EXTERNAL_TYPE.toInt() -> "External"
            else -> "TNF $tnf"
        }
}

/** Half-duplex request/response channel to the merchant device for the duration of one NFC tap. */
interface NfcTransport : Closeable {
    /** Chip serial number (NFC UID) used to detect a device change between taps. */
    val serial: ByteArray

    /** Link currently exposed by the device, e.g. xotp-conf://DEV-01/5/1, or null if none. */
    fun readDiscoveryUri(): String?

    /** Sends a request packet and blocks until the device answers with a response packet. */
    fun transceive(request: ByteArray): ByteArray

    /** Reads the first NDEF record from the tag without sending anything, or null if the tag is empty. */
    fun readCurrentRecord(): NfcRecord?
}

/**
 * Transport over the dynamic NFC tag (ST25DV) of the device: the request is written as an NDEF record,
 * the firmware processes it and replaces the NDEF content with the response, which is polled.
 *
 * Android caches the NDEF message of a connected tag, so [Ndef.getNdefMessage] does not see what the
 * device writes itself. The tag memory is therefore read with raw ISO 15693 commands ([NfcV]) whenever
 * the tag supports them.
 */
class NdefTransport private constructor(tag: Tag) : NfcTransport {

    override val serial: ByteArray = tag.id
    private val ndef: Ndef = Ndef.get(tag) ?: throw UnsupportedTagException()
    private val nfcV: NfcV? = NfcV.get(tag)
    private var connected: TagTechnology? = null

    override fun readDiscoveryUri(): String? {
        // Message read at tag discovery: the link exposed before any request was sent.
        val message = ndef.cachedNdefMessage ?: readMessage() ?: return null
        return message.records.firstNotNullOfOrNull { it.toUri()?.toString() }
    }

    override fun transceive(request: ByteArray): ByteArray {
        connect(ndef).writeNdefMessage(NdefMessage(arrayOf(createRequestRecord(request))))
        // Every poll is RF traffic that blocks the device's I2C access to the tag memory,
        // so the device needs idle gaps to read the request and write its response.
        val deadline = System.currentTimeMillis() + RESPONSE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS)
            val payload = try {
                readMessage()?.records?.firstOrNull()?.let(::payloadOf)
            } catch (_: TagBusyException) {
                null // The device is writing its response via I2C
            }
            if (payload != null && !payload.contentEquals(request) && ProtocolCodec.isResponse(payload)) {
                return payload
            }
        }
        throw ResponseTimeoutException(RESPONSE_TIMEOUT_MS)
    }

    override fun readCurrentRecord(): NfcRecord? {
        repeat(BUSY_RETRIES) {
            try {
                return readMessage()?.records?.firstOrNull()?.let {
                    NfcRecord(it.tnf.toInt(), String(it.type, Charsets.US_ASCII), it.payload)
                }
            } catch (_: TagBusyException) {
                Thread.sleep(POLL_INTERVAL_MS)
            }
        }
        throw IOException("Tag memory stayed busy (device writing via I2C)")
    }

    override fun close() {
        connected?.let(::closeQuietly)
        connected = null
    }

    /** Reads the current NDEF message bypassing Android's cache; null while it is empty or incomplete. */
    private fun readMessage(): NdefMessage? = try {
        if (nfcV != null) {
            Type5Memory(connect(nfcV)).readNdefMessage()?.let(::NdefMessage)
        } else {
            connect(ndef).ndefMessage
        }
    } catch (_: FormatException) {
        null // Device is rewriting the NDEF message
    }

    /** Only one technology of a tag can be connected at a time: switches the connection if needed. */
    private fun <T : TagTechnology> connect(technology: T): T {
        if (connected !== technology) {
            close()
            technology.connect()
            connected = technology
        }
        return technology
    }

    companion object {
        /** MIME type carrying binary protocol packets. */
        const val MIME_TYPE = "application/vnd.taler.xotp-conf"

        private const val RESPONSE_TIMEOUT_MS = 4000L
        private const val POLL_INTERVAL_MS = 150L
        private const val BUSY_RETRIES = 10

        fun open(tag: Tag): NfcTransport = NdefTransport(tag)

        /** True if [tag] is still in the field and can be connected again. */
        fun isPresent(tag: Tag): Boolean {
            val technology: TagTechnology = NfcV.get(tag) ?: Ndef.get(tag) ?: return false
            return try {
                technology.connect()
                closeQuietly(technology)
                true
            } catch (_: Exception) {
                false // Tag lost or handle out of date (SecurityException)
            }
        }

        /** A tag that left the field throws SecurityException ("Tag ... is out of date") even on close. */
        private fun closeQuietly(technology: TagTechnology) {
            try { technology.close() } catch (_: Exception) {}
        }

        private fun createRequestRecord(request: ByteArray): NdefRecord =
            NdefRecord.createMime(MIME_TYPE, request)

        private fun payloadOf(record: NdefRecord): ByteArray =
            if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT)) {
                // Strip the RTD Text header (status byte + language code).
                val languageLength = record.payload.firstOrNull()?.toInt()?.and(0x3F) ?: 0
                record.payload.copyOfRange(minOf(1 + languageLength, record.payload.size), record.payload.size)
            } else {
                record.payload
            }
    }
}

/**
 * The tag rejected an RF command because the device accesses the memory via I2C (e.g. while writing its
 * response). Transient: the command can be repeated.
 */
class TagBusyException(message: String) : IOException(message)

/** The device did not answer a request in time. */
class ResponseTimeoutException(timeoutMs: Long) : IOException("Device did not respond within $timeoutMs ms")

/** The tapped tag cannot carry protocol packets (no NDEF support). */
class UnsupportedTagException : IOException("Tag does not support NDEF")

/** Reads the NDEF message of an NFC Forum Type 5 tag directly from its memory blocks. */
private class Type5Memory(private val nfcV: NfcV) {

    /** Returns the raw NDEF message bytes, or null if there is no complete NDEF TLV. */
    fun readNdefMessage(): ByteArray? {
        val cc = readBlocks(0, 1)
        val magic = cc[0].toInt() and 0xFF
        if (magic != 0xE1 && magic != 0xE2) throw IOException("Tag has no NFC Forum capability container")
        // Memory size byte 0 means an 8-byte CC (extended memory size).
        val ccLength = if (cc[2].toInt() == 0) 8 else 4
        val firstBlock = ccLength / BLOCK_SIZE

        val head = readBlocks(firstBlock, 2)
        var i = 0
        while (i < head.size && head[i].toInt() == 0x00) i++ // NULL TLVs
        if (i + 1 >= head.size || (head[i].toInt() and 0xFF) != NDEF_TLV) return null
        val (length, headerLength) = if ((head[i + 1].toInt() and 0xFF) == 0xFF) {
            if (i + 3 >= head.size) return null
            (((head[i + 2].toInt() and 0xFF) shl 8) or (head[i + 3].toInt() and 0xFF)) to 4
        } else {
            (head[i + 1].toInt() and 0xFF) to 2
        }
        if (length == 0) return null
        val start = i + headerLength
        val blocks = (start + length + BLOCK_SIZE - 1) / BLOCK_SIZE
        return readBlocks(firstBlock, blocks).copyOfRange(start, start + length)
    }

    private fun readBlocks(first: Int, count: Int): ByteArray {
        val out = ByteArrayOutputStream()
        var block = first
        var remaining = count
        while (remaining > 0) {
            val n = minOf(remaining, MAX_BLOCKS_PER_READ)
            // Flags 0x02 (high data rate, non-addressed), READ MULTIPLE BLOCKS, first block, count - 1
            val response = try {
                nfcV.transceive(byteArrayOf(0x02, 0x23, block.toByte(), (n - 1).toByte()))
            } catch (e: TagLostException) {
                throw e
            } catch (e: IOException) {
                // No answer at all, e.g. while the I2C session has priority
                throw TagBusyException("No answer at block $block: ${e.message}")
            }
            if (response.isEmpty() || (response[0].toInt() and 0x01) != 0) {
                val code = response.getOrNull(1)?.let { "0x%02X".format(it) } ?: "none"
                throw TagBusyException("ISO 15693 read failed at block $block (error code $code)")
            }
            out.write(response, 1, response.size - 1)
            block += n
            remaining -= n
        }
        return out.toByteArray()
    }

    private companion object {
        const val BLOCK_SIZE = 4
        const val MAX_BLOCKS_PER_READ = 32
        const val NDEF_TLV = 0x03
    }
}
