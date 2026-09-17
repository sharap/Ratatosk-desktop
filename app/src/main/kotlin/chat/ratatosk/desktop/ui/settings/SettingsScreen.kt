package chat.ratatosk.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.ui.chat.formatFileSize
import chat.ratatosk.desktop.ui.components.SecretTextField
import chat.ratatosk.desktop.ui.theme.successColor
import chat.ratatosk.desktop.util.FilePicker
import chat.ratatosk.desktop.util.FileUtils
import org.ratatosk.core.FfiTransport
import org.ratatosk.core.FfiMailState
import org.ratatosk.core.FfiMailStatus
import org.ratatosk.core.FfiTorStatus
import org.ratatosk.core.lanWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: RatatoskViewModel,
    isTwoColumn: Boolean = false
) {
    val isCompanionMode by viewModel.isCompanionMode.collectAsState()
    
    val lanEnabled by viewModel.lanEnabled.collectAsState()
    val torEnabled by viewModel.torEnabled.collectAsState()
    val mailEnabled by viewModel.mailEnabled.collectAsState()
    
    val torStatus by viewModel.torStatus.collectAsState()
    val mailStatus by viewModel.mailStatus.collectAsState()
    val mailAccount by viewModel.mailAccount.collectAsState()
    val transportsReady by viewModel.transportsReady.collectAsState()
    val error by viewModel.error.collectAsState()

    val showName by viewModel.notificationsShowName.collectAsState()
    val showText by viewModel.notificationsShowText.collectAsState()
    var showLanWarning by remember { mutableStateOf(false) }
    var showMailSetup by remember { mutableStateOf(false) }
    var showMailDeleteConfirm by remember { mutableStateOf(false) }
    
    val chatTheme by viewModel.chatTheme.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.SETTINGS) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (isTwoColumn) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (!isCompanionMode) {
                            TransportsSection(
                                lanEnabled = lanEnabled,
                                torEnabled = torEnabled,
                                mailEnabled = mailEnabled,
                                torStatus = torStatus,
                                mailStatus = mailStatus,
                                transportsReady = transportsReady,
                                mailAccount = mailAccount,
                                onToggleLan = { showLanWarning = true },
                                onDisableLan = { viewModel.setLanEnabled(false) },
                                onToggleTor = { viewModel.setTransportEnabled(FfiTransport.ONION, it) },
                                onToggleMail = { viewModel.setTransportEnabled(FfiTransport.MAIL, it) },
                                onShowMailSetup = { showMailSetup = true },
                                onShowMailDelete = { showMailDeleteConfirm = true }
                            )
                            BluetoothSection(viewModel)
                            YggdrasilSection(viewModel)
                            NostrSection(viewModel)
                            Spacer(modifier = Modifier.height(24.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        PrivacySection(
                            showName = showName,
                            showText = showText,
                            onToggleName = { viewModel.setNotificationsShowName(it) },
                            onToggleText = { viewModel.setNotificationsShowText(it) }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(24.dp))
                        MessagesSection(viewModel)
                        if (isCompanionMode) {
                            Spacer(modifier = Modifier.height(24.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(24.dp))
                            CompanionSection(viewModel)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        StorageSection(
                            viewModel = viewModel,
                            isCompanionMode = isCompanionMode,
                            snackbarHostState = snackbarHostState,
                            scope = scope
                        )
                        if (!isCompanionMode) {
                            Spacer(modifier = Modifier.height(24.dp))
                            chat.ratatosk.desktop.ui.backup.BackupSection(viewModel)
                            Spacer(modifier = Modifier.height(24.dp))
                            PairedDevicesSection(viewModel)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(24.dp))
                        ThemeSection(
                            viewModel = viewModel,
                            chatTheme = chatTheme
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(24.dp))
                        DiagnosticsSection(viewModel)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        AccountSection(
                            viewModel = viewModel,
                            isCompanionMode = isCompanionMode
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    if (!isCompanionMode) {
                        TransportsSection(
                            lanEnabled = lanEnabled,
                            torEnabled = torEnabled,
                            mailEnabled = mailEnabled,
                            torStatus = torStatus,
                            mailStatus = mailStatus,
                            transportsReady = transportsReady,
                            mailAccount = mailAccount,
                            onToggleLan = { showLanWarning = true },
                            onDisableLan = { viewModel.setLanEnabled(false) },
                            onToggleTor = { viewModel.setTransportEnabled(FfiTransport.ONION, it) },
                            onToggleMail = { viewModel.setTransportEnabled(FfiTransport.MAIL, it) },
                            onShowMailSetup = { showMailSetup = true },
                            onShowMailDelete = { showMailDeleteConfirm = true }
                        )
                        BluetoothSection(viewModel)
                        YggdrasilSection(viewModel)
                        NostrSection(viewModel)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    }

                    PrivacySection(
                        showName = showName,
                        showText = showText,
                        onToggleName = { viewModel.setNotificationsShowName(it) },
                        onToggleText = { viewModel.setNotificationsShowText(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    MessagesSection(viewModel)
                    if (isCompanionMode) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        CompanionSection(viewModel)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    StorageSection(
                        viewModel = viewModel,
                        isCompanionMode = isCompanionMode,
                        snackbarHostState = snackbarHostState,
                        scope = scope
                    )
                    if (!isCompanionMode) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        chat.ratatosk.desktop.ui.backup.BackupSection(viewModel)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        PairedDevicesSection(viewModel)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    ThemeSection(
                        viewModel = viewModel,
                        chatTheme = chatTheme
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    DiagnosticsSection(viewModel)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    AccountSection(
                        viewModel = viewModel,
                        isCompanionMode = isCompanionMode
                    )
                }
            }
        }
    }

    if (showLanWarning) {
        AlertDialog(
            onDismissRequest = { showLanWarning = false },
            title = { Text(Strings.LAN_VISIBILITY) },
            // Текст ядра — вызов через границу: берём один раз, а не на кадр.
            text = { Text(remember { lanWarning() }) },
            confirmButton = {
                TextButton(onClick = { 
                    showLanWarning = false
                    viewModel.setLanEnabled(true)
                }) {
                    Text(Strings.ENABLE)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLanWarning = false }) {
                    Text(Strings.CANCEL)
                }
            }
        )
    }

    if (showMailSetup) {
        MailSetupDialog(
            torEnabled = torEnabled,
            account = mailAccount,
            error = error,
            onDismiss = { showMailSetup = false },
            onConfigure = { addr, pass, iHost, iPort, sHost, sPort, vTor ->
                viewModel.setMailAccount(addr, pass, iHost, iPort, sHost, sPort, vTor)
                showMailSetup = false
            },
            onRegister = { url, vTor ->
                viewModel.createMailAccount(url, vTor)
                showMailSetup = false
            }
        )
    }

    if (showMailDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showMailDeleteConfirm = false },
            title = { Text(Strings.MAIL_DELETE) },
            text = { Text(Strings.MAIL_DELETE_CONFIRM) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearMailAccount()
                    showMailDeleteConfirm = false
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(Strings.DELETE)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMailDeleteConfirm = false }) {
                    Text(Strings.CANCEL)
                }
            }
        )
    }
}

