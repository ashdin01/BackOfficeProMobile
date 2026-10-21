package au.com.harcourtapples.bopro.ui.receive.receive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
                   state.scan is LineScan.NotOnOrder

    Box(Modifier.fillMaxSize()) {
        if (scanning) {
            // ── Camera overlay ────────────────────────────────────────────────
            CameraPreview(isAnalyzing = state.scan is LineScan.Active, includeCartonFormats = true, onBarcodeDetected = { vm.onBarcodeDetected(it) })

            Box(Modifier.fillMaxSize()) {
                Box(Modifier.size(260.dp).align(Alignment.Center).border(2.dp, Color.White, RoundedCornerShape(8.dp)))
            }

            IconButton(onClick = { vm.stopScanning() }, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Stop scanning", tint = Color.White)
            }

            if (state.scan is LineScan.Active) {
                val flash = state.lastScan
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp, start = 16.dp, end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    if (flash == null) {
                        Text("Scan a carton or item barcode", color = Color.White)
                    } else {
                        Text("+${flash.added}${if (flash.isCarton) " (carton)" else ""} · ${flash.description}",
                            color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2)
                        Text("Counted so far: ${flash.total} units", color = Color.White)
                    }
                }
            }

            (state.scan as? LineScan.NotOnOrder)?.let { scan ->
                BottomSheetContainer {
                    Text("Not on this order", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Barcode ${scan.barcode} is not on PO ${state.po?.poNumber ?: ""}.",
                        style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { vm.dismissScan() }, modifier = Modifier.fillMaxWidth()) { Text("OK — continue scanning") }
                }
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
                        val hasQtys = true  // saving an empty count clears staged counts
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
                                        val countedLines = state.countedUnits.count { (_, u) -> u > 0 }
                                        Text("Save Count ($countedLines line${if (countedLines != 1) "s" else ""})")
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
                            Text("Count Saved!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("${result.linesSaved} line${if (result.linesSaved != 1) "s" else ""} counted.\nStock and PO status are unchanged — open the PO in BackOfficePro to confirm the receipt, check costs and match the invoice.",
                                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                        }
                    }
                    state.submitResult is SubmitResult.Failure -> {
                        val result = state.submitResult as SubmitResult.Failure
                        Column(Modifier.padding(padding).fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("Save failed", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
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
                                    countedUnits = state.countedUnits[line.id] ?: 0,
                                    onEdit = { vm.editLine(line) }
                                )
                                HorizontalDivider()
                            }
                            item { Spacer(Modifier.height(160.dp)) }
                        }
                    }
                }
            }
        }

        (state.scan as? LineScan.Edit)?.let { edit ->
            LineQtySheet(
                line = edit.line,
                currentUnits = state.countedUnits[edit.line.id] ?: 0,
                onConfirm = { units -> vm.setLineUnits(edit.line.id, units) },
                onCancel  = { vm.cancelEdit() }
            )
        }
    }
}

private fun cartonText(units: Int, packQty: Int): String =
    if (packQty > 1) " (${"%.1f".format(units.toDouble() / packQty).removeSuffix(".0")} ctn)" else ""

@Composable
private fun PoLineRow(line: PoLine, countedUnits: Int, onEdit: () -> Unit) {
    val previousUnits = (line.receivedQty * maxOf(1, line.packQty)).toInt()
    val orderedUnits = line.orderedUnits
    val remaining = orderedUnits - previousUnits
    val hasAny = countedUnits > 0
    val isComplete = hasAny && countedUnits >= remaining
    val isOver = hasAny && countedUnits > remaining
    val accent = when {
        isOver     -> MaterialTheme.colorScheme.error
        isComplete -> MaterialTheme.colorScheme.primary
        hasAny     -> MaterialTheme.colorScheme.tertiary
        else       -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    ListItem(
        modifier = Modifier.clickable(onClick = onEdit),
        leadingContent = {
            Icon(if (isComplete) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = accent)
        },
        headlineContent = {
            Text(line.description, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (isComplete) FontWeight.Normal else FontWeight.Medium)
        },
        supportingContent = {
            Column {
                Text(line.barcode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Ordered: ${line.orderedQty.toInt()} carton${if (line.orderedQty != 1.0) "s" else ""}" +
                    if (line.packQty > 1) " ($orderedUnits units)" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (previousUnits > 0) {
                    Text("Already received: $previousUnits units", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isOver) {
                    Text("Over by ${countedUnits - remaining}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(if (hasAny) "$countedUnits / $remaining" else "—",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
                if (hasAny) Text(cartonText(countedUnits, line.packQty).trim(), style = MaterialTheme.typography.labelSmall, color = accent)
            }
        }
    )
}

@Composable
private fun LineQtySheet(line: PoLine, currentUnits: Int, onConfirm: (Int) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(if (currentUnits > 0) currentUnits.toString() else "") }
    val pack = maxOf(1, line.packQty)
    val units = text.toIntOrNull() ?: 0

    BottomSheetContainer {
        Text(line.description, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
        Text("Barcode: ${line.barcode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (line.cartonSku.isNotBlank()) {
            Text("Carton SKU: ${line.cartonSku} ($pack units)", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        OutlinedTextField(value = text, onValueChange = { text = it.filter(Char::isDigit) },
            label = { Text("Units received${cartonText(units, pack)}") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, modifier = Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { text = (units - 1).coerceAtLeast(0).toString() }, modifier = Modifier.weight(1f)) { Text("−1") }
            OutlinedButton(onClick = { text = (units + 1).toString() }, modifier = Modifier.weight(1f)) { Text("+1") }
            if (pack > 1) {
                OutlinedButton(onClick = { text = (units - pack).coerceAtLeast(0).toString() }, modifier = Modifier.weight(1f)) { Text("−$pack") }
                OutlinedButton(onClick = { text = (units + pack).toString() }, modifier = Modifier.weight(1f)) { Text("+$pack") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = { onConfirm(units) }, modifier = Modifier.weight(1f)) { Text("Confirm") }
        }
    }
}
