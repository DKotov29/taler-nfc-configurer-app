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

package net.taler.xotpconfigurer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import net.taler.xotpconfigurer.data.AppTheme
import net.taler.xotpconfigurer.data.SavedConfig
import net.taler.xotpconfigurer.data.SavedConfigKind
import net.taler.xotpconfigurer.data.SavedConfigRepository
import net.taler.xotpconfigurer.data.ThemePreferences
import net.taler.xotpconfigurer.debug.BuiltRequest
import net.taler.xotpconfigurer.debug.DebugCommandBuilder
import net.taler.xotpconfigurer.debug.DebugDraft
import net.taler.xotpconfigurer.debug.DebugExchange
import net.taler.xotpconfigurer.debug.DebugState
import net.taler.xotpconfigurer.debug.DebugTapMode
import net.taler.xotpconfigurer.model.FieldInput
import net.taler.xotpconfigurer.model.GlobalConfig
import net.taler.xotpconfigurer.model.GlobalField
import net.taler.xotpconfigurer.model.SlotConfig
import net.taler.xotpconfigurer.model.SlotField
import net.taler.xotpconfigurer.model.SlotSummary
import net.taler.xotpconfigurer.model.UiText
import net.taler.xotpconfigurer.model.errorText
import net.taler.xotpconfigurer.model.uiText
import net.taler.xotpconfigurer.nfc.DeviceClient
import net.taler.xotpconfigurer.nfc.NfcReader
import net.taler.xotpconfigurer.nfc.NfcTransport
import net.taler.xotpconfigurer.nfc.ReaderState
import net.taler.xotpconfigurer.nfc.TagReader
import net.taler.xotpconfigurer.nfc.TagResult
import net.taler.xotpconfigurer.protocol.Algorithm
import net.taler.xotpconfigurer.protocol.Protocol
import net.taler.xotpconfigurer.protocol.ProtocolCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NfcViewModel(
    private val savedConfigRepository: SavedConfigRepository,
    private val themePreferences: ThemePreferences,
    private val reader: TagReader
) : ViewModel() {

    private val _uiState = MutableStateFlow(NfcUiState())
    val uiState: StateFlow<NfcUiState> = _uiState.asStateFlow()

    /** An operation is talking to a tag (main thread only). */
    private var running = false

    /**
     * Armed action already executed on a tag generation. Android delivers a tag only once while it stays in
     * the field, so an action that stays armed after its run (e.g. a failed page load) waits for a new tap
     * instead of repeating on the same tag forever. Cleared when nothing is armed any more.
     */
    private var handled: Pair<TapAction, Long>? = null

    init {
        savedConfigRepository.configs
            .onEach { configs -> _uiState.update { it.copy(savedConfigs = configs) } }
            .launchIn(viewModelScope)

        themePreferences.theme
            .onEach { theme -> _uiState.update { it.copy(appTheme = theme) } }
            .launchIn(viewModelScope)

        reader.state
            .onEach { state -> _uiState.update { it.copy(nfcStatus = state.toNfcStatus()) } }
            .launchIn(viewModelScope)

        // The single rule for NFC: an armed action runs as soon as there is a tag to run it on, whether the
        // tag was just tapped or is still lying on the phone from a previous operation.
        combine(_uiState.map { it.armedAction }.distinctUntilChanged(), reader.tag, reader.state) { action, _, _ -> action }
            .onEach { action ->
                if (action == null) handled = null
                runArmedAction()
            }
            .launchIn(viewModelScope)
    }

    fun onThemeSelected(theme: AppTheme) {
        themePreferences.setTheme(theme)
    }

    // ---------- NFC ----------

    private fun runArmedAction() {
        if (running || reader.state.value != ReaderState.Active) return
        val action = _uiState.value.armedAction ?: return
        val handle = reader.tag.value ?: return
        if (handled == action to handle.generation) return
        handled = action to handle.generation
        running = true

        viewModelScope.launch {
            _uiState.update { it.copy(isCommunicating = true) }
            val result = reader.withTag(handle) { transport ->
                if (action == TapAction.DebugSend || action == TapAction.DebugRead) {
                    debugExchange(action, transport)
                } else {
                    execute(action, DeviceClient(transport), handle.serial)
                }
            }
            when (result) {
                is TagResult.Success -> Unit
                // Gone before anything happened (e.g. lifted long ago): silently wait for the next tap.
                // Gone in the middle: tell the user; the next tap repeats what is left.
                is TagResult.TagGone -> if (result.duringExchange) {
                    _uiState.update { it.copy(message = uiText(R.string.error_tag_lost)) }
                }
                is TagResult.Failed -> onActionFailed(action, result.error)
            }
            // Whatever is armed now was triggered by this tap: it needs a new tap or a change of action.
            handled = _uiState.value.armedAction?.let { it to handle.generation }
            _uiState.update { it.copy(isCommunicating = false) }
            running = false
            runArmedAction() // A newer tag may have arrived meanwhile.
        }
    }

    private fun execute(action: TapAction, client: DeviceClient, serial: String) {
        val session = _uiState.value.session
        if (action != TapAction.Discover && session != null && session.serial != serial) {
            _uiState.update {
                it.copy(screen = Screen.DeviceMismatch(session.serial, serial, action))
            }
            return
        }
        when (action) {
            TapAction.Discover -> discover(client, serial)
            is TapAction.LoadSlotNames -> loadSlotNames(client, action.page)
            is TapAction.LoadSlot -> loadSlot(client, action.index)
            is TapAction.ApplySlot -> applySlot(client, action.index)
            is TapAction.SetupSlot -> setupSlot(client, action.index)
            TapAction.ApplyGlobal -> applyGlobal(client)
            TapAction.DebugSend, TapAction.DebugRead -> Unit
        }
    }

    private fun discover(client: DeviceClient, serial: String) {
        val info = client.readDeviceInfo()
        _uiState.update {
            it.copy(
                screen = Screen.Overview,
                session = DeviceSession(serial = serial, info = info),
                globalEditor = null,
                slotEditor = null,
                setupDraft = null
            )
        }
        loadSlotNames(client, page = 0)
    }

    /** Loads the global parameters (if missing) and slot names of [page]; failures lead to state 2b. */
    private fun loadSlotNames(client: DeviceClient, page: Int) {
        updateSession { it.copy(page = page, pageLoad = LoadState.LOADING) }
        try {
            if (_uiState.value.session?.global == null) {
                val global = client.readGlobal()
                _uiState.update { state ->
                    state.copy(
                        session = state.session?.let { session ->
                            session.copy(
                                global = global,
                                info = session.info.copy(activeSlotIndex = global.activeSlotIndex)
                            )
                        },
                        globalEditor = GlobalEditor(original = global, draft = global)
                    )
                }
            }
            val range = _uiState.value.session?.pageRange(page) ?: return
            val names = if (range.isEmpty()) emptyList() else client.readSlotNames(range.first, range.last)
            updateSession { session ->
                session.copy(
                    slots = session.slots + range.zip(names),
                    pageLoad = if (session.page == page) LoadState.LOADED else session.pageLoad
                )
            }
        } catch (e: Exception) {
            updateSession { it.copy(pageLoad = LoadState.FAILED) }
        }
    }

    private fun loadSlot(client: DeviceClient, index: Int) {
        val config = client.readSlot(index)
        if (config.algorithm == ALG_NONE) {
            // ALG_NONE: the slot is not configured, continue with the setup form.
            updateSession { it.copy(slots = it.slots + (index to SlotSummary.Empty)) }
            _uiState.update { it.copy(screen = Screen.SetupSlot(index), setupDraft = defaultSetupDraft(index)) }
            return
        }
        _uiState.update {
            it.copy(
                screen = Screen.SlotDetails(index),
                slotEditor = SlotEditor(index = index, original = config, draft = config)
            )
        }
    }

    private fun applySlot(client: DeviceClient, index: Int) {
        val editor = _uiState.value.slotEditor?.takeIf { it.index == index } ?: return
        // Each confirmed write is committed immediately, so a retry after a failure only sends what is left.
        for (field in editor.changedFields) {
            if (field == SlotField.SECRET) {
                val secret = _uiState.value.slotEditor?.newSecret ?: continue
                client.write(ProtocolCodec.setSlot(index, field.param, ProtocolCodec.encodeSecret(secret)))
                updateSlotEditor { it.copy(newSecret = null) }
            } else {
                val draft = _uiState.value.slotEditor?.draft ?: return
                client.write(ProtocolCodec.setSlot(index, field.param, ProtocolCodec.encodeSlotField(field.param, draft)))
                updateSlotEditor { it.copy(original = field.copyValue(from = it.draft, to = it.original)) }
            }
        }
        if (editor.makeActive) {
            writeActiveSlot(client, index)
            updateSlotEditor { it.copy(makeActive = false) }
        }
        val name = _uiState.value.slotEditor?.original?.slotName
        updateSession { it.copy(slots = it.slots + (index to SlotSummary.Configured(name?.ifEmpty { null }))) }
        _uiState.update { it.copy(screen = Screen.SlotDetails(index), message = uiText(R.string.msg_slot_applied, index + 1)) }
    }

    private fun setupSlot(client: DeviceClient, index: Int) {
        val draft = _uiState.value.setupDraft?.takeIf { it.index == index } ?: return
        val (config, secret) = validateSetup(draft) ?: run {
            _uiState.update { it.copy(screen = Screen.SetupSlot(index)) }
            return
        }
        client.write(ProtocolCodec.setSlot(index, Protocol.Param.ALL, ProtocolCodec.encodeFullSlot(config, secret)))
        updateSession { it.copy(slots = it.slots + (index to SlotSummary.Configured(config.slotName.ifEmpty { null }))) }
        _uiState.update {
            it.copy(screen = Screen.Overview, setupDraft = null, message = uiText(R.string.msg_slot_configured, index + 1))
        }
    }

    private fun applyGlobal(client: DeviceClient) {
        val editor = _uiState.value.globalEditor ?: return
        for (field in editor.changedFields) {
            val draft = _uiState.value.globalEditor?.draft ?: return
            client.write(ProtocolCodec.setGlobal(field.param, ProtocolCodec.encodeGlobalField(field.param, draft)))
            _uiState.update { state ->
                val current = state.globalEditor ?: return@update state
                state.copy(globalEditor = current.copy(original = field.copyValue(current.draft, current.original)))
            }
        }
        _uiState.update { state ->
            val committed = state.globalEditor?.original
            state.copy(
                screen = Screen.Overview,
                session = state.session?.let { session ->
                    session.copy(
                        global = committed,
                        info = committed?.let { session.info.copy(deviceId = it.deviceId) } ?: session.info
                    )
                },
                message = uiText(R.string.msg_global_applied)
            )
        }
    }

    private fun writeActiveSlot(client: DeviceClient, index: Int) {
        client.write(ProtocolCodec.setGlobal(Protocol.Param.ACTIVE_SLOT_INDEX, ProtocolCodec.encodeU8(index)))
        _uiState.update { state ->
            state.copy(
                session = state.session?.let { session ->
                    session.copy(
                        info = session.info.copy(activeSlotIndex = index),
                        global = session.global?.copy(activeSlotIndex = index)
                    )
                },
                globalEditor = state.globalEditor?.let {
                    it.copy(original = it.original.copy(activeSlotIndex = index), draft = it.draft.copy(activeSlotIndex = index))
                }
            )
        }
    }

    private fun onActionFailed(action: TapAction, error: Exception) {
        val reason = errorText(error)
        _uiState.update { state ->
            when (action) {
                TapAction.Discover -> state.copy(screen = Screen.Discover, message = uiText(R.string.msg_discover_failed, reason))
                is TapAction.LoadSlotNames -> state.copy(
                    session = state.session?.copy(pageLoad = LoadState.FAILED)
                )
                is TapAction.LoadSlot -> state.copy(
                    screen = Screen.Failure(
                        uiText(R.string.failure_load_slot_title),
                        uiText(R.string.failure_load_slot_message, action.index + 1, reason),
                        action
                    )
                )
                is TapAction.ApplySlot, is TapAction.SetupSlot -> state.copy(
                    screen = Screen.Failure(
                        uiText(R.string.failure_slot_write_title),
                        uiText(R.string.failure_slot_write_message, reason),
                        action
                    )
                )
                TapAction.ApplyGlobal -> state.copy(
                    screen = Screen.Failure(
                        uiText(R.string.failure_write_title),
                        uiText(R.string.failure_global_write_message, reason),
                        action
                    )
                )
                // Debug errors are logged in debugExchange, this only covers opening the transport.
                TapAction.DebugSend, TapAction.DebugRead -> state.copy(
                    debug = state.debug.copy(armed = null),
                    message = uiText(R.string.msg_debug_error, reason)
                )
            }
        }
    }

    // ---------- Navigation ----------

    fun onBack() {
        _uiState.update { state ->
            when (val screen = state.screen) {
                Screen.Discover -> state
                Screen.Overview -> state.copy(
                    screen = Screen.Discover,
                    session = null,
                    globalEditor = null,
                    slotEditor = null,
                    setupDraft = null,
                    editing = null,
                    editError = null,
                    message = null
                )
                is Screen.SlotDetails -> state.copy(screen = Screen.Overview, slotEditor = null, editing = null)
                is Screen.SetupSlot -> state.copy(screen = Screen.Overview, setupDraft = null)
                is Screen.TapPrompt -> state.copy(screen = returnTarget(screen.action))
                is Screen.Failure -> state.copy(screen = returnTarget(screen.retry))
                is Screen.DeviceMismatch -> state.copy(screen = returnTarget(screen.retry))
                Screen.Settings -> state.copy(screen = if (state.session != null) Screen.Overview else Screen.Discover)
                Screen.SavedConfigs -> state.copy(screen = Screen.Settings)
                Screen.TemplateDetails -> state.copy(screen = Screen.SavedConfigs, templateEditor = null, editing = null)
                // Merge step goes back to choosing the target, the first step back to where saving started.
                Screen.SaveTemplate -> when (val save = state.templateSave) {
                    null -> state.copy(screen = Screen.Settings)
                    else -> if (save.target != null) state.copy(templateSave = save.copy(target = null, choices = emptyMap()))
                    else state.copy(screen = save.returnTo, templateSave = null)
                }
                Screen.Debug -> state.copy(screen = Screen.Settings, debug = state.debug.copy(armed = null))
            }
        }
    }

    /** The app was opened or brought to front by tapping a device: starts over, unsaved edits are dropped. */
    fun onLaunchedByDevice() {
        handled = null
        _uiState.update {
            it.copy(
                screen = Screen.Discover,
                session = null,
                globalEditor = null,
                slotEditor = null,
                setupDraft = null,
                editing = null,
                editError = null,
                templateEditor = null,
                templateSave = null,
                message = null,
                debug = it.debug.copy(armed = null)
            )
        }
    }

    fun onRetry() {
        handled = null
        _uiState.update { state ->
            when (val screen = state.screen) {
                is Screen.Failure -> state.copy(screen = Screen.TapPrompt(screen.retry))
                is Screen.DeviceMismatch -> state.copy(screen = Screen.TapPrompt(screen.retry))
                else -> state
            }
        }
    }

    fun onOpenSettings() = navigate(Screen.Settings)

    fun onOpenSavedConfigs() = navigate(Screen.SavedConfigs)

    fun onOpenDebug() = navigate(Screen.Debug)

    private fun navigate(screen: Screen) = _uiState.update { it.copy(screen = screen) }

    private fun returnTarget(action: TapAction): Screen = when (action) {
        TapAction.Discover -> Screen.Discover
        is TapAction.LoadSlotNames, is TapAction.LoadSlot, TapAction.ApplyGlobal -> Screen.Overview
        is TapAction.ApplySlot -> Screen.SlotDetails(action.index)
        is TapAction.SetupSlot -> Screen.SetupSlot(action.index)
        TapAction.DebugSend, TapAction.DebugRead -> Screen.Debug
    }

    // ---------- Overview ----------

    fun onPageChange(delta: Int) {
        updateSession { session ->
            val page = (session.page + delta).coerceIn(0, session.pageCount - 1)
            session.copy(
                page = page,
                pageLoad = if (session.isPageLoaded(page)) LoadState.LOADED else LoadState.NOT_LOADED
            )
        }
    }

    fun onSlotClick(index: Int) {
        when (_uiState.value.session?.slots?.get(index)) {
            SlotSummary.Empty -> _uiState.update {
                it.copy(screen = Screen.SetupSlot(index), setupDraft = defaultSetupDraft(index))
            }
            is SlotSummary.Configured -> navigate(Screen.TapPrompt(TapAction.LoadSlot(index)))
            null -> Unit
        }
    }

    fun onApplyGlobal() {
        if (_uiState.value.globalEditor?.changedFields?.isNotEmpty() == true) {
            navigate(Screen.TapPrompt(TapAction.ApplyGlobal))
        }
    }

    fun onResetGlobal() {
        _uiState.update { state ->
            state.copy(globalEditor = state.globalEditor?.let { it.copy(draft = it.original) })
        }
    }

    // ---------- Slot details ----------

    fun onToggleMakeActive() = updateSlotEditor { it.copy(makeActive = !it.makeActive) }

    fun onApplySlot() {
        val editor = _uiState.value.slotEditor ?: return
        if (editor.changeCount > 0) navigate(Screen.TapPrompt(TapAction.ApplySlot(editor.index)))
    }

    fun onResetSlot() = updateSlotEditor { it.copy(draft = it.original, newSecret = null, makeActive = false) }

    // ---------- Field editing (bottom sheet) ----------

    fun onEditField(target: EditTarget) = _uiState.update { it.copy(editing = target, editError = null) }

    fun onDismissEdit() = _uiState.update { it.copy(editing = null, editError = null) }

    fun onSaveEdit(input: String) {
        val state = _uiState.value
        val target = state.editing ?: return
        val error: UiText? = if (state.screen == Screen.TemplateDetails) editTemplateField(target, input) else when (target) {
            is EditTarget.Slot -> editSlotField(target.field, input)
            is EditTarget.Global -> {
                val editor = state.globalEditor ?: return
                when (val result = target.field.apply(editor.draft, input)) {
                    is FieldInput.Valid -> {
                        _uiState.update { it.copy(globalEditor = editor.copy(draft = result.value)) }
                        null
                    }
                    is FieldInput.Invalid -> result.reason
                }
            }
        }
        _uiState.update { if (error == null) it.copy(editing = null, editError = null) else it.copy(editError = error) }
    }

    private fun editSlotField(field: SlotField, input: String): UiText? {
        val editor = _uiState.value.slotEditor ?: return uiText(R.string.error_no_slot)
        if (field == SlotField.SECRET) {
            return when (val result = SlotField.parseSecret(input)) {
                is FieldInput.Valid -> { updateSlotEditor { it.copy(newSecret = result.value) }; null }
                is FieldInput.Invalid -> result.reason
            }
        }
        return when (val result = field.apply(editor.draft, input)) {
            is FieldInput.Valid -> { updateSlotEditor { it.copy(draft = result.value) }; null }
            is FieldInput.Invalid -> result.reason
        }
    }

    // ---------- Setup blank slot ----------

    fun onSetupValueChange(field: SlotField, value: String) {
        _uiState.update { state ->
            state.copy(setupDraft = state.setupDraft?.let {
                it.copy(values = it.values + (field to value), errors = it.errors - field)
            })
        }
    }

    fun onApplySetup() {
        val draft = _uiState.value.setupDraft ?: return
        if (validateSetup(draft) != null) navigate(Screen.TapPrompt(TapAction.SetupSlot(draft.index)))
    }

    /** Validates the setup form; stores field errors and returns null if invalid. */
    private fun validateSetup(draft: SetupDraft): Pair<SlotConfig, ByteArray>? {
        val (config, fieldErrors) = parseSetupFields(draft)
        val errors = fieldErrors.toMutableMap()
        val secret = when (val result = SlotField.parseSecret(draft.values[SlotField.SECRET].orEmpty())) {
            is FieldInput.Valid -> result.value
            is FieldInput.Invalid -> { errors[SlotField.SECRET] = result.reason; null }
        }
        _uiState.update { it.copy(setupDraft = it.setupDraft?.copy(errors = errors)) }
        return if (errors.isEmpty() && secret != null) config to secret else null
    }

    /** Parses the readable (non-secret) fields of the setup form, with the errors of invalid ones. */
    private fun parseSetupFields(draft: SetupDraft): Pair<SlotConfig, Map<SlotField, UiText>> {
        val errors = mutableMapOf<SlotField, UiText>()
        var config = EMPTY_SLOT
        for (field in SlotField.readable) {
            when (val result = field.apply(config, draft.values[field].orEmpty())) {
                is FieldInput.Valid -> config = result.value
                is FieldInput.Invalid -> errors[field] = result.reason
            }
        }
        return config to errors
    }

    private fun defaultSetupDraft(index: Int) = SetupDraft(
        index = index,
        values = mapOf(
            SlotField.ALGORITHM to Algorithm.XTOTP_SHA1.code.toString(),
            SlotField.DIGITS to "8",
            SlotField.INTERVAL to "30",
            SlotField.SLOT_NAME to "",
            SlotField.CURRENCY to "KUDOS",
            SlotField.FRACTION to "100",
            SlotField.MERCHANT_TEMPLATE to "",
            SlotField.SECRET to ""
        )
    )

    // ---------- Saved configurations ("templates") ----------

    /** Starts the "Save Template" flow with the values of the current screen. */
    fun onSaveTemplate() {
        val state = _uiState.value
        val values = when (state.screen) {
            is Screen.SlotDetails -> state.slotEditor?.draft?.let { draft ->
                SlotField.readable.associate { it.name to it.raw(draft) }
            }
            is Screen.SetupSlot -> state.setupDraft?.let { draft ->
                val (config, errors) = parseSetupFields(draft)
                if (errors.isNotEmpty()) {
                    _uiState.update {
                        it.copy(setupDraft = draft.copy(errors = errors), message = uiText(R.string.msg_template_fix_fields))
                    }
                    return
                }
                SlotField.readable.associate { it.name to it.raw(config) }
            }
            Screen.Overview -> state.globalEditor?.draft?.let { draft ->
                SavedConfigKind.GLOBAL.fields.filterIsInstance<EditTarget.Global>().associate { it.key to it.field.raw(draft) }
            }
            else -> null
        } ?: return
        val kind = if (state.screen == Screen.Overview) SavedConfigKind.GLOBAL else SavedConfigKind.SLOT
        _uiState.update {
            it.copy(screen = Screen.SaveTemplate, templateSave = TemplateSave(kind, values, returnTo = state.screen))
        }
    }

    fun onSaveAsNewTemplate(name: String) {
        val save = _uiState.value.templateSave ?: return
        if (templateNameError(name, templateNames(save.kind)) != null) return
        savedConfigRepository.save(SavedConfig(name.trim(), save.kind, save.values))
        finishTemplateSave(uiText(R.string.msg_template_saved, name.trim()))
    }

    /** Opens the merge step for [target]; nothing to merge if it already holds the same values. */
    fun onSelectMergeTarget(target: SavedConfig) {
        val save = _uiState.value.templateSave?.copy(target = target) ?: return
        val differing = save.differing
        if (differing.isEmpty()) {
            finishTemplateSave(uiText(R.string.msg_template_unchanged, target.name))
            return
        }
        val choices = differing.associate { it.key to MergeChoice(custom = save.values[it.key].orEmpty()) }
        _uiState.update { it.copy(templateSave = save.copy(choices = choices)) }
    }

    fun onMergeChoiceChange(field: EditTarget, choice: MergeChoice) = updateTemplateSave {
        it.copy(choices = it.choices + (field.key to choice.copy(error = null)))
    }

    /** Writes the chosen values into the merge target; custom values must be valid. */
    fun onConfirmMerge() {
        val save = _uiState.value.templateSave ?: return
        val target = save.target ?: return
        val (merged, errors) = save.merge()
        if (errors.isNotEmpty()) {
            updateTemplateSave { it.copy(choices = it.choices.mapValues { (key, choice) -> choice.copy(error = errors[key]) }) }
            return
        }
        savedConfigRepository.save(target.copy(values = merged))
        finishTemplateSave(uiText(R.string.msg_template_updated, target.name))
    }

    private fun finishTemplateSave(message: UiText) = _uiState.update { state ->
        state.copy(screen = state.templateSave?.returnTo ?: state.screen, templateSave = null, message = message)
    }

    fun templateNames(kind: SavedConfigKind): List<String> =
        _uiState.value.savedConfigs.filter { it.kind == kind }.map { it.name }

    fun onOpenTemplate(config: SavedConfig) = _uiState.update {
        it.copy(screen = Screen.TemplateDetails, templateEditor = TemplateEditor(original = config, draft = config))
    }

    fun onRenameTemplate(name: String) {
        val editor = _uiState.value.templateEditor ?: return
        if (templateNameError(name, templateNames(editor.draft.kind) - editor.original.name) != null) return
        updateTemplateEditor { it.copy(draft = it.draft.copy(name = name.trim())) }
    }

    fun onResetTemplate() = updateTemplateEditor { it.copy(draft = it.original) }

    fun onSaveTemplateChanges() {
        val editor = _uiState.value.templateEditor?.takeIf { it.changeCount > 0 } ?: return
        savedConfigRepository.replace(editor.original, editor.draft)
        _uiState.update {
            it.copy(
                templateEditor = editor.copy(original = editor.draft),
                message = uiText(R.string.msg_template_updated, editor.draft.name)
            )
        }
    }

    fun onDeleteOpenTemplate() {
        val editor = _uiState.value.templateEditor ?: return
        savedConfigRepository.delete(editor.original)
        _uiState.update { it.copy(screen = Screen.SavedConfigs, templateEditor = null) }
    }

    private fun editTemplateField(target: EditTarget, input: String): UiText? =
        when (val result = target.normalize(input)) {
            is FieldInput.Valid -> {
                updateTemplateEditor { it.copy(draft = it.draft.copy(values = it.draft.values + (target.key to result.value))) }
                null
            }
            is FieldInput.Invalid -> result.reason
        }

    fun onLoadTemplate(config: SavedConfig) {
        _uiState.update { state ->
            when (state.screen) {
                is Screen.SlotDetails -> state.copy(slotEditor = state.slotEditor?.let { editor ->
                    editor.copy(draft = applySlotValues(editor.draft, config.values))
                })
                is Screen.SetupSlot -> state.copy(setupDraft = state.setupDraft?.let { draft ->
                    val values = SlotField.readable.mapNotNull { f -> config.values[f.name]?.let { f to it } }
                    draft.copy(values = draft.values + values, errors = emptyMap())
                })
                Screen.Overview -> state.copy(globalEditor = state.globalEditor?.let { editor ->
                    var draft = editor.draft
                    for (field in SavedConfigKind.GLOBAL.fields.filterIsInstance<EditTarget.Global>().map { it.field }) {
                        val value = config.values[field.name] ?: continue
                        (field.apply(draft, value) as? FieldInput.Valid)?.let { draft = it.value }
                    }
                    editor.copy(draft = draft)
                })
                else -> state
            }.copy(message = uiText(R.string.msg_template_loaded, config.name))
        }
    }

    fun onDeleteTemplate(config: SavedConfig) = savedConfigRepository.delete(config)

    private fun applySlotValues(base: SlotConfig, values: Map<String, String>): SlotConfig {
        var config = base
        for (field in SlotField.readable) {
            val value = values[field.name] ?: continue
            (field.apply(config, value) as? FieldInput.Valid)?.let { config = it.value }
        }
        return config
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    // ---------- Debug console ----------

    fun onDebugDraftChange(draft: DebugDraft) = updateDebug { it.copy(draft = draft) }

    /** Arms the next NFC tap; sending requires a buildable packet. */
    fun onDebugArm(mode: DebugTapMode) {
        val draft = _uiState.value.debug.draft
        if (mode == DebugTapMode.SEND && DebugCommandBuilder.build(draft) is BuiltRequest.Error) return
        updateDebug { it.copy(armed = mode) }
    }

    fun onDebugDisarm() = updateDebug { it.copy(armed = null) }

    fun onDebugClearLog() = updateDebug { it.copy(log = emptyList()) }

    /** Sends the composed packet unmodified (no session or serial checks) and logs the exchange. */
    private fun debugExchange(action: TapAction, transport: NfcTransport) {
        val request = if (action == TapAction.DebugSend) {
            (DebugCommandBuilder.build(_uiState.value.debug.draft) as? BuiltRequest.Packet)?.bytes
        } else null
        val start = System.currentTimeMillis()
        var response: ByteArray? = null
        var error: String? = null
        var record = runCatching { if (request == null) transport.readCurrentRecord() else null }.getOrNull()
        if (request != null) {
            try {
                response = transport.transceive(request)
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
                // Show what the tag holds instead of the expected response.
                record = runCatching { transport.readCurrentRecord() }.getOrNull()
            }
        }
        val exchange = DebugExchange(
            id = start,
            timestamp = start,
            serial = transport.serial.toSerialString(),
            request = request,
            response = response,
            tagRecord = record,
            error = error,
            durationMs = System.currentTimeMillis() - start
        )
        updateDebug { it.copy(armed = null, log = listOf(exchange) + it.log.take(DEBUG_LOG_SIZE - 1)) }
    }

    private fun updateDebug(transform: (DebugState) -> DebugState) =
        _uiState.update { it.copy(debug = transform(it.debug)) }

    // ---------- Helpers ----------

    private fun updateSession(transform: (DeviceSession) -> DeviceSession) =
        _uiState.update { state -> state.copy(session = state.session?.let(transform)) }

    private fun updateSlotEditor(transform: (SlotEditor) -> SlotEditor) =
        _uiState.update { state -> state.copy(slotEditor = state.slotEditor?.let(transform)) }

    private fun updateTemplateEditor(transform: (TemplateEditor) -> TemplateEditor) =
        _uiState.update { state -> state.copy(templateEditor = state.templateEditor?.let(transform)) }

    private fun updateTemplateSave(transform: (TemplateSave) -> TemplateSave) =
        _uiState.update { state -> state.copy(templateSave = state.templateSave?.let(transform)) }

    companion object {
        /** ALG_NONE in crypto_algorithms: slot not configured. */
        private const val ALG_NONE = 0

        private const val DEBUG_LOG_SIZE = 50

        private val EMPTY_SLOT = SlotConfig(
            interval = 30, digits = 8, algorithm = Algorithm.XTOTP_SHA1.code,
            slotName = "", currency = "", fraction = 100, merchantTemplate = ""
        )

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                NfcViewModel(
                    savedConfigRepository = SavedConfigRepository(app),
                    themePreferences = ThemePreferences(app),
                    reader = NfcReader.get(app)
                )
            }
        }
    }
}

