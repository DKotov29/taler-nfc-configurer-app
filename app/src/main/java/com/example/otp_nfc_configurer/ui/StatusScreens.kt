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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.otp_nfc_configurer.NfcStatus
import com.example.otp_nfc_configurer.TapAction
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
        AppTopBar(title = "Discover Device", onSettings = onSettings)
        NfcStatusBanner(nfcStatus, onEnableNfc)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CenteredMessage(
                heading = "Tap Phone to Device",
                description = "Ensure the merchant device is switched to configuration mode."
            ) { PulseIcon(animate = nfcStatus == NfcStatus.AVAILABLE) }
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
            "Load Slot", "Tap Phone to Load Slot",
            "Hold your phone near the device to fetch the configuration of slot ${action.index} (GET_SLOT)."
        )
        is TapAction.ApplySlot -> Triple(
            "Apply Changes", "Tap to Confirm Write",
            "Hold your phone near the device to transmit and execute $changeCount changed value(s) (SET_SLOT / SET_GLOBAL)."
        )
        is TapAction.SetupSlot -> Triple(
            "Apply Changes", "Tap to Confirm Write",
            "Hold your phone near the device to write the complete configuration of slot ${action.index} (SET_SLOT)."
        )
        TapAction.ApplyGlobal -> Triple(
            "Global Apply", "Apply Global Changes",
            "Tap device to apply $changeCount changed global parameter(s) (SET_GLOBAL)."
        )
        is TapAction.LoadSlotNames, TapAction.Discover, TapAction.DebugSend, TapAction.DebugRead -> Triple(
            "Discover Device", "Tap Phone to Device", "Hold your phone near the device."
        )
    }
    FullScreen {
        AppTopBar(title = title, onBack = onCancel)
        NfcStatusBanner(nfcStatus, onEnableNfc)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CenteredMessage(heading = heading, description = description) {
                PulseIcon(animate = nfcStatus == NfcStatus.AVAILABLE)
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
                    Text("Cancel", color = TextSecondary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** 3c. Slot Write Failed, 2g. Global Write Fail */
@Composable
fun FailureScreen(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onAbort: () -> Unit
) {
    FullScreen {
        AppTopBar(title = title)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CenteredMessage(heading = title, description = message) {
                PulseIcon(color = ErrorRed, container = ErrorContainer, animate = false) { ExclamationMark() }
            }
        }
        BottomActions(secondary = "Abort", onSecondary = onAbort, primary = "Retry", onPrimary = onRetry)
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
        AppTopBar(title = "Write Failed")
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CenteredMessage(
                heading = "Device Changed Mid-Write",
                description = "The NFC chip serial number no longer matches the device you scanned. " +
                        "To protect against writing to the wrong device, the operation was cancelled and no data was changed."
            ) {
                PulseIcon(color = WarningOrange, container = WarningContainer, animate = false) { ExclamationMark() }
            }
            Spacer(Modifier.height(24.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceMuted, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                SerialRow("Expected Serial", expectedSerial, TextPrimary)
                SerialRow("Detected Serial", detectedSerial, ErrorRed)
            }
        }
        BottomActions(secondary = "Cancel", onSecondary = onCancel, primary = "Try Again", onPrimary = onRetry)
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
                text = if (status == NfcStatus.DISABLED) "NFC is disabled" else "NFC not supported",
                fontWeight = FontWeight.Bold,
                color = ErrorRed
            )
            Text(
                text = if (status == NfcStatus.DISABLED) "Enable NFC to communicate with the device."
                else "This phone has no NFC hardware.",
                fontSize = 13.sp,
                color = TextPrimary
            )
        }
        if (status == NfcStatus.DISABLED) {
            Button(
                onClick = onEnableNfc,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Enable") }
        }
    }
}
