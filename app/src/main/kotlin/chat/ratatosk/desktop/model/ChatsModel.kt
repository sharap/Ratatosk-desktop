package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.backend.AppEvent
import chat.ratatosk.desktop.util.hexToByteArray
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
import org.ratatosk.core.deletionNotice
import org.ratatosk.core.editNotice
import org.ratatosk.core.forwardNotice
import org.ratatosk.core.maxEditAgeMs
import org.ratatosk.core.retractionNotice
import org.ratatosk.core.waitingNotice
import kotlinx.coroutines.flow.first
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

    /**
     * Отправляет записанное голосовое.
     *
     * Волна кладётся в превью до отправки: ядро просит его в момент
     * отправки, а после — неоткуда взять, декодировать Opus нечем.
     */
    fun sendVoice(chatId: ByteArray, file: File, waveform: ByteArray?)
    /**
     * Недоставленное (`UNDELIVERABLE`) текстовое — отправить заново: прежнее
     * удаляется у себя, тот же текст уходит с той же цитатой. Повтора в ядре
     * нет; простой повтор текста рядом с недоставленным давал дубль (ревью 5.4).
     * Вложения повторить нельзя — путей к исходным файлам у приложения нет.
     */
    fun resendMessage(chatId: ByteArray, message: FfiMessage)
    /** Догрузить историю постарше: ещё [page] сообщений. */
    fun loadOlder(chatId: ByteArray, page: Int = 100)
    /** Загружено ли всё, что есть (последняя догрузка не принесла нового). */
    fun isHistoryComplete(chatId: ByteArray): Boolean
    /**
     * Догружать историю, пока сообщение не окажется в ленте, — не дальше
     * [maxMessages]. `true` — нашлось.
     */
    suspend fun ensureMessageLoaded(chatId: ByteArray, msgId: ByteArray, maxMessages: Int = 5000): Boolean
    /** Отметить прочитанным до [upTo]; повторы для того же сообщения не уходят в ядро. */
    fun markReadUpTo(chatId: ByteArray, upTo: ByteArray)
    val chatNotices: ChatNotices
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

    override fun sendVoice(chatId: ByteArray, file: File, waveform: ByteArray?) {
        if (waveform != null) chat.ratatosk.desktop.backend.rememberVoiceWaveform(file.absolutePath, waveform)
        sendFiles(chatId, listOf(file), "")
    }

    override fun sendFiles(chatId: ByteArray, files: List<File>, text: String) {
        session.io("Failed to send files") { it.sendFiles(chatId, files, text) }
    }

    override fun resendMessage(chatId: ByteArray, message: FfiMessage) {
        if (message.files.isNotEmpty() || message.body.isBlank()) return
        session.io("Failed to send") { backend ->
            backend.deleteMessages(chatId, listOf(message.msgId))
            val replyTo = message.replyTo
            if (replyTo != null) backend.reply(chatId, replyTo, message.body) else backend.sendText(chatId, message.body)
        }
    }

    override fun loadOlder(chatId: ByteArray, page: Int) {
        val hex = chatId.toHexString()
        if (isHistoryComplete(chatId)) return
        loadMessages(chatId, (loadedLimits[hex] ?: DEFAULT_PAGE) + page)
    }

    override fun isHistoryComplete(chatId: ByteArray): Boolean {
        val hex = chatId.toHexString()
        val limit = loadedLimits[hex] ?: return false
        // Пришло меньше, чем просили, — значит, старше ничего нет.
        return (_messages.value[hex]?.size ?: 0) < limit
    }

    override suspend fun ensureMessageLoaded(chatId: ByteArray, msgId: ByteArray, maxMessages: Int): Boolean {
        val hex = chatId.toHexString()
        fun found() = _messages.value[hex]?.any { it.msgId.contentEquals(msgId) } == true
        while (true) {
            if (found()) return true
            val limit = loadedLimits[hex] ?: DEFAULT_PAGE
            if (limit >= maxMessages || isHistoryComplete(chatId)) return false
            val before = _messages.value[hex]?.size ?: 0
            loadMessages(chatId, minOf(limit + 500, maxMessages))
            // Ответ приходит событием; ждём, пока лента вырастет, но не вечно.
            kotlinx.coroutines.withTimeoutOrNull(5_000) {
                _messages.first { (it[hex]?.size ?: 0) > before || it[hex]?.any { m -> m.msgId.contentEquals(msgId) } == true }
            } ?: return found()
        }
    }

    /** Докуда уже отметили прочитанным, по чатам. */
    private val markedRead = ConcurrentHashMap<String, String>()

    override fun markReadUpTo(chatId: ByteArray, upTo: ByteArray) {
        val hex = chatId.toHexString()
        val upToHex = upTo.toHexString()
        if (markedRead.put(hex, upToHex) == upToHex) return
        markRead(chatId, upTo)
    }

    override val chatNotices: ChatNotices by lazy {
        ChatNotices(
            edit = runCatching { editNotice() }.getOrDefault(""),
            deletion = runCatching { deletionNotice() }.getOrDefault(""),
            forward = runCatching { forwardNotice() }.getOrDefault(""),
            retraction = runCatching { retractionNotice() }.getOrDefault(""),
            waiting = runCatching { waitingNotice() }.getOrDefault(""),
            maxEditAgeMs = runCatching { maxEditAgeMs().toLong() }.getOrDefault(7L * 24 * 3600 * 1000),
        )
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
        // Цитата часто есть в уже загруженной ленте — у компаньона другого пути и нет.
        _messages.value.values.asSequence().flatten().firstOrNull { it.msgId.contentEquals(msgId) }?.let { return it }
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

    /** В каком загруженном чате лежит это вложение. */
    private fun chatIdWithFile(fileId: ByteArray): ByteArray? =
        _messages.value.entries
            .firstOrNull { (_, list) -> list.any { msg -> msg.files.any { it.fileId.contentEquals(fileId) } } }
            ?.key?.hexToByteArray()

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
            // Файл собрался — в сообщении он всё ещё «не полный»: `FfiFile`
            // это снимок, а события о ходе передачи чата не называют. Находим
            // чат по `fileId` и перечитываем его, иначе кнопки «Открыть» и
            // «Сохранить» появлялись только после повторного входа в чат.
            is AppEvent.FileProgress -> if (event.fraction >= 1f) {
                chatIdWithFile(event.fileId)?.let { loadMessages(it) }
            }
            is AppEvent.StatusChanged -> {
                _messageStatuses.update { it + (event.msgId.toHexString() to event.status) }
            }
            else -> {}
        }
    }

    override fun reset() {
        searchJob?.cancel()
        loadedLimits.clear()
        markedRead.clear()
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

/** Тексты ядра, которые экран чата обязан показать до действия (FFI.md). */
class ChatNotices(
    val edit: String,
    val deletion: String,
    val forward: String,
    val retraction: String,
    /** Что сказать про статус WAITING: отправится само, когда собеседник появится. */
    val waiting: String,
    val maxEditAgeMs: Long,
)
