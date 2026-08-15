package au.com.harcourtapples.bopro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import au.com.harcourtapples.bopro.api.ApiClient
import au.com.harcourtapples.bopro.api.models.Supplier
import au.com.harcourtapples.bopro.ui.home.HomeScreen
import au.com.harcourtapples.bopro.ui.orderprep.prep.OrderPrepScreen
import au.com.harcourtapples.bopro.ui.orderprep.suppliers.SupplierListScreen
import au.com.harcourtapples.bopro.ui.receive.list.PoListScreen
import au.com.harcourtapples.bopro.ui.receive.receive.ReceiveScreen
import au.com.harcourtapples.bopro.ui.settings.SettingsScreen
import au.com.harcourtapples.bopro.ui.stocktake.detail.SessionDetailScreen
import au.com.harcourtapples.bopro.ui.stocktake.scan.ScanScreen
import au.com.harcourtapples.bopro.ui.stocktake.sessions.SessionsScreen
import au.com.harcourtapples.bopro.ui.stocktake.sync.SyncScreen
import au.com.harcourtapples.bopro.ui.theme.BoproTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = (application as BoproApplication).settingsStore
        setContent {
            BoproTheme {
                val serverUrl          by store.serverUrl.collectAsState(initial = SettingsStore.DEFAULT_URL)
                val apiKey             by store.apiKey.collectAsState(initial = "")
                val offline            by store.offlineMode.collectAsState(initial = false)
                val trustedFingerprint by store.trustedCertFingerprint.collectAsState(initial = "")

                LaunchedEffect(trustedFingerprint) {
                    ApiClient.setTrustedFingerprint(trustedFingerprint.ifBlank { null })
                }

                val nav = rememberNavController()
                var selectedSupplier by remember { mutableStateOf<Supplier?>(null) }

                NavHost(nav, startDestination = "home") {

                    composable("home") {
                        HomeScreen(
                            onStocktake  = { nav.navigate("stocktake/sessions") },
                            onReceive    = { nav.navigate("receive/list") },
                            onOrderPrep  = { nav.navigate("orderprep/suppliers") },
                            onSettings   = { nav.navigate("settings") }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(store = store, onBack = { nav.popBackStack() })
                    }

                    // ── Stocktake ──────────────────────────────────────────────

                    composable("stocktake/sessions") {
                        SessionsScreen(
                            serverUrl     = serverUrl,
                            apiKey        = apiKey,
                            offline       = offline,
                            onOpenSession = { id -> nav.navigate("stocktake/session/$id") },
                            onBack        = { nav.popBackStack() },
                            onSync        = { nav.navigate("stocktake/sync") }
                        )
                    }

                    composable("stocktake/session/{sessionId}") { back ->
                        val sessionId = back.arguments?.getString("sessionId")?.toIntOrNull() ?: return@composable
                        SessionDetailScreen(
                            sessionId = sessionId,
                            serverUrl = serverUrl,
                            apiKey    = apiKey,
                            offline   = offline,
                            onScan    = { nav.navigate("stocktake/scan/$sessionId") },
                            onBack    = { nav.popBackStack() }
                        )
                    }

                    composable("stocktake/scan/{sessionId}") { back ->
                        val sessionId = back.arguments?.getString("sessionId")?.toIntOrNull() ?: return@composable
                        ScanScreen(
                            sessionId = sessionId,
                            serverUrl = serverUrl,
                            apiKey    = apiKey,
                            offline   = offline,
                            onBack    = { nav.popBackStack() }
                        )
                    }

                    composable("stocktake/sync") {
                        SyncScreen(serverUrl = serverUrl, apiKey = apiKey, onBack = { nav.popBackStack() })
                    }

                    // ── Receive ────────────────────────────────────────────────

                    composable("receive/list") {
                        PoListScreen(
                            serverUrl  = serverUrl,
                            apiKey     = apiKey,
                            onSelectPo = { poNumber -> nav.navigate("receive/po/${poNumber}") },
                            onBack     = { nav.popBackStack() }
                        )
                    }

                    composable("receive/po/{poNumber}") { back ->
                        val poNumber = back.arguments?.getString("poNumber") ?: return@composable
                        ReceiveScreen(
                            poNumber  = poNumber,
                            serverUrl = serverUrl,
                            apiKey    = apiKey,
                            onBack    = { nav.popBackStack() }
                        )
                    }

                    // ── Order Prep ─────────────────────────────────────────────

                    composable("orderprep/suppliers") {
                        SupplierListScreen(
                            serverUrl        = serverUrl,
                            apiKey           = apiKey,
                            onSelectSupplier = { supplier ->
                                selectedSupplier = supplier
                                nav.navigate("orderprep/items")
                            },
                            onBack = { nav.popBackStack() }
                        )
                    }

                    composable("orderprep/items") {
                        val supplier = selectedSupplier ?: return@composable
                        OrderPrepScreen(
                            supplierId   = supplier.id,
                            supplierName = supplier.name,
                            serverUrl    = serverUrl,
                            apiKey       = apiKey,
                            onBack       = { nav.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
