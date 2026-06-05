package com.example.otp_nfc_configurer

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.otp_nfc_configurer.ui.theme.OTPNFCConfigurerTheme
import java.nio.charset.Charset

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var nfcData by mutableStateOf<NfcData?>(null)
    private var isNfcEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        isNfcEnabled = nfcAdapter?.isEnabled == true

        setContent {
            OTPNFCConfigurerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NfcReaderScreen(
                        nfcData = nfcData,
                        isNfcEnabled = isNfcEnabled,
                        onEnableNfcClick = { openNfcSettings() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isNfcEnabled = nfcAdapter?.isEnabled == true

        if (!isNfcEnabled) return
        val options = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        }

        nfcAdapter?.enableReaderMode(
            this,
            { tag ->
                val data = parseTag(tag)
                runOnUiThread { nfcData = data }
            },
            NfcAdapter.FLAG_READER_NFC_A
                    or NfcAdapter.FLAG_READER_NFC_B
                    or NfcAdapter.FLAG_READER_NFC_F
                    or NfcAdapter.FLAG_READER_NFC_V
                    or NfcAdapter.FLAG_READER_NFC_BARCODE,
            options
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    private fun parseTag(tag: Tag): NfcData {
        val tagId = tag.id.joinToString(":") { "%02X".format(it) }
        val techList = tag.techList.joinToString(", ")

        var ndefMessage: String?
        try {
            val ndef = Ndef.get(tag)
            ndefMessage = try {
                ndef.connect()
                val message = ndef.ndefMessage
                message?.records?.joinToString("\n") { record ->
                    String(record.payload, Charset.forName("UTF-8"))
                }
            } catch (e: Exception) {
                "NDEF not supported: ${e.message}"
            } finally {
                try { ndef.close() } catch (e: Exception) {
                    "Exception: ${e.message}"
                }
            }
        } catch (e: Exception) {
            ndefMessage = "NDEF error: ${e.message}"
        }

        return NfcData(
            uid = tagId,
            technologies = techList,
            ndefMessage = ndefMessage,
            error = null
        )
    }

    private fun openNfcSettings() {
        startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
    }
}

data class NfcData(
    val uid: String? = null,
    val technologies: String? = null,
    val ndefMessage: String? = null,
    val error: String? = null
)

@Composable
fun NfcReaderScreen(
    nfcData: NfcData?,
    isNfcEnabled: Boolean,
    onEnableNfcClick: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "NFC Reader",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (!isNfcEnabled) {
            NfcDisabledCard(onEnableNfcClick = onEnableNfcClick)
        } else if (nfcData?.error != null) {
            ErrorCard(error = nfcData.error)
        } else {
            if (nfcData != null) {
                NfcDataCard(nfcData = nfcData)
            } else {
                InstructionCard()
            }
        }
    }
}

@Composable
fun NfcDisabledCard(onEnableNfcClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "NFC is disabled",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = "Please enable NFC in your device settings.",
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )
            Button(
                onClick = onEnableNfcClick,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Enable NFC")
            }
        }
    }
}

@Composable
fun InstructionCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Hold an NFC tag, bank card, or other NFC device near the back of your phone.",
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "Data will appear here automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun ErrorCard(error: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Error:",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(text = error, fontSize = 16.sp)
        }
    }
}

@Composable
fun NfcDataCard(nfcData: NfcData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "NFC Data:",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (nfcData.uid != null) {
                DataRow(label = "Tag UID:", value = nfcData.uid)
            }

            if (nfcData.technologies != null) {
                DataRow(label = "Technologies:", value = nfcData.technologies)
            }

            if (nfcData.ndefMessage != null) {
                DataRow(label = "NDEF Message:", value = nfcData.ndefMessage)
            }
        }
    }
}

@Composable
fun DataRow(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(text = label, fontWeight = FontWeight.Bold)
        Text(
            text = value,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            fontSize = 14.sp
        )
        Divider()
    }
}
