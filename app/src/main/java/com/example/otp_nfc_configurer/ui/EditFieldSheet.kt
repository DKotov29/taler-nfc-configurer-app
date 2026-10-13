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
import com.example.otp_nfc_configurer.model.SlotField
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
                            onValueChange = { value = it },
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
                        if (kind == InputKind.NUMBER && target is EditTarget.Slot && target.field != SlotField.FRACTION) {
                            Spacer(Modifier.padding(start = 8.dp))
                            Stepper(
                                onDecrement = { value = ((value.toIntOrNull() ?: 0) - 1).toString() },
                                onIncrement = { value = ((value.toIntOrNull() ?: 0) + 1).toString() }
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
