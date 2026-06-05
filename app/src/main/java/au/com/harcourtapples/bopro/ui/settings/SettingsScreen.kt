package au.com.harcourtapples.bopro.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import au.com.harcourtapples.bopro.SettingsStore
import au.com.harcourtapples.bopro.api.ApiClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(store: SettingsStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val savedUrl         by store.serverUrl.collectAsState(initial = SettingsStore.DEFAULT_URL)
    val savedKey         by store.apiKey.collectAsState(initial = "")
    val savedFingerprint by store.trustedCertFingerprint.collectAsState(initial = "")

    var urlInput   by remember(savedUrl)  { mutableStateOf(savedUrl) }
    var keyInput   by remember(savedKey)  { mutableStateOf(savedKey) }
    var testStatus by remember { mutableStateOf("") }
    var isTesting  by remember { mutableStateOf(false) }
    var saved      by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Server Connection", style = MaterialTheme.typography.titleMedium)
            Text(
                "Enter the IP address of the PC running BackOfficePro (same WiFi network).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(value = urlInput, onValueChange = { urlInput = it; saved = false },
                label = { Text("Server URL") }, placeholder = { Text("https://192.168.1.100:5050") },
                singleLine = true, modifier = Modifier.fillMaxWidth())

            OutlinedTextField(value = keyInput, onValueChange = { keyInput = it; saved = false },
                label = { Text("API Key") }, placeholder = { Text("Paste from BackOfficePro Settings → API Access") },
                singleLine = true, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        isTesting = true; testStatus = ""
                        scope.launch {
                            try {
                                val fpForTest = if (urlInput.trimEnd('/') == savedUrl.trimEnd('/'))
                                    savedFingerprint.ifBlank { null } else null
                                ApiClient.setTrustedFingerprint(fpForTest)
                                val health = ApiClient.service(urlInput, keyInput).health()
                                if (!health.isSuccessful) {
                                    testStatus = "Server error ${health.code()}"
                                } else {
                                    val auth = ApiClient.service(urlInput, keyInput).getDepartments()
                                    when {
                                        auth.code() == 401 -> testStatus = "Reachable, but API key is invalid"
                                        !auth.isSuccessful  -> testStatus = "Server error ${auth.code()}"
                                        else -> {
                                            val seen = ApiClient.lastSeenFingerprint
                                            if (seen != null && seen != savedFingerprint) {
                                                store.setTrustedCertFingerprint(seen)
                                                testStatus = "Connected! Certificate trusted."
                                            } else testStatus = "Connected!"
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                testStatus = "Cannot connect: ${e.message}"
                            } finally { isTesting = false }
                        }
                    },
                    enabled = !isTesting
                ) {
                    if (isTesting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Test")
                }

                Button(
                    onClick = {
                        scope.launch {
                            val urlChanged = urlInput.trimEnd('/') != savedUrl.trimEnd('/')
                            store.setServerUrl(urlInput); store.setApiKey(keyInput)
                            if (urlChanged) store.clearTrustedCertFingerprint()
                            ApiClient.invalidate(); saved = true
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }
            }

            if (testStatus.isNotEmpty()) Text(testStatus,
                color = if (testStatus.startsWith("Connected")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error)
            if (saved) Text("Saved", color = MaterialTheme.colorScheme.primary)

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Certificate Trust", style = MaterialTheme.typography.titleMedium)

            if (savedFingerprint.isBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.LockOpen, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Text("Not yet trusted — tap Test to connect and trust the server certificate.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Trusted certificate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Text(savedFingerprint, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedButton(
                    onClick = { scope.launch { store.clearTrustedCertFingerprint() } },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) { Text("Forget Certificate") }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("About", style = MaterialTheme.typography.titleMedium)
            Text("BackOfficePro Mobile  v1.0.0\n\nStocktake and Receive Stock in one app.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
