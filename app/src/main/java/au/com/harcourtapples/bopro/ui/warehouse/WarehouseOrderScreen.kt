package au.com.harcourtapples.bopro.ui.warehouse

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import au.com.harcourtapples.bopro.BoproApplication
import au.com.harcourtapples.bopro.api.models.WarehouseOrderItem
import au.com.harcourtapples.bopro.scanner.rememberScanFeedback
import au.com.harcourtapples.bopro.ui.stocktake.scan.CameraPreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehouseOrderScreen(serverUrl: String, apiKey: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as BoproApplication
    val vm: WarehouseOrderViewModel = viewModel(factory = WarehouseOrderViewModel.factory(app.warehouseOrderRepo))
    val state by vm.state.collectAsState()
    val feedback = rememberScanFeedback()

    LaunchedEffect(serverUrl, apiKey) { vm.load(serverUrl, apiKey) }
    LaunchedEffect(state.scanResult) {
        when (state.scanResult) {
            is WarehouseScanResult.Listed -> feedback.confirm()
            is WarehouseScanResult.NotListed -> feedback.reject()
            null -> {}
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCameraPermission = it }

    var confirmSend by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WarehouseOrderItem?>(null) }

    // Back from the camera returns to the list rather than leaving the screen.
    BackHandler(enabled = state.scanning) { vm.stopScanning() }

    val success = state.submitResult as? WarehouseSubmitResult.Success

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.scanning) "Scan Barcode" else "Warehouse Order") },
                navigationIcon = {
                    IconButton(onClick = { if (state.scanning) vm.stopScanning() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (!state.scanning && success == null && state.items.isNotEmpty()) {
                Surface(shadowElevation = 8.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${state.lineCount} item${if (state.lineCount != 1) "s" else ""} · ${fmt(state.totalUnits)} units",
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = state.notes, onValueChange = vm::setNotes,
                            label = { Text("Notes (optional)") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
                                    vm.startScanning()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp)); Text("Scan")
                            }
                            Button(
                                onClick = { confirmSend = true },
                                enabled = state.lineCount > 0 && !state.isSubmitting,
                                modifier = Modifier.weight(2f)
                            ) {
                                if (state.isSubmitting) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                    Spacer(Modifier.width(8.dp)); Text("Sending…")
                                } else Text("Confirm order & send")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            success != null -> Column(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Order sent!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${success.lineCount} item${if (success.lineCount != 1) "s" else ""}, ${fmt(success.totalUnits)} units.\n" +
                        "Saved to the PC's Downloads folder:\n${success.pdf}\n${success.csv}",
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            }
            state.isLoading -> Box(Modifier.padding(padding).fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            state.error != null -> Column(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { vm.load(serverUrl, apiKey) }) { Text("Retry") }
            }
            state.items.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize()) {
                Text(
                    "No products are on the Warehouse Order list.\nTick 'Warehouse Order' in Product Detail in BackOfficePro.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                )
            }
            state.scanning -> Box(Modifier.padding(padding).fillMaxSize().background(Color.Black)) {
                if (!hasCameraPermission) {
                    Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Camera permission required", color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Grant Permission") }
                    }
                } else {
                    CameraPreview(isAnalyzing = state.scanResult == null, onBarcodeDetected = vm::onBarcodeDetected)
                    Box(Modifier.size(260.dp).align(Alignment.Center).border(2.dp, Color.White, RoundedCornerShape(8.dp)))
                    Column(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "${state.lineCount} item${if (state.lineCount != 1) "s" else ""} · ${fmt(state.totalUnits)} units on order",
                            color = Color.White,
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Button(onClick = vm::stopScanning) { Text("Done scanning") }
                    }
                }
            }
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(state.items, key = { it.barcode }) { item ->
                    val q = state.qty[item.barcode] ?: 0.0
                    ListItem(
                        modifier = Modifier.clickable { editing = item },
                        headlineContent = {
                            Text(item.description, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        },
                        supportingContent = {
                            Text(item.barcode, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingContent = {
                            Text(
                                if (q > 0) fmt(q) else "—",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (q > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    HorizontalDivider()
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    // ── Scan result: add quantity, or reject an unlisted barcode ───────────────
    when (val r = state.scanResult) {
        is WarehouseScanResult.Listed -> QtyDialog(
            title = r.item.description,
            message = if (r.currentQty > 0) "Already on order: ${fmt(r.currentQty)}\nEnter the quantity to ADD (e.g. from the second location)."
                      else "Enter the quantity to order.",
            initial = "1", confirmLabel = if (r.currentQty > 0) "Add to order" else "Add",
            onConfirm = { vm.addQty(r.item.barcode, it) }, onCancel = vm::dismissScanResult
        )
        is WarehouseScanResult.NotListed -> AlertDialog(
            onDismissRequest = vm::dismissScanResult,
            title = { Text("Not on Warehouse Order list") },
            text = { Text("Barcode ${r.barcode} isn't flagged for Warehouse Order in BackOfficePro.") },
            confirmButton = { TextButton(onClick = vm::dismissScanResult) { Text("OK") } }
        )
        null -> {}
    }

    // ── Edit a line's total from the list (0 removes it) ───────────────────────
    editing?.let { item ->
        QtyDialog(
            title = item.description,
            message = "Set the total order quantity. Enter 0 to remove it.",
            initial = state.qty[item.barcode]?.let(::fmt) ?: "", confirmLabel = "Set",
            allowZero = true,
            onConfirm = { vm.setQty(item.barcode, it); editing = null }, onCancel = { editing = null }
        )
    }

    if (confirmSend) {
        AlertDialog(
            onDismissRequest = { confirmSend = false },
            title = { Text("Send warehouse order?") },
            text = { Text("${state.lineCount} item${if (state.lineCount != 1) "s" else ""}, ${fmt(state.totalUnits)} units.\nA CSV and PDF will be saved on the PC.") },
            confirmButton = { TextButton(onClick = { confirmSend = false; vm.submit(serverUrl, apiKey) }) { Text("Send") } },
            dismissButton = { TextButton(onClick = { confirmSend = false }) { Text("Cancel") } }
        )
    }

    (state.submitResult as? WarehouseSubmitResult.Failure)?.let { f ->
        AlertDialog(
            onDismissRequest = vm::clearResult,
            title = { Text("Order not sent") },
            text = { Text("${f.message}\n\nYour order is still saved on this phone.") },
            confirmButton = { TextButton(onClick = { vm.clearResult(); vm.submit(serverUrl, apiKey) }) { Text("Retry") } },
            dismissButton = { TextButton(onClick = vm::clearResult) { Text("Close") } }
        )
    }
}

@Composable
private fun QtyDialog(
    title: String, message: String, initial: String, confirmLabel: String,
    allowZero: Boolean = false, onConfirm: (Double) -> Unit, onCancel: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    val value = text.toDoubleOrNull()
    val valid = value != null && value <= 99_999 && (if (allowZero) value >= 0 else value > 0)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message)
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value ?: return@TextButton) }, enabled = valid) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

private fun fmt(qty: Double): String = if (qty == qty.toInt().toDouble()) qty.toInt().toString() else qty.toString()
