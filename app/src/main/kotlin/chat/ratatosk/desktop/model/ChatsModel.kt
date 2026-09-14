package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.core.RatatoskCore
import chat.ratatosk.desktop.util.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ratatosk.core.FfiCompanionEvent
import org.ratatosk.core.FfiCompanionOutgoing
import org.ratatosk.core.FfiDeliveryStatus
import org.ratatosk.core.FfiEvent
import org.ratatosk.core.FfiMessage
import org.ratatosk.core.FfiOutgoingFile
import org.ratatosk.core.retractionNotice
import java.io.File

interface ChatsApi {
    val messages: StateFlow<Map<String, List<FfiMessage>>>
    val messageStatuses: StateFlow<Map<String, FfiDeliveryStatus>>
    val repliedMessages: StateFlow<Map<String, FfiMessage?>>
    val unreadCounts: StateFlow<Map<String, Int>>
    val totalUnreadCount: StateFlow<Int>
    val searchResults: StateFlow<List<FfiMessage>>
    val isSearching: StateFlow<Boolean>
    val activeChatId: SharedFlow<ByteArray?>
    val activeChatIdFlow: StateFlow<ByteArray?>
    val activeContactIdFlow: StateFlow<ByteArray?>
    fun loadMessages(chatId: ByteArray, limit: Int? = null)
    fun setActiveChat(chatId: ByteArray?)
    fun setActiveContact(chatId: ByteArray?)
    fun searchMessages(chatId: ByteArray?, query: String)
    fun clearSearch()
    fun sendText(chatId: ByteArray, text: String)
    fun sendFiles(chatId: ByteArray, files: List<File>, text: String)
    fun resendMessage(chatId: ByteArray, body: String)
    fun clearChat(chatId: ByteArray)
    fun deleteMessages(chatId: ByteArray, msgIds: List<ByteArray>)
    fun retractMessages(chatId: ByteArray, msgIds: List<ByteArray>)
    fun editMessage(chatId: ByteArray, msgId: ByteArray, text: String)
    fun reply(chatId: ByteArray, replyTo: ByteArray, text: String)
    fun forwardMessages(chatId: ByteArray, msgIds: List<ByteArray>)
    fun markRead(chatId: ByteArray, upTo: ByteArray)
    fun setReaction(chatId: ByteArray, msgId: ByteArray, emoji: String?)
    fun getMessage(msgId: ByteArray): FfiMessage?
    fun getRetractionNotice(): String
    fun companionSendText(chatId: ByteArray, text: String)
    fun companionSetReaction(chatId: ByteArray, msgId: ByteArray, emoji: String?)
    fun companionEditMessage(chatId: ByteArray, msgId: ByteArray, text: String)
    fun companionDeleteMessages(chatId: ByteArray, msgIds: List<ByteArray>)
    fun companionRetractMessages(chatId: ByteArray, msgIds: List<ByteArray>)
    fun companionReply(chatId: ByteArray, replyTo: ByteArray, text: String)
    fun companionSendFile(chatId: ByteArray, file: File, text: String)
    fun companionLoadHistory(chatId: ByteArray, limit: UInt, before: ByteArray?)
}

