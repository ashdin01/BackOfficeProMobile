package au.com.harcourtapples.bopro.ui.stock

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import au.com.harcourtapples.bopro.BoproApplication
import au.com.harcourtapples.bopro.api.models.StockInfo
import au.com.harcourtapples.bopro.scanner.rememberScanFeedback
import au.com.harcourtapples.bopro.ui.stocktake.scan.BottomSheetContainer
import au.com.harcourtapples.bopro.ui.stocktake.scan.CameraPreview
import java.util.Locale

private fun qty(v: Double) = if (v % 1 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.3f", v).trimEnd('0')
private fun money(v: Double) = String.format(Locale.US, "$%.2f", v)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockCheckScreen(serverUrl: String, apiKey: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as BoproApplication
    val vm: StockCheckViewModel = viewModel(factory = StockCheckViewModel.factory(app.stockRepo))
    val state by vm.state.collectAsState()
    val feedback = rememberScanFeedback()
    LaunchedEffect(state) {
        when (state) {
            is StockState.Found, is StockState.Saved -> feedback.confirm()
            is StockState.NotFound, is StockState.Error, is StockState.Changed -> feedback.reject()
            else -> {}
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCameraPermission = it }
    LaunchedEffect(Unit) { if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Stock Check") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.6f),
                    titleContentColor = Color.White, navigationIconContentColor = Color.White))
        },
        containerColor = Color.Black
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (!hasCameraPermission) {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera permission required", color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Grant Permission") }
                }
                return@Box
            }
            CameraPreview(isAnalyzing = state is StockState.Scanning,
                onBarcodeDetected = { vm.onBarcodeDetected(it, serverUrl, apiKey) })
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.size(260.dp).align(Alignment.Center).border(2.dp, Color.White, RoundedCornerShape(8.dp)))
            }

            when (val s = state) {
                is StockState.Scanning -> Text("Scan a product to check stock", color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 8.dp))
                is StockState.LookingUp, is StockState.Saving ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                is StockState.Found -> InfoSheet(s.info, onAdjust = { vm.startAdjust(s.info) }, onDone = { vm.reset() })
                is StockState.Adjusting -> AdjustSheet(s.info, notice = null,
                    onSave = { vm.save(s.info, it, serverUrl, apiKey) }, onCancel = { vm.reset() })
                is StockState.Changed -> AdjustSheet(s.info,
                    notice = "Stock on hand changed since you scanned it (now ${qty(s.info.sohQty)}). Re-enter the new total.",
                    onSave = { vm.save(s.info, it, serverUrl, apiKey) }, onCancel = { vm.reset() })
                is StockState.Saved -> SavedSheet(s, onDone = { vm.reset() })
                is StockState.NotFound -> MessageSheet("Not Found", "Barcode ${s.barcode} is not in BackOfficePro.") { vm.reset() }
                is StockState.Error -> MessageSheet("Error", s.message) { vm.reset() }
            }
        }
    }
}

@Composable
private fun InfoSheet(info: StockInfo, onAdjust: () -> Unit, onDone: () -> Unit) {
    BottomSheetContainer {
        Text(info.description, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(info.barcode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        InfoRow("Stock on Hand", qty(info.sohQty), bold = true)
        InfoRow("Stock on Order", qty(info.onOrderQty))
        InfoRow("Default Supplier", info.defaultSupplier ?: "—")
        InfoRow("Cost (inc GST)", money(info.costIncGst))
        InfoRow("Sell Price", money(info.sellPrice))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Scan Next") }
            Button(onClick = onAdjust, modifier = Modifier.weight(1f)) { Text("Adjust Stock On Hand") }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AdjustSheet(info: StockInfo, notice: String?, onSave: (Double) -> Unit, onCancel: () -> Unit) {
    var text by remember(info.sohQty) { mutableStateOf("") }
    val newQty = text.toDoubleOrNull()?.takeIf { it >= 0 }
    BottomSheetContainer {
        Text(info.description, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (notice != null) Text(notice, color = MaterialTheme.colorScheme.error)
        InfoRow("Current Stock on Hand", qty(info.sohQty), bold = true)
        OutlinedTextField(value = text, onValueChange = { text = it },
            label = { Text("New total Stock on Hand") },
            supportingText = {
                if (newQty != null) {
                    val d = newQty - info.sohQty
                    Text(if (d == 0.0) "No change" else "Adjustment: ${if (d > 0) "+" else ""}${qty(d)}")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = { newQty?.let(onSave) }, enabled = newQty != null, modifier = Modifier.weight(1f)) { Text("Save") }
        }
    }
}

@Composable
private fun SavedSheet(s: StockState.Saved, onDone: () -> Unit) {
    val r = s.result
    BottomSheetContainer {
        Text(if (r.delta == 0.0) "No change" else "Stock Updated", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(s.description)
        Text("${qty(r.oldQty)} → ${qty(r.newQty)}" + if (r.delta != 0.0) "   (adjustment ${if (r.delta > 0) "+" else ""}${qty(r.delta)})" else "")
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Scan Next") }
    }
}

@Composable
private fun MessageSheet(title: String, message: String, onDismiss: () -> Unit) {
    BottomSheetContainer {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(message)
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("OK") }
    }
}
