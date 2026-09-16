package chat.ratatosk.desktop.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.ui.components.Avatar
import chat.ratatosk.desktop.ui.components.AvatarCropDialog
import chat.ratatosk.desktop.util.ClipboardUtils
import chat.ratatosk.desktop.util.FilePicker
import chat.ratatosk.desktop.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import qrcode.QRCode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: RatatoskViewModel,
    isTwoColumn: Boolean = false
) {
    val fingerprint by viewModel.fingerprint.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val notices by viewModel.honestNotices.collectAsState()
    val myAvatar by viewModel.myAvatar.collectAsState()
    val torEnabled by viewModel.torEnabled.collectAsState()
    val onionAddress by viewModel.onionAddress.collectAsState()
    val cardVersion by viewModel.cardVersion.collectAsState()
    val myContactUri by viewModel.myContactUri.collectAsState()
    val isCompanionMode by viewModel.isCompanionMode.collectAsState()
    val scope = rememberCoroutineScope()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val copyLink: () -> Unit = {
        viewModel.copyMyContactUri { copied ->
            scope.launch { snackbarHostState.showSnackbar(if (copied) Strings.LINK_COPIED else Strings.LINK_COPY_FAILED) }
        }
    }

    var showMyQr by remember { mutableStateOf(false) }
    // Выбор файла — в своей корутине: системный диалог блокирует поток.
    var pickAvatar by remember { mutableStateOf(false) }
    var cropFile by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(pickAvatar) {
        if (!pickAvatar) return@LaunchedEffect
        val picked = withContext(Dispatchers.IO) { FilePicker.pickImage() }
        pickAvatar = false
        if (picked != null) cropFile = picked
    }
    var showEditName by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(Strings.PROFILE) },
                actions = {
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        }
    ) { innerPadding ->
        if (isTwoColumn) {
            Row(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Column 1: Header & Logout
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ProfileHeader(
                        myAvatar = myAvatar,
                        userName = userName,
                        onEditAvatar = { pickAvatar = true },
                        onEditName = if (isCompanionMode) null else ({
                            newName = userName ?: ""
                            showEditName = true
                        }),
                        isCompact = false
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    OutlinedButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сменить аккаунт")
                    }
                }

                // Column 2: Details & Notices
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (isCompanionMode) {
                        // Ключи, отпечаток и ссылка живут на телефоне: на втором
                        // экране их нет, и показывать их было бы обманом.
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = Strings.PROFILE_COMPANION_DESC,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        ProfileDetails(
                            fingerprint = fingerprint,
                            cardVersion = cardVersion,
                            torEnabled = torEnabled,
                            onionAddress = onionAddress,
                            onShowQr = { showMyQr = true },
                            onCopyLink = copyLink
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        ProfileNotices(notices = notices)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProfileHeader(
                    myAvatar = myAvatar,
                    userName = userName,
                    onEditAvatar = { pickAvatar = true },
                    onEditName = if (isCompanionMode) null else ({
                        newName = userName ?: ""
                        showEditName = true
                    }),
                    isCompact = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isCompanionMode) {
                    Text(
                        text = Strings.PROFILE_COMPANION_DESC,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ProfileDetails(
                        fingerprint = fingerprint,
                        cardVersion = cardVersion,
                        torEnabled = torEnabled,
                        onionAddress = onionAddress,
                        onShowQr = { showMyQr = true },
                        onCopyLink = copyLink
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedButton(
                    onClick = { viewModel.logout() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Сменить аккаунт")
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (!isCompanionMode) ProfileNotices(notices = notices)
            }
        }
    }

    cropFile?.let { file ->
        AvatarCropDialog(
            file = file,
            maxBytes = viewModel.maxAvatarBytes.value,
            onDismiss = { cropFile = null },
            onResult = { viewModel.setAvatar(it) },
            onError = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
        )
    }

    if (showEditName) {
        AlertDialog(
            onDismissRequest = { showEditName = false },
            title = { Text(Strings.EDIT) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(Strings.DISPLAY_NAME) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = Strings.NAME_RESTART_NOTE,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.setDisplayName(newName)
                        showEditName = false
                    }
                }) {
                    Text(Strings.SAVE)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditName = false }) {
                    Text(Strings.CANCEL)
                }
            }
        )
    }

    // Запрос в ядро — один раз при открытии диалога, а не на каждой перерисовке.
    LaunchedEffect(showMyQr) {
        if (showMyQr) viewModel.getMyContactUri()
    }

    if (showMyQr) {
        AlertDialog(
            onDismissRequest = { showMyQr = false },
            title = { Text(Strings.MY_QR_CODE) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val uri = myContactUri
                    if (uri != null) {
                        val qrBitmap = remember(uri) {
                            (QRCode(uri).render().nativeImage() as java.awt.image.BufferedImage).toComposeImageBitmap()
                        }
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = Strings.MY_QR_CODE,
                            modifier = Modifier.size(200.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uri,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    myContactUri?.let { ClipboardUtils.copyToClipboard(it) }
                }) {
                    Text(Strings.COPY)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMyQr = false }) {
                    Text(Strings.CLOSE)
                }
            }
        )
    }
}

@Composable
fun ProfileHeader(
    myAvatar: ByteArray?,
    userName: String?,
    onEditAvatar: () -> Unit,
    /** `null` — имя менять нельзя (у компаньона его задаёт телефон). */
    onEditName: (() -> Unit)?,
    isCompact: Boolean = false
) {
    Box(contentAlignment = Alignment.BottomEnd) {
        Avatar(
            avatarBytes = myAvatar,
            name = userName ?: "U",
            modifier = if (isCompact) Modifier.fillMaxWidth().widthIn(max = 320.dp).aspectRatio(1f) else Modifier.size(200.dp),
            size = null,
            shape = RectangleShape
        )
        SmallFloatingActionButton(
            onClick = onEditAvatar,
            modifier = Modifier.size(32.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = Strings.EDIT, modifier = Modifier.size(16.dp))
        }
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = userName ?: "User",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        if (onEditName != null) {
            IconButton(onClick = onEditName) {
                Icon(Icons.Default.Edit, contentDescription = Strings.EDIT, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun ProfileDetails(
    fingerprint: String?,
    cardVersion: kotlin.ULong?,
    torEnabled: Boolean,
    onionAddress: String?,
    onShowQr: () -> Unit,
    onCopyLink: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = Strings.FINGERPRINT,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = fingerprint ?: "Загрузка...",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                if (cardVersion != null) {
                    Text(
                        text = "v$cardVersion",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
                IconButton(onClick = {
                    fingerprint?.let { ClipboardUtils.copyToClipboard(it) }
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                }
            }
        }
    }

    if (torEnabled) {
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = Strings.ONION_ADDRESS,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = onionAddress ?: "Ожидание Tor...",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (onionAddress != null) {
                        IconButton(onClick = { ClipboardUtils.copyToClipboard(onionAddress) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onShowQr,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.QrCode, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(Strings.MY_QR_CODE)
        }

        OutlinedButton(
            onClick = onCopyLink,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(Strings.COPY_LINK)
        }
    }
}

@Composable
fun ProfileNotices(notices: List<String>) {
    Text(
        text = Strings.SECURITY_NOTICES,
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.height(8.dp))
    notices.forEach { notice ->
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
