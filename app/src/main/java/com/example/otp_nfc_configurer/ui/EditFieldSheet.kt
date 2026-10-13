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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.otp_nfc_configurer.EditTarget
import com.example.otp_nfc_configurer.R
import com.example.otp_nfc_configurer.model.InputKind
import com.example.otp_nfc_configurer.model.limitedInput
import com.example.otp_nfc_configurer.model.UiText
import com.example.otp_nfc_configurer.ui.theme.Border
import com.example.otp_nfc_configurer.ui.theme.TextPrimary
import com.example.otp_nfc_configurer.ui.theme.TextSecondary

/** 3a / 2d: bottom sheet editing one parameter. Changes are only kept locally until "Apply Changes". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFieldSheet(
    target: EditTarget,
    initialValue: String,
    error: UiText?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val (labelRes, hint, kind) = when (target) {
        is EditTarget.Slot -> Triple(target.field.label, target.field.hint, target.field.kind)
        is EditTarget.Global -> Triple(target.field.label, target.field.hint, InputKind.TEXT)
    }
    val limit: (String) -> String = when (target) {
        is EditTarget.Slot -> target.field::limit
        is EditTarget.Global -> target.field::limit
    }
    val range = (target as? EditTarget.Slot)?.field?.range
    val label = stringResource(labelRes)
    var value by rememberSaveable(target) { mutableStateOf(initialValue) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .imePadding()
        ) {
            Text(stringResource(R.string.edit_field, label), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            SectionLabel(label)
            Spacer(Modifier.height(8.dp))

            when (kind) {
                InputKind.ALGORITHM -> AlgorithmSelector(
                    selectedCode = value.toIntOrNull(),
                    onSelect = { value = it.code.toString() }
                )
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = limitedInput(value, it, limit) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            isError = error != null,
                            placeholder = { if (kind == InputKind.SECRET) Text(stringResource(R.string.edit_secret_placeholder, "JBSWY3DPEHPK3PXP")) },
                            visualTransformation = if (kind == InputKind.SECRET) PasswordVisualTransformation() else VisualTransformation.None,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = when (kind) {
                                    InputKind.NUMBER -> KeyboardType.Number
                                    InputKind.SECRET -> KeyboardType.Password
                                    else -> KeyboardType.Uri
                                }
                            ),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outline)
                        )
                        if (range != null) {
                            Spacer(Modifier.padding(start = 8.dp))
                            Stepper(
                                onDecrement = { value = ((value.toIntOrNull() ?: range.first) - 1).coerceIn(range).toString() },
                                onIncrement = { value = ((value.toIntOrNull() ?: range.first) + 1).coerceIn(range).toString() }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = (error ?: hint).asString(),
                fontSize = 12.sp,
                color = if (error != null) MaterialTheme.colorScheme.error else TextSecondary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlineButton(stringResource(R.string.action_cancel), onDismiss, Modifier.weight(1f))
                DarkButton(stringResource(R.string.action_save), { onSave(value) }, Modifier.weight(1f))
            }
        }
    }
}
