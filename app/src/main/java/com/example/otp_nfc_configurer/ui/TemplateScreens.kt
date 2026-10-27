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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.otp_nfc_configurer.EditTarget
import com.example.otp_nfc_configurer.MergeChoice
import com.example.otp_nfc_configurer.MergeSource
import com.example.otp_nfc_configurer.R
import com.example.otp_nfc_configurer.TemplateEditor
import com.example.otp_nfc_configurer.TemplateSave
import com.example.otp_nfc_configurer.data.SavedConfig
import com.example.otp_nfc_configurer.data.SavedConfigKind
import com.example.otp_nfc_configurer.display
import com.example.otp_nfc_configurer.fields
import com.example.otp_nfc_configurer.hint
import com.example.otp_nfc_configurer.inputKind
import com.example.otp_nfc_configurer.key
import com.example.otp_nfc_configurer.label
import com.example.otp_nfc_configurer.limit
import com.example.otp_nfc_configurer.model.InputKind
import com.example.otp_nfc_configurer.model.UiText
import com.example.otp_nfc_configurer.model.limitedInput
import com.example.otp_nfc_configurer.templateNameError
import com.example.otp_nfc_configurer.ui.theme.ErrorRed
import com.example.otp_nfc_configurer.ui.theme.TextPrimary
import com.example.otp_nfc_configurer.ui.theme.TextSecondary

/** 8a. Template Details: the stored values of one template, editable like a slot. */
@Composable
fun TemplateDetailsScreen(
    editor: TemplateEditor,
    takenNames: List<String>,
    onBack: () -> Unit,
    onEdit: (EditTarget) -> Unit,
    onRename: (String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit
) {
    val draft = editor.draft
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    FullScreen {
        AppTopBar(title = draft.name, onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusChip(stringResource(kindLabel(draft.kind)))
            SectionCard {
                InfoRow(
                    label = stringResource(R.string.template_name),
                    value = draft.name,
                    pending = editor.renamed,
                    onEdit = { renaming = true }
                )
            }
            SectionCard {
                val fields = draft.kind.fields
                fields.forEach { field ->
                    InfoRow(
                        label = stringResource(field.label),
                        value = field.display(draft.values[field.key]).asString(),
                        pending = field.key in editor.changedKeys,
                        onEdit = { onEdit(field) },
                        showDivider = field != fields.last()
                    )
                }
            }
            OutlineButton(
                stringResource(R.string.template_delete), { deleting = true }, Modifier.fillMaxWidth(),
                contentColor = MaterialTheme.colorScheme.error
            )
        }
        ApplyChangesBar(
            changeCount = editor.changeCount,
            onApply = onSave,
            onReset = onReset,
            applyLabel = stringResource(R.string.template_save_changes, editor.changeCount),
            noChangesLabel = stringResource(R.string.template_no_changes)
        )
    }
    if (renaming) {
        TemplateNameDialog(
            initialName = draft.name,
            takenNames = takenNames,
            onDismiss = { renaming = false },
            onConfirm = { onRename(it); renaming = false }
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.template_delete_title)) },
            text = { Text(stringResource(R.string.template_delete_message, editor.original.name)) },
            confirmButton = {
                TextButton(onClick = { deleting = false; onDelete() }) { Text(stringResource(R.string.action_delete), color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun TemplateNameDialog(
    initialName: String,
    takenNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val error = templateNameError(name, takenNames)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.template_rename)) },
        text = { TemplateNameInput(name, error, onValueChange = { name = it }) },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = error == null) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun TemplateNameInput(name: String, error: UiText?, onValueChange: (String) -> Unit) {
    // An empty name is not worth an error message while the user has not typed anything yet.
    val shownError = error.takeIf { name.isNotBlank() }
    OutlinedTextField(
        value = name,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.template_name)) },
        isError = shownError != null,
        supportingText = shownError?.let { { Text(it.asString()) } },
        shape = RoundedCornerShape(10.dp)
    )
}

/** 9. Save Template: choose a new or an existing template; for an existing one, merge field by field (9a). */
@Composable
fun SaveTemplateScreen(
    save: TemplateSave,
    templates: List<SavedConfig>,
    onBack: () -> Unit,
    onSaveNew: (String) -> Unit,
    onSelectTarget: (SavedConfig) -> Unit,
    onChoiceChange: (EditTarget, MergeChoice) -> Unit,
    onConfirmMerge: () -> Unit
) {
    val target = save.target
    if (target == null) {
        SaveTargetChooser(save, templates, onBack, onSaveNew, onSelectTarget)
    } else {
        MergeTemplate(save, target, onBack, onChoiceChange, onConfirmMerge)
    }
}

