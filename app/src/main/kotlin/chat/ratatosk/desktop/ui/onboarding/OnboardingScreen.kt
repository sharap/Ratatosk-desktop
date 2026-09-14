package chat.ratatosk.desktop.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.ui.components.SecretTextField

@Composable
fun OnboardingScreen(viewModel: RatatoskViewModel) {
    var label by remember { mutableStateOf("Основной") }
    var displayName by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    val notices by viewModel.honestNotices.collectAsState()
    val onboardingError by viewModel.error.collectAsState()
    val availableAccounts by viewModel.availableAccounts.collectAsState()
    
    var showCompanionSetup by remember { mutableStateOf(false) }
    var showNoPinWarning by remember { mutableStateOf(false) }
    var bindToDevice by remember { mutableStateOf(false) }
    val secretStoreAvailable by viewModel.secretStoreAvailable.collectAsState()

    val createIdentity = {
        viewModel.initialize(
            label,
            pin.takeIf { it.isNotEmpty() },
            displayName.takeIf { it.isNotEmpty() } ?: "User",
            bindToDevice = bindToDevice && secretStoreAvailable == true
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = "Welcome to Ratatosk",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Secure, decentralized messenger",
            style = MaterialTheme.typography.bodyMedium
        )

        onboardingError?.let { err ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text(
                    text = err,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Ярлык аккаунта (локально)") },
            modifier = Modifier.fillMaxWidth(0.8f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text(Strings.DISPLAY_NAME) },
            modifier = Modifier.fillMaxWidth(0.8f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SecretTextField(
            value = pin,
            onValueChange = { pin = it },
            label = Strings.ENTER_PIN,
            modifier = Modifier.fillMaxWidth(0.8f)
        )

        // Выбор на всю жизнь аккаунта: секрет устройства задаётся при создании.
        if (secretStoreAvailable == true) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(0.8f),
                verticalAlignment = Alignment.Top
            ) {
                Checkbox(checked = bindToDevice, onCheckedChange = { bindToDevice = it })
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(Strings.BIND_TO_DEVICE, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (bindToDevice) Strings.BIND_TO_DEVICE_WARNING else Strings.BIND_TO_DEVICE_DESC,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (bindToDevice) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(0.8f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (availableAccounts.isNotEmpty()) {
                OutlinedButton(
                    onClick = { viewModel.setCreatingNewAccount(false) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(Strings.CANCEL)
                }
            }
            Button(
                // Без PIN ключ базы лежит в ней открыто, и ядро требует сказать
                // об этом до создания (FFI.md, §8.6), а не после.
                // С секретом устройства и без PIN база защищена машиной — это
                // законное сочетание, предупреждать не о чем.
                onClick = { if (pin.isEmpty() && !(bindToDevice && secretStoreAvailable == true)) showNoPinWarning = true else createIdentity() },
                modifier = Modifier.weight(1f),
                enabled = displayName.isNotBlank() && label.isNotBlank()
            ) {
                Text(Strings.GENERATE_IDENTITY)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = { showCompanionSetup = true }) {
            Icon(Icons.Default.Devices, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(Strings.LINK_COMPANION)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = Strings.SECURITY_NOTICES,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(0.8f)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(0.8f).weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(notices) { notice ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = notice,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    if (showNoPinWarning) {
        val warning = remember { runCatching { org.ratatosk.core.noPinWarning() }.getOrNull() }
        AlertDialog(
            onDismissRequest = { showNoPinWarning = false },
            title = { Text(Strings.NO_PIN_TITLE) },
            text = { Text(warning ?: Strings.NO_PIN_FALLBACK) },
            confirmButton = {
                TextButton(onClick = {
                    showNoPinWarning = false
                    createIdentity()
                }) {
                    Text(Strings.CREATE_WITHOUT_PIN, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                Button(onClick = { showNoPinWarning = false }) {
                    Text(Strings.CANCEL)
                }
            }
        )
    }

    if (showCompanionSetup) {
        CompanionSetupDialog(
            canRemember = secretStoreAvailable == true,
            onDismiss = { showCompanionSetup = false },
            onLink = { uri, useCache ->
                viewModel.initializeCompanion(uri, useCache)
                showCompanionSetup = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionSetupDialog(
    canRemember: Boolean,
    onDismiss: () -> Unit,
    onLink: (String, Boolean) -> Unit
) {
    var uri by remember { mutableStateOf("") }
    var useCache by remember(canRemember) { mutableStateOf(canRemember) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.LINK_COMPANION) },
        text = {
            Column {
                Text(Strings.COMPANION_DESC, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = uri,
                    onValueChange = { uri = it },
                    label = { Text(Strings.PASTE_LINK) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("ratatosk:v0:pair:...") }
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useCache, onCheckedChange = { useCache = it }, enabled = canRemember)
                    Text(Strings.COMPANION_CACHE_DESC, style = MaterialTheme.typography.bodySmall)
                }
                if (!canRemember) {
                    // Ссылка — секрет сопряжения; без хранилища ОС её не запоминаем.
                    Text(
                        Strings.COMPANION_NOT_REMEMBERED,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onLink(uri, useCache) },
                enabled = uri.startsWith("ratatosk:v0:pair:")
            ) {
                Text(Strings.ADD)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.CANCEL)
            }
        }
    )
}
