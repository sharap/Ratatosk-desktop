package chat.ratatosk.desktop.backend

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import org.ratatosk.core.FfiFile
import java.io.File

/**
 * Открытый аккаунт: полный клиент ([ClientBackend]) или второй экран
 * телефона ([CompanionBackend]).
 *
 * Здесь только то, что умеют оба. Возможности полного клиента (поиск,
 * сверка, транспорты, автоприём…) — у [ClientBackend.client], и модель,
 * которой они нужны, берёт их явно; в режиме компаньона их просто нет.
 *
 * Все команды блокирующие (FFI) и бросают исключения — звать не на UI-потоке.
 * Результаты запросов и всё, что случилось с аккаунтом, приходят в [events].
 */
interface Backend {
    val events: SharedFlow<AppEvent>
    val isCompanion: Boolean

    /** Начать переводить события ядра в [events]. Подписаться на [events] — до этого. */
    fun start(scope: CoroutineScope)

    /** Перестать слушать ядро. Сам аккаунт закрывает `RatatoskCore.logout()`. */
    fun close()

    // --- Чаты и лица ------------------------------------------------------
    fun requestChats()
    fun requestAvatar(chatId: ByteArray?)
    fun setMyAvatar(bytes: ByteArray?)
    fun addSharedContact(msgId: ByteArray)
    /** Послать в [chatId] карточку человека из [whoChatId]; `null` — свою. */
    fun shareContact(chatId: ByteArray, whoChatId: ByteArray?)

    // --- Группы (§11) ------------------------------------------------------
    // Предупреждения §11.4–11.5 (groupJoinNotice, evictionNotice, leaveNotice,
    // ownerLeaveNotice) показывает окно **до** вызова — телефон и ядро за этим
    // не следят и следить не могут (DESKTOP.md, «Группы»).

    /** Ответ — [AppEvent.GroupCreated] и новый список чатов. */
    fun createGroup(title: String)
    fun renameGroup(chatId: ByteArray, title: String)
    /** Позвать может любой участник; позванный обязан быть контактом. */
    fun inviteToGroup(chatId: ByteArray, memberChatId: ByteArray)
    /** Только создатель; себя исключить нельзя. */
    fun evictFromGroup(chatId: ByteArray, memberChatId: ByteArray)
    fun leaveGroup(chatId: ByteArray)
    fun setGroupAvatar(chatId: ByteArray, bytes: ByteArray?)
    /** Ответ — [AppEvent.MembersLoaded]. */
    fun requestMembers(chatId: ByteArray)

    // --- Переписка --------------------------------------------------------
    fun requestHistory(chatId: ByteArray, limit: UInt)
    /** Чат открыт на экране. */
    fun chatOpened(chatId: ByteArray)
    fun sendText(chatId: ByteArray, text: String)
    fun sendFiles(chatId: ByteArray, files: List<File>, text: String)
    fun reply(chatId: ByteArray, replyTo: ByteArray, text: String)
    fun editMessage(chatId: ByteArray, msgId: ByteArray, text: String)
    fun deleteMessages(chatId: ByteArray, msgIds: List<ByteArray>)
    fun retractMessages(chatId: ByteArray, msgIds: List<ByteArray>)
    fun forwardMessages(chatId: ByteArray, msgIds: List<ByteArray>)
    fun setReaction(chatId: ByteArray, msgId: ByteArray, emoji: String?)
    fun markRead(chatId: ByteArray, upTo: ByteArray)
    fun clearChat(chatId: ByteArray)

    // --- Вложения ---------------------------------------------------------
    fun acceptFile(chatId: ByteArray, fileId: ByteArray)
    fun declineFile(chatId: ByteArray, fileId: ByteArray)
    /**
     * Перестать качать входящий файл, не отказываясь от него: приехавшее
     * остаётся, предложение живёт, и `acceptFile` продолжит с того же места
     * (`FFI` о `pause_file`). Это не отказ — путать их нельзя.
     */
    fun pauseFile(chatId: ByteArray, fileId: ByteArray)
    fun requestPreview(fileId: ByteArray)

    /**
     * Сохранить расшифрованное вложение в [destination] и вернуться, когда
     * файл лежит на месте целиком. Отмена корутины отменяет сохранение.
     */
    suspend fun saveFile(file: FfiFile, destination: File)
}
