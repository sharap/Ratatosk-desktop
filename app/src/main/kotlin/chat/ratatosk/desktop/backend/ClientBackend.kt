package chat.ratatosk.desktop.backend

import chat.ratatosk.desktop.core.RatatoskCore
import chat.ratatosk.desktop.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.ratatosk.core.FfiEvent
import org.ratatosk.core.FfiFile
import org.ratatosk.core.FfiFileReader
import org.ratatosk.core.FfiOutgoingFile
import org.ratatosk.core.RatatoskClient
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Полный клиент: своя база, свои ключи, свои ступени доставки. */
class ClientBackend(val client: RatatoskClient) : Backend {
    private val _events = MutableSharedFlow<AppEvent>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<AppEvent> = _events.asSharedFlow()
    override val isCompanion = false

    private var job: Job? = null

    private fun emit(event: AppEvent) {
        if (!_events.tryEmit(event)) Log.w(TAG, "Event buffer full")
    }

    override fun start(scope: CoroutineScope) {
        job = RatatoskCore.events.onEach { translate(it) }.launchIn(scope)
    }

    override fun close() {
        job?.cancel()
        job = null
    }

    private fun translate(event: FfiEvent) {
        when (event) {
            is FfiEvent.MessageReceived -> emit(AppEvent.MessageArrived(event.chatId, event.msgId))
            is FfiEvent.StatusChanged -> emit(AppEvent.StatusChanged(event.msgId, event.status))
            is FfiEvent.MessageEdited -> emit(AppEvent.MessagesChanged(event.chatId))
            is FfiEvent.ReactionChanged -> emit(AppEvent.MessagesChanged(event.chatId))
            is FfiEvent.MessagesDeleted -> emit(AppEvent.MessagesChanged(event.chatId))
            is FfiEvent.ContactAdded, is FfiEvent.ContactChanged, is FfiEvent.ContactRemoved -> emit(AppEvent.ChatsChanged)
            is FfiEvent.GroupCreated -> {
                emit(AppEvent.ChatsChanged)
                emit(AppEvent.GroupCreated(event.chatId))
            }
            is FfiEvent.GroupRenamed -> emit(AppEvent.ChatsChanged)
            is FfiEvent.GroupMembershipChanged -> {
                emit(AppEvent.ChatsChanged)
                emit(AppEvent.GroupChanged(event.chatId))
            }
            is FfiEvent.GroupAvatarChanged -> {
                emit(AppEvent.AvatarChanged(event.chatId))
                emit(AppEvent.ChatsChanged)
            }
            is FfiEvent.AvatarChanged -> {
                // Ядро называет человека ключом; экрану нужен его чат.
                val chatId = runCatching { client.contacts().find { it.peerIk.contentEquals(event.peerIk) }?.chatId }.getOrNull()
                if (chatId != null) emit(AppEvent.AvatarChanged(chatId))
                emit(AppEvent.ChatsChanged)
            }
            is FfiEvent.OwnAvatarChanged -> emit(AppEvent.AvatarChanged(null))
            is FfiEvent.FileProgress -> {
                val fraction = if (event.total > 0UL) event.received.toFloat() / event.total.toFloat() else 0f
                emit(AppEvent.FileProgress(event.fileId, fraction))
            }
            is FfiEvent.TorStatus -> emit(AppEvent.TorStatus(event.fraction, event.note, event.blocked))
            is FfiEvent.MailAccountReady -> emit(AppEvent.MailAccountReady(event.address))
            is FfiEvent.MailAccountFailed -> emit(AppEvent.MailAccountFailed(event.reason))
            is FfiEvent.MailLoginFailed -> emit(AppEvent.MailLoginFailed(event.reason))
            is FfiEvent.CommandRefused -> emit(AppEvent.Refused(event.reason))
            is FfiEvent.PairingReady -> emit(AppEvent.PairingReady(event.deviceId, event.uri))
            is FfiEvent.PairingRevoked -> emit(AppEvent.PairingRevoked(event.deviceId))
            is FfiEvent.DeviceLink -> emit(AppEvent.DeviceLink(event.deviceId, event.connected))
            else -> {}
        }
    }

