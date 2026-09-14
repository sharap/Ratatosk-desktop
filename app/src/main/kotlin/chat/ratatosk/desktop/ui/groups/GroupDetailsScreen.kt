package chat.ratatosk.desktop.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.backend.GroupMember
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.ui.components.Avatar
import chat.ratatosk.desktop.ui.components.CreateGroupDialog
import chat.ratatosk.desktop.util.toHexString
import java.text.DateFormat
import java.util.Date

/**
 * Сведения о группе: состав, приглашение, исключение, переименование, выход.
 *
 * Кнопки распоряжения — только при [RatatoskViewModel.canManageGroup]:
 * нарисованные всем, они получали бы отказ. Тексты §11.4–11.5 показываются
 * в подтверждении **до** действия (DESKTOP.md, «Группы»).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailsScreen(
    viewModel: RatatoskViewModel,
    chatId: ByteArray,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    showBackButton: Boolean = true,
) {
    val groups by viewModel.groups.collectAsState()
    val allMembers by viewModel.groupMembers.collectAsState()
    val groupAvatars by viewModel.groupAvatars.collectAsState()
    val contacts by viewModel.contacts.collectAsState()

    val hex = remember(chatId) { chatId.toHexString() }
    val group = groups.find { it.chatId.contentEquals(chatId) }
    val members = allMembers[hex]
    // Права компаньона приходят вместе с составом — пересчитать, когда он пришёл.
    val canManage = remember(group, members) { viewModel.canManageGroup(chatId) }
    val notices = viewModel.groupNotices

    var showRename by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    var showLeave by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var evicting by remember { mutableStateOf<GroupMember?>(null) }

    LaunchedEffect(hex) { viewModel.loadMembers(chatId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.GROUP_INFO) },
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
        if (group == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(Strings.GROUP_NOT_FOUND)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val avatar = groupAvatars[hex] ?: viewModel.getGroupAvatar(chatId)
            if (avatar != null) {
                Avatar(avatarBytes = avatar, name = group.title, size = 96.dp)
            } else {
                Surface(
                    modifier = Modifier.size(96.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Groups, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(group.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (canManage) {
                    IconButton(onClick = { showRename = true }) {
                        Icon(Icons.Default.Edit, contentDescription = Strings.GROUP_RENAME)
                    }
                }
            }
            group.createdMs?.let {
                Text(
                    Strings.GROUP_CREATED_AT.format(DateFormat.getDateInstance().format(Date(it.toLong()))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (!group.joined) {
                Spacer(Modifier.height(8.dp))
                Text(Strings.GROUP_YOU_LEFT, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenChat) {
                    Icon(Icons.AutoMirrored.Filled.Chat, null)
                    Spacer(Modifier.width(8.dp))
                    Text(Strings.GROUP_OPEN_CHAT)
                }
                if (group.joined) {
                    OutlinedButton(onClick = { showInvite = true }) {
                        Icon(Icons.Default.PersonAdd, null)
                        Spacer(Modifier.width(8.dp))
                        Text(Strings.GROUP_INVITE)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                if (members != null) Strings.GROUP_MEMBERS_COUNT.format(members.size) else Strings.GROUP_MEMBERS,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )
            if (members == null) {
                Text(Strings.GROUP_MEMBERS_LOADING, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
            } else {
                members.forEach { member ->
                    ListItem(
                        headlineContent = { Text(if (member.isMe) Strings.GROUP_YOU else member.name) },
                        supportingContent = if (member.isOwner == true) {
                            { Text(Strings.GROUP_OWNER) }
                        } else null,
                        leadingContent = { Avatar(avatarBytes = null, name = member.name) },
                        trailingContent = if (canManage && !member.isMe) {
                            {
                                IconButton(onClick = { evicting = member }) {
                                    Icon(Icons.Default.PersonRemove, contentDescription = Strings.GROUP_EVICT, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        } else null
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            if (group.joined) {
                OutlinedButton(
                    onClick = { showLeave = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null)
                    Spacer(Modifier.width(8.dp))
                    Text(Strings.GROUP_LEAVE)
                }
            } else {
                // Удалить группу ядро не умеет; вышедшему остаётся очистить историю.
                OutlinedButton(
                    onClick = { showClear = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(8.dp))
                    Text(Strings.CLEAR_CHAT)
                }
            }
        }
    }

    if (showRename && group != null) {
        CreateGroupDialog(
            joinNotice = "",
            maxTitleChars = notices.maxTitleChars,
            initialTitle = group.title,
            confirmLabel = Strings.GROUP_RENAME,
            onDismiss = { showRename = false },
            onConfirm = {
                viewModel.renameGroup(chatId, it)
                showRename = false
            }
        )
    }

    if (showInvite) {
        // Позвать можно только контакт, и только того, кого в группе ещё нет.
        val memberIds = members?.map { it.chatId.toHexString() }?.toSet() ?: emptySet()
        val candidates = contacts.filter { it.chatId.toHexString() !in memberIds }
        AlertDialog(
            onDismissRequest = { showInvite = false },
            title = { Text(Strings.GROUP_INVITE_TITLE) },
            text = {
                if (candidates.isEmpty()) {
                    Text(Strings.GROUP_INVITE_NOBODY)
                } else {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(candidates, key = { it.chatId.toHexString() }) { contact ->
                            val name = contact.localName ?: contact.displayName
                            ListItem(
                                headlineContent = { Text(name) },
                                leadingContent = { Avatar(avatarBytes = null, name = name) },
                                modifier = Modifier.clickable {
                                    viewModel.inviteToGroup(chatId, contact.chatId)
                                    showInvite = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showInvite = false }) { Text(Strings.CANCEL) } }
        )
    }

    evicting?.let { member ->
        ConfirmDialog(
            title = Strings.GROUP_EVICT_TITLE.format(member.name),
            texts = listOf(notices.eviction),
            confirmLabel = Strings.GROUP_EVICT,
            onDismiss = { evicting = null },
            onConfirm = {
                viewModel.evictFromGroup(chatId, member.chatId)
                evicting = null
            }
        )
    }

    if (showLeave) {
        val iAmOwner = members?.any { it.isMe && it.isOwner == true } == true || canManage
        ConfirmDialog(
            title = Strings.GROUP_LEAVE,
            texts = listOfNotNull(notices.leave, notices.ownerLeave.takeIf { iAmOwner }),
            confirmLabel = Strings.GROUP_LEAVE,
            onDismiss = { showLeave = false },
            onConfirm = {
                viewModel.leaveGroup(chatId)
                showLeave = false
            }
        )
    }

    if (showClear) {
        ConfirmDialog(
            title = Strings.CLEAR_CHAT,
            texts = listOf(Strings.CLEAR_CHAT_DESC),
            confirmLabel = Strings.CLEAR_CHAT,
            onDismiss = { showClear = false },
            onConfirm = {
                viewModel.clearChat(chatId)
                showClear = false
            }
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    texts: List<String>,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                texts.filter { it.isNotBlank() }.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text(Strings.CANCEL) }
        }
    )
}
