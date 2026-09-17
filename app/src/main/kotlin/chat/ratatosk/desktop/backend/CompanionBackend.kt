package chat.ratatosk.desktop.backend

import chat.ratatosk.desktop.core.RatatoskCore
import chat.ratatosk.desktop.util.Log
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.util.hexToByteArray
import chat.ratatosk.desktop.util.toHexString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.ratatosk.core.FfiCompanionEvent
import org.ratatosk.core.FfiCompanionOutgoing
import org.ratatosk.core.FfiFile
import org.ratatosk.core.RatatoskCompanion
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Второй экран телефона (§13.4): своей базы и ключей нет, каждая команда
 * уходит телефону, каждый ответ приходит событием.
 */
class CompanionBackend(val companion: RatatoskCompanion) : Backend {
    private val _events = MutableSharedFlow<AppEvent>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<AppEvent> = _events.asSharedFlow()
    override val isCompanion = true

    private var job: Job? = null

    /** Сохранения, ждущие `FileSaved` от ядра. */
    private val pendingSaves = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    /**
     * Ждёт конца выкладывания.
     *
     * Верим не только событию: файл под настоящим именем и нужного размера
     * означает, что ядро уже закончило. Без этой проверки пропавшее
     * `FileSaved` вешало ожидание навсегда — вложение доезжало до конца
     * и не открывалось.
     */
    private suspend fun awaitSaved(done: CompletableDeferred<Unit>, file: FfiFile, destination: File) {
        val expected = file.sizeBytes.toLong()
        while (true) {
            if (done.isCompleted) return done.await()
            val onDisk = withContext(Dispatchers.IO) { if (destination.isFile) destination.length() else -1L }
            if (expected > 0L && onDisk == expected) {
                Log.d(TAG, "save finished by file size, without FileSaved")
                return
            }
            delay(200)
        }
    }

    /** Какое вложение забираем сейчас: ядро берёт по одному за раз. */
    private fun pendingSaveId(): ByteArray? =
        pendingSaves.keys.firstOrNull()?.hexToByteArray()

    private fun failPendingSaves(reason: String) {
        val waiting = pendingSaves.values.toList()
        pendingSaves.clear()
        waiting.forEach { it.completeExceptionally(IllegalStateException(reason)) }
    }

    private fun emit(event: AppEvent) {
        if (!_events.tryEmit(event)) Log.w(TAG, "Event buffer full")
    }

    override fun start(scope: CoroutineScope) {
        job = RatatoskCore.companionEvents.onEach { translate(it) }.launchIn(scope)
    }

    override fun close() {
        job?.cancel()
        job = null
        pendingSaves.values.forEach { it.cancel() }
        pendingSaves.clear()
    }

