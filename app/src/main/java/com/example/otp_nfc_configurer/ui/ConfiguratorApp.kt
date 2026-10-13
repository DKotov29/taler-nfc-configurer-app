package com.example.otp_nfc_configurer.ui

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
import com.example.otp_nfc_configurer.EditTarget
import com.example.otp_nfc_configurer.NfcUiState
import com.example.otp_nfc_configurer.NfcViewModel
import com.example.otp_nfc_configurer.Screen
import com.example.otp_nfc_configurer.TapAction
import com.example.otp_nfc_configurer.data.LanguagePreferences
import com.example.otp_nfc_configurer.data.SavedConfigKind
import com.example.otp_nfc_configurer.model.SlotField

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
    var templateDialog by rememberSaveable { mutableStateOf<TemplateDialog?>(null) }

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
                onSaveTemplate = { templateDialog = TemplateDialog.SAVE },
                onLoadTemplate = { templateDialog = TemplateDialog.LOAD }
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
                onSaveTemplate = { templateDialog = TemplateDialog.SAVE },
                onLoadTemplate = { templateDialog = TemplateDialog.LOAD }
            )
        }

        is Screen.SetupSlot -> state.setupDraft?.let { draft ->
            SetupSlotScreen(
                draft = draft,
                onBack = viewModel::onBack,
                onValueChange = viewModel::onSetupValueChange,
                onApply = viewModel::onApplySetup,
                onSaveTemplate = { templateDialog = TemplateDialog.SAVE },
                onLoadTemplate = { templateDialog = TemplateDialog.LOAD }
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
            onDelete = viewModel::onDeleteTemplate
        )
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

    val templateKind = if (state.screen == Screen.Overview) SavedConfigKind.GLOBAL else SavedConfigKind.SLOT
    when (templateDialog) {
        TemplateDialog.SAVE -> SaveTemplateDialog(
            onDismiss = { templateDialog = null },
            onSave = { viewModel.onSaveTemplate(it); templateDialog = null }
        )
        TemplateDialog.LOAD -> LoadTemplateDialog(
            configs = state.savedConfigs.filter { it.kind == templateKind },
            onDismiss = { templateDialog = null },
            onLoad = { viewModel.onLoadTemplate(it); templateDialog = null }
        )
        null -> Unit
    }
}

private enum class TemplateDialog { SAVE, LOAD }

private fun initialEditValue(state: NfcUiState, target: EditTarget): String = when (target) {
    is EditTarget.Slot -> if (target.field == SlotField.SECRET) "" else state.slotEditor?.let { target.field.raw(it.draft) }.orEmpty()
    is EditTarget.Global -> state.globalEditor?.let { target.field.raw(it.draft) }.orEmpty()
}
