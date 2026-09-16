package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.backend.Group
import chat.ratatosk.desktop.util.DateUtils
import chat.ratatosk.desktop.util.toHexString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ratatosk.core.FfiAnomalies
import org.ratatosk.core.FfiContact
import org.ratatosk.core.FfiMessage
import org.ratatosk.core.FfiReachability

class ChatListTest {
    private fun contact(id: Int, name: String) = FfiContact(
        peerIk = ByteArray(32) { id.toByte() }, chatId = ByteArray(16) { id.toByte() }, fingerprint = "", displayName = name,
        localName = null, verified = false, seenOnLan = false, seenOnBt = false, hasAvatar = false, onion = null,
        chatmail = null, cardVersion = 0UL, addedMs = 0UL, reachability = FfiReachability(emptyList(), null, null),
        directChannel = null, anomalies = FfiAnomalies(0UL, 0UL, 0UL, 0UL, 0UL), ygg = null, nostrRelays = emptyList(),
    )

    private fun group(id: Int, title: String, joined: Boolean = true) =
        Group(ByteArray(16) { id.toByte() }, title, joined, canManage = true, avatarMs = 0UL, createdMs = null)

    private fun message(wallMs: ULong) = FfiMessage(
        msgId = byteArrayOf(1), body = "x", mine = false, author = null, authorIk = null, wallMs = wallMs, status = null,
        editedAtMs = null, forwarded = false, reactions = emptyList(), files = emptyList(), replyTo = null, sharedContact = null,
    )

    private fun messages(vararg pairs: Pair<ByteArray, ULong>) =
        pairs.associate { (chatId, ms) -> chatId.toHexString() to listOf(message(ms)) }

    @Test
    fun silentContactsAreHiddenUntilOpened() {
        val quiet = contact(1, "Тихий")
        val talkative = contact(2, "Говорун")
        val msgs = messages(talkative.chatId to 100UL)

        val list = buildChatList(listOf(quiet, talkative), emptyList(), msgs, activeChatId = null)
        assertEquals(listOf("Говорун"), list.map { it.title })

        // Открытый чат виден, даже пока в нём нет ни одного сообщения.
        val opened = buildChatList(listOf(quiet, talkative), emptyList(), msgs, activeChatId = quiet.chatId)
        assertEquals(setOf("Тихий", "Говорун"), opened.map { it.title }.toSet())
    }

    @Test
    fun groupsAlwaysShowEvenLeftAndEmpty() {
        val left = group(3, "Покинутая", joined = false)
        val list = buildChatList(emptyList(), listOf(left), emptyMap(), activeChatId = null)
        assertEquals(listOf("Покинутая"), list.map { it.title })
        assertTrue(list.single() is ChatItem.GroupChat)
    }

    @Test
    fun newestFirstThenByTitle() {
        val a = contact(1, "Аня")
        val b = contact(2, "Боря")
        val g = group(3, "Группа")
        val list = buildChatList(
            listOf(a, b), listOf(g),
            messages(a.chatId to 100UL, b.chatId to 300UL),
            activeChatId = null,
        )
        // Группа без сообщений — в конце, но не выпадает из списка.
        assertEquals(listOf("Боря", "Аня", "Группа"), list.map { it.title })
    }

    @Test
    fun searchMatchesTitleIgnoringCase() {
        val a = contact(1, "Аня")
        val list = buildChatList(listOf(a), listOf(group(3, "Работа")), messages(a.chatId to 1UL), null, query = "раб")
        assertEquals(listOf("Работа"), list.map { it.title })
        assertEquals(emptyList<String>(), buildChatList(listOf(a), emptyList(), messages(a.chatId to 1UL), null, query = "зз").map { it.title })
    }

    @Test
    fun contactsAreSortedAndFilteredByEitherName() {
        val named = contact(1, "Zed").copy(localName = "Аня")
        val plain = contact(2, "Боря")
        assertEquals(listOf("Аня", "Боря"), filterContacts(listOf(plain, named)).map { it.localName ?: it.displayName })
        // Найти можно и по тому имени, которым он назвался сам.
        assertEquals(1, filterContacts(listOf(named, plain), "zed").size)
    }

    @Test
    fun chatTimeShowsTimeTodayAndDateLater() {
        val now = 1_700_000_000_000L
        val today = DateUtils.formatChatTime(now.toULong(), now)
        assertTrue("время дня: $today", today.matches(Regex("\\d{2}:\\d{2}")))
        assertEquals("вчера", DateUtils.formatChatTime((now - 24L * 3600 * 1000).toULong(), now))
        assertEquals("", DateUtils.formatChatTime(0UL, now))
        // Прошлый год — полная дата.
        assertTrue(DateUtils.formatChatTime((now - 400L * 24 * 3600 * 1000).toULong(), now).matches(Regex("\\d{2}\\.\\d{2}\\.\\d{4}")))
    }
}
