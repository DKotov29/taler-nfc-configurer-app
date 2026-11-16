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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.taler.xotpconfigurer.DeviceSession
import net.taler.xotpconfigurer.GlobalEditor
import net.taler.xotpconfigurer.LoadState
import net.taler.xotpconfigurer.R
import net.taler.xotpconfigurer.model.GlobalField
import net.taler.xotpconfigurer.model.SlotSummary
import net.taler.xotpconfigurer.ui.theme.Primary
import net.taler.xotpconfigurer.ui.theme.TextPrimary
import net.taler.xotpconfigurer.ui.theme.TextSecondary

/** 2. Available Slots incl. loading (2a), failure (2b) and pending global changes (2e). */
@Composable
fun OverviewScreen(
    session: DeviceSession,
    globalEditor: GlobalEditor?,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onEditGlobal: (GlobalField) -> Unit,
    onSlotClick: (Int) -> Unit,
    onPageChange: (Int) -> Unit,
    onApplyGlobal: () -> Unit,
    onResetGlobal: () -> Unit,
    onSaveTemplate: () -> Unit,
    onLoadTemplate: () -> Unit
) {
    val changed = globalEditor?.changedFields.orEmpty()
    FullScreen {
        AppTopBar(title = stringResource(R.string.overview_title), onBack = onBack, onSettings = onSettings)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard {
                if (globalEditor != null) TemplateButtons(onSave = onSaveTemplate, onLoad = onLoadTemplate)
                SectionLabel(stringResource(R.string.overview_device_info), Modifier.padding(top = 8.dp, bottom = 4.dp))
                val draft = globalEditor?.draft
                InfoRow(
                    label = stringResource(GlobalField.DEVICE_ID.label),
                    value = draft?.deviceId ?: session.info.deviceId,
                    pending = GlobalField.DEVICE_ID in changed,
                    onEdit = draft?.let { { onEditGlobal(GlobalField.DEVICE_ID) } }
                )
                InfoRow(
                    label = stringResource(R.string.overview_active_slot),
                    value = stringResource(R.string.slot_number, session.info.activeSlotIndex + 1),
                    emphasized = true
                )
                if (draft != null) {
                    InfoRow(
                        label = stringResource(GlobalField.MERCHANT_BACKEND.label),
                        value = draft.merchantBackend.ifEmpty { "—" },
                        pending = GlobalField.MERCHANT_BACKEND in changed,
                        onEdit = { onEditGlobal(GlobalField.MERCHANT_BACKEND) }
                    )
                    InfoRow(
                        label = stringResource(GlobalField.DEVICE_DESCRIPTION.label),
                        value = draft.deviceDescription.ifEmpty { "—" },
                        pending = GlobalField.DEVICE_DESCRIPTION in changed,
                        onEdit = { onEditGlobal(GlobalField.DEVICE_DESCRIPTION) }
                    )
                }
            }

            when (session.pageLoad) {
                LoadState.LOADED -> SlotList(session, onSlotClick)
                LoadState.LOADING -> SectionCard {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Primary)
                        Spacer(Modifier.width(12.dp))
                        SectionLabel(stringResource(R.string.overview_loading_names))
                    }
                }
                LoadState.FAILED -> TapToLoadCard(
                    heading = stringResource(R.string.overview_load_failed_title),
                    description = stringResource(R.string.overview_load_failed_desc)
                )
                LoadState.NOT_LOADED -> TapToLoadCard(
                    heading = stringResource(R.string.overview_slots_range, session.pageRange.first + 1, session.pageRange.last + 1),
                    description = stringResource(R.string.overview_tap_to_load)
                )
            }

            if (session.pageCount > 1 || session.pageLoad == LoadState.LOADED) {
                Pagination(session, onPageChange)
            }
        }
        if (changed.isNotEmpty()) {
            ApplyChangesBar(changeCount = changed.size, onApply = onApplyGlobal, onReset = onResetGlobal)
        }
    }
}

@Composable
private fun SlotList(session: DeviceSession, onSlotClick: (Int) -> Unit) {
    SectionCard(title = stringResource(R.string.overview_title)) {
        val range = session.pageRange
        range.forEach { index ->
            SlotRow(
                index = index,
                summary = session.slots[index],
                isActive = index == session.info.activeSlotIndex,
                onClick = { onSlotClick(index) }
            )
            if (index != range.last) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun SlotRow(index: Int, summary: SlotSummary?, isActive: Boolean, onClick: () -> Unit) {
    val name = (summary as? SlotSummary.Configured)?.name
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (name != null) stringResource(R.string.slot_number_named, index + 1, name)
            else stringResource(R.string.slot_number, index + 1),
            fontSize = 14.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (isActive) {
            Text(
                stringResource(R.string.slot_active_badge),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        StatusChip(stringResource(if (summary is SlotSummary.Configured) R.string.slot_status_configured else R.string.slot_status_empty))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun TapToLoadCard(heading: String, description: String) {
    SectionCard {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NfcTapAnimation(active = true, communicating = false)
            Spacer(Modifier.height(8.dp))
            Text(heading, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(description, fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Pagination(session: DeviceSession, onPageChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onPageChange(-1) }, enabled = session.page > 0) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.page_previous))
        }
        val range = session.pageRange
        Text(
            stringResource(R.string.pagination, range.first + 1, range.last + 1, session.info.slotCount),
            fontSize = 13.sp,
            color = TextSecondary
        )
        IconButton(onClick = { onPageChange(1) }, enabled = session.page < session.pageCount - 1) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.page_next))
        }
    }
}
