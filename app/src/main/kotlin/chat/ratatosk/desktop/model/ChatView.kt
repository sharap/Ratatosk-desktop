package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.backend.Group
import chat.ratatosk.desktop.backend.GroupMember
import chat.ratatosk.desktop.util.toHexString
import org.ratatosk.core.FfiContact
import org.ratatosk.core.FfiDeliveryStatus
import org.ratatosk.core.FfiMessage

/** Кто написал сообщение в группе — так, как это показать. */
class MessageAuthor(
    val name: String,
    /** Ключ лица в [ContactsApi.contactAvatars] (hex `peerIk`), если автор — наш контакт. */
    val avatarKey: String?,
    /** Личный чат с автором, если он есть, — щелчок по имени открывает карточку. */
    val chatId: ByteArray?,
)

/**
 * Присланная кем-то карточка человека (§4.1).
 *
 * @param fingerprint `null` — отпечатка здесь нет: у компаньона ключ границу
 *   устройства не пересекает, и сверять нечего.
 * @param chatId известная переписка с этим человеком; `null` — его нет в контактах.
 */
class SharedCard(
    val name: String,
    val fingerprint: String?,
    val chatId: ByteArray?,
    val mine: Boolean,
)

/** Реакции одним смайликом: сколько и есть ли среди них моя. */
class ReactionChip(val emoji: String, val count: Int, val mine: Boolean)

/**
 * Сообщение, собранное для экрана чата: статус с учётом событий, автор
 * в группе, реакции по смайликам, признак «первое в серии одного автора».
 */
class ChatMessage(
    val raw: FfiMessage,
    val status: FfiDeliveryStatus?,
    val author: MessageAuthor?,
    val reactions: List<ReactionChip>,
    /** Карточка человека в сообщении; `null` — её там нет. */
    val shared: SharedCard?,
    /** Предыдущее (более старое) сообщение от другого автора — показать имя и лицо. */
    val startsRun: Boolean,
) {
    val msgId: ByteArray get() = raw.msgId
    val key: String = raw.msgId.toHexString()
}

/**
 * Собирает ленту для экрана. Чистая функция: всё, что нужно, приходит
 * аргументами, — проверяется тестом без ядра.
 *
 * Автор в группе ищется так: наш контакт по `authorIk` (своё имя для него),
 * иначе участник группы (личный чат — первые 16 байт `IK`), иначе подпись
 * `author`, которую прислал компаньон. Выдумывать автора по `msgId`, как
 * было в Android, нельзя: имя получалось случайным.
 */
fun buildChatMessages(
    messages: List<FfiMessage>,
    statuses: Map<String, FfiDeliveryStatus>,
    isGroup: Boolean,
    contacts: List<FfiContact>,
    members: List<GroupMember>?,
): List<ChatMessage> {
    var previousAuthorKey: String? = null
    return messages.map { msg ->
        val author = if (isGroup && !msg.mine) resolveAuthor(msg, contacts, members) else null
        val authorKey = if (msg.mine) "me" else author?.name ?: ""
        val startsRun = authorKey != previousAuthorKey
        previousAuthorKey = authorKey
        ChatMessage(
            raw = msg,
            status = statuses[msg.msgId.toHexString()] ?: msg.status,
            author = author,
            reactions = msg.reactions.groupBy { it.emoji }.map { (emoji, list) ->
                ReactionChip(emoji, list.size, list.any { it.mine })
            },
            shared = msg.sharedContact?.let { card ->
                SharedCard(
                    name = card.displayName,
                    fingerprint = card.fingerprint.takeIf { it.isNotBlank() },
                    // Чат человека — первые 16 байт `IK`; у компаньона там
                    // уже сам `chatId`, и обрезка ничего не меняет.
                    chatId = card.peerIk.takeIf { card.alreadyKnown && it.size >= 16 }?.copyOf(16),
                    mine = card.mine,
                )
            },
            startsRun = startsRun,
        )
    }
}

private fun resolveAuthor(msg: FfiMessage, contacts: List<FfiContact>, members: List<GroupMember>?): MessageAuthor? {
    val ik = msg.authorIk?.takeIf { it.isNotEmpty() }
    if (ik != null) {
        contacts.firstOrNull { it.peerIk.contentEquals(ik) }?.let {
            return MessageAuthor(it.localName ?: it.displayName, it.peerIk.toHexString(), it.chatId)
        }
        val memberChat = ik.copyOf(16)
        members?.firstOrNull { it.chatId.contentEquals(memberChat) }?.let {
            return MessageAuthor(it.name, null, null)
        }
    }
    return msg.author?.takeIf { it.isNotBlank() }?.let { MessageAuthor(it, null, null) }
}

/** Заголовок чата: группа или контакт. */
fun chatTitle(chatId: ByteArray, contacts: List<FfiContact>, groups: List<Group>): String? =
    groups.firstOrNull { it.chatId.contentEquals(chatId) }?.title
        ?: contacts.firstOrNull { it.chatId.contentEquals(chatId) }?.let { it.localName ?: it.displayName }
