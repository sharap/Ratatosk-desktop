package chat.ratatosk.desktop.ui.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.core.RatatoskCore
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.ui.components.AddContactDialog
import chat.ratatosk.desktop.ui.components.Avatar
import chat.ratatosk.desktop.util.toHexString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    viewModel: RatatoskViewModel,
    onContactClick: (ByteArray) -> Unit,
    isTwoColumn: Boolean = false,
    gridState: LazyGridState = rememberLazyGridState(),
    showFab: Boolean = true
) {
    val contacts by viewModel.contacts.collectAsState()
    val contactAvatars by viewModel.contactAvatars.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshContacts()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.CONTACTS) },
                actions = {
                    IconButton(onClick = { 
                        viewModel.refreshContacts()
                        try {
                            RatatoskCore.getClient().networkChanged()
                        } catch (e: Exception) {}
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = Strings.RETRY)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = Strings.ADD_CONTACT)
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (contacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(
                        text = Strings.NO_CONTACTS,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isTwoColumn) 2 else 1),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(contacts.size) { index ->
                        val contact = contacts[index]
                        ListItem(
                            headlineContent = { 
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    val name = contact.localName ?: contact.displayName
                                    Text(name, modifier = Modifier.weight(1f))
                                    if (contact.seenOnLan) {
                                        Surface(
                                            modifier = Modifier.size(8.dp),
                                            shape = androidx.compose.foundation.shape.CircleShape,
                                            color = androidx.compose.ui.graphics.Color.Green
                                        ) {}
                                    }
                                }
                            },
                            supportingContent = { 
                                Column {
                                    if (contact.localName != null) {
                                        Text(contact.displayName, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(contact.fingerprint)
                                }
                            },
                            leadingContent = {
                                val ikHex = contact.peerIk.toHexString()
                                val avatarBytes = contactAvatars[ikHex] ?: viewModel.getAvatarOf(contact.peerIk)
                                Avatar(
                                    avatarBytes = avatarBytes,
                                    name = contact.localName ?: contact.displayName
                                )
                            },
                            trailingContent = {
                                if (contact.verified) {
                                    Text(Strings.IDENTITY_VERIFIED, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.clickable { onContactClick(contact.chatId) }
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
            }
        )
    }
}
