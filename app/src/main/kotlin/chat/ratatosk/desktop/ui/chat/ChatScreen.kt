package chat.ratatosk.desktop.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.model.ChatMessage
import chat.ratatosk.desktop.model.buildChatMessages
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.ui.chatlist.StaleStrip
import chat.ratatosk.desktop.ui.components.Avatar
import chat.ratatosk.desktop.ui.components.PickChatDialog
import chat.ratatosk.desktop.util.ClipboardUtils
import chat.ratatosk.desktop.util.FilePicker
import chat.ratatosk.desktop.util.FileUtils
import chat.ratatosk.desktop.util.MessagePreview
import chat.ratatosk.desktop.util.toHexString
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.ratatosk.core.FfiDeliveryStatus
import org.ratatosk.core.FfiMessage
import java.awt.datatransfer.DataFlavor
import java.io.File

/**
 * Экран переписки. Состояние, которое должно пережить прокрутку (раскрытые
 * длинные сообщения и спойлеры), держит экран, а не пузырь: пузырь в ленте
 * пропадает, как только уходит с экрана (ревью Android 5.19).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ChatScreen(
    viewModel: RatatoskViewModel,
    chatId: ByteArray,
    onBack: () -> Unit,
    onHeaderClick: () -> Unit,
    showBackButton: Boolean = true,
    isCompact: Boolean = false,
) {
    val chatHex = remember(chatId) { chatId.toHexString() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // Голосовое: запись живёт на экране, а не в композере, — иначе она
    // обрывалась бы при каждой его перерисовке.
    val voiceRecording = rememberVoiceRecording(
        chatId = chatId,
        onSend = { file, waveform -> viewModel.sendVoice(chatId, file, waveform) },
        onError = { message -> scope.launch { snackbar.showSnackbar(message) } },
    )
    val listState = rememberLazyListState()
    val composerFocus = remember { FocusRequester() }

    // --- Данные -----------------------------------------------------------
    val allMessages by viewModel.messages.collectAsState()
    val statuses by viewModel.messageStatuses.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val allMembers by viewModel.groupMembers.collectAsState()
    val contactAvatars by viewModel.contactAvatars.collectAsState()
    val groupAvatars by viewModel.groupAvatars.collectAsState()
    val repliedMessages by viewModel.repliedMessages.collectAsState()
    val chatTheme by viewModel.chatTheme.collectAsState()
    val sendWithCtrlEnter by viewModel.sendWithCtrlEnter.collectAsState()
    val isCompanion by viewModel.isCompanionMode.collectAsState()
    val isFresh by viewModel.isCompanionFresh.collectAsState()
    val companionLinked by viewModel.isCompanionLinked.collectAsState()
    val notices = viewModel.chatNotices

    val contact = contacts.firstOrNull { it.chatId.contentEquals(chatId) }
    val group = groups.firstOrNull { it.chatId.contentEquals(chatId) }
    val isGroup = group != null
    val members = allMembers[chatHex]

    val raw = allMessages[chatHex] ?: emptyList()
    val chatMessages = remember(raw, statuses, contacts, members, isGroup) {
        buildChatMessages(raw, statuses, isGroup, contacts, members)
    }
    // Лента снизу вверх: нулевой элемент — самое новое сообщение.
    val display = remember(chatMessages) { chatMessages.asReversed() }
    // Корутинам — всегда свежий список: захваченный при запуске устаревает.
    val currentDisplay by rememberUpdatedState(display)

    // --- Состояние экрана -------------------------------------------------
    var text by remember { mutableStateOf(TextFieldValue("")) }
    var replyTo by remember { mutableStateOf<FfiMessage?>(null) }
    var editing by remember { mutableStateOf<FfiMessage?>(null) }
    var attachments by remember { mutableStateOf<List<File>>(emptyList()) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var highlightKey by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var revealed by remember { mutableStateOf(mapOf<String, Set<Int>>()) }
    var dragging by remember { mutableStateOf(false) }
    var forwardIds by remember { mutableStateOf<List<ByteArray>?>(null) }
    var confirmDelete by remember { mutableStateOf<FfiMessage?>(null) }
    var confirmRetract by remember { mutableStateOf<FfiMessage?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(chatHex) {
        viewModel.loadMessages(chatId)
        if (isGroup) viewModel.loadMembers(chatId)
        runCatching { composerFocus.requestFocus() }
    }
    // Отказ ядра или неудачу с файлом человек должен видеть там, где он работает.
    LaunchedEffect(Unit) {
        viewModel.error.collect { message ->
            if (message != null) {
                snackbar.showSnackbar(message)
                viewModel.clearError()
            }
        }
    }
    LaunchedEffect(query, searchOpen) {
        if (searchOpen) viewModel.searchMessages(chatId, query) else viewModel.clearSearch()
    }

    // «Прочитано» — когда последнее входящее видно, окно в фокусе и чат открыт.
    LaunchedEffect(chatHex, display) {
        val newestIncoming = display.firstOrNull { !it.raw.mine } ?: return@LaunchedEffect
        combine(
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == newestIncoming.key } },
            viewModel.isWindowFocused,
        ) { visible, focused -> visible && focused }
            .distinctUntilChanged()
            .collectLatest { if (it) viewModel.markReadUpTo(chatId, newestIncoming.msgId) }
    }

    // Догрузка истории — когда долистали до самого старого.
    LaunchedEffect(chatHex) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }.distinctUntilChanged().collectLatest { atTop ->
            if (atTop && currentDisplay.isNotEmpty() && !viewModel.isHistoryComplete(chatId)) viewModel.loadOlder(chatId)
        }
    }

    // Своё новое сообщение — показать, даже если листали историю.
    val newestKey = display.firstOrNull()?.key
    LaunchedEffect(newestKey) {
        if (currentDisplay.firstOrNull()?.raw?.mine == true) listState.animateScrollToItem(0)
    }

    fun jumpTo(msgId: ByteArray) {
        scope.launch {
            searchOpen = false
            query = ""
            if (viewModel.ensureMessageLoaded(chatId, msgId)) {
                delay(50)
                val index = currentDisplay.indexOfFirst { it.msgId.contentEquals(msgId) }
                if (index >= 0) {
                    listState.animateScrollToItem(index)
                    highlightKey = msgId.toHexString()
                    delay(2000)
                    highlightKey = null
                }
            } else {
                snackbar.showSnackbar(Strings.CHAT_NOT_FOUND)
            }
        }
    }

    fun authorName(msg: FfiMessage): String = when {
        msg.mine -> Strings.CHAT_YOU
        else -> chatMessages.firstOrNull { it.msgId.contentEquals(msg.msgId) }?.author?.name
            ?: msg.author
            ?: contact?.let { it.localName ?: it.displayName }
            ?: Strings.CHAT_UNKNOWN_AUTHOR
    }

    fun send() {
        val body = text.text
        val edit = editing
        val reply = replyTo
        when {
            // Вложения первыми: правка с вложениями их молча теряла бы.
            attachments.isNotEmpty() -> {
                viewModel.sendFiles(chatId, attachments, body)
                attachments = emptyList()
            }
            edit != null -> if (body.isNotBlank()) viewModel.editMessage(chatId, edit.msgId, body)
            reply != null -> viewModel.reply(chatId, reply.msgId, body)
            body.isNotBlank() -> viewModel.sendText(chatId, body)
        }
        text = TextFieldValue("")
        editing = null
        replyTo = null
    }

    fun canEdit(msg: FfiMessage) = msg.mine && msg.body.isNotBlank() && msg.files.isEmpty() &&
        System.currentTimeMillis() - msg.wallMs.toLong() < notices.maxEditAgeMs

    fun startEdit(msg: FfiMessage) {
        editing = msg
        replyTo = null
        text = TextFieldValue(msg.body, androidx.compose.ui.text.TextRange(msg.body.length))
        runCatching { composerFocus.requestFocus() }
    }

    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { dragging = true }
            override fun onExited(event: DragAndDropEvent) { dragging = false }
            override fun onEnded(event: DragAndDropEvent) { dragging = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                dragging = false
                val transferable = event.awtTransferable
                if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false
                val files = (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                    ?.filterIsInstance<File>()?.filter { it.isFile }.orEmpty()
                if (files.isEmpty()) return false
                attachments = (attachments + files).distinct()
                return true
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    e.key == Key.F && e.isCtrlPressed && !isCompanion -> { searchOpen = true; true }
                    // Esc снимает сначала поиск; «назад» — только если снимать нечего.
                    e.key == Key.Escape && searchOpen -> { searchOpen = false; query = ""; true }
                    else -> false
                }
            }
            .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        if (showBackButton) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Strings.CANCEL) }
                    },
                    title = {
                        if (searchOpen) {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text(Strings.SEARCH_HINT) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                            )
                        } else {
                            ChatHeader(
                                title = group?.title ?: contact?.let { it.localName ?: it.displayName } ?: Strings.CHATS,
                                subtitle = when {
                                    group != null && !group.joined -> Strings.GROUP_LEFT_BADGE
                                    group != null -> members?.let { Strings.GROUP_MEMBERS_COUNT.format(it.size) } ?: Strings.GROUP_INFO
                                    contact?.seenOnLan == true -> Strings.ONLINE_LAN
                                    else -> null
                                },
                                avatar = if (group != null) groupAvatars[chatHex] ?: viewModel.getGroupAvatar(chatId)
                                    else contact?.let { contactAvatars[it.peerIk.toHexString()] ?: viewModel.getAvatarOf(it.peerIk) },
                                onClick = onHeaderClick,
                            )
                        }
                    },
                    actions = {
                        if (searchOpen) {
                            IconButton(onClick = { searchOpen = false; query = "" }) { Icon(Icons.Default.Close, Strings.CANCEL) }
                        } else {
                            if (!isCompanion) IconButton(onClick = { searchOpen = true }) { Icon(Icons.Default.Search, Strings.SEARCH) }
                            Box {
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, Strings.CHAT_MORE) }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text(Strings.CLEAR_CHAT) },
                                        leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                                        onClick = { menuOpen = false; confirmClear = true },
                                    )
                                }
                            }
                        }
                    },
                )
                // Чем объяснить тишину — один признак, и слова к нему
                // ядра (§15): выбор главного сделан там же, где факты.
                group?.channel?.let { ch ->
                    if (ch.signal != org.ratatosk.core.FfiChannelSignal.FINE) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = viewModel.channelSignalText(ch.signal),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                // Вступление в канал историю не тянет (§7.4): лента
                // начинается с первого живого слова, а более раннее — по
                // просьбе. Иначе подписавшийся оплачивал бы год чужой
                // переписки, которого не просил.
                group?.channel?.let {
                    val pulling by viewModel.historyPulling.collectAsState()
                    val ended by viewModel.historyEnded.collectAsState()
                    val hex = chatId.toHexString()
                    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            when {
                                pulling[hex] == true -> {
                                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(Strings.CHANNEL_HISTORY_PULLING, style = MaterialTheme.typography.labelSmall)
                                }
                                ended[hex] == true -> {
                                    Text(
                                        Strings.CHANNEL_HISTORY_END,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { viewModel.pullOlderHistory(chatId) }) {
                                        Text(Strings.RETRY)
                                    }
                                }
                                else -> {
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = { viewModel.pullOlderHistory(chatId) }) {
                                        Text(Strings.CHANNEL_HISTORY_PULL)
                                    }
                                }
                            }
                        }
                    }
                }
                TransportStrip(viewModel, isCompanion, companionLinked)
                if (isCompanion && companionLinked && !isFresh) StaleStrip()
            }
        },
        // Запись голосового держит экран: жест и полоса — в композере,
        // микрофон и отправка — здесь.
        bottomBar = {
            // В канале поле ввода гасится правом, а не составом (§6.2):
            // состоять и мочь говорить — разные вещи, и каждой причине
            // свои слова.
            val channelBlock = when (chat.ratatosk.desktop.model.channelInput(group)) {
                chat.ratatosk.desktop.model.ChannelInput.ALLOWED -> null
                chat.ratatosk.desktop.model.ChannelInput.NO_RIGHT -> Strings.CHANNEL_NO_WRITE_RIGHT
                // Те же две причины, что у признака канала, — и слова
                // оттуда же: полоса и поле не должны говорить разного.
                chat.ratatosk.desktop.model.ChannelInput.AWAITING ->
                    viewModel.channelSignalText(org.ratatosk.core.FfiChannelSignal.AWAITING)
                chat.ratatosk.desktop.model.ChannelInput.NOT_READABLE ->
                    viewModel.channelSignalText(org.ratatosk.core.FfiChannelSignal.NOT_READABLE)
            }
            if (channelBlock != null) {
                Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Text(channelBlock, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.outline)
                }
            } else if (group != null && !group.joined) {
                Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (group.channel != null) Strings.CHANNEL_LEFT else Strings.GROUP_YOU_LEFT,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
                Composer(
                    voice = if (isCompanion) null else voiceRecording,
                    value = text,
                    onValueChange = { text = it },
                    banner = editing?.let { ComposerBanner.Edit(notices.edit) }
                        ?: replyTo?.let { ComposerBanner.Reply(authorName(it), MessagePreview.of(it)) },
                    onCancelBanner = {
                        if (editing != null) text = TextFieldValue("")
                        editing = null
                        replyTo = null
                    },
                    attachments = attachments,
                    onRemoveAttachment = { f -> attachments = attachments - f },
                    onAttach = { attachments = (attachments + FilePicker.pickFiles()).distinct() },
                    onAttachFiles = { files -> attachments = (attachments + files).distinct() },
                    sendWithCtrlEnter = sendWithCtrlEnter,
                    onSend = { send() },
                    onEditLast = {
                        val last = chatMessages.lastOrNull { canEdit(it.raw) }?.raw
                        if (last != null) { startEdit(last); true } else false
                    },
                    focusRequester = composerFocus,
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            chatTheme.backgroundImageUri?.let { uri ->
                Image(rememberAsyncImagePainter(uri), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = chatTheme.backgroundOpacity)
            }

            if (searchOpen && query.isNotBlank()) {
                SearchResults(viewModel, onPick = { jumpTo(it.msgId) })
            } else {
                val outgoing = if (chatTheme.themeColor != Color.Unspecified) chatTheme.themeColor else MaterialTheme.colorScheme.primary
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    items(display, key = { it.key }) { message ->
                        MessageItem(
                            message = message,
                            isGroup = isGroup,
                            outgoing = outgoing,
                            highlighted = message.key == highlightKey,
                            expanded = message.key in expanded,
                            onToggleExpanded = { expanded = if (message.key in expanded) expanded - message.key else expanded + message.key },
                            revealed = revealed[message.key].orEmpty(),
                            onReveal = { i -> revealed = revealed + (message.key to (revealed[message.key].orEmpty() + i)) },
                            viewModel = viewModel,
                            chatId = chatId,
                            contactAvatars = contactAvatars,
                            repliedMessages = repliedMessages,
                            authorName = ::authorName,
                            waitingNotice = notices.waiting,
                            onJump = ::jumpTo,
                            onSaved = { path ->
                                scope.launch {
                                    val result = snackbar.showSnackbar(Strings.CHAT_SAVED_TO.format(File(path).name), actionLabel = Strings.CHAT_SHOW_IN_FOLDER, duration = SnackbarDuration.Short)
                                    if (result == SnackbarResult.ActionPerformed) FileUtils.openDirectory(File(path))
                                }
                            },
                            actions = MessageActions(
                                onReply = { replyTo = message.raw; editing = null; runCatching { composerFocus.requestFocus() } },
                                onReact = { emoji -> viewModel.setReaction(chatId, message.msgId, emoji) },
                                onCopy = { ClipboardUtils.copyToClipboard(message.raw.body) },
                                onForward = { forwardIds = listOf(message.msgId) },
                                onDeleteForMe = { confirmDelete = message.raw },
                                onRetractForAll = if (message.raw.mine) ({ confirmRetract = message.raw }) else null,
                                onEdit = if (canEdit(message.raw)) ({ startEdit(message.raw) }) else null,
                                onRetry = if (message.raw.mine && message.status == FfiDeliveryStatus.UNDELIVERABLE && message.raw.files.isEmpty())
                                    ({ viewModel.resendMessage(chatId, message.raw) }) else null,
                            ),
                            onAuthorClick = message.author?.chatId?.let { authorChat -> { viewModel.openContact(authorChat, fromChat = true) } },
                            sharedActions = SharedCardActions(
                                // Добавляется не «человек», а карточка из этого
                                // сообщения: ключ берёт ядро, а не экран.
                                onAdd = { viewModel.addSharedContact(message.msgId) },
                                onOpenChat = { target -> viewModel.openChat(target) },
                            ),
                        )
                    }
                    item(key = "history-start") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (viewModel.isHistoryComplete(chatId) || display.isEmpty()) Strings.CHAT_HISTORY_START else Strings.CHAT_LOADING_OLDER,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }

                // Кнопка «вниз», когда листаем историю.
                if (listState.firstVisibleItemIndex > 2) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    ) { Icon(Icons.Default.KeyboardArrowDown, Strings.CHAT_SCROLL_DOWN) }
                }
            }

            if (dragging) {
                Box(
                    Modifier.fillMaxSize().padding(12.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text(Strings.CHAT_DROP_FILES, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }

    forwardIds?.let { ids ->
        PickChatDialog(
            viewModel = viewModel,
            title = Strings.FORWARD_TO,
            notice = notices.forward,
            onDismiss = { forwardIds = null },
            onPick = { target ->
                viewModel.forwardMessages(target, ids)
                forwardIds = null
            },
        )
    }
    confirmDelete?.let { msg ->
        ConfirmDialog(Strings.CHAT_DELETE_TITLE, notices.deletion, Strings.CHAT_DELETE_FOR_ME, onDismiss = { confirmDelete = null }) {
            viewModel.deleteMessages(chatId, listOf(msg.msgId)); confirmDelete = null
        }
    }
    confirmRetract?.let { msg ->
        // Текст ядра до отзыва: удалить у собеседника можно только попросить (FFI.md).
        ConfirmDialog(Strings.CHAT_RETRACT_TITLE, notices.retraction, Strings.CHAT_RETRACT_FOR_ALL, onDismiss = { confirmRetract = null }) {
            viewModel.retractMessages(chatId, listOf(msg.msgId)); confirmRetract = null
        }
    }
    if (confirmClear) {
        ConfirmDialog(Strings.CLEAR_CHAT, Strings.CLEAR_CHAT_DESC, Strings.CLEAR_CHAT, onDismiss = { confirmClear = false }) {
            viewModel.clearChat(chatId); confirmClear = false
        }
    }
}

@Composable
private fun MessageItem(
    message: ChatMessage,
    isGroup: Boolean,
    outgoing: Color,
    highlighted: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    revealed: Set<Int>,
    onReveal: (Int) -> Unit,
    viewModel: RatatoskViewModel,
    chatId: ByteArray,
    contactAvatars: Map<String, ByteArray>,
    repliedMessages: Map<String, FfiMessage?>,
    authorName: (FfiMessage) -> String,
    waitingNotice: String,
    onJump: (ByteArray) -> Unit,
    onSaved: (String) -> Unit,
    actions: MessageActions,
    sharedActions: SharedCardActions,
    onAuthorClick: (() -> Unit)?,
) {
    val replyId = message.raw.replyTo
    // Цитата — через поток: пришла позже — перерисуется (ревью Android 2.5).
    val reply = replyId?.let { repliedMessages[it.toHexString()] ?: viewModel.getMessage(it) }
    MessageRow(
        message = message,
        isGroup = isGroup,
        outgoingColor = outgoing,
        highlighted = highlighted,
        expanded = expanded,
        onToggleExpanded = onToggleExpanded,
        revealedSpoilers = revealed,
        onRevealSpoiler = onReveal,
        authorAvatar = message.author?.avatarKey?.let { contactAvatars[it] },
        onAuthorClick = onAuthorClick,
        reply = reply,
        replyAuthor = reply?.let(authorName),
        onReplyClick = replyId?.let { id -> { onJump(id) } },
        waitingNotice = waitingNotice,
        actions = actions,
        sharedActions = sharedActions,
        attachments = { _, _ ->
            AttachmentList(message.raw.files, chatId, viewModel, onSaved)
        },
    )
}

@Composable
private fun ChatHeader(title: String, subtitle: String?, avatar: ByteArray?, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(avatarBytes = avatar, name = title, size = 36.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** Полоса под заголовком: Tor поднимается, компаньон ещё не связался с телефоном. */
@Composable
private fun TransportStrip(viewModel: RatatoskViewModel, isCompanion: Boolean, companionLinked: Boolean) {
    val torEnabled by viewModel.torEnabled.collectAsState()
    val torStatus by viewModel.torStatus.collectAsState()
    val status = torStatus
    when {
        isCompanion && !companionLinked -> {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(Strings.CHAT_COMPANION_LINKING, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        }
        !isCompanion && torEnabled && status != null && status.fraction < 1f -> {
            LinearProgressIndicator(progress = { status.fraction }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SearchResults(viewModel: RatatoskViewModel, onPick: (FfiMessage) -> Unit) {
    val results by viewModel.searchResults.collectAsState()
    val searching by viewModel.isSearching.collectAsState()
    when {
        searching && results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(Strings.CHAT_NO_RESULTS, color = MaterialTheme.colorScheme.outline) }
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(results, key = { it.msgId.toHexString() }) { msg ->
                ListItem(
                    headlineContent = { Text(MessagePreview.of(msg), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(java.util.Date(msg.wallMs.toLong())), style = MaterialTheme.typography.labelSmall)
                    },
                    modifier = Modifier.clickable { onPick(msg) },
                )
            }
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { if (text.isNotBlank()) Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { Button(onClick = onDismiss) { Text(Strings.CANCEL) } },
    )
}
