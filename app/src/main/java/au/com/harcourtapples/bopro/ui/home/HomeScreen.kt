package au.com.harcourtapples.bopro.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStocktake: () -> Unit,
    onReceive: () -> Unit,
    onOrderPrep: () -> Unit,
    onSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BackOfficePro Mobile") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
        ) {
            Text(
                "What would you like to do?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            FeatureCard(
                icon = { Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(40.dp)) },
                title = "Stocktake",
                description = "Scan barcodes to record stock counts.\nWiFi mode syncs live; offline mode saves locally.",
                onClick = onStocktake,
                modifier = Modifier.fillMaxWidth()
            )

            FeatureCard(
                icon = { Icon(Icons.Default.Inventory, null, modifier = Modifier.size(40.dp)) },
                title = "Receive Stock",
                description = "Scan incoming deliveries against\na purchase order.",
                onClick = onReceive,
                modifier = Modifier.fillMaxWidth()
            )

            FeatureCard(
                icon = { Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.size(40.dp)) },
                title = "Prepare Order",
                description = "Pick a supplier, count what's on hand,\nand build a market order from min/max levels.",
                onClick = onOrderPrep,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FeatureCard(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { icon() }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start)
            }
        }
    }
}
