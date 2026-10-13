package com.example.otp_nfc_configurer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.otp_nfc_configurer.R
import com.example.otp_nfc_configurer.data.AppLanguage
import com.example.otp_nfc_configurer.data.AppTheme
import com.example.otp_nfc_configurer.data.SavedConfig
import com.example.otp_nfc_configurer.data.SavedConfigKind
import com.example.otp_nfc_configurer.ui.theme.Border
import com.example.otp_nfc_configurer.ui.theme.ErrorRed
import com.example.otp_nfc_configurer.ui.theme.SurfaceMuted
import com.example.otp_nfc_configurer.ui.theme.TextPrimary
import com.example.otp_nfc_configurer.ui.theme.TextSecondary

/** 7. Settings */
@Composable
fun SettingsScreen(
    currentTheme: AppTheme,
    onThemeSelect: (AppTheme) -> Unit,
    currentLanguage: AppLanguage,
    onLanguageSelect: (AppLanguage) -> Unit,
    onBack: () -> Unit,
    onSavedConfigs: () -> Unit,
    onDebug: () -> Unit
) {
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }

    FullScreen {
        AppTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
        SettingsEntry(stringResource(R.string.settings_saved_configs), onSavedConfigs)
        SettingsEntry(stringResource(R.string.settings_debug), onDebug)
        SettingsEntry(
            title = stringResource(R.string.settings_theme, stringResource(currentTheme.title)),
            onClick = { showThemeDialog = true }
        )
        SettingsEntry(
            title = stringResource(R.string.settings_language, languageName(currentLanguage)),
            onClick = { showLanguageDialog = true }
        )
    }
    if (showThemeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_theme_dialog),
            options = AppTheme.entries,
            selected = currentTheme,
            optionLabel = { stringResource(it.title) },
            onSelect = onThemeSelect,
            onDismiss = { showThemeDialog = false }
        )
    }
    if (showLanguageDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_language_dialog),
            options = AppLanguage.entries,
            selected = currentLanguage,
            optionLabel = ::languageName,
            // Translated name as a hint, unless it is the same as the native one.
            optionSubtitle = { language ->
                stringResource(language.label).takeIf { language.nativeName != null && it != language.nativeName }
            },
            onSelect = onLanguageSelect,
            onDismiss = { showLanguageDialog = false }
        )
    }
}

@Composable
private fun languageName(language: AppLanguage): String = language.nativeName ?: stringResource(language.label)

/** Single-choice dialog; selecting an option applies it and closes the dialog. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    optionSubtitle: @Composable (T) -> String? = { null }
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    val choose = {
                        onDismiss()
                        onSelect(option)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = choose)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = choose)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(optionLabel(option), color = MaterialTheme.colorScheme.onSurface)
                            optionSubtitle(option)?.let {
                                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun SettingsEntry(title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 8. Template List */
@Composable
fun SavedConfigsScreen(configs: List<SavedConfig>, onBack: () -> Unit, onDelete: (SavedConfig) -> Unit) {
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    FullScreen {
        AppTopBar(title = stringResource(R.string.templates_title), onBack = onBack)
        if (configs.isEmpty()) {
            Text(
                stringResource(R.string.templates_empty),
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            items(configs, key = { "${it.kind}:${it.name}" }) { config ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(config.name, fontSize = 15.sp, color = TextPrimary)
                        Text(stringResource(kindLabel(config.kind)), fontSize = 12.sp, color = TextSecondary)
                    }
                    IconButton(onClick = { pendingDelete = "${config.kind}:${config.name}" }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.template_delete_description, config.name), tint = TextSecondary)
                    }
                }
                HorizontalDivider(color = Border)
            }
        }
    }
    val toDelete = configs.firstOrNull { "${it.kind}:${it.name}" == pendingDelete }
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.template_delete_title)) },
            text = { Text(stringResource(R.string.template_delete_message, toDelete.name)) },
            confirmButton = {
                TextButton(onClick = { onDelete(toDelete); pendingDelete = null }) { Text(stringResource(R.string.action_delete), color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
fun SaveTemplateDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.template_save)) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.template_name)) })
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
fun LoadTemplateDialog(configs: List<SavedConfig>, onDismiss: () -> Unit, onLoad: (SavedConfig) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.template_load)) },
        text = {
            if (configs.isEmpty()) {
                Text(stringResource(R.string.template_none_matching), color = TextSecondary)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    configs.forEach { config ->
                        Text(
                            config.name,
                            fontSize = 15.sp,
                            color = TextPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLoad(config) }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}

private fun kindLabel(kind: SavedConfigKind) = when (kind) {
    SavedConfigKind.SLOT -> R.string.template_kind_slot
    SavedConfigKind.GLOBAL -> R.string.template_kind_global
}
