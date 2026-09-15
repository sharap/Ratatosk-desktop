package chat.ratatosk.desktop.backend

import org.ratatosk.core.FfiContact
import org.ratatosk.core.FfiDeliveryStatus
import org.ratatosk.core.FfiMessage

/**
 * Что происходит с аккаунтом — одним языком для полного клиента и компаньона.
 *
 * Модели слушают только эти события. [Backend] переводит в них события ядра
 * и **отвечает ими на запросы**: `requestChats` → [ChatsLoaded],
 * `requestHistory` → [HistoryLoaded], и так у обоих режимов, хотя клиент
 * мог бы вернуть результат сразу. Так модели не различают, откуда пришло.
 *
 * Человек и чат называются `chatId`: у компаньона другого имени нет (§13.4).
 */
sealed interface AppEvent {
    // --- Связь с телефоном (компаньон) и отказы ---------------------------
    data object Linked : AppEvent
    data object Unlinked : AppEvent
    data class Refused(val reason: String) : AppEvent

    // --- Список чатов и лица ---------------------------------------------
    /** Список чатов целиком: личные и группы. [fresh] ложно, пока показан снимок из кэша. */
    data class ChatsLoaded(val chats: List<FfiContact>, val groups: List<Group>, val fresh: Boolean) : AppEvent
    /** Список устарел — перезапросить. */
    data object ChatsChanged : AppEvent
    /** Лицо чата; `chatId == null` — своё. `bytes == null` — лица нет. */
    data class AvatarLoaded(val chatId: ByteArray?, val bytes: ByteArray?) : AppEvent
    /** Лицо сменилось — перезапросить. */
    data class AvatarChanged(val chatId: ByteArray?) : AppEvent

    // --- Группы -----------------------------------------------------------
    data class MembersLoaded(val chatId: ByteArray, val members: List<GroupMember>) : AppEvent
    /** Состав или права в группе изменились — перечитать состав. */
    data class GroupChanged(val chatId: ByteArray) : AppEvent
    /** Заведённая нами группа появилась. */
    data class GroupCreated(val chatId: ByteArray) : AppEvent

    // --- Переписка --------------------------------------------------------
    /** Последние сообщения чата — заменяют показанные. */
    data class HistoryLoaded(val chatId: ByteArray, val messages: List<FfiMessage>, val fresh: Boolean) : AppEvent
    data class MessageArrived(val chatId: ByteArray, val msgId: ByteArray) : AppEvent
    /** Сообщения чата изменились (правка, реакция, удаление, своя отправка) — перечитать. */
    data class MessagesChanged(val chatId: ByteArray) : AppEvent
    data class StatusChanged(val msgId: ByteArray, val status: FfiDeliveryStatus) : AppEvent

    // --- Вложения ---------------------------------------------------------
    data class FileProgress(val fileId: ByteArray, val fraction: Float) : AppEvent
    data class PreviewLoaded(val fileId: ByteArray, val bytes: ByteArray?) : AppEvent

    // --- Только полный клиент: сопряжение второго экрана (§13.4) ----------
    /** Ссылка сопряжения — показать сразу: второго показа не будет. */
    data class PairingReady(val deviceId: ByteArray, val uri: String) : AppEvent
    data class PairingRevoked(val deviceId: ByteArray) : AppEvent
    data class DeviceLink(val deviceId: ByteArray, val connected: Boolean) : AppEvent

    // --- Только полный клиент: ступени доставки ---------------------------
    data class TorStatus(val fraction: Float, val note: String, val blocked: String?) : AppEvent
    data class MailAccountReady(val address: String) : AppEvent
    data class MailAccountFailed(val reason: String) : AppEvent
    data class MailLoginFailed(val reason: String) : AppEvent
}
