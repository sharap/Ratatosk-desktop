package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.backend.AppEvent
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.util.MessagePreview
import chat.ratatosk.desktop.util.newForeignReactions
import chat.ratatosk.desktop.util.reactionToAnnounce
import chat.ratatosk.desktop.util.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.ratatosk.core.FfiMessage

/**
 * Что показать системным уведомлением. [key] — одно уведомление на ключ:
 * новое с тем же ключом заменяет прежнее.
 */
data class NotificationRequest(
    val key: String,
    val title: String,
    val body: String,
    val chatId: ByteArray,
    val msgId: ByteArray?,
)

interface NotificationsApi {
    val notificationRequests: SharedFlow<NotificationRequest>
    /** Ключи уведомлений, которые пора убрать: человек открыл этот чат. */
    val notificationDismissals: SharedFlow<String>
    fun setWindowFocused(focused: Boolean)
    /** Окно в фокусе — для «прочитано» и тишины уведомлений. */
    val isWindowFocused: StateFlow<Boolean>
}

/**
 * Решает, о чём уведомлять и какими словами; показывает — окно приложения.
 *
 * Молчим, только когда человек и так это видит: окно в фокусе **и** открыт
 * именно этот чат. Одно уведомление на чат, новое заменяет прежнее с «+N ещё».
 */
