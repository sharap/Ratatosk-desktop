package chat.ratatosk.desktop.ui.chatlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.model.ChatItem
import chat.ratatosk.desktop.model.buildChatList
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.ui.components.AddContactDialog
import chat.ratatosk.desktop.ui.components.Avatar
import chat.ratatosk.desktop.ui.components.CreateGroupDialog
import chat.ratatosk.desktop.util.DateUtils
import chat.ratatosk.desktop.util.MessagePreview
import chat.ratatosk.desktop.util.toHexString

/**
 * Список чатов: группы и те контакты, с кем есть переписка, в порядке
 * последнего сообщения. Отбор и порядок считает [buildChatList].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ChatListScreen(
    viewModel: RatatoskViewModel,
    onChatClick: (ByteArray) -> Unit,
    onOpenCard: (ChatItem) -> Unit,
    selectedChatId: ByteArray? = null,
    listState: LazyListState = rememberLazyListState(),
    showFab: Boolean = true,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }

    val isCompanionMode by viewModel.isCompanionMode.collectAsState()
    val isCompanionLinked by viewModel.isCompanionLinked.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val groupAvatars by viewModel.groupAvatars.collectAsState()
    val contactAvatars by viewModel.contactAvatars.collectAsState()
    val unreadCounts by viewModel.unreadCounts.collectAsState()
    val allMessages by viewModel.messages.collectAsState()
    val activeChatId by viewModel.activeChatIdFlow.collectAsState()

    val chats = remember(contacts, groups, allMessages, activeChatId, query) {
        buildChatList(contacts, groups, allMessages, activeChatId, query)
    }

    // Только что заведённую группу — сразу открыть.
    LaunchedEffect(Unit) { viewModel.createdGroups.collect { onChatClick(it) } }
    LaunchedEffect(Unit) { viewModel.refreshContacts() }

    Scaffold(
        topBar = {
            Column {
                if (searchOpen) {
                    SearchBarRow(
                        query = query,
                        hint = Strings.SEARCH_CHATS_HINT,
                        onQueryChange = { query = it },
                        onClose = { searchOpen = false; query = "" },
                    )
                } else {
                    TopAppBar(
                        title = { Text(Strings.CHATS) },
                        actions = {
                            IconButton(onClick = { searchOpen = true }) { Icon(Icons.Default.Search, Strings.SEARCH) }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    )
                }
                TorStrip(viewModel)
                if (isCompanionMode && !isCompanionLinked) CompanionStrip()
            }
        },
        floatingActionButton = {
            if (showFab) {
                Box {
                    FloatingActionButton(onClick = {
                        // У компаньона контакты заводит телефон — остаётся только группа.
                        if (isCompanionMode) showCreateGroup = true else showFabMenu = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = Strings.ADD)
                    }
                    DropdownMenu(expanded = showFabMenu, onDismissRequest = { showFabMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(Strings.ADD_CONTACT) },
                            leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                            onClick = { showFabMenu = false; showAddDialog = true },
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.CREATE_GROUP) },
                            leadingIcon = { Icon(Icons.Default.Groups, null) },
                            onClick = { showFabMenu = false; showCreateGroup = true },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            if (chats.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (query.isBlank()) Strings.NO_CHATS else Strings.NOTHING_FOUND,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(chats, key = { it.key }) { item ->
                        val hexId = item.key
                        ChatRow(
                            item = item,
                            unread = unreadCounts[hexId] ?: 0,
                            selected = selectedChatId?.contentEquals(item.chatId) == true,
                            avatar = when (item) {
                                is ChatItem.GroupChat -> groupAvatars[hexId] ?: viewModel.getGroupAvatar(item.chatId)
                                is ChatItem.Direct -> contactAvatars[item.contact.peerIk.toHexString()] ?: viewModel.getAvatarOf(item.contact.peerIk)
                            },
                            onClick = { onChatClick(item.chatId) },
                            onOpenCard = { onOpenCard(item) },
                        )
                    }
                }
            }
        }
    }

    if (showCreateGroup) {
        val notices = viewModel.groupNotices
        CreateGroupDialog(
            joinNotice = notices.join,
            maxTitleChars = notices.maxTitleChars,
            onDismiss = { showCreateGroup = false },
            onConfirm = { title ->
                viewModel.createGroup(title)
                showCreateGroup = false
            },
        )
    }

    if (showAddDialog) {
        AddContactDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { uri, inPerson ->
                viewModel.addContact(uri, inPerson)
                showAddDialog = false
            },
        )
    }
}

/**
 * Строка списка. Правый щелчок открывает карточку — то же, что щелчок
 * по шапке чата, но не открывая сам чат.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun ChatRow(
    item: ChatItem,
    unread: Int,
    selected: Boolean,
    avatar: ByteArray?,
    onClick: () -> Unit,
    onOpenCard: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val last = item.lastMessage
    val isGroup = item is ChatItem.GroupChat

    Box(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .onPointerEvent(PointerEventType.Press) { if (it.buttons.isSecondaryPressed) menuOpen = true }
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (item is ChatItem.GroupChat && !item.group.joined) {
                        Spacer(Modifier.width(6.dp))
                        Text(Strings.GROUP_LEFT_BADGE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    if (item is ChatItem.Direct && item.contact.seenOnLan) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                    }
                    Spacer(Modifier.weight(1f))
                    last?.let {
                        Text(DateUtils.formatChatTime(it.wallMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            },
            supportingContent = {
                // Тем же помощником, что и уведомления: разметка снята,
                // у вложения — подпись вместо пустой строки.
                val preview = remember(last?.body, last?.files?.size, last?.sharedContact) {
                    last?.let { MessagePreview.of(it) }
                }
                Text(
                    text = when {
                        preview == null -> Strings.GROUP_NO_MESSAGES
                        last?.mine == true -> Strings.YOU_PREFIX.format(preview)
                        else -> preview
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingContent = {
                if (isGroup && avatar == null) {
                    Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                } else {
                    Avatar(avatarBytes = avatar, name = item.title)
                }
            },
            trailingContent = { if (unread > 0) Badge { Text(unread.toString()) } },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (isGroup) Strings.GROUP_CARD else Strings.OPEN_CARD) },
                onClick = { menuOpen = false; onOpenCard() },
            )
        }
    }
}

/** Поле поиска на месте заголовка — по названиям чатов, не по тексту. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchBarRow(query: String, hint: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(hint) },
                singleLine = true,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, Strings.CANCEL, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
    }
}

/** Полоса поднятия Tor: без `!!` в лямбде — состояние меняется между кадрами. */
@Composable
private fun TorStrip(viewModel: RatatoskViewModel) {
    val torEnabled by viewModel.torEnabled.collectAsState()
    val torStatus by viewModel.torStatus.collectAsState()
    val status = torStatus
    if (!torEnabled || status == null || status.fraction >= 1f) return
    LinearProgressIndicator(
        progress = { status.fraction },
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.primaryContainer,
    )
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = status.note + (status.blocked?.let { ": $it" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** У компаньона до связи с телефоном список неполон — сказать об этом. */
@Composable
private fun CompanionStrip() {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(Strings.CHAT_COMPANION_LINKING, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}
