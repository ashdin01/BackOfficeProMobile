package au.com.harcourtapples.bopro.ui.stocktake.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.WifiOff
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
import au.com.harcourtapples.bopro.api.models.Department
import au.com.harcourtapples.bopro.api.models.Session

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    serverUrl: String,
    apiKey: String = "",
    offline: Boolean,
    onOpenSession: (Int) -> Unit,
    onBack: () -> Unit,
    onSync: () -> Unit
) {
    val app = LocalContext.current.applicationContext as BoproApplication
    val vm: SessionsViewModel = viewModel(factory = SessionsViewModel.factory(app.stocktakeRepo))
    val state by vm.state.collectAsState()
    var showNewDialog by remember { mutableStateOf(false) }

    LaunchedEffect(serverUrl, apiKey, offline) { vm.load(serverUrl, offline, apiKey) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Stocktake Sessions")
                        if (offline) Icon(Icons.Default.WifiOff, "Offline", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (offline) IconButton(onClick = onSync) { Icon(Icons.Default.CloudUpload, "Sync") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewDialog = true }) { Icon(Icons.Default.Add, "New Session") }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Column(Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.load(serverUrl, offline, apiKey) }) { Text("Retry") }
                }
                state.sessions.isEmpty() -> Text(
                    if (offline) "No offline sessions yet.\nTap + to create one." else "No sessions yet. Tap + to create one.",
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.sessions, key = { it.id }) { session ->
                        ListItem(
                            modifier = Modifier.clickable { onOpenSession(session.id) },
                            headlineContent = { Text(session.label, fontWeight = FontWeight.SemiBold) },
                            supportingContent = {
                                Column {
                                    session.deptName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                    Text("${session.lineCount} items  •  ${session.startedAt.take(10)}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            trailingContent = {
                                Badge(containerColor = if (session.status == "OPEN") MaterialTheme.colorScheme.primaryContainer
                                      else MaterialTheme.colorScheme.surfaceVariant) { Text(session.status) }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showNewDialog) {
        NewSessionDialog(
            departments = if (offline) emptyList() else state.departments,
            offline = offline,
            onDismiss = { showNewDialog = false },
            onCreate = { label, deptId ->
                showNewDialog = false
                vm.createSession(serverUrl, offline, label, deptId, apiKey = apiKey, onSuccess = onOpenSession)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewSessionDialog(departments: List<Department>, offline: Boolean, onDismiss: () -> Unit, onCreate: (String, Int?) -> Unit) {
    var label by remember { mutableStateOf("") }
    var selectedDept by remember { mutableStateOf<Department?>(null) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Stocktake Session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it },
                    label = { Text("Session Label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (!offline && departments.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(value = selectedDept?.name ?: "All Departments", onValueChange = {}, readOnly = true,
                            label = { Text("Department (optional)") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text("All Departments") }, onClick = { selectedDept = null; expanded = false })
                            departments.forEach { dept ->
                                DropdownMenuItem(text = { Text(dept.name) }, onClick = { selectedDept = dept; expanded = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (label.isNotBlank()) onCreate(label.trim(), selectedDept?.id) }, enabled = label.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
