package com.example.otp_nfc_configurer

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.otp_nfc_configurer.ui.ConfiguratorApp
import com.example.otp_nfc_configurer.ui.theme.OTPNFCConfigurerTheme

/** Minimal host: owns the NFC reader mode lifecycle and forwards tag events to [NfcViewModel]. */
class MainActivity : ComponentActivity() {
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