/** Переписка: история, активный чат, отправка, правка, реакции, поиск. */
class ChatsModel(
    session: SessionContext,
    private val contacts: ContactsModel,
) : FeatureModel(session), ChatsApi {
    private val _messages = MutableStateFlow<Map<String, List<FfiMessage>>>(emptyMap())
    override val messages = _messages.asStateFlow()

    private val _messageStatuses = MutableStateFlow<Map<String, FfiDeliveryStatus>>(emptyMap())
    override val messageStatuses = _messageStatuses.asStateFlow()

    private val _repliedMessages = MutableStateFlow<Map<String, FfiMessage?>>(emptyMap())
    override val repliedMessages = _repliedMessages.asStateFlow()

    private val _unreadCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    override val unreadCounts = _unreadCounts.asStateFlow()

    override val totalUnreadCount: StateFlow<Int> = _unreadCounts
        .map { it.values.sum() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

    private val _searchResults = MutableStateFlow<List<FfiMessage>>(emptyList())
    override val searchResults = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    override val isSearching = _isSearching.asStateFlow()

    private val _activeChatId = MutableStateFlow<ByteArray?>(null)
    override val activeChatId = _activeChatId.asSharedFlow()

    private val _activeChatIdFlow = MutableStateFlow<ByteArray?>(null)
    override val activeChatIdFlow = _activeChatIdFlow.asStateFlow()

    private val _activeContactIdFlow = MutableStateFlow<ByteArray?>(null)
    override val activeContactIdFlow = _activeContactIdFlow.asStateFlow()

    private var searchJob: Job? = null

    override fun loadMessages(chatId: ByteArray, limit: Int?) {
        if (RatatoskCore.isCompanionMode()) {
            scope.launch(Dispatchers.IO) {
                try {
                    RatatoskCore.getCompanion().history(chatId, limit?.toUInt() ?: 100u, null)
                } catch (e: Exception) { }
            }
            return
        }
        val chatIdHex = chatId.toHexString()
        val currentSize = _messages.value[chatIdHex]?.size ?: 0
        val targetLimit = when {
            limit != null -> limit
            currentSize > 0 -> currentSize + 5
            else -> 100
        }

        scope.launch(Dispatchers.IO) {
            try {
                if (!RatatoskCore.isInitialized()) return@launch
                val msgs = RatatoskCore.getClient().messages(chatId, maxOf(targetLimit, 1).toUInt())
                _messages.update { currentMap ->
                    val existing = currentMap[chatIdHex] ?: emptyList()
                    if (limit == null && msgs.size < existing.size) {
                        currentMap
                    } else {
                        currentMap + (chatIdHex to msgs)
                    }
                }
            } catch (e: Exception) { }
        }
    }

    override fun setActiveChat(chatId: ByteArray?) {
        _activeChatId.value = chatId
        _activeChatIdFlow.value = chatId
        if (chatId != null) {
            _activeContactIdFlow.value = null
            if (session.isCompanionMode.value) {
                scope.launch(Dispatchers.IO) {
                    try {
                        RatatoskCore.getCompanion().history(chatId, 50u, null)
                        RatatoskCore.getCompanion().markRead(chatId, chatId)
                    } catch (e: Exception) {}
                }
            } else {
                _unreadCounts.update { it + (chatId.toHexString() to 0) }
            }
        }
    }

    override fun setActiveContact(chatId: ByteArray?) {
        _activeContactIdFlow.value = chatId
        if (chatId != null) {
            _activeChatId.value = null
            _activeChatIdFlow.value = null
        }
    }

    override fun searchMessages(chatId: ByteArray?, query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        _isSearching.value = true
        searchJob = scope.launch(Dispatchers.IO) {
            try {
                val results = RatatoskCore.getClient().search(chatId, query, 50u)
                _searchResults.value = results
            } catch (e: Exception) {
            } finally {
                _isSearching.value = false
            }
        }
    }

    override fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    override fun sendText(chatId: ByteArray, text: String) {
        scope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().sendText(chatId, text)
                loadMessages(chatId)
            } catch (e: Exception) {
                session._error.value = "Failed to send: ${e.message}"
            }
        }
    }

    override fun sendFiles(chatId: ByteArray, files: List<File>, text: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val outgoingFiles = files.map { file ->
                    FfiOutgoingFile(file.absolutePath, previewFor(file))
                }
                RatatoskCore.getClient().sendFiles(chatId, outgoingFiles, text)
                loadMessages(chatId)
            } catch (e: Exception) {
                session._error.value = "Failed to send files: ${e.message}"
            }
        }
    }

    override fun resendMessage(chatId: ByteArray, body: String) {
        sendText(chatId, body)
    }

    override fun clearChat(chatId: ByteArray) = clientCall(chatId) { it.clearChat(chatId) }

    override fun deleteMessages(chatId: ByteArray, msgIds: List<ByteArray>) =
        clientCall(chatId) { it.deleteMessages(chatId, msgIds) }

    override fun retractMessages(chatId: ByteArray, msgIds: List<ByteArray>) =
        clientCall(chatId) { it.retractMessages(chatId, msgIds) }

    override fun editMessage(chatId: ByteArray, msgId: ByteArray, text: String) =
        clientCall(chatId) { it.editMessage(chatId, msgId, text) }

    override fun reply(chatId: ByteArray, replyTo: ByteArray, text: String) =
        clientCall(chatId) { it.reply(chatId, replyTo, text) }

    override fun forwardMessages(chatId: ByteArray, msgIds: List<ByteArray>) =
        clientCall(chatId) { it.forwardMessages(chatId, msgIds) }

    override fun setReaction(chatId: ByteArray, msgId: ByteArray, emoji: String?) =
        clientCall(chatId) { it.setReaction(chatId, msgId, emoji) }

    /** Вызов клиента и перечитывание чата; ошибки молча, как было. */
    private fun clientCall(chatId: ByteArray, block: (org.ratatosk.core.RatatoskClient) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                block(RatatoskCore.getClient())
                loadMessages(chatId)
            } catch (e: Exception) { }
        }
    }

    override fun markRead(chatId: ByteArray, upTo: ByteArray) {
        scope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().markRead(chatId, upTo)
                _unreadCounts.update { it + (chatId.toHexString() to 0) }
            } catch (e: Exception) { }
        }
    }

    override fun getMessage(msgId: ByteArray): FfiMessage? {
        val hex = msgId.toHexString()
        _repliedMessages.value[hex]?.let { return it }
        scope.launch(Dispatchers.IO) {
            try {
                val msg = RatatoskCore.getClient().message(msgId)
                _repliedMessages.update { it + (hex to msg) }
            } catch (e: Exception) { }
        }
        return null
    }

    override fun getRetractionNotice(): String {
        return try {
            retractionNotice()
        } catch (e: Exception) {
            "Retract selected messages?"
        }
    }

    // --- Компаньон ---------------------------------------------------------

    private fun companionCall(errorPrefix: String?, block: (org.ratatosk.core.RatatoskCompanion) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                block(RatatoskCore.getCompanion())
            } catch (e: Exception) {
                if (errorPrefix != null) session._error.value = "$errorPrefix: ${e.message}"
            }
        }
    }

    override fun companionSendText(chatId: ByteArray, text: String) =
        companionCall("Failed to send") { it.sendText(chatId, text) }

    override fun companionSetReaction(chatId: ByteArray, msgId: ByteArray, emoji: String?) =
        companionCall(null) { it.setReaction(chatId, msgId, emoji ?: "") }

    override fun companionEditMessage(chatId: ByteArray, msgId: ByteArray, text: String) =
        companionCall("Failed to edit") { it.editMessage(chatId, msgId, text) }

    override fun companionDeleteMessages(chatId: ByteArray, msgIds: List<ByteArray>) =
        companionCall(null) { it.deleteMessages(chatId, msgIds) }

    override fun companionRetractMessages(chatId: ByteArray, msgIds: List<ByteArray>) =
        companionCall(null) { it.retractMessages(chatId, msgIds) }

    override fun companionReply(chatId: ByteArray, replyTo: ByteArray, text: String) =
        companionCall("Failed to reply") { it.sendReply(chatId, replyTo, text) }

    override fun companionSendFile(chatId: ByteArray, file: File, text: String) =
        companionCall("Failed to send file") {
            it.sendFiles(chatId, listOf(FfiCompanionOutgoing(file.absolutePath, previewFor(file))), text)
        }

    override fun companionLoadHistory(chatId: ByteArray, limit: UInt, before: ByteArray?) =
        companionCall(null) { it.history(chatId, limit, before) }

    // --- События -----------------------------------------------------------

    override fun onEvent(event: FfiEvent) {
        when (event) {
            is FfiEvent.MessageReceived -> {
                val hexId = event.chatId.toHexString()
                loadMessages(event.chatId)
                if (_activeChatId.value?.contentEquals(event.chatId) != true) {
                    _unreadCounts.update { current ->
                        val newCount = (current[hexId] ?: 0) + 1
                        current + (hexId to newCount)
                    }
                }
            }
            is FfiEvent.StatusChanged -> {
                _messageStatuses.update { it + (event.msgId.toHexString() to event.status) }
            }
            is FfiEvent.MessageEdited -> loadMessages(event.chatId)
            is FfiEvent.ReactionChanged -> loadMessages(event.chatId)
            is FfiEvent.MessagesDeleted -> loadMessages(event.chatId)
            else -> {}
        }
    }

    override fun onCompanionEvent(event: FfiCompanionEvent) {
        when (event) {
            is FfiCompanionEvent.Chats -> {
                event.chats.forEach { loadMessages(it.chatId) }
            }
            is FfiCompanionEvent.History -> {
                val fingerprint = contacts.fingerprint.value
                val mappedMessages = event.page.map { mapCompanionMessage(it, fingerprint) }
                _messages.update { it + (event.chatId.toHexString() to mappedMessages) }
                session._isCompanionFresh.value = event.fresh
            }
            is FfiCompanionEvent.Arrived -> {
                loadMessages(event.message.chatId)
                // Обновить список чатов: последнее сообщение, непрочитанное.
                scope.launch(Dispatchers.IO) {
                    try { RatatoskCore.getCompanion().chats() } catch (e: Exception) {}
                }
            }
            is FfiCompanionEvent.StatusChanged -> {
                _messageStatuses.update { it + (event.msgId.toHexString() to event.status) }
                _activeChatIdFlow.value?.let { loadMessages(it) }
            }
            is FfiCompanionEvent.Gone -> _activeChatIdFlow.value?.let { loadMessages(it) }
            is FfiCompanionEvent.Edited -> loadMessages(event.message.chatId)
            is FfiCompanionEvent.Reacted -> loadMessages(event.chatId)
            else -> {}
        }
    }

    override fun reset() {
        searchJob?.cancel()
        _activeChatId.value = null
        _activeChatIdFlow.value = null
        _activeContactIdFlow.value = null
        _messages.value = emptyMap()
        _messageStatuses.value = emptyMap()
        _repliedMessages.value = emptyMap()
        _unreadCounts.value = emptyMap()
        _searchResults.value = emptyList()
        _isSearching.value = false
    }
}
