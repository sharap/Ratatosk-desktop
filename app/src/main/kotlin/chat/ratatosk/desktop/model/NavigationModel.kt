package chat.ratatosk.desktop.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Что за список слева. */
enum class Section { CHATS, CONTACTS }

/** Что открыто справа. Одно место правды для всей раскладки. */
sealed interface Pane {
    data class Chat(val chatId: ByteArray) : Pane {
        override fun equals(other: Any?) = other is Chat && other.chatId.contentEquals(chatId)
        override fun hashCode() = chatId.contentHashCode()
    }
    data class Contact(val chatId: ByteArray) : Pane {
        override fun equals(other: Any?) = other is Contact && other.chatId.contentEquals(chatId)
        override fun hashCode() = chatId.contentHashCode()
    }
    data class GroupInfo(val chatId: ByteArray) : Pane {
        override fun equals(other: Any?) = other is GroupInfo && other.chatId.contentEquals(chatId)
        override fun hashCode() = chatId.contentHashCode()
    }
    data object Settings : Pane
    data object Profile : Pane
}

/**
 * Раздел слева и стопка открытого справа: верх стопки виден, «назад»
 * снимает его. Раскладка рисуется прямо по этому состоянию — ни навигатор
 * панелей, ни пейджер вкладок своего состояния не держат (прежде их было
 * три, и расходились они в заметные человеку ошибки).
 */
data class NavState(val section: Section = Section.CHATS, val stack: List<Pane> = emptyList()) {
    val top: Pane? get() = stack.lastOrNull()
}

interface NavigationApi {
    val navState: StateFlow<NavState>
    fun selectSection(section: Section)
    /** Чат — с чистого листа: слева «Чаты», справа только он. */
    fun openChat(chatId: ByteArray)
    /** Карточка контакта: из списка контактов — с чистого листа, из чата — поверх него. */
    fun openContact(chatId: ByteArray, fromChat: Boolean)
    fun openGroupInfo(chatId: ByteArray)
    fun openSettings()
    fun openProfile()
    /** `false` — снимать нечего. */
    fun back(): Boolean
}

class NavigationModel(
    session: SessionContext,
    /** Какой чат сейчас виден человеку — для непрочитанного и уведомлений. */
    private val onVisibleChat: (ByteArray?) -> Unit,
) : FeatureModel(session), NavigationApi {
    private val _navState = MutableStateFlow(NavState())
    override val navState = _navState.asStateFlow()

    private fun set(transform: (NavState) -> NavState) {
        _navState.update(transform)
        onVisibleChat((_navState.value.top as? Pane.Chat)?.chatId)
    }

    override fun selectSection(section: Section) = set { it.copy(section = section) }

    override fun openChat(chatId: ByteArray) = set { NavState(Section.CHATS, listOf(Pane.Chat(chatId))) }

    override fun openContact(chatId: ByteArray, fromChat: Boolean) = set {
        if (fromChat) it.copy(stack = it.stack + Pane.Contact(chatId))
        else NavState(Section.CONTACTS, listOf(Pane.Contact(chatId)))
    }

    override fun openGroupInfo(chatId: ByteArray) = set { it.copy(stack = it.stack + Pane.GroupInfo(chatId)) }

    // Настройки и профиль заменяют правую часть, список слева не трогают.
    override fun openSettings() = set { it.copy(stack = listOf(Pane.Settings)) }
    override fun openProfile() = set { it.copy(stack = listOf(Pane.Profile)) }

    override fun back(): Boolean {
        if (_navState.value.stack.isEmpty()) return false
        set { it.copy(stack = it.stack.dropLast(1)) }
        return true
    }

    override fun reset() {
        _navState.value = NavState()
    }
}
