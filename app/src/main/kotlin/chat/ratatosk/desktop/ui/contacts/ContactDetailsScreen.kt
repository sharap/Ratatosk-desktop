package chat.ratatosk.desktop.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.ui.components.Avatar
import chat.ratatosk.desktop.util.ClipboardUtils
import chat.ratatosk.desktop.util.toHexString
import org.ratatosk.core.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailsScreen(
    viewModel: RatatoskViewModel,
    chatId: ByteArray,
    onBack: () -> Unit,
    onChatClick: (ByteArray) -> Unit,
    showBackButton: Boolean = true,
    isCompact: Boolean = false
) {
    val contacts by viewModel.contacts.collectAsState()
    val contactAvatars by viewModel.contactAvatars.collectAsState()
    val contact = remember(contacts, chatId) {
        contacts.find { it.chatId.contentEquals(chatId) }
    }
    
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showRevokeDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showShareToChatDialog by remember { mutableStateOf(false) }
    
    var editNameText by remember { mutableStateOf("") }
    var purgeHistoryOnDelete by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.CONTACT_DETAILS) },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.CANCEL)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (contact == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Contact not found")
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
                val contentWidth = if (isCompact) 1f else 0.8f
                
                // Avatar
                Avatar(
                    avatarBytes = contact.peerIk.toHexString().let { contactAvatars[it] } ?: viewModel.getAvatarOf(contact.peerIk),
                    name = contact.localName ?: contact.displayName,
                    size = 100.dp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.localName ?: contact.displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { 
                        editNameText = contact.localName ?: ""
                        showEditNameDialog = true 
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = Strings.EDIT, modifier = Modifier.size(20.dp))
                    }
                }
                
                if (contact.localName != null) {
                    Text(
                        text = "(${contact.displayName})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (contact.seenOnLan) {
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = CircleShape,
                            color = Color.Green
                        ) {}
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = Strings.ONLINE_LAN,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = Strings.OFFLINE,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Text(
                    text = Strings.CONTACT_ADDED.format(java.util.Date(contact.addedMs.toLong()).toString()), // Simplified date
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Verification Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(contentWidth),
                    colors = CardDefaults.cardColors(
                        containerColor = if (contact.verified) 
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else 
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (contact.verified) Icons.Default.VerifiedUser else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (contact.verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (contact.verified) Strings.IDENTITY_VERIFIED else Strings.UNVERIFIED,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        if (!contact.verified) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = Strings.UNVERIFIED_WARNING,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.markVerified(contact.peerIk) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(Strings.VERIFY_IDENTITY)
                            }
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = { showRevokeDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.GppBad, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(Strings.REVOKE_TRUST)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions
                OutlinedButton(
                    onClick = { onChatClick(chatId) },
                    modifier = Modifier.fillMaxWidth(contentWidth)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Strings.MESSAGE)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Fingerprint and Share
                Card(modifier = Modifier.fillMaxWidth(contentWidth)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = Strings.FINGERPRINT,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = contact.fingerprint,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                ClipboardUtils.copyToClipboard(contact.fingerprint)
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = Strings.COPY)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(Strings.SHARE_CONTACT)
                            IconButton(onClick = { showShareToChatDialog = true }) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = Strings.SHARE_TO)
                            }
                        }

                        if (contact.onion != null || contact.chatmail != null) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }

                        contact.onion?.let { onion ->
                            Text(
                                text = Strings.ONION_ADDRESS,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(onion, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = { ClipboardUtils.copyToClipboard(onion) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        contact.chatmail?.let { chatmail ->
                            Text(
                                text = Strings.MAIL_ADDRESS,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(chatmail, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = { ClipboardUtils.copyToClipboard(chatmail) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Reachability
                Card(modifier = Modifier.fillMaxWidth(contentWidth)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = Strings.REACHABILITY,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        contact.reachability.rungs.forEach { rung ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(rung.transport.name, style = MaterialTheme.typography.bodyMedium)
                                Row {
                                    StatusChip(Strings.STATUS_ENABLED, rung.enabled)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    StatusChip(Strings.STATUS_READY, rung.ready)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    StatusChip(Strings.STATUS_ADDRESSABLE, rung.addressable)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Text(
                            text = Strings.ROUTE.format(contact.reachability.route?.name ?: "None"),
                            style = MaterialTheme.typography.bodySmall
                        )
                        contact.reachability.rising?.let { rising ->
                            Text(
                                text = Strings.RISING.format(rising.name),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        contact.directChannel?.let { direct ->
                            Text(
                                text = Strings.DIRECT_CHANNEL + ": ${direct.name}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Green
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Technical Info
                Card(modifier = Modifier.fillMaxWidth(contentWidth)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = Strings.TECHNICAL_DETAILS,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = Strings.CARD_VERSION.format(contact.cardVersion.toLong()),
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        if (contact.anomalies.unknownSession > 0uL || contact.anomalies.badTag > 0uL || contact.anomalies.malformed > 0uL) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = Strings.ANOMALIES,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            if (contact.anomalies.unknownSession > 0uL) {
                                Text(Strings.UNKNOWN_SESSIONS.format(contact.anomalies.unknownSession.toLong()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            if (contact.anomalies.badTag > 0uL) {
                                Text(Strings.BAD_TAGS.format(contact.anomalies.badTag.toLong()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            if (contact.anomalies.malformed > 0uL) {
                                Text(Strings.MALFORMED_FRAMES.format(contact.anomalies.malformed.toLong()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                TextButton(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Strings.DELETE_CONTACT)
                }
            }
        }
    }

    if (showEditNameDialog && contact != null) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text(Strings.EDIT_CONTACT_NAME) },
            text = {
                OutlinedTextField(
                    value = editNameText,
                    onValueChange = { editNameText = it },
                    label = { Text(Strings.NICKNAME) },
                    singleLine = true,
                    placeholder = { Text(contact.displayName) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setLocalName(contact.peerIk, editNameText.ifBlank { null })
                    showEditNameDialog = false
                }) {
                    Text(Strings.SAVE)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text(Strings.CANCEL)
                }
            }
        )
    }

    if (showRevokeDialog && contact != null) {
        AlertDialog(
            onDismissRequest = { showRevokeDialog = false },
            title = { Text(Strings.REVOKE_TRUST) },
            text = { Text(revocationNotice()) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.revokeVerification(contact.peerIk)
                    showRevokeDialog = false
                }) {
                    Text(Strings.REVOKE_TRUST)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeDialog = false }) {
                    Text(Strings.CANCEL)
                }
            }
        )
    }

    if (showDeleteDialog && contact != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(Strings.DELETE_CONTACT) },
            text = {
                Column {
                    Text(deletionNotice())
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = purgeHistoryOnDelete,
                            onCheckedChange = { purgeHistoryOnDelete = it }
                        )
                        Text(Strings.PURGE_HISTORY)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteContact(contact.peerIk, purgeHistoryOnDelete)
                    showDeleteDialog = false
                    onBack()
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(Strings.DELETE)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(Strings.CANCEL)
                }
            }
        )
    }

    if (showShareToChatDialog && contact != null) {
        val allContacts by viewModel.contacts.collectAsState()
        AlertDialog(
            onDismissRequest = { showShareToChatDialog = false },
            title = { Text(Strings.SHARE_TO) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(allContacts.filter { !it.chatId.contentEquals(contact.chatId) }) { target ->
                        ListItem(
                            headlineContent = { Text(target.localName ?: target.displayName) },
                            leadingContent = {
                                Avatar(
                                    avatarBytes = contactAvatars[target.peerIk.toHexString()] ?: viewModel.getAvatarOf(target.peerIk),
                                    name = target.localName ?: target.displayName
                                )
                            },
                            modifier = Modifier.clickable {
                                viewModel.shareContact(target.chatId, contact.peerIk)
                                showShareToChatDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showShareToChatDialog = false }) {
                    Text(Strings.CANCEL)
                }
            }
        )
    }
}

@Composable
fun StatusChip(text: String, active: Boolean) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline
        )
    }
}
