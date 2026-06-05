package au.com.harcourtapples.bopro.ui.receive.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import au.com.harcourtapples.bopro.api.models.PurchaseOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoListScreen(
    serverUrl: String,
    apiKey: String,
    onSelectPo: (String) -> Unit,
    onBack: () -> Unit
) {
    val app = LocalContext.current.applicationContext as BoproApplication
    val vm: PoListViewModel = viewModel(factory = PoListViewModel.factory(app.receiveRepo))
    val state by vm.state.collectAsState()
    var poNumberInput by remember { mutableStateOf("") }

    LaunchedEffect(serverUrl, apiKey) { vm.load(serverUrl, apiKey) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receive Stock") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            // Manual PO number entry
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = poNumberInput,
                    onValueChange = { poNumberInput = it.uppercase() },
                    label = { Text("Enter PO Number") },
                    placeholder = { Text("e.g. PO-00042") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { if (poNumberInput.isNotBlank()) onSelectPo(poNumberInput.trim()) },
                    enabled = poNumberInput.isNotBlank()
                ) {
                    Icon(Icons.Default.Search, null)
                }
            }

            HorizontalDivider()

            when {
                state.isLoading -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
                state.error != null -> Column(Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.load(serverUrl, apiKey) }) { Text("Retry") }
                }
                state.pos.isEmpty() -> Box(Modifier.fillMaxSize()) {
                    Text("No open purchase orders.\nCreate and send a PO in BackOfficePro first.",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                else -> {
                    Text("Open Purchase Orders", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.pos, key = { it.id }) { po ->
                            PoCard(po = po, onClick = { onSelectPo(po.poNumber) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PoCard(po: PurchaseOrder, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(po.poNumber, fontWeight = FontWeight.SemiBold) },
        supportingContent = {
            Column {
                Text(po.supplierName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    buildString {
                        append("${po.lineCount} line${if (po.lineCount != 1) "s" else ""}")
                        if (po.deliveryDate.isNotBlank()) append("  •  Due ${po.deliveryDate}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Badge(containerColor = if (po.status == "PARTIAL") MaterialTheme.colorScheme.tertiaryContainer
                  else MaterialTheme.colorScheme.primaryContainer) { Text(po.status) }
        }
    )
}
