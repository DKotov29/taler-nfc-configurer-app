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
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.otp_nfc_configurer.ui.ConfiguratorApp
import com.example.otp_nfc_configurer.ui.theme.OTPNFCConfigurerTheme

/**
 * Minimal host: owns the NFC reader mode lifecycle and forwards tag events to [NfcViewModel].
 * AppCompatActivity applies the per-app language chosen in settings.
 */
class MainActivity : AppCompatActivity() {
    private val viewModel: NfcViewModel by viewModels { NfcViewModel.Factory }
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            OTPNFCConfigurerTheme(appTheme = uiState.appTheme) {
                ConfiguratorApp(viewModel = viewModel, onEnableNfc = ::openNfcSettings)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfcAdapter
        val isEnabled = adapter?.isEnabled == true
        viewModel.onNfcStateChanged(isSupported = adapter != null, isEnabled = isEnabled)
        if (adapter == null || !isEnabled) return

        val options = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        }
        adapter.enableReaderMode(this, viewModel::onTagDiscovered, READER_FLAGS, options)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
        viewModel.onReaderModeDisabled()
    }

    private fun openNfcSettings() {
        startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
    }

    private companion object {
        const val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V
    }
}
