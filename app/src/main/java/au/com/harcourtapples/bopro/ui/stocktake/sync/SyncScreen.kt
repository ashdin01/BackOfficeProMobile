package au.com.harcourtapples.bopro.ui.stocktake.sync

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import au.com.harcourtapples.bopro.BoproApplication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(serverUrl: String, apiKey: String = "", onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as BoproApplication
    val vm: SyncViewModel = viewModel(factory = SyncViewModel.factory(app.stocktakeRepo))
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.loadPending() }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Sync Offline Data") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { padding ->
        Column(Modifier.padding(padding).padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Upload to BackOfficePro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            when {
                state.done -> {
                    Text("Sync complete!\n${state.syncedSessions} session(s) uploaded.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
                state.isSyncing -> {
                    Text("Uploading ${state.syncedSessions} of ${state.pendingSessions} sessions…", textAlign = TextAlign.Center)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                else -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(state.pendingSessions.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                                Text("Sessions", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(state.pendingCounts.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                                Text("Items", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
                    if (state.pendingSessions == 0) {
                        Text("Nothing to sync.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
                    } else {
                        Text("Make sure you are on the same WiFi as BackOfficePro, then tap Sync.",
                            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { vm.sync(serverUrl, apiKey) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Sync ${state.pendingSessions} session(s)")
                        }
                        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                    }
                }
            }
        }
    }
}
