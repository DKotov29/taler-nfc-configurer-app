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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.otp_nfc_configurer.R
import com.example.otp_nfc_configurer.SetupDraft
import com.example.otp_nfc_configurer.SlotEditor
import com.example.otp_nfc_configurer.model.InputKind
import com.example.otp_nfc_configurer.model.SlotField
import com.example.otp_nfc_configurer.model.UiText
import com.example.otp_nfc_configurer.model.limitedInput
import com.example.otp_nfc_configurer.protocol.Algorithm
import com.example.otp_nfc_configurer.ui.theme.Border
import com.example.otp_nfc_configurer.ui.theme.Primary
import com.example.otp_nfc_configurer.ui.theme.PrimaryContainer
import com.example.otp_nfc_configurer.ui.theme.SurfaceMuted
import com.example.otp_nfc_configurer.ui.theme.TextPrimary
import com.example.otp_nfc_configurer.ui.theme.TextSecondary

/** 3. Slot Details incl. pending changes (3b). */
@Composable
fun SlotDetailsScreen(
    editor: SlotEditor,
    isActiveSlot: Boolean,
    onBack: () -> Unit,
    onEdit: (SlotField) -> Unit,
    onToggleMakeActive: () -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onSaveTemplate: () -> Unit,
    onLoadTemplate: () -> Unit
) {
    val changed = editor.changedFields
    FullScreen {
        AppTopBar(title = stringResource(R.string.slot_details_title), onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.slot_details_heading, editor.index + 1),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            if (isActiveSlot) {
                StatusChip(stringResource(R.string.slot_active_chip))
            } else {
                OutlinedButton(
                    onClick = onToggleMakeActive,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (editor.makeActive) Primary else Border),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        containerColor = if (editor.makeActive) PrimaryContainer else PrimaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        stringResource(if (editor.makeActive) R.string.slot_make_active_pending else R.string.slot_make_active),
                        color = Primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
            SectionCard {
                TemplateButtons(onSave = onSaveTemplate, onLoad = onLoadTemplate)
                SlotField.entries.forEach { field ->
                    InfoRow(
                        label = stringResource(field.label),
                        value = if (field == SlotField.SECRET && editor.newSecret != null) stringResource(R.string.slot_new_secret)
                        else field.display(editor.draft).asString(),
                        pending = field in changed,
                        onEdit = { onEdit(field) },
                        showDivider = field != SlotField.entries.last()
                    )
                }
            }
        }
        ApplyChangesBar(changeCount = editor.changeCount, onApply = onApply, onReset = onReset)
    }
}

/** 4b. Setup Blank Slot */
@Composable
fun SetupSlotScreen(
    draft: SetupDraft,
    onBack: () -> Unit,
    onValueChange: (SlotField, String) -> Unit,
    onApply: () -> Unit,
    onSaveTemplate: () -> Unit,
    onLoadTemplate: () -> Unit
) {
    FullScreen {
        AppTopBar(title = stringResource(R.string.setup_title, draft.index + 1), onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.setup_scan_qr), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                SectionLabel(stringResource(R.string.setup_coming_soon))
            }
            TemplateButtons(onSave = onSaveTemplate, onLoad = onLoadTemplate)

            val setupOrder = listOf(
                SlotField.ALGORITHM, SlotField.SECRET, SlotField.DIGITS, SlotField.INTERVAL,
                SlotField.SLOT_NAME, SlotField.CURRENCY, SlotField.FRACTION, SlotField.MERCHANT_TEMPLATE
            )
            setupOrder.forEach { field ->
                Spacer(Modifier.height(4.dp))
                SectionLabel(stringResource(field.label))
                val value = draft.values[field].orEmpty()
                if (field.kind == InputKind.ALGORITHM) {
                    AlgorithmSelector(selectedCode = value.toIntOrNull(), onSelect = { onValueChange(field, it.code.toString()) })
                } else {
                    FieldTextInput(field = field, value = value, error = draft.errors[field], onValueChange = { onValueChange(field, it) })
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PrimaryButton(stringResource(R.string.setup_apply), onApply)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.setup_apply_note), fontSize = 12.sp, color = TextSecondary)
        }
    }
}

@Composable
fun AlgorithmSelector(selectedCode: Int?, onSelect: (Algorithm) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Border, RoundedCornerShape(12.dp))
    ) {
        Algorithm.entries.forEachIndexed { i, algorithm ->
            val selected = algorithm.code == selectedCode
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(algorithm) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected, onClick = { onSelect(algorithm) })
                Text(
                    algorithm.label,
                    color = if (selected) Primary else TextPrimary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            if (i < Algorithm.entries.lastIndex) HorizontalDivider(color = Border)
        }
    }
}

@Composable
fun FieldTextInput(
    field: SlotField,
    value: String,
    error: UiText?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(limitedInput(value, it, field::limit)) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        isError = error != null,
        supportingText = { Text((error ?: field.hint).asString(), color = if (error != null) androidx.compose.material3.MaterialTheme.colorScheme.error else TextSecondary) },
        visualTransformation = if (field.kind == InputKind.SECRET) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = when (field.kind) {
                InputKind.NUMBER -> KeyboardType.Number
                InputKind.SECRET -> KeyboardType.Password
                else -> KeyboardType.Text
            }
        ),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedBorderColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
fun Stepper(onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GrayButton("−", onDecrement, Modifier.width(56.dp))
        GrayButton("+", onIncrement, Modifier.width(56.dp))
    }
}
