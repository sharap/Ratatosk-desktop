package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.core.RatatoskCore
import chat.ratatosk.desktop.data.SettingsRepository
import chat.ratatosk.desktop.util.AppDirs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Все модели приложения и то, что их связывает: запуск сессии после
 * открытия аккаунта, доставка событий ядра каждой модели и выход.
 */
class AppModels(settings: SettingsRepository, scope: CoroutineScope) : SessionLifecycle {
    val session = SessionContext(scope, settings)
    val preferences = PreferencesModel(session)
    val contacts = ContactsModel(session)
    val chats = ChatsModel(session, contacts)
    val files = FilesModel(session, chats)
    val transports = TransportsModel(session)
    val accounts = AccountsModel(session, lifecycle = this)

    private val features: List<FeatureModel> = listOf(preferences, contacts, chats, files, transports, accounts)

    private var eventsJob: Job? = null
    private var companionEventsJob: Job? = null

    init {
        // Копии вложений от прошлого запуска, если он завершился не выходом.
        scope.launch(Dispatchers.IO) { AppDirs.clearMediaCache() }

        scope.launch {
            while (true) {
                if (!session.isInitialized.value && RatatoskCore.isInitialized()) {
                    session._isInitialized.value = true
                    session._activeAccountId.value = RatatoskCore.getActiveAccountId()
                    startClientSession()
                }
                delay(1000)
            }
        }

        if (RatatoskCore.isInitialized()) {
            if (RatatoskCore.isCompanionMode()) startCompanionSession() else startClientSession()
        }

        scope.launch {
            while (true) {
                if (RatatoskCore.isInitialized()) {
                    if (RatatoskCore.isCompanionMode()) {
                        try {
                            RatatoskCore.getCompanion().chats()
                        } catch (e: Exception) {}
                    } else {
                        contacts.refreshContacts()
                        transports.refreshTransportStatus()
                    }
                }
                delay(30000)
            }
        }
    }

    override fun startClientSession() {
        val scope = session.scope
        scope.launch(Dispatchers.IO) {
            try {
                contacts.loadIdentity()

                withContext(Dispatchers.Main) {
                    if (eventsJob == null) {
                        eventsJob = RatatoskCore.events
                            .onEach { event ->
                                session.recordEvent(event)
                                features.forEach { it.onEvent(event) }
                            }
                            .launchIn(scope)
                    }
                }

                launch(Dispatchers.IO) { contacts.loadMyAvatar() }
                launch(Dispatchers.IO) { files.loadAutoAcceptLimit() }
                launch(Dispatchers.IO) {
                    try {
                        val currentContacts = contacts.loadContacts()
                        transports.refreshTransportStatus()
                        currentContacts.forEach { chats.loadMessages(it.chatId) }
                    } catch (e: Exception) { }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    session._error.value = "Failed to load identity: ${e.message}"
                }
            }
        }
    }

    override fun startCompanionSession() {
        val scope = session.scope
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                if (companionEventsJob == null) {
                    companionEventsJob = RatatoskCore.companionEvents
                        .onEach { event -> features.forEach { it.onCompanionEvent(event) } }
                        .launchIn(scope)
                }
            }
            try {
                RatatoskCore.getCompanion().chats()
            } catch (e: Exception) {}
        }
    }

    override fun endSession() {
        // Сначала отписаться, потом закрыть: иначе событие, пришедшее между
        // закрытием и очисткой, снова наполнит состояние прошлого аккаунта.
        eventsJob?.cancel()
        eventsJob = null
        companionEventsJob?.cancel()
        companionEventsJob = null

        features.forEach { it.reset() }
        RatatoskCore.logout()
        session.reset()

        // Расшифрованные копии вложений лежат открытым текстом —
        // переживать выход из аккаунта им незачем.
        session.scope.launch(Dispatchers.IO) { AppDirs.clearMediaCache() }
    }
}