    private fun translate(event: FfiCompanionEvent) {
        when (event) {
            is FfiCompanionEvent.Linked -> {
                emit(AppEvent.Linked)
                emit(AppEvent.ChatsChanged)
            }
            is FfiCompanionEvent.Unlinked, is FfiCompanionEvent.Revoked -> {
                failPendingSaves(Strings.PHONE_OFFLINE)
                emit(AppEvent.Unlinked)
            }
            // Ядро отказалось словами — сохранение уже не придёт. Без этого
            // ожидание висело вечно, и вложение «просто не открывалось».
            is FfiCompanionEvent.Refused -> {
                failPendingSaves(event.reason)
                emit(AppEvent.Refused(event.reason))
            }

            is FfiCompanionEvent.Chats -> {
                val (groups, personal) = event.chats.partition { it.isGroup }
                // У компаньона момент смены лица приходит прямо в списке.
                val stamps = personal.associate { it.chatId.toHexString() to it.avatarMs.toString() }
                emit(AppEvent.ChatsLoaded(personal.map { mapCompanionChat(it) }, groups.map { mapCompanionGroup(it) }, event.fresh, stamps))
            }
            is FfiCompanionEvent.Members -> emit(AppEvent.MembersLoaded(event.chatId, event.members.map {
                GroupMember(chatId = it.chatId, name = it.name, isMe = it.mine, isOwner = it.owner)
            }))
            is FfiCompanionEvent.GroupCreated -> {
                emit(AppEvent.ChatsChanged)
                emit(AppEvent.GroupCreated(event.chatId))
            }
            is FfiCompanionEvent.ChatsChanged -> emit(AppEvent.ChatsChanged)
            is FfiCompanionEvent.Avatar -> emit(AppEvent.AvatarLoaded(event.chatId, event.bytes))
            is FfiCompanionEvent.AvatarChanged -> emit(AppEvent.AvatarChanged(event.chatId))

            is FfiCompanionEvent.History ->
                emit(AppEvent.HistoryLoaded(event.chatId, event.page.map { mapCompanionMessage(it) }, event.fresh))
            is FfiCompanionEvent.Arrived ->
                emit(AppEvent.MessageArrived(event.message.chatId, event.message.msgId, mapCompanionMessage(event.message)))
            is FfiCompanionEvent.StatusChanged -> emit(AppEvent.StatusChanged(event.msgId, event.status))
            is FfiCompanionEvent.Gone -> emit(AppEvent.MessagesChanged(event.chatId))
            is FfiCompanionEvent.Edited -> emit(AppEvent.MessagesChanged(event.message.chatId))
            is FfiCompanionEvent.Reacted -> {
                emit(AppEvent.MessagesChanged(event.chatId))
                emit(AppEvent.ReactionsChanged(event.chatId, event.msgId, null, event.reactions.map { it.emoji to it.mine }))
            }

            is FfiCompanionEvent.FileProgress -> {
                val fraction = if (event.chunkTotal > 0UL) event.haveChunks.toFloat() / event.chunkTotal.toFloat() else 0f
                emit(AppEvent.FileProgress(event.fileId, fraction))
            }
            is FfiCompanionEvent.FilePreview -> emit(AppEvent.PreviewLoaded(event.fileId, event.bytes))
            is FfiCompanionEvent.FileSaved -> {
                emit(AppEvent.SaveProgress(event.fileId, 1f))
                emit(AppEvent.FileWaiting(event.fileId, null))
                pendingSaves.remove(event.fileId.toHexString())?.complete(Unit)
            }
            // Приём не сорвался, а ждёт: записанное лежит в файле с припиской
            // `.part` и допишется с того же места (FFI, FetchPaused).
            is FfiCompanionEvent.FetchPaused -> pendingSaveId()?.let {
                emit(AppEvent.FileWaiting(it, Strings.FILE_WAITING_PHONE))
            }
            is FfiCompanionEvent.FetchResumed -> pendingSaveId()?.let { id ->
                emit(AppEvent.FileWaiting(id, null))
                if (event.total > 0UL) {
                    emit(AppEvent.SaveProgress(id, (event.done.toFloat() / event.total.toFloat()).coerceIn(0f, 1f)))
                }
            }
            is FfiCompanionEvent.FileGone -> {
                pendingSaves.remove(event.fileId.toHexString())
                    ?.completeExceptionally(IllegalStateException("File is no longer available"))
            }
            is FfiCompanionEvent.FilesSent -> {
                event.fileIds.forEach { emit(AppEvent.FileProgress(it, 1f)) }
                emit(AppEvent.ChatsChanged)
            }
            else -> {}
        }
    }

    // --- Чаты и лица ------------------------------------------------------

    override fun requestChats() = companion.chats()
    override fun requestAvatar(chatId: ByteArray?) = companion.avatar(chatId)
    override fun setMyAvatar(bytes: ByteArray?) = companion.setAvatar(bytes)
    override fun addSharedContact(msgId: ByteArray) = companion.addSharedContact(msgId)
    override fun shareContact(chatId: ByteArray, whoChatId: ByteArray?) = companion.shareContact(chatId, whoChatId)

    // --- Группы -----------------------------------------------------------

