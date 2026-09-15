package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NavigationModelTest {
    private val session = SessionContext(
        CoroutineScope(Dispatchers.Unconfined),
        SettingsRepository(File(Files.createTempDirectory("nav").toFile(), "s.preferences_pb")),
    )
    private var visible: ByteArray? = null
    private val nav = NavigationModel(session) { visible = it }
    private val a = ByteArray(16) { 1 }
    private val b = ByteArray(16) { 2 }

    @Test
    fun contactCardFromChatOpensOnTopAndBackReturnsToChat() {
        nav.openChat(a)
        assertTrue(visible.contentEquals(a))
        nav.openContact(a, fromChat = true)
        assertEquals(listOf(Pane.Chat(a), Pane.Contact(a)), nav.navState.value.stack)
        // Карточка поверх чата — чат не виден, уведомления о нём снова уместны.
        assertNull(visible)
        assertTrue(nav.back())
        assertEquals(Pane.Chat(a), nav.navState.value.top)
        assertTrue(visible.contentEquals(a))
    }

    @Test
    fun settingsKeepSectionListAndReplacePane() {
        nav.openChat(a)
        nav.openSettings()
        assertEquals(Section.CHATS, nav.navState.value.section)
        assertEquals(listOf(Pane.Settings), nav.navState.value.stack)
        assertNull(visible)
    }

    @Test
    fun openingChatFromContactsSwitchesSection() {
        nav.openContact(b, fromChat = false)
        assertEquals(Section.CONTACTS, nav.navState.value.section)
        nav.openChat(b)
        assertEquals(NavState(Section.CHATS, listOf(Pane.Chat(b))), nav.navState.value)
    }

    @Test
    fun backOnEmptyStackReportsNothingToDo() {
        assertFalse(nav.back())
        nav.openGroupInfo(a)
        assertTrue(nav.back())
        assertFalse(nav.back())
    }
}