    // --- Чаты и лица ------------------------------------------------------

    override fun requestChats() {
        emit(AppEvent.ChatsLoaded(client.contacts(), client.groups().map { it.toGroup() }, fresh = true))
    }

    // Создатель, ушедший из группы, остаётся создателем — но распоряжаться
    // может только вернувшись (`FfiGroup::mine`).
    private fun org.ratatosk.core.FfiGroup.toGroup() = Group(
        chatId = chatId,
        title = title,
        joined = joined,
        canManage = mine && joined,
        avatarMs = avatarMs,
        createdMs = createdMs,
    )

    override fun requestAvatar(chatId: ByteArray?) {
        if (chatId == null) {
            emit(AppEvent.AvatarLoaded(null, client.myAvatar()))
            return
        }
        if (groupOf(chatId) != null) {
            emit(AppEvent.AvatarLoaded(chatId, client.groupAvatar(chatId)))
            return
        }
        val peerIk = peerIkOf(chatId) ?: return
        emit(AppEvent.AvatarLoaded(chatId, client.avatarOf(peerIk)))
    }

    override fun setMyAvatar(bytes: ByteArray?) {
        client.setAvatar(bytes)
        emit(AppEvent.AvatarLoaded(null, bytes))
    }

    override fun addSharedContact(msgId: ByteArray) = client.addSharedContact(msgId)

    override fun shareContact(chatId: ByteArray, whoChatId: ByteArray?) {
        // Своя карточка у клиента — это ссылка на себя; ядро клиента ждёт ключ.
        val peerIk = whoChatId?.let { peerIkOf(it) ?: throw IllegalArgumentException("Unknown contact") }
            ?: throw UnsupportedOperationException("Sharing own card is not supported by the client API")
        client.shareContact(chatId, peerIk)
    }

    private fun peerIkOf(chatId: ByteArray): ByteArray? =
        client.contacts().find { it.chatId.contentEquals(chatId) }?.peerIk

    private fun groupOf(chatId: ByteArray) = client.groups().find { it.chatId.contentEquals(chatId) }

    /** `chatId` личного чата — первые 16 байт `IK` (ядро, `chat_id_for`). */
    private fun chatIdOfIk(ik: ByteArray): ByteArray = ik.copyOf(16)

    // --- Группы -----------------------------------------------------------

    override fun createGroup(title: String) = client.createGroup(title)
    override fun renameGroup(chatId: ByteArray, title: String) = client.renameGroup(chatId, title)
    override fun leaveGroup(chatId: ByteArray) = client.leaveGroup(chatId)

    override fun setGroupAvatar(chatId: ByteArray, bytes: ByteArray?) {
        client.setGroupAvatar(chatId, bytes)
        emit(AppEvent.AvatarLoaded(chatId, bytes))
    }

    override fun inviteToGroup(chatId: ByteArray, memberChatId: ByteArray) {
        val peerIk = peerIkOf(memberChatId) ?: throw IllegalArgumentException("Only a contact can be invited")
        client.inviteToGroup(chatId, peerIk)
    }

    override fun evictFromGroup(chatId: ByteArray, memberChatId: ByteArray) {
        // Участник может и не быть контактом: ключ берём из состава группы.
        val ik = groupOf(chatId)?.members?.find { chatIdOfIk(it.ik).contentEquals(memberChatId) }?.ik
            ?: throw IllegalArgumentException("Not a member of this group")
        client.evictFromGroup(chatId, ik)
    }

    override fun requestMembers(chatId: ByteArray) {
        val group = groupOf(chatId) ?: return
        val members = group.members.map {
            GroupMember(
                chatId = chatIdOfIk(it.ik),
                name = it.name,
                isMe = it.mine,
                // Клиент знает только, создатели ли мы сами.
                isOwner = if (it.mine) group.mine else null,
            )
        }
        emit(AppEvent.MembersLoaded(chatId, members))
    }

