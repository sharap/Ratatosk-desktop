package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.ui.theme.ChatThemeData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

interface PreferencesApi {
    val chatTheme: StateFlow<ChatThemeData>
    val notificationsShowName: StateFlow<Boolean>
    val notificationsShowText: StateFlow<Boolean>
    fun updateChatTheme(update: (ChatThemeData) -> ChatThemeData)
    fun setNotificationsShowName(show: Boolean)
    fun setNotificationsShowText(show: Boolean)
}

/** Тема и уведомления: только настройки приложения, ядро не участвует. */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesModel(session: SessionContext) : FeatureModel(session), PreferencesApi {
    private val settings = session.settings

    override val chatTheme = settings.chatTheme.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatThemeData()
    )

    override val notificationsShowName = session.activeAccountId.flatMapLatest { id ->
        if (id == null) flowOf(true) else settings.getNotificationsShowName(id)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), true)

    override val notificationsShowText = session.activeAccountId.flatMapLatest { id ->
        if (id == null) flowOf(true) else settings.getNotificationsShowText(id)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), true)

    override fun updateChatTheme(update: (ChatThemeData) -> ChatThemeData) {
        scope.launch {
            val newData = update(chatTheme.value)
            settings.updateChatTheme(newData)
        }
    }

    override fun setNotificationsShowName(show: Boolean) {
        val id = session.activeAccountId.value ?: return
        scope.launch {
            settings.setNotificationsShowName(id, show)
        }
    }

    override fun setNotificationsShowText(show: Boolean) {
        val id = session.activeAccountId.value ?: return
        scope.launch {
            settings.setNotificationsShowText(id, show)
        }
    }

    override fun reset() {}
}
