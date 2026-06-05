package au.com.harcourtapples.bopro.ui.stocktake.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
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
import au.com.harcourtapples.bopro.api.models.Count

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: Int,
    serverUrl: String,
    apiKey: String = "",
    offline: Boolean,
    onScan: () -> Unit,
    onBack: () -> Unit
) {
    val app = LocalContext.current.applicationContext as BoproApplication
    val vm: SessionDetailViewModel = viewModel(factory = SessionDetailViewModel.factory(app.stocktakeRepo))
    val state by vm.state.collectAsState()

    LaunchedEffect(sessionId, serverUrl, apiKey, offline) { vm.load(serverUrl, offline, apiKey, sessionId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.session?.label ?: "Session") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        },
        floatingActionButton = {
            if (state.session?.status == "OPEN" || offline) {
                ExtendedFloatingActionButton(
                    onClick = onScan,
                    icon = { Icon(Icons.Default.CameraAlt, "Scan") },
                    text = { Text("Scan Items") }
                )
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            state.session?.let { session ->
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        session.deptName?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        Text("${state.counts.size} items counted", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
                state.error != null -> Column(Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.load(serverUrl, offline, apiKey, sessionId) }) { Text("Retry") }
                }
                state.counts.isEmpty() -> Box(Modifier.fillMaxSize()) {
                    Text("No items counted yet.\nTap 'Scan Items' to start.",
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.counts, key = { it.id }) { count ->
                        CountRow(count = count, onDelete = { vm.deleteCount(serverUrl, offline, apiKey, sessionId, count.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun CountRow(count: Count, onDelete: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(count.description, fontWeight = FontWeight.Medium) },
        supportingContent = { Text("${count.barcode}  •  ${count.deptName ?: ""}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(count.countedQty.let { if (it % 1 == 0.0) it.toInt().toString() else String.format("%.2f", it) },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { showConfirm = true }) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
    if (showConfirm) AlertDialog(
        onDismissRequest = { showConfirm = false },
        title = { Text("Remove Item?") },
        text = { Text("Remove ${count.description} from this session?") },
        confirmButton = { TextButton(onClick = { showConfirm = false; onDelete() }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } }
    )
}