@Composable
private fun SaveTargetChooser(
    save: TemplateSave,
    templates: List<SavedConfig>,
    onBack: () -> Unit,
    onSaveNew: (String) -> Unit,
    onSelectTarget: (SavedConfig) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    val error = templateNameError(name, templates.map { it.name })
    FullScreen {
        AppTopBar(title = stringResource(R.string.template_save), onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard(title = stringResource(R.string.template_save_new)) {
                TemplateNameInput(name, error, onValueChange = { name = it })
                PrimaryButton(stringResource(R.string.template_create), { onSaveNew(name) }, enabled = error == null)
            }
            SectionCard(title = stringResource(R.string.template_save_existing)) {
                if (templates.isEmpty()) {
                    Text(
                        stringResource(R.string.template_none_matching),
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                templates.forEach { template ->
                    val differences = save.copy(target = template).differing.size
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelectTarget(template) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(template.name, fontSize = 15.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (differences == 0) stringResource(R.string.template_identical)
                                else pluralStringResource(R.plurals.template_differences, differences, differences),
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (template != templates.last()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun MergeTemplate(
    save: TemplateSave,
    target: SavedConfig,
    onBack: () -> Unit,
    onChoiceChange: (EditTarget, MergeChoice) -> Unit,
    onConfirm: () -> Unit
) {
    val differing = save.differing
    val identical = save.kind.fields.size - differing.size
    FullScreen {
        AppTopBar(title = stringResource(R.string.template_merge_title, target.name), onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.template_merge_hint), fontSize = 13.sp, color = TextSecondary)
            differing.forEach { field ->
                MergeField(
                    field = field,
                    current = save.values[field.key],
                    stored = target.values[field.key],
                    choice = save.choices[field.key] ?: MergeChoice(),
                    onChoiceChange = { onChoiceChange(field, it) }
                )
            }
            if (identical > 0) {
                Text(
                    pluralStringResource(R.plurals.template_merge_identical, identical, identical),
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            PrimaryButton(stringResource(R.string.template_save_into, target.name), onConfirm)
        }
    }
}

@Composable
private fun MergeField(
    field: EditTarget,
    current: String?,
    stored: String?,
    choice: MergeChoice,
    onChoiceChange: (MergeChoice) -> Unit
) {
    SectionCard(title = stringResource(field.label)) {
        MergeOption(
            title = stringResource(R.string.template_merge_current),
            value = field.display(current).asString(),
            selected = choice.source == MergeSource.CURRENT,
            onSelect = { onChoiceChange(choice.copy(source = MergeSource.CURRENT)) }
        )
        MergeOption(
            title = stringResource(R.string.template_merge_stored),
            value = field.display(stored).asString(),
            selected = choice.source == MergeSource.TEMPLATE,
            onSelect = { onChoiceChange(choice.copy(source = MergeSource.TEMPLATE)) }
        )
        MergeOption(
            title = stringResource(R.string.template_merge_custom),
            value = null,
            selected = choice.source == MergeSource.CUSTOM,
            onSelect = { onChoiceChange(choice.copy(source = MergeSource.CUSTOM)) }
        )
        if (choice.source == MergeSource.CUSTOM) {
            if (field.inputKind == InputKind.ALGORITHM) {
                AlgorithmSelector(
                    selectedCode = choice.custom.toIntOrNull(),
                    onSelect = { onChoiceChange(choice.copy(custom = it.code.toString())) }
                )
            } else {
                OutlinedTextField(
                    value = choice.custom,
                    onValueChange = { onChoiceChange(choice.copy(custom = limitedInput(choice.custom, it, field::limit))) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = choice.error != null,
                    supportingText = {
                        Text(
                            (choice.error ?: field.hint).asString(),
                            color = if (choice.error != null) MaterialTheme.colorScheme.error else TextSecondary
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (field.inputKind == InputKind.NUMBER) KeyboardType.Number else KeyboardType.Text
                    ),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outline)
                )
            }
        }
    }
}

@Composable
private fun MergeOption(title: String, value: String?, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(
            title,
            fontSize = 14.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        if (value != null) {
            Text(
                value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            )
        }
    }
}

fun kindLabel(kind: SavedConfigKind) = when (kind) {
    SavedConfigKind.SLOT -> R.string.template_kind_slot
    SavedConfigKind.GLOBAL -> R.string.template_kind_global
}
