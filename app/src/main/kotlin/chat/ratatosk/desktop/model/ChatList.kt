package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.backend.Group
import chat.ratatosk.desktop.util.toHexString
import org.ratatosk.core.FfiContact
import org.ratatosk.core.FfiMessage

/** Строка списка чатов: личная переписка или группа. */
sealed class ChatItem {
    abstract val chatId: ByteArray
    abstract val title: String

    /** Последнее сообщение — его показывает подпись и по нему идёт сортировка. */
    abstract val lastMessage: FfiMessage?

    val key: String get() = chatId.toHexString()

    class Direct(val contact: FfiContact, override val lastMessage: FfiMessage?) : ChatItem() {
        override val chatId: ByteArray get() = contact.chatId
        override val title: String get() = contact.localName ?: contact.displayName
    }

    class GroupChat(val group: Group, override val lastMessage: FfiMessage?) : ChatItem() {
        override val chatId: ByteArray get() = group.chatId
        override val title: String get() = group.title
    }
}

/**
 * Собирает список чатов. Чистая функция — проверяется тестом без ядра.
 *
 * Группы показываются всегда (в том числе покинутые: переписка остаётся
 * архивом), личные — только если переписка есть или чат открыт прямо сейчас:
 * иначе список чатов повторял бы список контактов. Порядок — по времени
 * последнего сообщения, [query] отбирает по названию.
 */
fun buildChatList(
    contacts: List<FfiContact>,
    groups: List<Group>,
    messages: Map<String, List<FfiMessage>>,
    activeChatId: ByteArray?,
    query: String = "",
): List<ChatItem> {
    fun last(chatId: ByteArray): FfiMessage? = messages[chatId.toHexString()]?.lastOrNull()

    val items = groups.map { ChatItem.GroupChat(it, last(it.chatId)) } +
        contacts.mapNotNull { contact ->
            val hasChat = !messages[contact.chatId.toHexString()].isNullOrEmpty() ||
                activeChatId?.contentEquals(contact.chatId) == true
            if (hasChat) ChatItem.Direct(contact, last(contact.chatId)) else null
        }

    val needle = query.trim()
    return items
        .filter { needle.isEmpty() || it.title.contains(needle, ignoreCase = true) }
        .sortedWith(compareByDescending<ChatItem> { it.lastMessage?.wallMs ?: 0UL }.thenBy { it.title.lowercase() })
}

/** Контакты для своего раздела: тот же отбор по названию, по алфавиту. */
fun filterContacts(contacts: List<FfiContact>, query: String = ""): List<FfiContact> {
    val needle = query.trim()
    return contacts
        .filter { needle.isEmpty() || (it.localName ?: it.displayName).contains(needle, ignoreCase = true) || it.displayName.contains(needle, ignoreCase = true) }
        .sortedBy { (it.localName ?: it.displayName).lowercase() }
}
