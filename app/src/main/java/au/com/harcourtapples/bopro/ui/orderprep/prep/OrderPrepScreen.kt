package au.com.harcourtapples.bopro.ui.orderprep.prep

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import au.com.harcourtapples.bopro.BoproApplication
import au.com.harcourtapples.bopro.api.models.OrderPrepItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderPrepScreen(
    supplierId: Int,
    supplierName: String,
    serverUrl: String,
    apiKey: String,
    onBack: () -> Unit
) {
    val app = LocalContext.current.applicationContext as BoproApplication
    val vm: OrderPrepViewModel = viewModel(factory = OrderPrepViewModel.factory(app.orderPrepRepo))
    val state by vm.state.collectAsState()

    LaunchedEffect(supplierId, serverUrl, apiKey) { vm.load(serverUrl, apiKey, supplierId, supplierName) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Prepare Order", style = MaterialTheme.typography.titleMedium)
                        Text(supplierName, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        },
        bottomBar = {
            if (state.items.isNotEmpty() && state.submitResult == null) {
                val includedCount = state.edits.count { (_, e) -> e.included && e.cartons > 0 }
                Surface(shadowElevation = 8.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.notes,
                            onValueChange = { vm.setNotes(it) },
                            label = { Text("Notes (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { vm.submit(serverUrl, apiKey) },
                            enabled = includedCount > 0 && !state.isSubmitting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isSubmitting) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text("Creating draft PO…")
                            } else {
                                Text("Create Draft PO ($includedCount item${if (includedCount != 1) "s" else ""})")
                            }
                        }
                    }
                }
            }
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
                Button(onClick = { vm.load(serverUrl, apiKey, supplierId, supplierName) }) { Text("Retry") }
            }
            state.submitResult is OrderPrepSubmitResult.Success -> {
                val result = state.submitResult as OrderPrepSubmitResult.Success
                Column(Modifier.padding(padding).fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Draft PO Created!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${result.poNumber}\n${result.lineCount} line${if (result.lineCount != 1) "s" else ""} added — " +
                        "finish it off in BackOfficePro",
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
            }
            state.submitResult is OrderPrepSubmitResult.Failure -> {
                val result = state.submitResult as OrderPrepSubmitResult.Failure
                Column(Modifier.padding(padding).fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Submission failed", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text(result.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { vm.submit(serverUrl, apiKey) }, modifier = Modifier.fillMaxWidth()) { Text("Retry") }
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
            }
            state.items.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize()) {
                Text("No products linked to this supplier.\nLink products to $supplierName in BackOfficePro first.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            else -> {
                LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                    items(state.items, key = { it.barcode }) { item ->
                        val edit = state.edits[item.barcode]
                        if (edit != null) {
                            OrderPrepItemRow(
                                item = item,
                                edit = edit,
                                onOnHandChange = { qty -> vm.setOnHand(item, qty) },
                                onCartonsChange = { cartons -> vm.setCartons(item.barcode, cartons) },
                                onIncludedChange = { included -> vm.setIncluded(item.barcode, included) }
                            )
                            HorizontalDivider()
                        }
                    }
                    item { Spacer(Modifier.height(160.dp)) }
                }
            }
        }
    }
}

@Composable
private fun OrderPrepItemRow(
    item: OrderPrepItem,
    edit: OrderPrepEdit,
    onOnHandChange: (Double) -> Unit,
    onCartonsChange: (Int) -> Unit,
    onIncludedChange: (Boolean) -> Unit
) {
    var onHandText by remember(item.barcode) { mutableStateOf(formatQty(edit.onHand)) }
    var cartonsText by remember(item.barcode, edit.cartons) { mutableStateOf(edit.cartons.toString()) }

    ListItem(
        leadingContent = {
            Checkbox(checked = edit.included, onCheckedChange = onIncludedChange)
        },
        headlineContent = {
            Text(item.description, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Column {
                Text(
                    "Min ${formatQty(item.reorderPoint)}" +
                    (if (item.reorderMax > 0) " · Max ${formatQty(item.reorderMax)}" else "") +
                    (if (item.packQty > 1) " · ${item.packQty} × ${item.packUnit}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (item.lastOrderedQty != null)
                        "Last ordered: ${formatQty(item.lastOrderedQty)} on ${item.lastOrderedDate}"
                    else "No previous order",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = onHandText,
                    onValueChange = { text ->
                        onHandText = text
                        text.toDoubleOrNull()?.let(onOnHandChange)
                    },
                    label = { Text("On hand") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.width(90.dp)
                )
                OutlinedTextField(
                    value = cartonsText,
                    onValueChange = { text ->
                        cartonsText = text
                        onCartonsChange(text.toIntOrNull() ?: 0)
                    },
                    label = { Text("Order") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(80.dp)
                )
            }
        }
    )
}

private fun formatQty(qty: Double): String =
    if (qty == qty.toInt().toDouble()) qty.toInt().toString() else qty.toString()