@Composable
fun TransportsSection(
    lanEnabled: Boolean,
    torEnabled: Boolean,
    mailEnabled: Boolean,
    torStatus: FfiTorStatus?,
    mailStatus: FfiMailStatus?,
    transportsReady: Map<FfiTransport, Boolean>,
    mailAccount: org.ratatosk.core.FfiMailAccount?,
    onToggleLan: (Boolean) -> Unit,
    onDisableLan: () -> Unit,
    onToggleTor: (Boolean) -> Unit,
    onToggleMail: (Boolean) -> Unit,
    onShowMailSetup: () -> Unit,
    onShowMailDelete: () -> Unit
) {
    Column {
        Text(text = Strings.TRANSPORTS, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        TransportItem(
            title = Strings.LAN_TRANSPORT,
            description = Strings.LAN_DESC,
            enabled = lanEnabled,
            ready = transportsReady[FfiTransport.LAN] ?: false,
            onToggle = { if (it) onToggleLan(true) else onDisableLan() }
        )

        TransportItem(
            title = Strings.TOR_TRANSPORT,
            description = Strings.TOR_DESC,
            enabled = torEnabled,
            ready = transportsReady[FfiTransport.ONION] ?: false,
            onToggle = onToggleTor,
            statusContent = {
                if (torEnabled && transportsReady[FfiTransport.ONION] != true) {
                    torStatus?.let { status ->
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            LinearProgressIndicator(
                                progress = { status.fraction },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                            )
                            Text(
                                text = if (status.blocked != null) Strings.TOR_BLOCKED.format(status.blocked) else Strings.TOR_BOOTSTRAP.format((status.fraction * 100).toInt()),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (status.blocked != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            Text(text = status.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        )

        TransportItem(
            title = Strings.MAIL_TRANSPORT,
            description = Strings.MAIL_DESC,
            enabled = mailEnabled,
            ready = transportsReady[FfiTransport.MAIL] ?: false,
            onToggle = onToggleMail,
            statusContent = {
                mailStatus?.let { status ->
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        val stateStr = when (status.state) {
                            FfiMailState.OFF -> Strings.MAIL_OFFLINE
                            FfiMailState.NO_ACCOUNT -> Strings.MAIL_NOT_CONFIGURED
                            FfiMailState.CONNECTING -> Strings.MAIL_CONNECTING
                            FfiMailState.READY -> Strings.MAIL_ONLINE
                            FfiMailState.FAILED -> Strings.MAIL_FAILED
                        }
                        Text(
                            text = stateStr + (status.address?.let { " ($it)" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (status.state == FfiMailState.FAILED) MaterialTheme.colorScheme.error else if (status.state == FfiMailState.READY) successColor else MaterialTheme.colorScheme.outline
                        )
                        // Почему именно так — словами ядра, а не нашей догадкой.
                        status.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        // Заполненность ящика: письма перестанут приходить молча.
                        val used = status.mailboxUsedBytes
                        val limit = status.mailboxLimitBytes
                        if (used != null && limit != null && limit > 0UL) {
                            Text(
                                Strings.MAIL_MAILBOX_USED.format(formatFileSize(used), formatFileSize(limit)),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (status.mailboxCrowded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                            )
                            LinearProgressIndicator(
                                progress = { (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                            )
                        }
                        if (status.mailboxCrowded) {
                            Text(Strings.MAIL_MAILBOX_CROWDED, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        
                        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (mailAccount == null) {
                                Button(onClick = onShowMailSetup, modifier = Modifier.weight(1f)) {
                                    Text(Strings.MAIL_SETUP)
                                }
                            } else {
                                OutlinedButton(onClick = onShowMailSetup, modifier = Modifier.weight(1f)) {
                                    Text(Strings.MAIL_EDIT)
                                }
                                IconButton(onClick = onShowMailDelete) {
                                    Icon(Icons.Default.Delete, contentDescription = Strings.MAIL_DELETE, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

/**
 * Второй экран: хранить ли копию переписки. Перед включением — слова о том,
 * от чего это шифрование **не** защищает (`DESKTOP.md`, §13.4).
 */
@Composable
fun CompanionSection(viewModel: RatatoskViewModel) {
    val cacheEnabled by viewModel.companionCacheEnabled.collectAsState()
    var confirmOn by remember { mutableStateOf(false) }

    Column {
        Text(text = Strings.COMPANION_SECTION, style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(Strings.COMPANION_CACHE)
                Text(
                    if (cacheEnabled) Strings.COMPANION_CACHE_ON_DESC else Strings.COMPANION_CACHE_OFF_DESC,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = cacheEnabled,
                onCheckedChange = { on -> if (on) confirmOn = true else viewModel.setCompanionCache(false) }
            )
        }
    }

    if (confirmOn) {
        AlertDialog(
            onDismissRequest = { confirmOn = false },
            title = { Text(Strings.COMPANION_CACHE_ON_TITLE) },
            text = { Text(Strings.COMPANION_CACHE_ON_DESC) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setCompanionCache(true)
                    confirmOn = false
                }) { Text(Strings.ENABLE) }
            },
            dismissButton = { TextButton(onClick = { confirmOn = false }) { Text(Strings.CANCEL) } }
        )
    }
}

/** Как отправлять: Enter или Ctrl+Enter. */
@Composable
fun MessagesSection(viewModel: RatatoskViewModel) {
    val ctrlEnter by viewModel.sendWithCtrlEnter.collectAsState()
    Column {
        Text(text = Strings.SETTINGS_MESSAGES, style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().clickable { viewModel.setSendWithCtrlEnter(!ctrlEnter) }.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(Strings.SETTINGS_CTRL_ENTER)
                Text(Strings.SETTINGS_CTRL_ENTER_DESC, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = ctrlEnter, onCheckedChange = { viewModel.setSendWithCtrlEnter(it) })
        }
    }
}

@Composable
fun PrivacySection(
    showName: Boolean,
    showText: Boolean,
    onToggleName: (Boolean) -> Unit,
    onToggleText: (Boolean) -> Unit
) {
    Column {
        Text(text = Strings.NOTIFICATION_PRIVACY, style = MaterialTheme.typography.titleMedium)
        Text(
            text = Strings.PRIVACY_DESC,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggleName(!showName) }.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text(Strings.SHOW_SENDER_NAME)
            Switch(checked = showName, onCheckedChange = onToggleName)
        }

        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggleText(!showText) }.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text(Strings.SHOW_MESSAGE_TEXT)
            Switch(checked = showText, onCheckedChange = onToggleText)
        }
    }
}

@Composable
fun StorageSection(
    viewModel: RatatoskViewModel,
    isCompanionMode: Boolean,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    Column {
        Text(text = Strings.FILE_SETTINGS, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (!isCompanionMode) {
            val autoAcceptLimit by viewModel.autoAcceptLimit.collectAsState()
            val limits = listOf(
                0UL to Strings.LIMIT_NEVER,
                1024UL * 1024UL to Strings.SIZE_MB.format("1"),
                1024UL * 1024UL * 10UL to Strings.SIZE_MB.format("10"),
                1024UL * 1024UL * 100UL to Strings.SIZE_MB.format("100"),
                null to Strings.LIMIT_ALWAYS
            )
            var showLimitMenu by remember { mutableStateOf(false) }

            Box(modifier = Modifier.padding(vertical = 8.dp)) {
                OutlinedButton(onClick = { showLimitMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    // Значение могло прийти не отсюда (телефон, другой клиент):
                    // показываем его как есть, а не молча «никогда».
                    val currentLabel = limits.find { it.first == autoAcceptLimit }?.second
                        ?: autoAcceptLimit?.let { Strings.LIMIT_UP_TO.format(formatFileSize(it)) }
                        ?: Strings.LIMIT_ALWAYS
                    Text("${Strings.AUTO_ACCEPT_LIMIT}: $currentLabel")
                }
                DropdownMenu(expanded = showLimitMenu, onDismissRequest = { showLimitMenu = false }) {
                    limits.forEach { (limit, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.setAutoAcceptLimit(limit)
                                showLimitMenu = false
                            }
                        )
                    }
                }
            }
        }

        val downloadPath by viewModel.downloadDirPath.collectAsState()
        OutlinedButton(
            onClick = { 
                FilePicker.pickDirectory()?.let { 
                    viewModel.setDownloadDirPath(it.absolutePath)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("${Strings.DOWNLOAD_DIR}: ${downloadPath ?: FileUtils.getDownloadsDir().absolutePath}")
        }

        if (!isCompanionMode) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    viewModel.sweepOrphanFiles { swept ->
                        val resultText = Strings.SWEEP_RESULT.format(
                            swept.files.toString(),
                            swept.chunks.toString(),
                            formatFileSize(swept.bytes),
                        )
                        scope.launch { snackbarHostState.showSnackbar(resultText) }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Strings.SWEEP_ORPHANS)
                    Text(Strings.SWEEP_ORPHANS_DESC, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun ThemeSection(
    viewModel: RatatoskViewModel,
    chatTheme: chat.ratatosk.desktop.ui.theme.ChatThemeData
) {
    Column {
        Text(text = Strings.CHAT_THEME, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Text(Strings.THEME_COLOR, style = MaterialTheme.typography.labelMedium)
        val themeColors = listOf(
            Color.Unspecified, Color.Gray, Color(0xFF2196F3), Color(0xFF4CAF50),
            Color(0xFFF44336), Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFF00BCD4)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(themeColors) { color ->
                val isSelected = chatTheme.themeColor == color
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (color == Color.Unspecified) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) else color)
                        .clickable { viewModel.updateChatTheme { it.copy(themeColor = color) } }
                        .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (color == Color.Unspecified) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = Strings.AUTO, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        // Системный диалог блокирует поток — выбираем файл вне отрисовки.
        var pickBackground by remember { mutableStateOf(false) }
        LaunchedEffect(pickBackground) {
            if (!pickBackground) return@LaunchedEffect
            val picked = withContext(Dispatchers.IO) { FilePicker.pickImage() }
            pickBackground = false
            if (picked != null) {
                viewModel.updateChatTheme { it.copy(backgroundImageUri = picked.toURI().toString()) }
            }
        }
        OutlinedButton(onClick = { pickBackground = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (chatTheme.backgroundImageUri == null) Strings.SET_BACKGROUND else Strings.CHANGE_BACKGROUND)
        }

        if (chatTheme.backgroundImageUri != null) {
            Spacer(modifier = Modifier.height(8.dp))
            // Пока тянем — только на экране; в настройки пишем по отпусканию,
            // иначе каждый кадр уходил бы в DataStore.
            var dragged by remember(chatTheme.backgroundOpacity) { mutableStateOf<Float?>(null) }
            val shown = dragged ?: chatTheme.backgroundOpacity
            Text(
                text = "${Strings.BACKGROUND_OPACITY}: ${(shown * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium
            )
            Slider(
                value = shown,
                onValueChange = { dragged = it },
                onValueChangeFinished = {
                    dragged?.let { opacity -> viewModel.updateChatTheme { it.copy(backgroundOpacity = opacity) } }
                },
                valueRange = 0f..1f
            )
            TextButton(onClick = { viewModel.updateChatTheme { it.copy(backgroundImageUri = null) } }) {
                Text(Strings.REMOVE_BACKGROUND)
            }
        }
    }
}

@Composable
fun AccountSection(
    viewModel: RatatoskViewModel,
    isCompanionMode: Boolean
) {
    Column {
        Text(text = Strings.ACCOUNT_SECTION, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.logout() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isCompanionMode) Strings.COMPANION_UNLINK else Strings.EXIT)
        }
    }
}

@Composable
fun TransportItem(
    title: String,
    description: String,
    enabled: Boolean,
    ready: Boolean,
    onToggle: (Boolean) -> Unit,
    statusContent: @Composable () -> Unit = {}
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (ready) successColor else MaterialTheme.colorScheme.outline))
                    }
                    Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            statusContent()
        }
    }
}

@Composable
fun MailSetupDialog(
    torEnabled: Boolean,
    /** Уже заведённый ящик — форма открывается заполненной, а не пустой. */
    account: org.ratatosk.core.FfiMailAccount?,
    /** Ошибка ядра: по ней снимаем ожидание, иначе кнопка остаётся мёртвой. */
    error: String?,
    onDismiss: () -> Unit,
    onConfigure: (address: String, password: String, imapHost: String, iPort: Int, sHost: String, sPort: Int, viaTor: Boolean) -> Unit,
    onRegister: (url: String, viaTor: Boolean) -> Unit
) {
    // Ящик есть — правим его, а не заводим новый.
    var isRegisterMode by remember(account) { mutableStateOf(account == null) }
    var isWorking by remember { mutableStateOf(false) }
    // Ядро ответило ошибкой — разблокировать кнопку: иначе диалог замирает.
    LaunchedEffect(error) { if (error != null) isWorking = false }

    var address by remember(account) { mutableStateOf(account?.address ?: "") }
    var password by remember(account) { mutableStateOf(account?.password ?: "") }
    var imapHost by remember(account) { mutableStateOf(account?.imapHost ?: "") }
    var imapPort by remember(account) { mutableStateOf(account?.imapPort?.toString() ?: "993") }
    var smtpHost by remember(account) { mutableStateOf(account?.smtpHost ?: "") }
    var smtpPort by remember(account) { mutableStateOf(account?.smtpPort?.toString() ?: "465") }

    var registerUrl by remember { mutableStateOf("") }
    var viaTor by remember(account) { mutableStateOf(account?.viaTor ?: torEnabled) }

    val imapOk = imapPort.toIntOrNull()?.let { it in 1..65535 } == true
    val smtpOk = smtpPort.toIntOrNull()?.let { it in 1..65535 } == true
    val manualOk = address.isNotBlank() && password.isNotBlank() &&
        imapHost.isNotBlank() && smtpHost.isNotBlank() && imapOk && smtpOk

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.MAIL_SETUP) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                TabRow(selectedTabIndex = if (isRegisterMode) 0 else 1) {
                    Tab(selected = isRegisterMode, onClick = { isRegisterMode = true }) {
                        Text(Strings.MAIL_REGISTER, modifier = Modifier.padding(8.dp))
                    }
                    Tab(selected = !isRegisterMode, onClick = { isRegisterMode = false }) {
                        Text(Strings.MAIL_SETUP, modifier = Modifier.padding(8.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isRegisterMode) {
                    Text(Strings.MAIL_REGISTER_DESC, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = registerUrl,
                        onValueChange = { registerUrl = it },
                        label = { Text(Strings.MAIL_SERVER_URL) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(Strings.MAIL_ADDRESS) }, modifier = Modifier.fillMaxWidth())
                    SecretTextField(value = password, onValueChange = { password = it }, label = Strings.MAIL_PASSWORD, modifier = Modifier.fillMaxWidth())
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = imapHost, onValueChange = { imapHost = it }, label = { Text(Strings.MAIL_IMAP_HOST) }, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = imapPort,
                            onValueChange = { imapPort = it.filter { c -> c.isDigit() } },
                            label = { Text(Strings.MAIL_IMAP_PORT) },
                            isError = !imapOk,
                            modifier = Modifier.weight(0.5f)
                        )
                    }
                    if (!imapOk || !smtpOk) {
                        Text(Strings.MAIL_PORT_INVALID, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = smtpHost, onValueChange = { smtpHost = it }, label = { Text(Strings.MAIL_SMTP_HOST) }, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = smtpPort,
                            onValueChange = { smtpPort = it.filter { c -> c.isDigit() } },
                            label = { Text(Strings.MAIL_SMTP_PORT) },
                            isError = !smtpOk,
                            modifier = Modifier.weight(0.5f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = viaTor, onCheckedChange = { viaTor = it })
                    Column {
                        Text(Strings.MAIL_VIA_TOR)
                        Text(Strings.MAIL_VIA_TOR_DESC, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                // Пустыми полями заведённый ящик не затираем.
                enabled = !isWorking && if (isRegisterMode) registerUrl.isNotBlank() else manualOk,
                onClick = {
                    isWorking = true
                    if (isRegisterMode) {
                        onRegister(registerUrl, viaTor)
                    } else {
                        onConfigure(address, password, imapHost, imapPort.toInt(), smtpHost, smtpPort.toInt(), viaTor)
                    }
                }
            ) {
                if (isWorking) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(Strings.SAVE)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.CANCEL)
            }
        }
    )
}
