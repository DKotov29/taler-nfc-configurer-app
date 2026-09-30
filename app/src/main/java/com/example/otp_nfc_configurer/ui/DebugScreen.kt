package com.example.otp_nfc_configurer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.otp_nfc_configurer.NfcStatus
import com.example.otp_nfc_configurer.debug.BuiltRequest
import com.example.otp_nfc_configurer.debug.DebugCommandBuilder
import com.example.otp_nfc_configurer.debug.DebugDraft
import com.example.otp_nfc_configurer.debug.DebugExchange
import com.example.otp_nfc_configurer.debug.DebugState
import com.example.otp_nfc_configurer.debug.DebugTapMode
import com.example.otp_nfc_configurer.debug.PayloadMode
import com.example.otp_nfc_configurer.protocol.PacketField
import com.example.otp_nfc_configurer.protocol.PacketInspector
import com.example.otp_nfc_configurer.protocol.ProtocolCodec
import com.example.otp_nfc_configurer.ui.theme.Border
import com.example.otp_nfc_configurer.ui.theme.ErrorRed
import com.example.otp_nfc_configurer.ui.theme.Primary
import com.example.otp_nfc_configurer.ui.theme.SurfaceMuted
import com.example.otp_nfc_configurer.ui.theme.TextPrimary
import com.example.otp_nfc_configurer.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Settings → Debug Console: compose raw protocol commands, send them on the next tap and inspect the answer. */
@Composable
fun DebugScreen(
    state: DebugState,
    nfcStatus: NfcStatus,
    isCommunicating: Boolean,
    onBack: () -> Unit,
    onEnableNfc: () -> Unit,
    onDraftChange: (DebugDraft) -> Unit,
    onArm: (DebugTapMode) -> Unit,
    onDisarm: () -> Unit,
    onClearLog: () -> Unit
) {
    val draft = state.draft
    val built = remember(draft) { DebugCommandBuilder.build(draft) }

    FullScreen {
        AppTopBar(title = "Debug Console", onBack = onBack)
        NfcStatusBanner(nfcStatus, onEnableNfc)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard(title = "Command") {
                CommandEditor(draft, onDraftChange, built)
            }

            SectionCard(title = "Request preview") {
                when (built) {
                    is BuiltRequest.Error -> Text(built.message, color = ErrorRed, fontSize = 13.sp)
                    is BuiltRequest.Packet -> PacketView(
                        bytes = built.bytes,
                        fields = PacketInspector.requestFields(built.bytes)
                    )
                }
            }

            if (state.armed != null) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    WaitingIndicator(isCommunicating)
                    Text(
                        if (state.armed == DebugTapMode.SEND) "Tap the device to send the request"
                        else "Tap the device to read its current NDEF record",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    TextButton(onClick = onDisarm) { Text("Cancel", color = TextSecondary) }
                }
            } else {
                PrimaryButton(
                    "Send on next tap",
                    onClick = { onArm(DebugTapMode.SEND) },
                    enabled = built is BuiltRequest.Packet
                )
                OutlineButton("Read tag only", { onArm(DebugTapMode.READ) }, Modifier.fillMaxWidth())
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Log (${state.log.size})", Modifier.weight(1f))
                if (state.log.isNotEmpty()) TextButton(onClick = onClearLog) { Text("Clear") }
            }
            state.log.forEach { ExchangeCard(it) }
        }
    }
}

