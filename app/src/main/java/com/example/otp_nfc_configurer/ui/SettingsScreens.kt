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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onBack: () -> Unit,
    onSavedConfigs: () -> Unit,
    onDebug: () -> Unit
) {
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }

    FullScreen {
        AppTopBar(title = "Settings", onBack = onBack)
        SettingsEntry("Saved Configurations", onSavedConfigs)
        SettingsEntry("Debug Console", onDebug)
        SettingsEntry(
            title = "Theme (${currentTheme.title})",
            onClick = { showThemeDialog = true }
        )
    }
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose Theme") },
            text = {
                Column {
                    AppTheme.entries.forEach { theme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onThemeSelect(theme)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = theme == currentTheme,
                                onClick = {
                                    onThemeSelect(theme)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(theme.title, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Close") }
            }
        )
    }
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
        AppTopBar(title = "Saved Templates", onBack = onBack)
        if (configs.isEmpty()) {
            Text(
                "No templates saved yet. Use \"Save Template\" on the device or slot screen.",
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
                        Text(kindLabel(config.kind), fontSize = 12.sp, color = TextSecondary)
                    }
                    IconButton(onClick = { pendingDelete = "${config.kind}:${config.name}" }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete ${config.name}", tint = TextSecondary)
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
            title = { Text("Delete template?") },
            text = { Text("\"${toDelete.name}\" will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(toDelete); pendingDelete = null }) { Text("Delete", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SaveTemplateDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Template") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun LoadTemplateDialog(configs: List<SavedConfig>, onDismiss: () -> Unit, onLoad: (SavedConfig) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Load Template") },
        text = {
            if (configs.isEmpty()) {
                Text("No matching templates saved.", color = TextSecondary)
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun kindLabel(kind: SavedConfigKind) = when (kind) {
    SavedConfigKind.SLOT -> "Slot configuration"
    SavedConfigKind.GLOBAL -> "Device configuration"
}
