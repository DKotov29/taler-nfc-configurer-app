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

package net.taler.xotpconfigurer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.taler.xotpconfigurer.EditTarget
import net.taler.xotpconfigurer.NfcUiState
import net.taler.xotpconfigurer.NfcViewModel
import net.taler.xotpconfigurer.Screen
import net.taler.xotpconfigurer.TapAction
import net.taler.xotpconfigurer.data.LanguagePreferences
import net.taler.xotpconfigurer.data.SavedConfigKind
import net.taler.xotpconfigurer.key
import net.taler.xotpconfigurer.model.SlotField

@Composable
fun ConfiguratorApp(viewModel: NfcViewModel, onEnableNfc: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it.resolve(context))
            viewModel.onMessageShown()
        }
    }
    BackHandler(enabled = state.screen != Screen.Discover && !state.isCommunicating) { viewModel.onBack() }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ScreenContent(state, viewModel, onEnableNfc)
        }
    }
}

@Composable
private fun ScreenContent(state: NfcUiState, viewModel: NfcViewModel, onEnableNfc: () -> Unit) {
    var loadingTemplate by rememberSaveable { mutableStateOf(false) }

    when (val screen = state.screen) {
        Screen.Discover -> DiscoverScreen(
            nfcStatus = state.nfcStatus,
            isCommunicating = state.isCommunicating,
            onEnableNfc = onEnableNfc,
            onSettings = viewModel::onOpenSettings
        )

        Screen.Overview -> state.session?.let { session ->
            OverviewScreen(
                session = session,
                globalEditor = state.globalEditor,
                onBack = viewModel::onBack,
                onSettings = viewModel::onOpenSettings,
                onEditGlobal = { viewModel.onEditField(EditTarget.Global(it)) },
                onSlotClick = viewModel::onSlotClick,
                onPageChange = viewModel::onPageChange,
                onApplyGlobal = viewModel::onApplyGlobal,
                onResetGlobal = viewModel::onResetGlobal,
                onSaveTemplate = viewModel::onSaveTemplate,
                onLoadTemplate = { loadingTemplate = true }
            )
        }

        is Screen.SlotDetails -> state.slotEditor?.let { editor ->
            SlotDetailsScreen(
                editor = editor,
                isActiveSlot = state.session?.info?.activeSlotIndex == editor.index,
                onBack = viewModel::onBack,
                onEdit = { viewModel.onEditField(EditTarget.Slot(it)) },
                onToggleMakeActive = viewModel::onToggleMakeActive,
                onApply = viewModel::onApplySlot,
                onReset = viewModel::onResetSlot,
                onSaveTemplate = viewModel::onSaveTemplate,
                onLoadTemplate = { loadingTemplate = true }
            )
        }

        is Screen.SetupSlot -> state.setupDraft?.let { draft ->
            SetupSlotScreen(
                draft = draft,
                onBack = viewModel::onBack,
                onValueChange = viewModel::onSetupValueChange,
                onApply = viewModel::onApplySetup,
                onSaveTemplate = viewModel::onSaveTemplate,
                onLoadTemplate = { loadingTemplate = true }
            )
        }

        is Screen.TapPrompt -> TapPromptScreen(
            action = screen.action,
            nfcStatus = state.nfcStatus,
            isCommunicating = state.isCommunicating,
            changeCount = when (screen.action) {
                is TapAction.ApplySlot -> state.slotEditor?.changeCount ?: 0
                TapAction.ApplyGlobal -> state.globalEditor?.changedFields?.size ?: 0
                else -> 0
            },
            onEnableNfc = onEnableNfc,
            onCancel = viewModel::onBack
        )

        is Screen.Failure -> FailureScreen(
            title = screen.title,
            message = screen.message,
            onRetry = viewModel::onRetry,
            onAbort = viewModel::onBack
        )

        is Screen.DeviceMismatch -> DeviceMismatchScreen(
            expectedSerial = screen.expectedSerial,
            detectedSerial = screen.detectedSerial,
            onRetry = viewModel::onRetry,
            onCancel = viewModel::onBack
        )

        Screen.Settings -> SettingsScreen(
            currentTheme = state.appTheme,
            onThemeSelect = viewModel::onThemeSelected,
            currentLanguage = LanguagePreferences.current(),
            onLanguageSelect = LanguagePreferences::set,
            onBack = viewModel::onBack,
            onSavedConfigs = viewModel::onOpenSavedConfigs,
            onDebug = viewModel::onOpenDebug
        )

        Screen.Debug -> DebugScreen(
            state = state.debug,
            nfcStatus = state.nfcStatus,
            isCommunicating = state.isCommunicating,
            onBack = viewModel::onBack,
            onEnableNfc = onEnableNfc,
            onDraftChange = viewModel::onDebugDraftChange,
            onArm = viewModel::onDebugArm,
            onDisarm = viewModel::onDebugDisarm,
            onClearLog = viewModel::onDebugClearLog
        )

        Screen.SavedConfigs -> SavedConfigsScreen(
            configs = state.savedConfigs,
            onBack = viewModel::onBack,
            onOpen = viewModel::onOpenTemplate,
            onDelete = viewModel::onDeleteTemplate
        )

        Screen.TemplateDetails -> state.templateEditor?.let { editor ->
            TemplateDetailsScreen(
                editor = editor,
                takenNames = viewModel.templateNames(editor.draft.kind) - editor.original.name,
                onBack = viewModel::onBack,
                onEdit = viewModel::onEditField,
                onRename = viewModel::onRenameTemplate,
                onSave = viewModel::onSaveTemplateChanges,
                onReset = viewModel::onResetTemplate,
                onDelete = viewModel::onDeleteOpenTemplate
            )
        }

        Screen.SaveTemplate -> state.templateSave?.let { save ->
            SaveTemplateScreen(
                save = save,
                templates = state.savedConfigs.filter { it.kind == save.kind },
                onBack = viewModel::onBack,
                onSaveNew = viewModel::onSaveAsNewTemplate,
                onSelectTarget = viewModel::onSelectMergeTarget,
                onChoiceChange = viewModel::onMergeChoiceChange,
                onConfirmMerge = viewModel::onConfirmMerge
            )
        }
    }

    state.editing?.let { target ->
        EditFieldSheet(
            target = target,
            initialValue = initialEditValue(state, target),
            error = state.editError,
            onDismiss = viewModel::onDismissEdit,
            onSave = viewModel::onSaveEdit
        )
    }

    if (loadingTemplate) {
        val templateKind = if (state.screen == Screen.Overview) SavedConfigKind.GLOBAL else SavedConfigKind.SLOT
        LoadTemplateDialog(
            configs = state.savedConfigs.filter { it.kind == templateKind },
            onDismiss = { loadingTemplate = false },
            onLoad = { viewModel.onLoadTemplate(it); loadingTemplate = false }
        )
    }
}

private fun initialEditValue(state: NfcUiState, target: EditTarget): String = if (state.screen == Screen.TemplateDetails) {
    state.templateEditor?.draft?.values?.get(target.key).orEmpty()
} else when (target) {
    is EditTarget.Slot -> if (target.field == SlotField.SECRET) "" else state.slotEditor?.let { target.field.raw(it.draft) }.orEmpty()
    is EditTarget.Global -> state.globalEditor?.let { target.field.raw(it.draft) }.orEmpty()
}
