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

package com.example.otp_nfc_configurer

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.otp_nfc_configurer.nfc.NfcReader
import com.example.otp_nfc_configurer.ui.ConfiguratorApp
import com.example.otp_nfc_configurer.ui.theme.OTPNFCConfigurerTheme

/**
 * Minimal host: hands its lifecycle to [NfcReader], which owns the NFC reader mode.
 * AppCompatActivity applies the per-app language chosen in settings.
 */
class MainActivity : AppCompatActivity() {
    private val viewModel: NfcViewModel by viewModels { NfcViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(NfcReader.get(this))
        // Not on recreation (e.g. rotation): the tap that launched the app was already handled.
        if (savedInstanceState == null) handleTagIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            OTPNFCConfigurerTheme(appTheme = uiState.appTheme) {
                ConfiguratorApp(viewModel = viewModel, onEnableNfc = ::openNfcSettings)
            }
        }
    }

    /** Tap on a device while the app is in the background (launchMode singleTop). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTagIntent(intent)
    }

    /** The app was opened by tapping a device in configuration mode, see the NDEF filters in the manifest. */
    private fun handleTagIntent(intent: Intent?) {
        if (intent?.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return
        val tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java) ?: return
        viewModel.onLaunchedByDevice()
        // Runs discovery once reader mode is on; if this handle is already stale by then,
        // reader mode delivers the same tag again.
        NfcReader.get(this).offer(tag)
    }

    private fun openNfcSettings() {
        startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
    }
}
