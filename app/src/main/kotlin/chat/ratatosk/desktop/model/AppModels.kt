package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.backend.AppEvent
import chat.ratatosk.desktop.backend.Backend
import chat.ratatosk.desktop.backend.ClientBackend
import chat.ratatosk.desktop.backend.CompanionBackend
import chat.ratatosk.desktop.core.RatatoskCore
import chat.ratatosk.desktop.data.SettingsRepository
import chat.ratatosk.desktop.util.AppDirs
import chat.ratatosk.desktop.util.Log
import chat.ratatosk.desktop.util.NetworkWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Все модели приложения и то, что их связывает: открытие сессии поверх
 * [Backend], доставка его событий каждой модели и выход.
 */
class AppModels(settings: SettingsRepository, scope: CoroutineScope) : SessionLifecycle {
    val session = SessionContext(scope, settings)
    val preferences = PreferencesModel(session)
    val contacts = ContactsModel(session)
    val chats = ChatsModel(session)
    val navigation = NavigationModel(session, onVisibleChat = { chats.setActiveChat(it) })
    val files = FilesModel(session)
    val transports = TransportsModel(session)
    val groups = GroupsModel(session)
    val yggdrasil = YggdrasilModel(session, transports)
    val nostr = NostrModel(session, transports)
    val accounts = AccountsModel(session, lifecycle = this)
    val pairing = PairingModel(session)
    val notifications = NotificationsModel(session, contacts, groups, chats)
    val backup = BackupModel(session, onAccountsChanged = { accounts.refreshAccounts() })
    val channels = ChannelsModel(session)

    private val features: List<FeatureModel> = listOf(preferences, contacts, chats, navigation, files, transports, groups, yggdrasil, nostr, accounts, backup, pairing, notifications, channels)

    private var eventsJob: Job? = null
    private var networkJob: Job? = null

    init {
        // Копии вложений от прошлого запуска, если он завершился не выходом.
        scope.launch(Dispatchers.IO) { AppDirs.clearMediaCache() }

        if (RatatoskCore.isInitialized()) {
            if (RatatoskCore.isCompanionMode()) startCompanionSession() else startClientSession()
        }

        // Страховка на случай пропущенного события: список и транспорты
        // перечитываются раз в полминуты.
        scope.launch {
            while (true) {
                delay(30_000)
                session.backend?.let {
                    contacts.refreshContacts()
                    transports.refreshTransportStatus()
                }
            }
        }
    }

    override fun startClientSession() = open(ClientBackend(RatatoskCore.getClient()))

    override fun startCompanionSession() = open(CompanionBackend(RatatoskCore.getCompanion()))

    private fun open(backend: Backend) {
        session.backend?.close()
        eventsJob?.cancel()

        session.backend = backend
        // Подписка — до start(): ответы на первые запросы иначе ушли бы в пустоту.
        eventsJob = session.scope.launch(Dispatchers.Main, start = CoroutineStart.UNDISPATCHED) {
            backend.events.collect { event -> dispatch(event) }
        }
        backend.start(session.scope)
        // Порт и ключ известны сразу после открытия: телефону их вводят руками,
        // когда mDNS молчит.
        session._companionEndpoint.value = runCatching { backend.companionEndpoint() }.getOrNull()

        // Переезд в другую сеть ядро само не заметит: адреса сменились, а маяк
        // и соединения привязаны к прежним.
        networkJob = NetworkWatcher.start(session.scope) { transports.networkChanged() }

        contacts.onSessionStarted()
        files.onSessionStarted()
        transports.refreshTransportStatus()
        yggdrasil.refresh()
        nostr.refresh()
        pairing.refreshDevices()
    }

    private fun dispatch(event: AppEvent) {
        when (event) {
            is AppEvent.Linked -> session._isCompanionLinked.value = true
            is AppEvent.Unlinked -> session._isCompanionLinked.value = false
            is AppEvent.Refused -> session._error.value = event.reason
            else -> {}
        }
        features.forEach { model ->
            try {
                model.onEvent(event)
            } catch (e: Exception) {
                Log.e("AppModels", "Model failed on ${event::class.simpleName}", e)
            }
        }
    }

    override fun endSession() {
        // Сначала отписаться, потом закрыть: иначе событие, пришедшее между
        // закрытием и очисткой, снова наполнит состояние прошлого аккаунта.
        eventsJob?.cancel()
        eventsJob = null
        networkJob?.cancel()
        networkJob = null
        session.backend?.close()

        features.forEach { it.reset() }
        RatatoskCore.logout()
        session.reset()

        // Расшифрованные копии вложений лежат открытым текстом —
        // переживать выход из аккаунта им незачем.
        session.scope.launch(Dispatchers.IO) { AppDirs.clearMediaCache() }
    }
}