    override fun createGroup(title: String) = companion.createGroup(title)
    override fun renameGroup(chatId: ByteArray, title: String) = companion.renameGroup(chatId, title)
    override fun inviteToGroup(chatId: ByteArray, memberChatId: ByteArray) = companion.inviteToGroup(chatId, memberChatId)
    override fun evictFromGroup(chatId: ByteArray, memberChatId: ByteArray) = companion.evictFromGroup(chatId, memberChatId)
    override fun leaveGroup(chatId: ByteArray) = companion.leaveGroup(chatId)
    override fun setGroupAvatar(chatId: ByteArray, bytes: ByteArray?) = companion.setGroupAvatar(chatId, bytes)
    override fun requestMembers(chatId: ByteArray) = companion.members(chatId)

    // --- Переписка --------------------------------------------------------

    override fun requestHistory(chatId: ByteArray, limit: UInt) = companion.history(chatId, limit, null)
    override fun chatOpened(chatId: ByteArray) {}
    override fun sendText(chatId: ByteArray, text: String) = companion.sendText(chatId, text)

    override fun sendFiles(chatId: ByteArray, files: List<File>, text: String) =
        companion.sendFiles(chatId, files.map { FfiCompanionOutgoing(it.absolutePath, previewFor(it)) }, text)

    override fun reply(chatId: ByteArray, replyTo: ByteArray, text: String) = companion.sendReply(chatId, replyTo, text)
    override fun editMessage(chatId: ByteArray, msgId: ByteArray, text: String) = companion.editMessage(chatId, msgId, text)
    override fun deleteMessages(chatId: ByteArray, msgIds: List<ByteArray>) = companion.deleteMessages(chatId, msgIds)
    override fun retractMessages(chatId: ByteArray, msgIds: List<ByteArray>) = companion.retractMessages(chatId, msgIds)
    override fun forwardMessages(chatId: ByteArray, msgIds: List<ByteArray>) = companion.forwardMessages(chatId, msgIds)
    // Пустая строка у телефона — «снять реакцию».
    override fun setReaction(chatId: ByteArray, msgId: ByteArray, emoji: String?) = companion.setReaction(chatId, msgId, emoji ?: "")
    override fun markRead(chatId: ByteArray, upTo: ByteArray) = companion.markRead(chatId, upTo)
    override fun clearChat(chatId: ByteArray) = companion.clearChat(chatId)

    // --- Вложения ---------------------------------------------------------

    override fun acceptFile(chatId: ByteArray, fileId: ByteArray) = companion.acceptFile(fileId)
    override fun declineFile(chatId: ByteArray, fileId: ByteArray) = companion.declineFile(fileId)
    override fun pauseFile(chatId: ByteArray, fileId: ByteArray) = companion.pauseFile(fileId)

    override fun setCachePath(path: String?) = companion.setCachePath(path)

    override fun companionEndpoint() =
        CompanionEndpoint(companion.port().toInt(), companion.desktopIk().toHexString())
    override fun requestPreview(fileId: ByteArray) = companion.preview(fileId)

    /**
     * Ядро пишет файл само (с `.part` до конца приёма) и сообщает `FileSaved`.
     * Сохранение у компаньона одно на всё окно: отмена — `cancelSave()`.
     *
     * `chunkTotal` и `chunkBytes` — из той же записи вложения, оба: своей
     * разбивки у десктопа нет, а у каждого файла она своя.
     */
    override suspend fun saveFile(file: FfiFile, destination: File) {
        val key = file.fileId.toHexString()
        // Ядро берёт по одному вложению за раз: второй вызов до конца первого
        // вернётся отказом, а не встанет в очередь (FFI, save_file).
        if (pendingSaves.isNotEmpty() && !pendingSaves.containsKey(key)) {
            throw IllegalStateException(Strings.FETCH_BUSY)
        }
        val done = CompletableDeferred<Unit>()
        pendingSaves.put(key, done)?.cancel()
        try {
            withContext(Dispatchers.IO) {
                destination.parentFile?.mkdirs()
                companion.saveFile(file.fileId, file.chunkTotal, file.chunkBytes.toULong(), destination.absolutePath)
            }
            awaitSaved(done, file, destination)
        } catch (e: CancellationException) {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                runCatching { companion.cancelSave() }
            }
            throw e
        } finally {
            pendingSaves.remove(key, done)
        }
    }

    private companion object {
        const val TAG = "CompanionBackend"
    }
}
