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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(Strings.SELECT_ACCOUNT) },
                actions = {
                    IconButton(onClick = { showCompanionSetup = true }) {
                        Icon(Icons.Default.Devices, contentDescription = Strings.LINK_COMPANION)
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
                                    modifier = Modifier.clickable { viewModel.initializeCompanion(item.pairing.inviteUri, item.pairing.useCache, item.pairing.deviceId) }
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
                    OutlinedTextField(
                        value = hiddenPin,
                        onValueChange = { hiddenPin = it },
                        label = { Text("PIN") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.findHiddenAccount(
                            pin = hiddenPin,
                            onFound = { id ->
                                showHiddenDialog = false
                                viewModel.selectAccount(FfiAccount(id, "Скрытый аккаунт", 0UL))
                            },
                            onNotFound = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Скрытый аккаунт не найден с таким PIN")
                                }
                            }
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

    if (showCompanionSetup) {
        chat.ratatosk.desktop.ui.onboarding.CompanionSetupDialog(
            onDismiss = { showCompanionSetup = false },
            onLink = { uri, useCache ->
                viewModel.initializeCompanion(uri, useCache)
                showCompanionSetup = false
            }
        )
    }
}

private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
