package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.backend.AppEvent
import chat.ratatosk.desktop.util.toHexString
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
import org.ratatosk.core.FfiDeliveryStatus
import org.ratatosk.core.FfiMessage
import org.ratatosk.core.retractionNotice
import java.io.File
import java.util.concurrent.ConcurrentHashMap

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
    fun loadMessages(chatId: ByteArray, limit: Int? = null)
    /** Какой чат виден человеку; зовёт навигация. */
    fun setActiveChat(chatId: ByteArray?)
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
}

/** Переписка: история, активный чат, отправка, правка, реакции, поиск. */
class ChatsModel(session: SessionContext) : FeatureModel(session), ChatsApi {
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

    /**
     * Сколько последних сообщений показано в чате. Перечитывание по событию
     * берёт столько же: список не сжимается, пока человек листает историю,
     * и удалённое действительно исчезает.
     */
    private val loadedLimits = ConcurrentHashMap<String, Int>()

    private var searchJob: Job? = null

    override fun loadMessages(chatId: ByteArray, limit: Int?) {
        val hex = chatId.toHexString()
        val target = maxOf(limit ?: loadedLimits[hex] ?: DEFAULT_PAGE, 1)
        loadedLimits[hex] = target
        session.io { it.requestHistory(chatId, target.toUInt()) }
    }

    override fun setActiveChat(chatId: ByteArray?) {
        if (chatId != null && _activeChatIdFlow.value?.contentEquals(chatId) == true) return
        _activeChatId.value = chatId
        _activeChatIdFlow.value = chatId
        if (chatId != null) {
            _unreadCounts.update { it + (chatId.toHexString() to 0) }
            loadMessages(chatId)
            session.io { it.chatOpened(chatId) }
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
        // Поиск — по базе полного клиента; у компаньона базы нет.
        searchJob = session.clientIo { client ->
            try {
                _searchResults.value = client.search(chatId, query, 50u)
            } finally {
                _isSearching.value = false
            }
        }
        if (session.client == null) _isSearching.value = false
    }

    override fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    override fun sendText(chatId: ByteArray, text: String) {
        session.io("Failed to send") { it.sendText(chatId, text) }
    }

    override fun sendFiles(chatId: ByteArray, files: List<File>, text: String) {
        session.io("Failed to send files") { it.sendFiles(chatId, files, text) }
    }

    override fun resendMessage(chatId: ByteArray, body: String) {
        sendText(chatId, body)
    }

    override fun clearChat(chatId: ByteArray) {
        session.io("Failed to clear chat") { it.clearChat(chatId) }
    }

    override fun deleteMessages(chatId: ByteArray, msgIds: List<ByteArray>) {
        session.io("Failed to delete") { it.deleteMessages(chatId, msgIds) }
    }

    override fun retractMessages(chatId: ByteArray, msgIds: List<ByteArray>) {
        session.io("Failed to retract") { it.retractMessages(chatId, msgIds) }
    }

    override fun editMessage(chatId: ByteArray, msgId: ByteArray, text: String) {
        session.io("Failed to edit") { it.editMessage(chatId, msgId, text) }
    }

    override fun reply(chatId: ByteArray, replyTo: ByteArray, text: String) {
        session.io("Failed to reply") { it.reply(chatId, replyTo, text) }
    }

    override fun forwardMessages(chatId: ByteArray, msgIds: List<ByteArray>) {
        session.io("Failed to forward") { it.forwardMessages(chatId, msgIds) }
    }

    override fun setReaction(chatId: ByteArray, msgId: ByteArray, emoji: String?) {
        session.io { it.setReaction(chatId, msgId, emoji) }
    }

    override fun markRead(chatId: ByteArray, upTo: ByteArray) {
        session.io { backend ->
            backend.markRead(chatId, upTo)
            _unreadCounts.update { it + (chatId.toHexString() to 0) }
        }
    }

    override fun getMessage(msgId: ByteArray): FfiMessage? {
        val hex = msgId.toHexString()
        _repliedMessages.value[hex]?.let { return it }
        // Цитата по идентификатору — из базы полного клиента.
        session.clientIo { client ->
            val msg = client.message(msgId)
            _repliedMessages.update { it + (hex to msg) }
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

    override fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.ChatsLoaded -> {
                // Историю — только тем, чья ещё не загружена: список приходит
                // на каждое входящее, а перечитывать все чаты незачем.
                (event.chats.map { it.chatId } + event.groups.map { it.chatId }).forEach { chatId ->
                    if (!loadedLimits.containsKey(chatId.toHexString())) loadMessages(chatId)
                }
            }
            is AppEvent.HistoryLoaded -> {
                _messages.update { it + (event.chatId.toHexString() to event.messages) }
                if (session.backend?.isCompanion == true) session._isCompanionFresh.value = event.fresh
            }
            is AppEvent.MessageArrived -> {
                val hex = event.chatId.toHexString()
                loadMessages(event.chatId)
                if (_activeChatId.value?.contentEquals(event.chatId) != true) {
                    _unreadCounts.update { it + (hex to (it[hex] ?: 0) + 1) }
                }
            }
            is AppEvent.MessagesChanged -> loadMessages(event.chatId)
            is AppEvent.StatusChanged -> {
                _messageStatuses.update { it + (event.msgId.toHexString() to event.status) }
            }
            else -> {}
        }
    }

    override fun reset() {
        searchJob?.cancel()
        loadedLimits.clear()
        _activeChatId.value = null
        _activeChatIdFlow.value = null
        _messages.value = emptyMap()
        _messageStatuses.value = emptyMap()
        _repliedMessages.value = emptyMap()
        _unreadCounts.value = emptyMap()
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    private companion object {
        const val DEFAULT_PAGE = 100
    }
}
