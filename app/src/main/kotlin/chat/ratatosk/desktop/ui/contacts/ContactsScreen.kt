package chat.ratatosk.desktop.ui.contacts

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
import androidx.compose.material.icons.filled.Refresh
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
import chat.ratatosk.desktop.model.filterContacts
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.ui.chatlist.SearchBarRow
import chat.ratatosk.desktop.ui.components.AddContactDialog
import chat.ratatosk.desktop.ui.components.Avatar
import chat.ratatosk.desktop.util.toHexString
import org.ratatosk.core.FfiContact

/** Список контактов: карточка по щелчку, чат — из меню по правому щелчку. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    viewModel: RatatoskViewModel,
    onContactClick: (ByteArray) -> Unit,
    onChatClick: (ByteArray) -> Unit,
    selectedChatId: ByteArray? = null,
    listState: LazyListState = rememberLazyListState(),
    showFab: Boolean = true,
) {
    val contacts by viewModel.contacts.collectAsState()
    val contactAvatars by viewModel.contactAvatars.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val shown = remember(contacts, query) { filterContacts(contacts, query) }

    LaunchedEffect(Unit) { viewModel.refreshContacts() }

    Scaffold(
        topBar = {
            if (searchOpen) {
                SearchBarRow(
                    query = query,
                    hint = Strings.SEARCH_CONTACTS_HINT,
                    onQueryChange = { query = it },
                    onClose = { searchOpen = false; query = "" },
                )
            } else {
                TopAppBar(
                    title = { Text(Strings.CONTACTS) },
                    actions = {
                        IconButton(onClick = { searchOpen = true }) { Icon(Icons.Default.Search, Strings.SEARCH) }
                        // Пересмотреть сеть: адреса собеседников могли поменяться.
                        IconButton(onClick = { viewModel.refreshContacts(); viewModel.networkChanged() }) {
                            Icon(Icons.Default.Refresh, contentDescription = Strings.RETRY)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                )
            }
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = Strings.ADD_CONTACT)
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (query.isBlank()) Strings.NO_CONTACTS else Strings.NOTHING_FOUND,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(shown, key = { it.chatId.toHexString() }) { contact ->
                        ContactRow(
                            contact = contact,
                            selected = selectedChatId?.contentEquals(contact.chatId) == true,
                            avatar = contactAvatars[contact.peerIk.toHexString()] ?: viewModel.getAvatarOf(contact.peerIk),
                            onClick = { onContactClick(contact.chatId) },
                            onOpenChat = { onChatClick(contact.chatId) },
                        )
                    }
                }
            }
        }
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ContactRow(
    contact: FfiContact,
    selected: Boolean,
    avatar: ByteArray?,
    onClick: () -> Unit,
    onOpenChat: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val name = contact.localName ?: contact.displayName

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
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (contact.seenOnLan) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                    }
                }
            },
            supportingContent = {
                Column {
                    // Своё имя для контакта не скрывает, как он назвался сам.
                    if (contact.localName != null) {
                        Text(contact.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(contact.fingerprint, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            leadingContent = { Avatar(avatarBytes = avatar, name = name) },
            trailingContent = {
                if (contact.verified) {
                    Text(Strings.IDENTITY_VERIFIED, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(Strings.GROUP_OPEN_CHAT) },
                onClick = { menuOpen = false; onOpenChat() },
            )
        }
    }
}