@Composable
private fun CommandEditor(draft: DebugDraft, onChange: (DebugDraft) -> Unit, built: BuiltRequest) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchRow("Edit raw packet (hex)", draft.rawPacket != null) { raw ->
            val prefill = (built as? BuiltRequest.Packet)?.bytes?.let(PacketInspector::hex).orEmpty()
            onChange(draft.copy(rawPacket = if (raw) prefill else null))
        }
        if (draft.rawPacket != null) {
            MonoInput("Packet", draft.rawPacket, "Complete packet incl. checksum, e.g. 13 00 04 05 12") {
                onChange(draft.copy(rawPacket = it))
            }
            return@Column
        }

        OptionDropdown(
            label = "Operation",
            options = PacketInspector.opCodes.map { (op, name) -> op to "0b%s %s".format(Integer.toBinaryString(op).padStart(4, '0'), name) },
            selected = draft.opCode
        ) { op ->
            val next = draft.copy(opCode = op)
            // Keep the parameter if it exists for the new operation.
            onChange(if (next.paramOptions.any { it.id == draft.param }) next else next.copy(param = next.paramOptions.first().id))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (draft.hasSlot) {
                SmallNumberInput(if (draft.hasEndSlot) "Start slot" else "Slot ID", draft.slot, Modifier.weight(1f)) {
                    onChange(draft.copy(slot = it))
                }
            }
            if (draft.hasEndSlot) {
                SmallNumberInput("End slot", draft.endSlot, Modifier.weight(1f)) { onChange(draft.copy(endSlot = it)) }
            }
        }
        if (draft.hasParam) {
            OptionDropdown(
                label = "Parameter",
                options = draft.paramOptions.map { it.id to it.label },
                selected = draft.param
            ) { onChange(draft.copy(param = it)) }
        }
        if (draft.hasPayload) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = draft.payloadMode == PayloadMode.TYPED,
                    onClick = { onChange(draft.copy(payloadMode = PayloadMode.TYPED)) },
                    label = { Text("Typed value") }
                )
                FilterChip(
                    selected = draft.payloadMode == PayloadMode.HEX,
                    onClick = { onChange(draft.copy(payloadMode = PayloadMode.HEX)) },
                    label = { Text("Hex bytes") }
                )
            }
            MonoInput("Payload", draft.payload, DebugCommandBuilder.payloadHint(draft)) {
                onChange(draft.copy(payload = it))
            }
        }

        var advanced by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { advanced = !advanced }) {
            Text(if (advanced) "Hide advanced" else "Advanced: version, checksum")
        }
        if (advanced) {
            SmallNumberInput("Protocol version (0–15)", draft.version, Modifier.fillMaxWidth()) {
                onChange(draft.copy(version = it))
            }
            SwitchRow("Automatic XOR checksum", draft.autoChecksum) { onChange(draft.copy(autoChecksum = it)) }
            if (!draft.autoChecksum) {
                MonoInput("Checksum byte", draft.manualChecksum, "One hex byte, e.g. FF to provoke ERR_CHECKSUM") {
                    onChange(draft.copy(manualChecksum = it))
                }
            }
        }
    }
}

@Composable
private fun ExchangeCard(exchange: DebugExchange) {
    val time = remember(exchange.timestamp) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(exchange.timestamp))
    }
    SectionCard {
        Text(
            "$time · ${exchange.durationMs} ms · UID ${exchange.serial}",
            fontSize = 11.sp,
            color = TextSecondary,
            fontFamily = FontFamily.Monospace
        )
        exchange.request?.let {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Request")
            PacketView(it, PacketInspector.requestFields(it))
        }
        exchange.response?.let {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Response")
            PacketView(it, PacketInspector.responseFields(exchange.request, it))
        }
        exchange.error?.let {
            Spacer(Modifier.height(8.dp))
            Text("Error: $it", color = ErrorRed, fontSize = 13.sp)
        }
        exchange.tagRecord?.let { record ->
            Spacer(Modifier.height(8.dp))
            SectionLabel("Tag content")
            Text(
                "${record.tnfName} · ${record.type} · ${record.payload.size} B",
                fontSize = 12.sp,
                color = TextSecondary
            )
            val fields = if (ProtocolCodec.isResponse(record.payload)) {
                // A protocol response stored on the tag can be parsed without knowing the request.
                PacketInspector.responseFields(exchange.request, record.payload)
            } else {
                listOf(PacketField("Payload", record.payload, "\"${String(record.payload, Charsets.UTF_8)}\""))
            }
            PacketView(record.payload, fields)
        }
    }
}

/** Hex line, annotated fields and an expandable per-byte binary table. */
@Composable
private fun PacketView(bytes: ByteArray, fields: List<PacketField>) {
    var showBinary by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 4.dp)) {
        SelectionContainer {
            Text(
                PacketInspector.hex(bytes),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = TextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceMuted, RoundedCornerShape(6.dp))
                    .padding(8.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        fields.forEach { field ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(field.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(96.dp))
                Column(Modifier.weight(1f)) {
                    if (field.bytes.isNotEmpty()) {
                        Text(PacketInspector.hex(field.bytes), fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Primary)
                    }
                    Text(field.meaning, fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
        TextButton(onClick = { showBinary = !showBinary }) {
            Text(if (showBinary) "Hide binary" else "Show binary (${bytes.size} B)", fontSize = 12.sp)
        }
        if (showBinary) {
            SelectionContainer {
                Text(
                    bytes.mapIndexed { i, b ->
                        "%3d  %02X  %s  %c".format(i, b, PacketInspector.binary(b), PacketInspector.ascii(b))
                    }.joinToString("\n"),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TextPrimary
                )
            }
        }
        HorizontalDivider(color = Border)
    }
}

@Composable
private fun OptionDropdown(label: String, options: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    options.firstOrNull { it.first == selected }?.second ?: "0x%02X".format(selected),
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, text) ->
                    DropdownMenuItem(
                        text = { Text(text, fontFamily = FontFamily.Monospace) },
                        onClick = { onSelect(value); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallNumberInput(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Border)
    )
}

@Composable
private fun MonoInput(label: String, value: String, hint: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = { Text(hint) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Border)
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
