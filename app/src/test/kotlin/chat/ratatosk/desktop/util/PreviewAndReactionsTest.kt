package chat.ratatosk.desktop.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ratatosk.core.FfiFile
import org.ratatosk.core.FfiMessage
import org.ratatosk.core.FfiReaction

class PreviewAndReactionsTest {
    private val me = byteArrayOf(1)
    private val other = byteArrayOf(2)

    private fun message(mine: Boolean = false, body: String = "", reactions: List<FfiReaction> = emptyList(), files: List<String> = emptyList()) = FfiMessage(
        msgId = byteArrayOf(9), body = body, mine = mine, author = null, authorIk = null, wallMs = 0uL, status = null,
        editedAtMs = null, forwarded = false, reactions = reactions,
        files = files.map { FfiFile(fileId = ByteArray(16), name = it, sizeBytes = 1uL, incoming = true, accepted = true, complete = true, receivedChunks = 1uL, chunkTotal = 1uL, hasPreview = false, chunkBytes = 0u) },
        replyTo = null, sharedContact = null,
    )

    @Test
    fun reactionIsAnnouncedOnlyForMyMessageAndForeignAuthor() {
        // Полный набор случаев из android-версии: один и тот же помощник
        // в двух приложениях обязан решать одинаково.
        val other = byteArrayOf(2, 2, 2)
        val third = byteArrayOf(3, 3, 3)
        val thumbs = FfiReaction("👍", other, false)

        assertEquals("👍", reactionToAnnounce(message(mine = true, reactions = listOf(thumbs)), other)?.emoji)
        // Реакцию сняли — сообщать нечего.
        assertNull(reactionToAnnounce(message(mine = true), other))
        // На чужое сообщение — не наше дело.
        assertNull(reactionToAnnounce(message(mine = false, reactions = listOf(thumbs)), other))
        // Своя реакция на своё сообщение.
        assertNull(reactionToAnnounce(message(mine = true, reactions = listOf(FfiReaction("👍", other, true))), other))

        val both = message(mine = true, reactions = listOf(thumbs, FfiReaction("🔥", third, false)))
        assertEquals("👍", reactionToAnnounce(both, other)?.emoji)
        assertEquals("🔥", reactionToAnnounce(both, third)?.emoji)
    }

    @Test
    fun attachmentCountReadsNaturallyInAnyLanguage() {
        // Формы числа зависят от языка, поэтому проверяем не буквы, а смысл:
        // число на месте, а «одно» и «много» звучат по-разному.
        val one = MessagePreview.attachments(1)
        val two = MessagePreview.attachments(2)
        val five = MessagePreview.attachments(5)
        assertTrue(one.contains("1"))
        assertTrue(two.contains("2"))
        assertTrue(five.contains("5"))
        assertNotEquals(one.replace("1", "#"), two.replace("2", "#"))
    }

    @Test
    fun markupIsStrippedToOneLine() {
        assertEquals("жирный код ссылка", MarkdownUtils.toPlainText("**жирный** `код`\n\n[ссылка](https://x.y)", "спойлер"))
        assertEquals("до [спойлер] после", MarkdownUtils.toPlainText("до ||тайна|| после", "спойлер"))
        assertEquals("", MarkdownUtils.toPlainText("   ", "спойлер"))
    }

    @Test
    fun previewNamesAttachmentsWhenNoText() {
        assertEquals("[фото]", MessagePreview.of(message(files = listOf("a.JPG"))))
        assertEquals("[файл]", MessagePreview.of(message(files = listOf("a.pdf"))))
        assertEquals("[3 вложения]", MessagePreview.of(message(files = listOf("a", "b", "c"))))
        assertEquals("[5 вложений]", MessagePreview.attachments(5))
        assertEquals("[21 вложение]", MessagePreview.attachments(21))
        assertEquals("[11 вложений]", MessagePreview.attachments(11))
        assertEquals("[сообщение]", MessagePreview.of(message()))
        assertEquals("привет", MessagePreview.of(message(body = "_привет_", files = listOf("a.jpg"))))
    }

    @Test
    fun clientReactionDecisions() {
        val foreign = FfiReaction("👍", other, false)
        assertEquals("👍", reactionToAnnounce(message(mine = true, reactions = listOf(foreign)), other)?.emoji)
        assertNull(reactionToAnnounce(message(mine = true), other))
        assertNull(reactionToAnnounce(message(mine = false, reactions = listOf(foreign)), other))
        assertNull(reactionToAnnounce(message(mine = true, reactions = listOf(FfiReaction("👍", me, true))), me))
    }

    @Test
    fun companionReactionDiff() {
        assertEquals(emptyList<String>(), newForeignReactions(null, listOf("👍" to false)))
        assertEquals(listOf("👍"), newForeignReactions(emptyList(), listOf("👍" to false)))
        assertEquals(emptyList<String>(), newForeignReactions(listOf("👍" to false), listOf("👍" to false)))
        assertEquals(listOf("👍"), newForeignReactions(listOf("👍" to false), listOf("👍" to false, "👍" to false)))
        // Своя и снятая — не новость.
        assertEquals(emptyList<String>(), newForeignReactions(emptyList(), listOf("❤️" to true)))
        assertEquals(emptyList<String>(), newForeignReactions(listOf("👍" to false), emptyList()))
    }
}
