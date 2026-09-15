package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.backend.AppEvent
import chat.ratatosk.desktop.backend.Backend
import chat.ratatosk.desktop.backend.Group
import chat.ratatosk.desktop.backend.GroupMember
import chat.ratatosk.desktop.data.SettingsRepository
import chat.ratatosk.desktop.util.toHexString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ratatosk.core.FfiAnomalies
import org.ratatosk.core.FfiContact
import org.ratatosk.core.FfiDeliveryStatus
import org.ratatosk.core.FfiFile
import org.ratatosk.core.FfiMessage
import org.ratatosk.core.FfiReachability
import java.io.File
import java.nio.file.Files
import java.util.Collections

/**
 * Модели поверх подставного [Backend]: без ядра, без сети, без настроек человека.
 * Главное здесь — что команды экрана доходят до Backend в любом режиме
 * (раньше в режиме компаньона они шли в несуществующий клиент и падали).
 */
class ModelsWithFakeBackendTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val settingsDir = Files.createTempDirectory("settings").toFile()
    private val session = SessionContext(scope, SettingsRepository(File(settingsDir, "s.preferences_pb")))
    private val backend = FakeBackend(isCompanion = true)

    init {
        session.backend = backend
    }

    @After
    fun tearDown() {
        scope.cancel()
        settingsDir.deleteRecursively()
    }

    @Test
    fun companionSendReachesBackend() {
        val chats = ChatsModel(session)
        chats.sendText(chatA, "привет")
        chats.setReaction(chatA, msg1, "👍")
        waitUntil { backend.calls.size >= 2 }
        assertTrue(backend.calls.contains("sendText:${chatA.toHexString()}:привет"))
        assertTrue(backend.calls.contains("setReaction:${msg1.toHexString()}:👍"))
    }

    @Test
    fun historyReplacesMessagesAndIsRequestedOncePerChat() {
        val chats = ChatsModel(session)
        chats.onEvent(AppEvent.ChatsLoaded(listOf(contact(chatA, ikA)), emptyList(), fresh = true))
        chats.onEvent(AppEvent.ChatsLoaded(listOf(contact(chatA, ikA)), emptyList(), fresh = true))
        waitUntil { backend.calls.any { it.startsWith("requestHistory") } }
        Thread.sleep(50)
        assertEquals(1, backend.calls.count { it == "requestHistory:${chatA.toHexString()}:100" })

        chats.onEvent(AppEvent.HistoryLoaded(chatA, listOf(message(msg1)), fresh = false))
        assertEquals(1, chats.messages.value[chatA.toHexString()]?.size)
        assertEquals(false, session.isCompanionFresh.value)
    }

    @Test
    fun unreadCountsOnlyForInactiveChats() {
        val chats = ChatsModel(session)
        chats.setActiveChat(chatA)
        chats.onEvent(AppEvent.MessageArrived(chatA, msg1))
        chats.onEvent(AppEvent.MessageArrived(chatB, msg1))
        chats.onEvent(AppEvent.MessageArrived(chatB, msg1))
        assertEquals(0, chats.unreadCounts.value[chatA.toHexString()])
        assertEquals(2, chats.unreadCounts.value[chatB.toHexString()])
    }

    @Test
    fun avatarsAreKeyedByPeerIkAndRequestedOnce() {
        val contacts = ContactsModel(session)
        contacts.onEvent(AppEvent.ChatsLoaded(listOf(contact(chatA, ikA)), emptyList(), fresh = true))
        assertNull(contacts.getAvatarOf(ikA))
        assertNull(contacts.getAvatarOf(ikA))
        waitUntil { backend.calls.any { it.startsWith("requestAvatar") } }
        Thread.sleep(50)
        assertEquals(1, backend.calls.count { it == "requestAvatar:${chatA.toHexString()}" })

        val face = byteArrayOf(1, 2, 3)
        contacts.onEvent(AppEvent.AvatarLoaded(chatA, face))
        assertArrayEquals(face, contacts.getAvatarOf(ikA))
        contacts.onEvent(AppEvent.AvatarLoaded(null, face))
        assertArrayEquals(face, contacts.myAvatar.value)
    }

    @Test
    fun clientOnlyFeaturesAreQuietInCompanionMode() {
        val contacts = ContactsModel(session)
        contacts.markVerified(ikA)
        contacts.addContact("ratatosk:v0:x", metInPerson = false)
        Thread.sleep(50)
        assertTrue(backend.calls.isEmpty())
        assertNull(session.error.value)
    }

    @Test
    fun companionGroupRightsComeFromMembers() {
        val groups = GroupsModel(session)
        groups.onEvent(AppEvent.ChatsLoaded(emptyList(), listOf(group(chatB, joined = true, canManage = null)), fresh = true))
        // Состава ещё нет — распоряжаться нельзя.
        assertFalse(groups.canManageGroup(chatB))

        groups.onEvent(AppEvent.MembersLoaded(chatB, listOf(
            GroupMember(chatA, "Я", isMe = true, isOwner = true),
            GroupMember(ByteArray(16) { 9 }, "Б", isMe = false, isOwner = false),
        )))
        assertTrue(groups.canManageGroup(chatB))
    }

    @Test
    fun leftOwnerCannotManage() {
        val groups = GroupsModel(session)
        groups.onEvent(AppEvent.ChatsLoaded(emptyList(), listOf(group(chatB, joined = false, canManage = true)), fresh = true))
        assertFalse(groups.canManageGroup(chatB))
    }

    @Test
    fun groupCommandsReachBackendAndGroupsGetHistory() {
        val groups = GroupsModel(session)
        val chats = ChatsModel(session)
        val event = AppEvent.ChatsLoaded(emptyList(), listOf(group(chatB, joined = true, canManage = true)), fresh = true)
        groups.onEvent(event)
        chats.onEvent(event)
        groups.evictFromGroup(chatB, chatA)
        groups.leaveGroup(chatB)
        waitUntil { backend.calls.contains("leave") && backend.calls.contains("evict:${chatA.toHexString()}") }
        waitUntil { backend.calls.contains("requestHistory:${chatB.toHexString()}:100") }
    }

    @Test
    fun notificationsForCompanionMessagesAndReactions() {
        val contacts = ContactsModel(session)
        val groups = GroupsModel(session)
        val chats = ChatsModel(session)
        val notifications = NotificationsModel(session, contacts, groups, chats)
        val requests = Collections.synchronizedList(mutableListOf<NotificationRequest>())
        scope.launch { notifications.notificationRequests.collect { requests += it } }

        contacts.onEvent(AppEvent.ChatsLoaded(listOf(contact(chatA, ikA)), emptyList(), fresh = true))
        val mineMsg = message(msg1).copy(mine = true, body = "моё")
        notifications.onEvent(AppEvent.HistoryLoaded(chatA, listOf(mineMsg), fresh = true))

        // Своё сообщение — молчим; чужое — уведомляем, с именем чата и превью.
        notifications.onEvent(AppEvent.MessageArrived(chatA, ByteArray(16) { 5 }, message(ByteArray(16) { 5 }).copy(mine = true)))
        notifications.onEvent(AppEvent.MessageArrived(chatA, ByteArray(16) { 6 }, message(ByteArray(16) { 6 }).copy(body = "**привет**")))
        waitUntil { requests.size == 1 }
        assertEquals("A", requests[0].title)
        assertEquals("привет", requests[0].body)

        // Повтор того же события — не новое уведомление.
        notifications.onEvent(AppEvent.MessageArrived(chatA, ByteArray(16) { 6 }, message(ByteArray(16) { 6 })))
        // Второе сообщение того же чата — «+1 ещё».
        notifications.onEvent(AppEvent.MessageArrived(chatA, ByteArray(16) { 7 }, message(ByteArray(16) { 7 }).copy(body = "ещё")))
        waitUntil { requests.size == 2 }
        assertEquals("ещё\n+1 ещё", requests[1].body)
        assertEquals(requests[0].key, requests[1].key)

        // Реакция компаньона на моё сообщение: новая чужая — уведомляем.
        notifications.onEvent(AppEvent.ReactionsChanged(chatA, msg1, null, listOf("👍" to false)))
        waitUntil { requests.size == 3 }
        assertEquals("👍 на: моё", requests[2].body)
    }

    @Test
    fun noNotificationWhenChatIsOpenInFocusedWindow() {
        val contacts = ContactsModel(session)
        val chats = ChatsModel(session)
        val notifications = NotificationsModel(session, contacts, GroupsModel(session), chats)
        val requests = Collections.synchronizedList(mutableListOf<NotificationRequest>())
        scope.launch { notifications.notificationRequests.collect { requests += it } }

        chats.setActiveChat(chatA)
        notifications.setWindowFocused(true)
        notifications.onEvent(AppEvent.MessageArrived(chatA, ByteArray(16) { 6 }, message(ByteArray(16) { 6 })))
        // Другой чат в том же окне — уведомляем.
        notifications.onEvent(AppEvent.MessageArrived(chatB, ByteArray(16) { 7 }, message(ByteArray(16) { 7 })))
        waitUntil { requests.size == 1 }
        Thread.sleep(50)
        assertEquals(1, requests.size)
        assertTrue(requests[0].chatId.contentEquals(chatB))
    }

    // --- Подставной Backend ------------------------------------------------

    private class FakeBackend(override val isCompanion: Boolean) : Backend {
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override val events: SharedFlow<AppEvent> = MutableSharedFlow()
        private fun rec(s: String) { calls += s }

        override fun start(scope: CoroutineScope) {}
        override fun close() {}
        override fun requestChats() = rec("requestChats")
        override fun requestAvatar(chatId: ByteArray?) = rec("requestAvatar:${chatId?.toHexString()}")
        override fun setMyAvatar(bytes: ByteArray?) = rec("setMyAvatar")
        override fun addSharedContact(msgId: ByteArray) = rec("addSharedContact")
        override fun shareContact(chatId: ByteArray, whoChatId: ByteArray?) = rec("shareContact")
        override fun createGroup(title: String) = rec("create:$title")
        override fun renameGroup(chatId: ByteArray, title: String) = rec("rename:$title")
        override fun inviteToGroup(chatId: ByteArray, memberChatId: ByteArray) = rec("invite:${memberChatId.toHexString()}")
        override fun evictFromGroup(chatId: ByteArray, memberChatId: ByteArray) = rec("evict:${memberChatId.toHexString()}")
        override fun leaveGroup(chatId: ByteArray) = rec("leave")
        override fun setGroupAvatar(chatId: ByteArray, bytes: ByteArray?) = rec("groupAvatar")
        override fun requestMembers(chatId: ByteArray) = rec("members")
        override fun requestHistory(chatId: ByteArray, limit: UInt) = rec("requestHistory:${chatId.toHexString()}:$limit")
        override fun chatOpened(chatId: ByteArray) = rec("chatOpened")
        override fun sendText(chatId: ByteArray, text: String) = rec("sendText:${chatId.toHexString()}:$text")
        override fun sendFiles(chatId: ByteArray, files: List<File>, text: String) = rec("sendFiles")
        override fun reply(chatId: ByteArray, replyTo: ByteArray, text: String) = rec("reply")
        override fun editMessage(chatId: ByteArray, msgId: ByteArray, text: String) = rec("edit")
        override fun deleteMessages(chatId: ByteArray, msgIds: List<ByteArray>) = rec("delete")
        override fun retractMessages(chatId: ByteArray, msgIds: List<ByteArray>) = rec("retract")
        override fun forwardMessages(chatId: ByteArray, msgIds: List<ByteArray>) = rec("forward")
        override fun setReaction(chatId: ByteArray, msgId: ByteArray, emoji: String?) = rec("setReaction:${msgId.toHexString()}:$emoji")
        override fun markRead(chatId: ByteArray, upTo: ByteArray) = rec("markRead")
        override fun clearChat(chatId: ByteArray) = rec("clearChat")
        override fun acceptFile(chatId: ByteArray, fileId: ByteArray) = rec("accept")
        override fun declineFile(chatId: ByteArray, fileId: ByteArray) = rec("decline")
        override fun requestPreview(fileId: ByteArray) = rec("preview")
        override suspend fun saveFile(file: FfiFile, destination: File) = rec("save")
    }

    private companion object {
        val chatA = ByteArray(16) { 1 }
        val chatB = ByteArray(16) { 2 }
        val ikA = ByteArray(32) { 3 }
        val msg1 = ByteArray(16) { 4 }

        fun contact(chatId: ByteArray, peerIk: ByteArray) = FfiContact(
            peerIk = peerIk, chatId = chatId, fingerprint = "", displayName = "A", localName = null,
            verified = false, seenOnLan = false, hasAvatar = false, onion = null, chatmail = null,
            cardVersion = 0UL, addedMs = 0UL, reachability = FfiReachability(emptyList(), null, null),
            directChannel = null, anomalies = FfiAnomalies(0UL, 0UL, 0UL, 0UL, 0UL), ygg = null, nostrRelays = emptyList(),
        )

        fun group(chatId: ByteArray, joined: Boolean, canManage: Boolean?) =
            Group(chatId, "Группа", joined, canManage, avatarMs = 0UL, createdMs = null)

        fun message(msgId: ByteArray) = FfiMessage(
            msgId = msgId, body = "x", mine = false, wallMs = 0UL, status = FfiDeliveryStatus.DELIVERED,
            editedAtMs = null, forwarded = false, reactions = emptyList(), files = emptyList(), replyTo = null,
            sharedContact = null, author = null, authorIk = null,
        )

        fun waitUntil(timeoutMs: Long = 2000, condition: () -> Boolean) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (!condition()) {
                check(System.currentTimeMillis() < deadline) { "condition not met in $timeoutMs ms" }
                Thread.sleep(5)
            }
        }
    }
}
