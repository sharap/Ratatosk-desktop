package chat.ratatosk.desktop.ui.unlock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.ui.AccountItem
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.ui.components.SecretTextField
import kotlinx.coroutines.launch
import org.ratatosk.core.FfiAccount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSelectionScreen(viewModel: RatatoskViewModel) {
    val accounts by viewModel.allAccounts.collectAsState()
    val isFindingHidden by viewModel.isFindingHidden.collectAsState()
    var showHiddenDialog by remember { mutableStateOf(false) }
    var showCompanionSetup by remember { mutableStateOf(false) }
    var hiddenPin by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var confirmDelete by remember { mutableStateOf<FfiAccount?>(null) }
    var hiddenError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(Strings.SELECT_ACCOUNT) },
                actions = {
                    IconButton(onClick = { showCompanionSetup = true }) {
                        Icon(Icons.Default.Devices, contentDescription = Strings.LINK_COMPANION)
                    }
                    TextButton(onClick = { showImport = true }) {
                        Text(Strings.IMPORT_ARCHIVE)
                    }
                    TextButton(onClick = { showHiddenDialog = true }) {
                        Text(Strings.FIND_HIDDEN)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.setCreatingNewAccount(true) }) {
                Icon(Icons.Default.Add, contentDescription = "Add Account")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (accounts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(Strings.NO_ACCOUNTS)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(accounts) { item ->
                        when (item) {
                            is AccountItem.Local -> {
                                ListItem(
                                    headlineContent = { Text(item.account.label) },
                                    supportingContent = { Text("ID: ${item.account.id.toHexString().take(8)}...") },
                                    leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                                    trailingContent = {
                                        IconButton(onClick = { confirmDelete = item.account }) {
                                            Icon(Icons.Default.Delete, contentDescription = Strings.ACCOUNT_DELETE, tint = MaterialTheme.colorScheme.error)
                                        }
                                    },
                                    modifier = Modifier.clickable { viewModel.selectAccount(item.account) }
                                )
                            }
                            is AccountItem.Companion -> {
                                ListItem(
                                    headlineContent = { Text(item.pairing.phoneName) },
                                    supportingContent = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            SuggestionChip(
                                                onClick = {},
                                                label = { Text("Компаньон", style = MaterialTheme.typography.labelSmall) },
                                                icon = { Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("ID: ${item.pairing.deviceId.take(8)}...", style = MaterialTheme.typography.bodySmall)
                                        }
                                    },
                                    leadingContent = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
                                    trailingContent = {
                                        IconButton(onClick = { viewModel.removeCompanionPairing(item.pairing.deviceId) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Удалить сопряжение", tint = MaterialTheme.colorScheme.error)
                                        }
                                    },
                                    modifier = Modifier.clickable { viewModel.openCompanionPairing(item.pairing) }
                                )
                            }
                        }
                    }
                }
            }

            if (isFindingHidden) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(Strings.FINDING_HIDDEN_DESC)
                    }
                }
            }
        }
    }

    if (showHiddenDialog) {
        AlertDialog(
            onDismissRequest = { if (!isFindingHidden) showHiddenDialog = false },
            title = { Text(Strings.FIND_HIDDEN) },
            text = {
                Column {
                    Text(Strings.HIDDEN_PIN_DESC)
                    Spacer(modifier = Modifier.height(8.dp))
                    SecretTextField(
                        value = hiddenPin,
                        onValueChange = { hiddenPin = it; hiddenError = null },
                        label = "PIN",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isFindingHidden
                    )
                    hiddenError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (isFindingHidden) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(Strings.OPENING_SLOW_NOTE, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        hiddenError = null
                        viewModel.findHiddenAccount(
                            pin = hiddenPin,
                            onFound = { id ->
                                showHiddenDialog = false
                                // Тем же PIN и открываем: спрашивать его дважды незачем.
                                viewModel.openHidden(id, hiddenPin)
                            },
                            // Ответ — там же, где вопрос: снэкбар под диалогом не виден.
                            onNotFound = { hiddenError = Strings.HIDDEN_NOT_FOUND }
                        )
                    },
                    enabled = hiddenPin.isNotEmpty() && !isFindingHidden
                ) {
                    Text(Strings.FIND)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showHiddenDialog = false },
                    enabled = !isFindingHidden
                ) {
                    Text(Strings.CANCEL)
                }
            }
        )
    }

    if (showImport) {
        chat.ratatosk.desktop.ui.backup.ImportArchiveFlow(viewModel, onClose = { showImport = false })
    }
    chat.ratatosk.desktop.ui.backup.BackupProgressAndResult(viewModel)

    confirmDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(Strings.ACCOUNT_DELETE_TITLE.format(account.label)) },
            text = {
                Column {
                    Text(Strings.ACCOUNT_DELETE_DESC)
                    Spacer(Modifier.height(8.dp))
                    // Словами ядра: «стёрто безвозвратно» обещать нельзя.
                    Text(
                        Strings.ACCOUNT_DELETE_FLASH_NOTE,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.wipeAccount(account)
                        confirmDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(Strings.DELETE) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(Strings.CANCEL) } }
        )
    }

    if (showCompanionSetup) {
        chat.ratatosk.desktop.ui.onboarding.CompanionSetupDialog(
            canRemember = viewModel.secretStoreAvailable.collectAsState().value == true,
            onDismiss = { showCompanionSetup = false },
            onLink = { uri, useCache, port, peerAddr, useTor ->
                viewModel.initializeCompanion(uri, useCache, port = port, peerAddr = peerAddr, useTor = useTor)
                showCompanionSetup = false
            }
        )
    }
}

private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