    // --- Переписка --------------------------------------------------------

    override fun requestHistory(chatId: ByteArray, limit: UInt) {
        emit(AppEvent.HistoryLoaded(chatId, client.messages(chatId, maxOf(limit, 1u)), fresh = true))
    }

    override fun chatOpened(chatId: ByteArray) {}

    /** Команда клиента меняет его же базу, а событие об этом приходит не всегда. */
    private inline fun changing(chatId: ByteArray, block: () -> Unit) {
        block()
        emit(AppEvent.MessagesChanged(chatId))
    }

    override fun sendText(chatId: ByteArray, text: String) = changing(chatId) { client.sendText(chatId, text) }

    override fun sendFiles(chatId: ByteArray, files: List<File>, text: String) = changing(chatId) {
        client.sendFiles(chatId, files.map { FfiOutgoingFile(it.absolutePath, previewFor(it)) }, text)
    }

    override fun reply(chatId: ByteArray, replyTo: ByteArray, text: String) = changing(chatId) { client.reply(chatId, replyTo, text) }
    override fun editMessage(chatId: ByteArray, msgId: ByteArray, text: String) = changing(chatId) { client.editMessage(chatId, msgId, text) }
    override fun deleteMessages(chatId: ByteArray, msgIds: List<ByteArray>) = changing(chatId) { client.deleteMessages(chatId, msgIds) }
    override fun retractMessages(chatId: ByteArray, msgIds: List<ByteArray>) = changing(chatId) { client.retractMessages(chatId, msgIds) }
    override fun forwardMessages(chatId: ByteArray, msgIds: List<ByteArray>) = changing(chatId) { client.forwardMessages(chatId, msgIds) }
    override fun setReaction(chatId: ByteArray, msgId: ByteArray, emoji: String?) = changing(chatId) { client.setReaction(chatId, msgId, emoji) }
    override fun clearChat(chatId: ByteArray) = changing(chatId) { client.clearChat(chatId) }
    override fun markRead(chatId: ByteArray, upTo: ByteArray) = client.markRead(chatId, upTo)

    // --- Вложения ---------------------------------------------------------

    override fun acceptFile(chatId: ByteArray, fileId: ByteArray) = changing(chatId) { client.acceptFile(fileId) }
    override fun declineFile(chatId: ByteArray, fileId: ByteArray) = changing(chatId) { client.declineFile(fileId) }

    override fun requestPreview(fileId: ByteArray) {
        emit(AppEvent.PreviewLoaded(fileId, client.previewOf(fileId)))
    }

    /**
     * Пишется во временный `.part` рядом и переносится на место одним ходом:
     * недописанный файл не выглядит готовым ни человеку, ни открытию из кэша,
     * которое сверяет размер уже лежащей копии.
     */
    override suspend fun saveFile(file: FfiFile, destination: File) = withContext(Dispatchers.IO) {
        val part = File(destination.parentFile, destination.name + ".part")
        var reader: FfiFileReader? = null
        var saved = false
        try {
            destination.parentFile?.mkdirs()
            reader = client.openFile(file.fileId) ?: throw IllegalStateException("File is not available")
            part.outputStream().use { output ->
                val total = reader.chunkTotal()
                for (i in 0UL until total) {
                    currentCoroutineContext().ensureActive()
                    val chunk = reader.chunk(i) ?: throw IllegalStateException("File is incomplete")
                    output.write(chunk)
                    emit(AppEvent.FileProgress(file.fileId, (i + 1UL).toFloat() / total.toFloat()))
                }
            }
            Files.move(part.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            saved = true
        } finally {
            reader?.destroy()
            if (!saved) part.delete()
        }
    }

    private companion object {
        const val TAG = "ClientBackend"
    }
}