class NotificationsModel(
    session: SessionContext,
    private val contacts: ContactsModel,
    private val groups: GroupsModel,
    private val chats: ChatsModel,
) : FeatureModel(session), NotificationsApi {
    private val _requests = MutableSharedFlow<NotificationRequest>(extraBufferCapacity = 32)
    override val notificationRequests = _requests.asSharedFlow()

    private val _dismissals = MutableSharedFlow<String>(extraBufferCapacity = 32)
    override val notificationDismissals = _dismissals.asSharedFlow()

    private val _isWindowFocused = MutableStateFlow(false)
    override val isWindowFocused: StateFlow<Boolean> = _isWindowFocused.asStateFlow()

    private val windowFocused: Boolean get() = _isWindowFocused.value

    /** О чём уже сказали: события могут прийти повторно (replay шины ядра). */
    private val announced = object : LinkedHashMap<String, Unit>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > 256
    }

    /** Сколько непоказанных сообщений в уведомлении чата. */
    private val pendingByChat = HashMap<String, Int>()

    /**
     * Компаньон: последние известные сообщения и их реакции — автора реакции
     * в событии нет, «новая ли» узнаётся сравнением с прошлым списком.
     */
    private val reactionsByMsg = object : LinkedHashMap<String, List<Pair<String, Boolean>>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Pair<String, Boolean>>>?) = size > 2000
    }
    private val messagesById = object : LinkedHashMap<String, FfiMessage>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FfiMessage>?) = size > 2000
    }

    init {
        chats.activeChatIdFlow.onEach { dismissIfSeen() }.launchIn(scope)
    }

    override fun setWindowFocused(focused: Boolean) {
        _isWindowFocused.value = focused
        dismissIfSeen()
    }

    private fun isSeen(chatId: ByteArray): Boolean =
        windowFocused && chats.activeChatIdFlow.value?.contentEquals(chatId) == true

    private fun dismissIfSeen() {
        val chatId = chats.activeChatIdFlow.value ?: return
        if (!windowFocused) return
        val hex = chatId.toHexString()
        synchronized(pendingByChat) { pendingByChat.remove(hex) }
        _dismissals.tryEmit(chatKey(hex))
        _dismissals.tryEmit(reactionKey(hex))
    }

    /**
     * Показывать ли имя и текст — прямо из настроек: у потоков модели настроек
     * без открытого экрана нет подписчика, и значение в них не обновляется.
     */
    private suspend fun privacy(): Pair<Boolean, Boolean> {
        val id = session.activeAccountId.value ?: return true to true
        return session.settings.getNotificationsShowName(id).first() to session.settings.getNotificationsShowText(id).first()
    }

    private fun firstTime(key: String): Boolean = synchronized(announced) { announced.put(key, Unit) == null }

    override fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.HistoryLoaded -> synchronized(reactionsByMsg) {
                event.messages.forEach { remember(it) }
            }
            is AppEvent.MessageArrived -> onMessage(event)
            is AppEvent.ReactionsChanged -> onReactions(event)
            else -> {}
        }
    }

    private fun remember(msg: FfiMessage) {
        val hex = msg.msgId.toHexString()
        messagesById[hex] = msg
        reactionsByMsg[hex] = msg.reactions.map { it.emoji to it.mine }
    }

    private fun onMessage(event: AppEvent.MessageArrived) {
        event.message?.let { synchronized(reactionsByMsg) { remember(it) } }
        if (isSeen(event.chatId)) return
        if (!firstTime("msg:" + event.msgId.toHexString())) return

        scope.launch(Dispatchers.IO) {
            val msg = event.message ?: runCatching { session.client?.message(event.msgId) }.getOrNull()
            if (msg?.mine == true) return@launch
            if (isSeen(event.chatId)) return@launch

            val hex = event.chatId.toHexString()
            val count = synchronized(pendingByChat) { (pendingByChat[hex] ?: 0).plus(1).also { pendingByChat[hex] = it } }
            val (showName, showText) = privacy()

            val group = groups.getGroup(event.chatId)
            val title = if (showName) chatTitle(event.chatId) else Strings.APP_NAME
            var body = if (showText && msg != null) {
                val preview = MessagePreview.of(msg)
                // В группе без имени автора текст непонятно чей.
                if (group != null && showName && !msg.author.isNullOrBlank()) "${msg.author}: $preview" else preview
            } else {
                Strings.NOTIFY_NEW_MESSAGE
            }
            if (count > 1) body = Strings.NOTIFY_MORE.format(body, count - 1)

            _requests.tryEmit(NotificationRequest(chatKey(hex), title, body, event.chatId, event.msgId))
        }
    }

    private fun onReactions(event: AppEvent.ReactionsChanged) {
        val msgHex = event.msgId.toHexString()
        val authorIk = event.authorIk
        if (authorIk != null) {
            // Полный клиент: смотрим в само сообщение, событие одно на постановку и снятие.
            scope.launch(Dispatchers.IO) {
                val client = session.client ?: return@launch
                val msg = runCatching { client.message(event.msgId) }.getOrNull() ?: return@launch
                val reaction = reactionToAnnounce(msg, authorIk) ?: return@launch
                if (!firstTime("reaction:$msgHex:${authorIk.toHexString()}:${reaction.emoji}")) return@launch
                val who = runCatching {
                    client.contacts().firstOrNull { it.peerIk.contentEquals(authorIk) }?.let { it.localName ?: it.displayName }
                        ?: client.groups().firstOrNull { it.chatId.contentEquals(event.chatId) }
                            ?.members?.firstOrNull { it.ik.contentEquals(authorIk) }?.name
                }.getOrNull()
                announceReaction(event.chatId, event.msgId, reaction.emoji, msg, who)
            }
            return
        }

        // Компаньон: новые чужие смайлики — по сравнению с тем, что было.
        val current = event.reactions ?: return
        val (previous, known) = synchronized(reactionsByMsg) {
            val prev = reactionsByMsg[msgHex]
            reactionsByMsg[msgHex] = current
            prev to messagesById[msgHex]
        }
        // Реакции на чужие сообщения — шум; на неизвестное нам — не угадываем.
        if (known?.mine != true) return
        val added = newForeignReactions(previous, current).lastOrNull() ?: return
        val msg = known
        scope.launch(Dispatchers.IO) { announceReaction(event.chatId, event.msgId, added, msg, who = null) }
    }

    private suspend fun announceReaction(chatId: ByteArray, msgId: ByteArray, emoji: String, msg: FfiMessage?, who: String?) {
        if (isSeen(chatId)) return
        val (showName, showText) = privacy()
        // Автор реакции известен только полному клиенту; компаньону — название чата.
        val title = if (showName) who ?: chatTitle(chatId) else Strings.APP_NAME
        val body = if (showText && msg != null) {
            Strings.NOTIFY_REACTION_TO.format(emoji, MessagePreview.of(msg))
        } else {
            Strings.NOTIFY_REACTION.format(emoji)
        }
        _requests.tryEmit(NotificationRequest(reactionKey(chatId.toHexString()), title, body, chatId, msgId))
    }

    private fun chatTitle(chatId: ByteArray): String =
        groups.getGroup(chatId)?.title
            ?: contacts.getContactByChatId(chatId)?.let { it.localName ?: it.displayName }
            ?: Strings.APP_NAME

    private fun chatKey(hex: String) = "chat:$hex"
    private fun reactionKey(hex: String) = "reaction:$hex"

    override fun reset() {
        synchronized(pendingByChat) { pendingByChat.clear() }
        synchronized(reactionsByMsg) {
            reactionsByMsg.clear()
            messagesById.clear()
        }
        synchronized(announced) { announced.clear() }
    }
}