private fun ReaderState.toNfcStatus(): NfcStatus = when (this) {
    ReaderState.Unsupported -> NfcStatus.UNSUPPORTED
    ReaderState.AdapterOff -> NfcStatus.DISABLED
    ReaderState.Paused, ReaderState.Active -> NfcStatus.AVAILABLE
}

fun ByteArray.toSerialString(): String = joinToString(":") { "%02X".format(it) }

private fun SlotField.copyValue(from: SlotConfig, to: SlotConfig): SlotConfig = when (this) {
    SlotField.SLOT_NAME -> to.copy(slotName = from.slotName)
    SlotField.ALGORITHM -> to.copy(algorithm = from.algorithm)
    SlotField.DIGITS -> to.copy(digits = from.digits)
    SlotField.INTERVAL -> to.copy(interval = from.interval)
    SlotField.CURRENCY -> to.copy(currency = from.currency)
    SlotField.FRACTION -> to.copy(fraction = from.fraction)
    SlotField.MERCHANT_TEMPLATE -> to.copy(merchantTemplate = from.merchantTemplate)
    SlotField.SECRET -> to
}

private fun GlobalField.copyValue(from: GlobalConfig, to: GlobalConfig) =
    when (this) {
        GlobalField.DEVICE_ID -> to.copy(deviceId = from.deviceId)
        GlobalField.MERCHANT_BACKEND -> to.copy(merchantBackend = from.merchantBackend)
        GlobalField.DEVICE_DESCRIPTION -> to.copy(deviceDescription = from.deviceDescription)
    }
