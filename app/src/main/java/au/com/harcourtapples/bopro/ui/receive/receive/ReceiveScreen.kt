package au.com.harcourtapples.bopro.ui.receive.receive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import au.com.harcourtapples.bopro.BoproApplication
import au.com.harcourtapples.bopro.api.models.PoLine
import au.com.harcourtapples.bopro.ui.stocktake.scan.BottomSheetContainer
import au.com.harcourtapples.bopro.ui.stocktake.scan.CameraPreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    poNumber: String,
    serverUrl: String,
    apiKey: String,
    onBack: () -> Unit
) {
    val app = LocalContext.current.applicationContext as BoproApplication
    val vm: ReceiveViewModel = viewModel(factory = ReceiveViewModel.factory(app.receiveRepo))
    val state by vm.state.collectAsState()

    LaunchedEffect(poNumber, serverUrl, apiKey) { vm.loadPo(serverUrl, apiKey, poNumber) }

    val scanning = state.scan is LineScan.Active ||
                   state.scan is LineScan.Found  ||
                   state.scan is LineScan.NotOnOrder

    Box(Modifier.fillMaxSize()) {
        if (scanning) {
            // ── Camera overlay ────────────────────────────────────────────────
            CameraPreview(isAnalyzing = state.scan is LineScan.Active, onBarcodeDetected = { vm.onBarcodeDetected(it) })

            Box(Modifier.fillMaxSize()) {
                Box(Modifier.size(260.dp).align(Alignment.Center).border(2.dp, Color.White, RoundedCornerShape(8.dp)))
            }

            IconButton(onClick = { vm.stopScanning() }, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Stop scanning", tint = Color.White)
            }

            if (state.scan is LineScan.Active) {
                Text("Scan a barcode on the delivery", color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp))
            }

            when (val scan = state.scan) {
                is LineScan.Found -> LineQtySheet(
                    line = scan.line,
                    currentQty = scan.alreadyReceived,
                    onConfirm = { qty -> vm.setLineQty(scan.line.id, qty) },
                    onCancel  = { vm.dismissScan() }
                )
                is LineScan.NotOnOrder -> BottomSheetContainer {
                    Text("Not on this order", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Barcode ${scan.barcode} is not on PO ${state.po?.poNumber ?: ""}.",
                        style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { vm.dismissScan() }, modifier = Modifier.fillMaxWidth()) { Text("OK — continue scanning") }
                }
                else -> {}
            }
        } else {
            // ── Normal PO detail view ─────────────────────────────────────────
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(state.po?.poNumber ?: poNumber, style = MaterialTheme.typography.titleMedium)
                                state.po?.let { Text(it.supplierName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        },
                        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
                    )
                },
                floatingActionButton = {
                    if (state.po != null && !state.isSubmitting && state.submitResult == null) {
                        FloatingActionButton(onClick = { vm.startScanning() }) {
                            Icon(Icons.Default.CameraAlt, "Scan barcode")
                        }
                    }
                },
                bottomBar = {
                    if (state.po != null && state.submitResult == null) {
                        val hasQtys = state.receivedQtys.any { (_, qty) -> qty > 0 }
                        Surface(shadowElevation = 8.dp) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = state.invoiceNumber,
                                    onValueChange = { vm.setInvoiceNumber(it) },
                                    label = { Text("Invoice Number (optional)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = { vm.submit(serverUrl, apiKey) },
                                    enabled = hasQtys && !state.isSubmitting,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (state.isSubmitting) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Submitting…")
                                    } else {
                                        val receivedCount = state.receivedQtys.count { (_, qty) -> qty > 0 }
                                        Text("Submit Receipt ($receivedCount line${if (receivedCount != 1) "s" else ""})")
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
                        Button(onClick = { vm.loadPo(serverUrl, apiKey, poNumber) }) { Text("Retry") }
                    }
                    state.submitResult is SubmitResult.Success -> {
                        val result = state.submitResult as SubmitResult.Success
                        Column(Modifier.padding(padding).fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(16.dp))
                            Text("Receipt Submitted!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("${result.linesReceived} line${if (result.linesReceived != 1) "s" else ""} received\nPO status: ${result.status}",
                                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                        }
                    }
                    state.submitResult is SubmitResult.Failure -> {
                        val result = state.submitResult as SubmitResult.Failure
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
                    state.po != null -> {
                        val po = state.po!!
                        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                            if (po.status == "PARTIAL") {
                                item {
                                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                                        Text("Partial receipt — some lines already received",
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            items(po.lines, key = { it.id }) { line ->
                                PoLineRow(
                                    line = line,
                                    receivedQty = state.receivedQtys[line.id] ?: 0.0,
                                    onQtyChange = { qty -> vm.setLineQty(line.id, qty) }
                                )
                                HorizontalDivider()
                            }
                            item { Spacer(Modifier.height(160.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PoLineRow(line: PoLine, receivedQty: Double, onQtyChange: (Double) -> Unit) {
    var editing by remember(line.id) { mutableStateOf(false) }
    val isFullyReceived = receivedQty >= line.orderedQty && line.orderedQty > 0
    val hasAny = receivedQty > 0

    ListItem(
        leadingContent = {
            Icon(
                if (isFullyReceived) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                null,
                tint = when {
                    isFullyReceived -> MaterialTheme.colorScheme.primary
                    hasAny         -> MaterialTheme.colorScheme.tertiary
                    else           -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        },
        headlineContent = {
            Text(line.description, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (isFullyReceived) FontWeight.Normal else FontWeight.Medium)
        },
        supportingContent = {
            Column {
                Text(line.barcode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Ordered: ${line.orderedQty.toInt()} carton${if (line.orderedQty != 1.0) "s" else ""}" +
                    if (line.packQty > 1) " (${line.orderedUnits} units)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            if (editing) {
                var text by remember { mutableStateOf(if (receivedQty > 0) receivedQty.toInt().toString() else "") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(value = text, onValueChange = { text = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.width(72.dp), label = { Text("Qty") })
                    TextButton(onClick = { onQtyChange(text.toDoubleOrNull() ?: 0.0); editing = false }) { Text("OK") }
                }
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (receivedQty > 0) "${receivedQty.toInt()} / ${line.orderedQty.toInt()}" else "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isFullyReceived -> MaterialTheme.colorScheme.primary
                            hasAny         -> MaterialTheme.colorScheme.tertiary
                            else           -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    TextButton(onClick = { editing = true }, contentPadding = PaddingValues(0.dp)) {
                        Text("Edit", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    )
}

@Composable
private fun LineQtySheet(line: PoLine, currentQty: Double, onConfirm: (Double) -> Unit, onCancel: () -> Unit) {
    val defaultQty = if (currentQty > 0) currentQty else line.orderedQty
    var text by remember { mutableStateOf(defaultQty.toInt().toString()) }

    BottomSheetContainer {
        Text(line.description, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
        Text("Barcode: ${line.barcode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Ordered: ${line.orderedQty.toInt()} carton${if (line.orderedQty != 1.0) "s" else ""}" +
            if (line.packQty > 1) " (${line.orderedUnits} units)" else "",
            style = MaterialTheme.typography.bodySmall
        )
        if (currentQty > 0) {
            Text("Previously received: ${currentQty.toInt()} carton${if (currentQty != 1.0) "s" else ""}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { text = ((text.toIntOrNull() ?: 1) - 1).coerceAtLeast(0).toString() }) {
                Text("−", style = MaterialTheme.typography.headlineSmall)
            }
            OutlinedTextField(value = text, onValueChange = { text = it },
                label = { Text("Cartons received") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.weight(1f))
            IconButton(onClick = { text = ((text.toIntOrNull() ?: 0) + 1).toString() }) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = { onConfirm(text.toDoubleOrNull() ?: 0.0) }, modifier = Modifier.weight(1f)) { Text("Confirm") }
        }
    }
}
