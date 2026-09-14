package chat.ratatosk.desktop.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.withStyle
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.ui.components.Avatar
import chat.ratatosk.desktop.util.ClipboardUtils
import chat.ratatosk.desktop.util.MarkdownUtils
import chat.ratatosk.desktop.util.FilePicker
import coil3.compose.rememberAsyncImagePainter
import org.ratatosk.core.FfiDeliveryStatus
import org.ratatosk.core.FfiMessage
import org.ratatosk.core.FfiFile
import org.ratatosk.core.FfiSharedContact

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: RatatoskViewModel,
    chatId: ByteArray,
    onBack: () -> Unit,
    onHeaderClick: () -> Unit,
    showBackButton: Boolean = true,
    isCompact: Boolean = false
) {
    var text by remember { mutableStateOf("") }
    var editingMessage by remember { mutableStateOf<FfiMessage?>(null) }
    var replyingTo by remember { mutableStateOf<FfiMessage?>(null) }
    var showForwardDialog by remember { mutableStateOf<List<ByteArray>?>(null) }
    var selectedFiles by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    
    val chatTheme by viewModel.chatTheme.collectAsState()
    val allMessages by viewModel.messages.collectAsState()
    val messageStatuses by viewModel.messageStatuses.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val contactAvatars by viewModel.contactAvatars.collectAsState()
    
    val chatIdHex = remember(chatId) { chatId.toHexString() }
    val messages = allMessages[chatIdHex] ?: emptyList()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val contact = remember(contacts, chatIdHex) {
        contacts.find { it.chatId.toHexString() == chatIdHex }
    }
    val groups by viewModel.groups.collectAsState()
    val groupAvatars by viewModel.groupAvatars.collectAsState()
    val group = remember(groups, chatIdHex) {
        groups.find { it.chatId.toHexString() == chatIdHex }
    }

    val displayMessages = remember(messages) { messages.reversed() }

    var showChatMenu by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(chatIdHex) {
        viewModel.setActiveChat(chatId)
        viewModel.loadMessages(chatId)
    }

    LaunchedEffect(searchQuery) {
        viewModel.searchMessages(chatId, searchQuery)
    }

    Scaffold(
        topBar = { 
            Column {
                TopAppBar(
                    title = { 
                        Row(
                            modifier = Modifier.clickable { onHeaderClick() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            group?.let {
                                Avatar(
                                    avatarBytes = groupAvatars[chatIdHex] ?: viewModel.getGroupAvatar(it.chatId),
                                    name = it.title,
                                    size = 32.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            contact?.let {
                                Avatar(
                                    avatarBytes = it.peerIk.toHexString().let { ik -> contactAvatars[ik] } ?: viewModel.getAvatarOf(it.peerIk),
                                    name = it.localName ?: it.displayName,
                                    size = 32.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Column {
                                Text(group?.title ?: contact?.let { it.localName ?: it.displayName } ?: Strings.CHATS)
                                if (group != null) {
                                    Text(
                                        text = if (group.joined) Strings.GROUP_INFO else Strings.GROUP_LEFT_BADGE,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                if (contact?.seenOnLan == true) {
                                    Text(
                                        text = Strings.ONLINE_LAN,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        if (showBackButton) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.CANCEL)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearching = !isSearching }) {
                            Icon(Icons.Default.Search, contentDescription = Strings.SEARCH)
                        }
                        IconButton(onClick = { showChatMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showChatMenu,
                            onDismissRequest = { showChatMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(Strings.CLEAR_CHAT) },
                                onClick = {
                                    showChatMenu = false
                                    showClearChatDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                            )
                        }
                    }
                )
                if (isSearching) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text(Strings.SEARCH_HINT) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            IconButton(onClick = { 
                                isSearching = false
                                searchQuery = ""
                                viewModel.clearSearch()
                            }) {
                                Icon(Icons.Default.Close, null)
                            }
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        },
        bottomBar = {
            // Вышедший из группы читает, но не пишет: ядро отказало бы.
            if (group != null && !group.joined) {
                Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        Strings.GROUP_YOU_LEFT,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
            Surface(tonalElevation = 2.dp) {
                Column {
                    // Reply Preview
                    replyingTo?.let { msg ->
                        Row(
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.width(4.dp).height(32.dp).background(MaterialTheme.colorScheme.primary))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(if (msg.mine) "Вы" else (msg.author ?: contact?.localName ?: contact?.displayName ?: "???"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(msg.body, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { replyingTo = null }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                    }
                    
                    // Files Preview
                    if (selectedFiles.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedFiles.forEach { file ->
                                Card(modifier = Modifier.size(64.dp)) {
                                    Box(contentAlignment = Alignment.TopEnd) {
                                        Column(modifier = Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                            Icon(Icons.Default.InsertDriveFile, null, modifier = Modifier.size(24.dp))
                                            Text(file.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        }
                                        IconButton(onClick = { selectedFiles = selectedFiles - file }, modifier = Modifier.size(16.dp)) {
                                            Icon(Icons.Default.Cancel, null, tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { 
                            selectedFiles = selectedFiles + FilePicker.pickFiles()
                        }) {
                            Icon(Icons.Default.Add, contentDescription = Strings.ATTACH_FILES)
                        }
                        
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(if (editingMessage != null) Strings.EDIT else Strings.MESSAGE) },
                            maxLines = 4,
                            leadingIcon = if (editingMessage != null) {
                                {
                                    IconButton(onClick = { 
                                        editingMessage = null
                                        text = ""
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = Strings.CANCEL)
                                    }
                                }
                            } else null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (text.isNotBlank() || selectedFiles.isNotEmpty()) {
                                    val currentEditing = editingMessage
                                    val currentReply = replyingTo
                                    if (currentEditing != null) {
                                        viewModel.editMessage(chatId, currentEditing.msgId, text)
                                        editingMessage = null
                                    } else if (selectedFiles.isNotEmpty()) {
                                        viewModel.sendFiles(chatId, selectedFiles, text)
                                        selectedFiles = emptyList()
                                    } else if (currentReply != null) {
                                        viewModel.reply(chatId, currentReply.msgId, text)
                                        replyingTo = null
                                    } else {
                                        viewModel.sendText(chatId, text)
                                    }
                                    text = ""
                                }
                            },
                            enabled = text.isNotBlank() || selectedFiles.isNotEmpty()
                        ) {
                            if (editingMessage != null) {
                                Icon(Icons.Default.Check, contentDescription = Strings.SAVE)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = Strings.SEND)
                            }
                        }
                    }
                }
            }
            }
        },
        snackbarHost = { SnackbarHost(remember { SnackbarHostState() }) }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            chatTheme.backgroundImageUri?.let { uriString ->
                Image(
                    painter = rememberAsyncImagePainter(model = uriString),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = chatTheme.backgroundOpacity
                )
            }
            
            val searchResults by viewModel.searchResults.collectAsState()
            val listMessages = if (isSearching && searchQuery.isNotBlank()) searchResults.reversed() else displayMessages

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(listMessages, key = { it.msgId.toHexString() }) { msg ->
                    val status = messageStatuses[msg.msgId.toHexString()] ?: msg.status
                    Box(modifier = Modifier.fillMaxWidth().animateItem()) {
                        MessageBubble(
                            message = msg,
                            viewModel = viewModel,
                            chatId = chatId,
                            outgoingColor = if (chatTheme.themeColor != Color.Unspecified) chatTheme.themeColor else MaterialTheme.colorScheme.primary,
                            status = status,
                            onRetry = { viewModel.resendMessage(chatId, msg.body) },
                            onDelete = { viewModel.deleteMessages(chatId, listOf(msg.msgId)) },
                            onRetract = { viewModel.retractMessages(chatId, listOf(msg.msgId)) },
                            onEdit = { 
                                editingMessage = msg
                                text = msg.body
                            },
                            onReply = { replyingTo = msg },
                            onForward = { showForwardDialog = listOf(msg.msgId) },
                            onReaction = { emoji -> viewModel.setReaction(chatId, msg.msgId, emoji) },
                            onReplyClick = { replyId ->
                                scope.launch {
                                    val index = listMessages.indexOfFirst { it.msgId.contentEquals(replyId) }
                                    if (index != -1) {
                                        listState.animateScrollToItem(index)
                                    }
                                }
                            },
                            retractionNotice = { viewModel.getRetractionNotice() }
                        )
                    }
                }
                
                if (!isSearching) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = Strings.ENCRYPTED_CONNECTION,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = { Text(Strings.CLEAR_CHAT) },
            text = { Text(Strings.CLEAR_CHAT_DESC) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearChat(chatId)
                        showClearChatDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(Strings.DELETE)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) {
                    Text(Strings.CANCEL)
                }
            }
        )
    }

    if (showForwardDialog != null) {
        val allContacts by viewModel.contacts.collectAsState()
        AlertDialog(
            onDismissRequest = { showForwardDialog = null },
            title = { Text(Strings.FORWARD_TO) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(allContacts) { targetContact ->
                        ListItem(
                            headlineContent = { Text(targetContact.localName ?: targetContact.displayName) },
                            leadingContent = {
                                val contactAvatars by viewModel.contactAvatars.collectAsState()
                                Avatar(
                                    avatarBytes = contactAvatars[targetContact.peerIk.toHexString()] ?: viewModel.getAvatarOf(targetContact.peerIk),
                                    name = targetContact.localName ?: targetContact.displayName
                                )
                            },
                            modifier = Modifier.clickable {
                                viewModel.forwardMessages(targetContact.chatId, showForwardDialog!!)
                                showForwardDialog = null
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showForwardDialog = null }) {
                    Text(Strings.CANCEL)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: FfiMessage,
    viewModel: RatatoskViewModel,
    chatId: ByteArray,
    outgoingColor: Color,
    status: FfiDeliveryStatus?,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onRetract: () -> Unit,
    onEdit: () -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onReaction: (String?) -> Unit,
    onReplyClick: (ByteArray) -> Unit,
    retractionNotice: () -> String
) {
    val alignment = if (message.mine) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (message.mine) outgoingColor else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (message.mine) {
        if (bubbleColor.luminance() > 0.5f) Color.Black else Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    val linkColor = if (message.mine) contentColor else MaterialTheme.colorScheme.primary
    val fullAnnotatedBody = remember(message.body, linkColor) {
        MarkdownUtils.parseMarkdown(message.body, linkColor)
    }

    var isExpanded by remember { mutableStateOf(false) }
    var revealedSpoilers by remember { mutableStateOf(setOf<Int>()) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    
    val threshold = 300
    val isLong = message.body.length > threshold

    val annotatedBody = remember(fullAnnotatedBody, isExpanded, isLong, linkColor, revealedSpoilers) {
        val base = if (isLong && !isExpanded) {
            val safeThreshold = if (fullAnnotatedBody.length > threshold) threshold else fullAnnotatedBody.length
            buildAnnotatedString {
                append(fullAnnotatedBody.subSequence(0, safeThreshold))
                append("... ")
                pushStringAnnotation(tag = "EXPAND", annotation = "expand")
                withStyle(style = SpanStyle(color = linkColor, fontWeight = FontWeight.Bold)) {
                    append("еще")
                }
                pop()
            }
        } else {
            fullAnnotatedBody
        }
        
        val spoilers = base.getStringAnnotations("SPOILER", 0, base.length)
        if (spoilers.isEmpty()) base else buildAnnotatedString {
            append(base)
            spoilers.forEach { annotation ->
                val isRevealed = revealedSpoilers.contains(annotation.start)
                addStyle(
                    style = SpanStyle(
                        background = if (isRevealed) Color.Gray.copy(alpha = 0.2f) else Color.DarkGray,
                        color = if (isRevealed) contentColor else Color.Transparent
                    ),
                    start = annotation.start,
                    end = annotation.end
                )
            }
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (message.mine && status == FfiDeliveryStatus.UNDELIVERABLE) {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = Strings.RETRY, tint = MaterialTheme.colorScheme.error)
                    }
                }

                Card(
                    shape = RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (message.mine) 16.dp else 0.dp,
                        bottomEnd = if (message.mine) 0.dp else 16.dp
                    ),
                    colors = CardDefaults.cardColors(containerColor = bubbleColor, contentColor = contentColor),
                    modifier = Modifier.widthIn(max = 450.dp).bringIntoViewRequester(bringIntoViewRequester)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp).pointerInput(annotatedBody) {
                            detectTapGestures(
                                onTap = { offset ->
                                    textLayoutResult?.let { layout ->
                                        val characterIndex = layout.getOffsetForPosition(offset)
                                        annotatedBody.getStringAnnotations("URL", characterIndex, characterIndex)
                                            .firstOrNull()?.let { annotation ->
                                                uriHandler.openUri(annotation.item)
                                                return@detectTapGestures
                                            }
                                        annotatedBody.getStringAnnotations("EXPAND", characterIndex, characterIndex)
                                            .firstOrNull()?.let {
                                                isExpanded = true
                                                return@detectTapGestures
                                            }
                                        annotatedBody.getStringAnnotations("SPOILER", characterIndex, characterIndex)
                                            .firstOrNull()?.let { annotation ->
                                                if (!revealedSpoilers.contains(annotation.start)) {
                                                    revealedSpoilers = revealedSpoilers + annotation.start
                                                    return@detectTapGestures
                                                }
                                            }
                                    }
                                    if (isLong) isExpanded = !isExpanded
                                },
                                onLongPress = { showMenu = true }
                            )
                        }
                    ) {
                        // Reply to UI
                        message.replyTo?.let { replyId ->
                            val repliedMsg = viewModel.getMessage(replyId)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(contentColor.copy(alpha = 0.1f))
                                    .clickable { onReplyClick(replyId) }
                                    .padding(8.dp)
                            ) {
                                Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(linkColor))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = repliedMsg?.body ?: Strings.MESSAGE_UNAVAILABLE,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    color = contentColor.copy(alpha = 0.8f)
                                )
                            }
                        }

                        if (message.forwarded) {
                            Text(
                                text = Strings.FORWARDED,
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        
                        // Files UI
                        if (message.files.isNotEmpty()) {
                            FileAttachment(message.files, viewModel, chatId, contentColor, linkColor)
                        }

                        // Shared Contact UI
                        message.sharedContact?.let { shared ->
                            SharedContactCard(shared, { viewModel.addSharedContact(message.msgId) }, contentColor, linkColor)
                        }

                        if (message.body.isNotBlank()) {
                            Text(
                                text = annotatedBody,
                                style = MaterialTheme.typography.bodyMedium.copy(color = contentColor),
                                modifier = Modifier.padding(bottom = 4.dp),
                                onTextLayout = { textLayoutResult = it }
                            )
                        }

                        Row(
                            modifier = Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (message.editedAtMs != null) {
                                Text("изм.", style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.6f))
                            }
                            Text(
                                text = java.text.SimpleDateFormat("HH:mm").format(java.util.Date(message.wallMs.toLong())),
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.8f)
                            )
                            if (message.mine) MessageStatusIcon(status, contentColor)
                        }
                    }
                }
            }
            
            // Reactions
            if (message.reactions.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val groups = message.reactions.groupBy { it.emoji }
                    groups.forEach { (emoji, list) ->
                        val hasMine = list.any { it.mine }
                        Surface(
                            color = if (hasMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable { onReaction(if (hasMine) null else emoji) }
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text(emoji, style = MaterialTheme.typography.labelSmall)
                                if (list.size > 1) {
                                    Spacer(Modifier.width(2.dp))
                                    Text(list.size.toString(), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            Row(modifier = Modifier.padding(8.dp)) {
                val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")
                emojis.forEach { emoji ->
                    val isSelected = message.reactions.any { it.mine && it.emoji == emoji }
                    IconButton(onClick = { 
                        onReaction(if (isSelected) null else emoji)
                        showMenu = false 
                    }) {
                        Text(emoji, style = MaterialTheme.typography.bodyLarge, color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified)
                    }
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(Strings.COPY) }, 
                onClick = { 
                    ClipboardUtils.copyToClipboard(message.body)
                    showMenu = false 
                },
                leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
            )
            DropdownMenuItem(
                text = { Text(Strings.REPLY) },
                onClick = { showMenu = false; onReply() },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, null) }
            )
            if (message.mine) DropdownMenuItem(
                text = { Text(Strings.EDIT) }, 
                onClick = { showMenu = false; onEdit() },
                leadingIcon = { Icon(Icons.Default.Edit, null) }
            )
            DropdownMenuItem(
                text = { Text(Strings.FORWARD) }, 
                onClick = { showMenu = false; onForward() },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
            )
            if (message.mine) DropdownMenuItem(
                text = { Text(Strings.RETRACT) }, 
                onClick = { showMenu = false; onRetract() },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, null) }
            )
            DropdownMenuItem(
                text = { Text(Strings.DELETE) }, 
                onClick = { showMenu = false; onDelete() },
                leadingIcon = { Icon(Icons.Default.Delete, null) }
            )
        }
    }
}

@Composable
fun SharedContactCard(
    sharedContact: FfiSharedContact,
    onAdd: () -> Unit,
    contentColor: Color,
    linkColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(contentColor.copy(alpha = 0.1f))
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                tint = linkColor,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = sharedContact.displayName, style = MaterialTheme.typography.titleSmall, color = contentColor)
                Text(text = sharedContact.fingerprint, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.6f))
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (sharedContact.mine) {
            Text(text = "Это вы", style = MaterialTheme.typography.labelMedium, color = contentColor.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.End))
        } else if (sharedContact.alreadyKnown) {
            Text(text = "Уже в контактах", style = MaterialTheme.typography.labelMedium, color = contentColor.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.End))
        } else {
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = linkColor)
            ) {
                Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(Strings.ADD_CONTACT)
            }
        }
    }
}

@Composable
fun FileAttachment(
    files: List<FfiFile>,
    viewModel: RatatoskViewModel,
    chatId: ByteArray,
    contentColor: Color,
    linkColor: Color
) {
    files.forEach { file ->
        val progress by viewModel.fileProgress.collectAsState()
        val activeJobs by viewModel.activeJobsFlow.collectAsState()
        val fileIdHex = file.fileId.toHexString()
        val isExportingActive = activeJobs.contains(fileIdHex)
        val currentProgress = progress[fileIdHex] ?: (if (file.complete) 1f else if (file.receivedChunks > 0UL) file.receivedChunks.toFloat() / file.chunkTotal.toFloat() else 0f)
        val scope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(contentColor.copy(alpha = 0.1f))
                .clickable(enabled = file.complete || !file.incoming) {
                    if (file.complete || !file.incoming) {
                        viewModel.openFile(file)
                    }
                }
                .padding(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.InsertDriveFile, null, tint = linkColor, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = contentColor)
                    Text(text = formatFileSize(file.sizeBytes), style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.6f))
                }
                
                if (file.incoming && !file.accepted && !file.complete) {
                    Row {
                        IconButton(onClick = { viewModel.declineFile(chatId, file.fileId) }) {
                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = { viewModel.acceptFile(chatId, file.fileId) }) {
                            Icon(Icons.Default.Download, null, tint = linkColor)
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isExportingActive) {
                            CircularProgressIndicator(progress = { currentProgress }, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            IconButton(onClick = { viewModel.cancelFileJob(file.fileId) }) {
                                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            }
                        } else {
                            if (!file.complete && file.incoming) {
                                CircularProgressIndicator(progress = { currentProgress }, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                            if (file.complete || !file.incoming) {
                                IconButton(onClick = {
                                    viewModel.downloadFile(file) { path ->
                                        // TODO: show notification or snackbar
                                    }
                                }) {
                                    Icon(Icons.Default.Download, null, tint = linkColor)
                                }
                            }
                        }
                    }
                }
            }
            
            if (file.hasPreview) {
                 val previewBytes = viewModel.getFilePreview(file.fileId)
                 if (previewBytes != null) {
                     Image(
                         painter = rememberAsyncImagePainter(previewBytes),
                         contentDescription = null,
                         modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(4.dp)).padding(top = 8.dp),
                         contentScale = ContentScale.Fit
                     )
                 }
            }
        }
    }
}

@Composable
fun MessageStatusIcon(status: FfiDeliveryStatus?, color: Color) {
    val icon: ImageVector? = when (status) {
        FfiDeliveryStatus.PENDING -> Icons.Default.Refresh
        FfiDeliveryStatus.WAITING -> Icons.Default.Refresh
        FfiDeliveryStatus.SENT -> Icons.Default.Done
        FfiDeliveryStatus.DELIVERED, FfiDeliveryStatus.READ -> Icons.Default.Done
        FfiDeliveryStatus.UNDELIVERABLE -> Icons.Default.Error
        null -> null
    }
    
    val tint = if (status == FfiDeliveryStatus.READ) Color(0xFF40C4FF) else color

    icon?.let {
        Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(16.dp), tint = tint)
    }
}

fun formatFileSize(bytes: ULong): String {
    val b = bytes.toDouble()
    return when {
        b < 1024 -> "%.0f B".format(b)
        b < 1024 * 1024 -> "%.1f KB".format(b / 1024)
        b < 1024 * 1024 * 1024 -> "%.1f MB".format(b / (1024 * 1024))
        else -> "%.1f GB".format(b / (1024 * 1024 * 1024))
    }
}

private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
private fun String.hexToByteArray() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
