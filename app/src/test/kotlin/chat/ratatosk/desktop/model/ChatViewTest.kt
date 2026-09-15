package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.backend.GroupMember
import org.ratatosk.core.FfiAnomalies
import org.ratatosk.core.FfiContact
import org.ratatosk.core.FfiDeliveryStatus
import org.ratatosk.core.FfiMessage
import org.ratatosk.core.FfiReachability
import org.ratatosk.core.FfiReaction
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class ChatViewTest {
    private val aliceIk = ByteArray(32) { 1 }
    private val bobIk = ByteArray(32) { 2 }

    private fun message(id: Int, mine: Boolean = false, authorIk: ByteArray? = null, author: String? = null, reactions: List<FfiReaction> = emptyList()) = FfiMessage(
        msgId = byteArrayOf(id.toByte()), body = "x", mine = mine, author = author, authorIk = authorIk, wallMs = 0uL,
        status = FfiDeliveryStatus.SENT, editedAtMs = null, forwarded = false, reactions = reactions, files = emptyList(),
        replyTo = null, sharedContact = null,
    )

    private val alice = FfiContact(
        peerIk = aliceIk, chatId = aliceIk.copyOf(16), fingerprint = "", displayName = "Alice", localName = "Алиса",
        verified = false, seenOnLan = false, seenOnBt = false, hasAvatar = false, onion = null, chatmail = null,
        cardVersion = 0UL, addedMs = 0UL, reachability = FfiReachability(emptyList(), null, null),
        directChannel = null, anomalies = FfiAnomalies(0UL, 0UL, 0UL, 0UL, 0UL), ygg = null, nostrRelays = emptyList(),
    )

    @Test
    fun groupAuthorResolvesContactThenMemberThenSignature() {
        val bob = GroupMember(bobIk.copyOf(16), "Боб", isMe = false, isOwner = false)
        val list = buildChatMessages(
            listOf(
                message(1, authorIk = aliceIk),
                message(2, authorIk = bobIk),
                message(3, author = "Кэрол"),
                message(4, mine = true),
            ),
            statuses = emptyMap(), isGroup = true, contacts = listOf(alice), members = listOf(bob),
        )
        assertEquals("Алиса", list[0].author?.name)
        assertTrue(list[0].author?.chatId?.contentEquals(alice.chatId) == true)
        assertEquals("Боб", list[1].author?.name)
        assertNull(list[1].author?.chatId)
        assertEquals("Кэрол", list[2].author?.name)
        assertNull(list[3].author)
    }

    @Test
    fun statusFromEventsWinsAndRunsAndReactionsGroup() {
        val m1 = message(1, authorIk = aliceIk, reactions = listOf(FfiReaction("👍", bobIk, false), FfiReaction("👍", aliceIk, true), FfiReaction("❤️", bobIk, false)))
        val m2 = message(2, authorIk = aliceIk)
        val m3 = message(3, mine = true)
        val list = buildChatMessages(
            listOf(m1, m2, m3),
            statuses = mapOf("03" to FfiDeliveryStatus.DELIVERED), isGroup = true, contacts = listOf(alice), members = null,
        )
        assertTrue(list[0].startsRun)
        assertFalse(list[1].startsRun)
        assertTrue(list[2].startsRun)
        assertEquals(FfiDeliveryStatus.SENT, list[0].status)
        assertEquals(FfiDeliveryStatus.DELIVERED, list[2].status)
        val thumbs = list[0].reactions.first { it.emoji == "👍" }
        assertEquals(2, thumbs.count)
        assertTrue(thumbs.mine)
        assertEquals(1, list[0].reactions.first { it.emoji == "❤️" }.count)
    }

    @Test
    fun directChatHasNoAuthors() {
        val list = buildChatMessages(listOf(message(1, authorIk = aliceIk)), emptyMap(), isGroup = false, contacts = listOf(alice), members = null)
        assertNull(list[0].author)
    }
}
