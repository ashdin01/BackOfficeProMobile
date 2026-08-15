package au.com.harcourtapples.bopro.ui.orderprep.suppliers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import au.com.harcourtapples.bopro.api.models.Supplier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierListScreen(
    serverUrl: String,
    apiKey: String,
    onSelectSupplier: (Supplier) -> Unit,
    onBack: () -> Unit
) {
    val app = LocalContext.current.applicationContext as BoproApplication
    val vm: SupplierListViewModel = viewModel(factory = SupplierListViewModel.factory(app.orderPrepRepo))
    val state by vm.state.collectAsState()

    LaunchedEffect(serverUrl, apiKey) { vm.load(serverUrl, apiKey) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prepare Order") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.padding(padding).fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            state.error != null -> Column(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { vm.load(serverUrl, apiKey) }) { Text("Retry") }
            }
            state.suppliers.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize()) {
                Text("No suppliers found.\nAdd a supplier in BackOfficePro first.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            else -> {
                Column(Modifier.padding(padding).fillMaxSize()) {
                    Text("Choose a supplier", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.suppliers, key = { it.id }) { supplier ->
                            ListItem(
                                modifier = Modifier.clickable { onSelectSupplier(supplier) },
                                headlineContent = { Text(supplier.name, fontWeight = FontWeight.SemiBold) },
                                supportingContent = if (supplier.code.isNotBlank()) {
                                    { Text(supplier.code, style = MaterialTheme.typography.bodySmall) }
                                } else null
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
