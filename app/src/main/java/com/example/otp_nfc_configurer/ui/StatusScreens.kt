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

package com.example.otp_nfc_configurer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.otp_nfc_configurer.NfcStatus
import com.example.otp_nfc_configurer.R
import com.example.otp_nfc_configurer.TapAction
import com.example.otp_nfc_configurer.model.UiText
import com.example.otp_nfc_configurer.model.uiPlural
import com.example.otp_nfc_configurer.ui.theme.ErrorContainer
import com.example.otp_nfc_configurer.ui.theme.ErrorRed
import com.example.otp_nfc_configurer.ui.theme.SurfaceMuted
import com.example.otp_nfc_configurer.ui.theme.TextPrimary
import com.example.otp_nfc_configurer.ui.theme.TextSecondary
import com.example.otp_nfc_configurer.ui.theme.WarningContainer
import com.example.otp_nfc_configurer.ui.theme.WarningOrange

/** 1. Discover Device */
@Composable
fun DiscoverScreen(
    nfcStatus: NfcStatus,
    isCommunicating: Boolean,
    onEnableNfc: () -> Unit,
    onSettings: () -> Unit
) {
    FullScreen {
        AppTopBar(title = stringResource(R.string.discover_title), onSettings = onSettings)
        NfcStatusBanner(nfcStatus, onEnableNfc)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CenteredMessage(
                heading = stringResource(R.string.discover_heading),
                description = stringResource(R.string.discover_desc)
            ) { NfcTapAnimation(active = nfcStatus == NfcStatus.AVAILABLE, communicating = isCommunicating) }
        }
        Box(Modifier.fillMaxWidth().padding(bottom = 96.dp), contentAlignment = Alignment.Center) {
            if (nfcStatus == NfcStatus.AVAILABLE) WaitingIndicator(isCommunicating)
        }
    }
}

/** 2c. Tap to Load Slot, X. Tap to Apply Changes, 2f. Global Tap to Apply */
@Composable
fun TapPromptScreen(
    action: TapAction,
    nfcStatus: NfcStatus,
    isCommunicating: Boolean,
    changeCount: Int,
    onEnableNfc: () -> Unit,
    onCancel: () -> Unit
) {
    val (title, heading, description) = when (action) {
        is TapAction.LoadSlot -> Triple(
            stringResource(R.string.tap_load_title), stringResource(R.string.tap_load_heading),
            stringResource(R.string.tap_load_desc, action.index + 1)
        )
        is TapAction.ApplySlot -> Triple(
            stringResource(R.string.tap_apply_title), stringResource(R.string.tap_apply_heading),
            uiPlural(R.plurals.tap_apply_desc, changeCount).asString()
        )
        is TapAction.SetupSlot -> Triple(
            stringResource(R.string.tap_apply_title), stringResource(R.string.tap_apply_heading),
            stringResource(R.string.tap_setup_desc, action.index + 1)
        )
        TapAction.ApplyGlobal -> Triple(
            stringResource(R.string.tap_global_title), stringResource(R.string.tap_global_heading),
            uiPlural(R.plurals.tap_global_desc, changeCount).asString()
        )
        is TapAction.LoadSlotNames, TapAction.Discover, TapAction.DebugSend, TapAction.DebugRead -> Triple(
            stringResource(R.string.discover_title), stringResource(R.string.discover_heading),
            stringResource(R.string.tap_generic_desc)
        )
    }
    FullScreen {
        AppTopBar(title = title, onBack = onCancel)
        NfcStatusBanner(nfcStatus, onEnableNfc)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CenteredMessage(heading = heading, description = description) {
                NfcTapAnimation(active = nfcStatus == NfcStatus.AVAILABLE, communicating = isCommunicating)
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (nfcStatus == NfcStatus.AVAILABLE) WaitingIndicator(isCommunicating)
            Spacer(Modifier.height(24.dp))
            if (!isCommunicating) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel), color = TextSecondary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** 3c. Slot Write Failed, 2g. Global Write Fail */
@Composable
fun FailureScreen(
    title: UiText,
    message: UiText,
    onRetry: () -> Unit,
    onAbort: () -> Unit
) {
    val titleText = title.asString()
    FullScreen {
        AppTopBar(title = titleText)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CenteredMessage(heading = titleText, description = message.asString()) {
                StatusIcon(color = ErrorRed, container = ErrorContainer) { ExclamationMark() }
            }
        }
        BottomActions(
            secondary = stringResource(R.string.action_abort), onSecondary = onAbort,
            primary = stringResource(R.string.action_retry), onPrimary = onRetry
        )
    }
}

/** 5. Device Mismatch */
@Composable
fun DeviceMismatchScreen(
    expectedSerial: String,
    detectedSerial: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    FullScreen {
        AppTopBar(title = stringResource(R.string.failure_write_title))
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CenteredMessage(
                heading = stringResource(R.string.mismatch_heading),
                description = stringResource(R.string.mismatch_desc)
            ) {
                StatusIcon(color = WarningOrange, container = WarningContainer) { ExclamationMark() }
            }
            Spacer(Modifier.height(24.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceMuted, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                SerialRow(stringResource(R.string.mismatch_expected), expectedSerial, TextPrimary)
                SerialRow(stringResource(R.string.mismatch_detected), detectedSerial, ErrorRed)
            }
        }
        BottomActions(
            secondary = stringResource(R.string.action_cancel), onSecondary = onCancel,
            primary = stringResource(R.string.action_try_again), onPrimary = onRetry
        )
    }
}

@Composable
private fun SerialRow(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun ExclamationMark() {
    Text("!", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun BottomActions(secondary: String, onSecondary: () -> Unit, primary: String, onPrimary: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextButton(onClick = onSecondary) {
            Text(secondary, color = TextSecondary, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        DarkButton(primary, onPrimary)
    }
}

@Composable
fun NfcStatusBanner(status: NfcStatus, onEnableNfc: () -> Unit) {
    if (status == NfcStatus.AVAILABLE) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(ErrorContainer, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = stringResource(if (status == NfcStatus.DISABLED) R.string.nfc_disabled_title else R.string.nfc_unsupported_title),
                fontWeight = FontWeight.Bold,
                color = ErrorRed
            )
            Text(
                text = stringResource(if (status == NfcStatus.DISABLED) R.string.nfc_disabled_desc else R.string.nfc_unsupported_desc),
                fontSize = 13.sp,
                color = TextPrimary
            )
        }
        if (status == NfcStatus.DISABLED) {
            Button(
                onClick = onEnableNfc,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                shape = RoundedCornerShape(8.dp)
            ) { Text(stringResource(R.string.action_enable)) }
        }
    }
}
