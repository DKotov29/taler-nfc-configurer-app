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

package net.taler.xotpconfigurer.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.taler.xotpconfigurer.data.AppTheme
import net.taler.xotpconfigurer.ui.NfcTapArtwork
import net.taler.xotpconfigurer.ui.NfcTapPose
import net.taler.xotpconfigurer.ui.theme.OTPNFCConfigurerTheme

@Preview(name = "NFC Anim 1 — Idle")
@Composable
private fun IdlePreview() = PoseFrame(NfcTapPose.Idle)

@Preview(name = "NFC Anim 2 — Approach")
@Composable
private fun ApproachPreview() = PoseFrame(NfcTapPose.Approach)

@Preview(name = "NFC Anim 3 — Near")
@Composable
private fun NearPreview() = PoseFrame(NfcTapPose.Near)

@Preview(name = "NFC Anim 4 — Contact")
@Composable
private fun ContactPreview() = PoseFrame(NfcTapPose.Contact)

@Preview(name = "NFC Anim 5 — Retreat")
@Composable
private fun RetreatPreview() = PoseFrame(NfcTapPose.Retreat)

@Preview(name = "Contact, exchanging, dark")
@Composable
private fun ContactDarkPreview() = PoseFrame(NfcTapPose.Contact.copy(pulse = 0.4f), AppTheme.DARK)

@Preview(name = "NFC off")
@Composable
private fun DimmedPreview() = PoseFrame(NfcTapPose.Near, dimmed = true)

@Composable
private fun PoseFrame(pose: NfcTapPose, theme: AppTheme = AppTheme.LIGHT, dimmed: Boolean = false) {
    OTPNFCConfigurerTheme(appTheme = theme) {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(20.dp)) {
            NfcTapArtwork(pose, dimmed = dimmed)
        }
    }
}
