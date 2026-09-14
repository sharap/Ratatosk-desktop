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
    fun requestPreview(fileId: ByteArray)

    /**
     * Сохранить расшифрованное вложение в [destination] и вернуться, когда
     * файл лежит на месте целиком. Отмена корутины отменяет сохранение.
     */
    suspend fun saveFile(file: FfiFile, destination: File)
}
