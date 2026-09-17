package chat.ratatosk.desktop.backend

import chat.ratatosk.desktop.util.ImageUtils
import org.ratatosk.core.FfiAnomalies
import org.ratatosk.core.FfiCompanionAttachment
import org.ratatosk.core.FfiCompanionChat
import org.ratatosk.core.FfiCompanionMessage
import org.ratatosk.core.FfiContact
import org.ratatosk.core.FfiFile
import org.ratatosk.core.FfiMessage
import org.ratatosk.core.FfiReachability
import org.ratatosk.core.FfiReaction
import org.ratatosk.core.maxPreviewBytes
import java.io.File

// Перевод того, что компаньон получает от телефона, в типы, которые рисуют
// экраны. Экраны пока знают только типы полного клиента.

internal fun mapCompanionChat(chat: FfiCompanionChat): FfiContact {
    return FfiContact(
        peerIk = chat.chatId,
        chatId = chat.chatId,
        fingerprint = "",
        displayName = chat.title,
        localName = null,
        verified = chat.verified,
        seenOnLan = false,
        seenOnBt = false,
        hasAvatar = chat.avatarMs > 0UL,
        onion = null,
        chatmail = null,
        cardVersion = 0UL,
        addedMs = 0UL,
        reachability = FfiReachability(emptyList(), null, null),
        directChannel = null,
        anomalies = FfiAnomalies(0UL, 0UL, 0UL, 0UL, 0UL),
        ygg = null,
        nostrRelays = emptyList()
    )
}

/** Права в группе компаньон узнаёт из состава (`mine && owner`), здесь их ещё нет. */
internal fun mapCompanionGroup(chat: FfiCompanionChat): Group = Group(
    chatId = chat.chatId,
    title = chat.title,
    joined = chat.joined,
    canManage = null,
    avatarMs = chat.avatarMs,
    createdMs = null,
)

internal fun mapCompanionMessage(msg: FfiCompanionMessage): FfiMessage {
    return FfiMessage(
        msgId = msg.msgId,
        body = msg.body,
        mine = msg.mine,
        wallMs = msg.wallMs,
        status = msg.status,
        editedAtMs = msg.editedAtMs,
        forwarded = msg.forwarded,
        // Ключа автора реакции у компаньона нет (§13.4) — только признак «моя».
        reactions = msg.reactions.map { FfiReaction(it.emoji, ByteArray(0), it.mine) },
        files = msg.files.map { mapCompanionAttachment(it, msg.mine) },
        replyTo = msg.replyTo,
        sharedContact = null,
        // Сопоставление подписи автора с участником группы — этап 3.
        author = msg.author,
        authorIk = null
    )
}

internal fun mapCompanionAttachment(att: FfiCompanionAttachment, mine: Boolean): FfiFile {
    return FfiFile(
        fileId = att.fileId,
        name = att.name,
        sizeBytes = att.sizeBytes,
        incoming = !mine,
        accepted = att.accepted,
        complete = att.haveChunks == att.chunkTotal,
        receivedChunks = att.haveChunks,
        chunkTotal = att.chunkTotal,
        hasPreview = att.hasPreview,
        // Разбивка у каждого файла своя, и брать её надо из той же записи:
        // без неё ядро не сходится с телефоном и приём не кончается никогда.
        chunkBytes = att.chunkBytes.toUInt()
    )
}

/** Превью исходящей картинки в пределах, которые задаёт ядро; не картинка — `null`. */
internal fun previewFor(file: File): ByteArray? {
    if (file.extension.lowercase() !in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")) return null
    val limit = try { maxPreviewBytes().toInt() } catch (e: Exception) { return null }
    return ImageUtils.makePreview(file, limit)
}
