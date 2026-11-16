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

package net.taler.xotpconfigurer.model

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
