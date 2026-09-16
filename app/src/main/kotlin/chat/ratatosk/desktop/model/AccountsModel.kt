package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.core.RatatoskCore
import chat.ratatosk.desktop.data.SettingsRepository
import chat.ratatosk.desktop.ui.AccountItem
import chat.ratatosk.desktop.util.AppDirs
import chat.ratatosk.desktop.util.Log
import chat.ratatosk.desktop.util.SecretStore
import chat.ratatosk.desktop.util.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ratatosk.core.FfiAccount
import org.ratatosk.core.honestNotices
import java.io.File

interface AccountsApi {
    val availableAccounts: StateFlow<List<FfiAccount>>
    val companionPairings: StateFlow<List<SettingsRepository.CompanionPairing>>
    val allAccounts: StateFlow<List<AccountItem>>
    val selectedAccount: StateFlow<FfiAccount?>
    val isCreatingNewAccount: StateFlow<Boolean>
    val honestNotices: StateFlow<List<String>>
    val secretStoreAvailable: StateFlow<Boolean?>
    val isFindingHidden: StateFlow<Boolean>
    val accountExists: StateFlow<Boolean>
    fun refreshAccounts()
    fun findHiddenAccount(pin: String, onFound: (ByteArray) -> Unit, onNotFound: () -> Unit)
    fun initialize(label: String, pin: String?, displayName: String, bindToDevice: Boolean = false)
    fun unlock(account: FfiAccount, pin: String?)
    fun logout()
    fun selectAccount(account: FfiAccount?)
    fun setCreatingNewAccount(creating: Boolean)
    fun openCompanionPairing(pairing: SettingsRepository.CompanionPairing)
    fun initializeCompanion(
        uri: String,
        useCache: Boolean,
        deviceId: String? = null,
        /** `null` — порт выберет система: телефон узнаёт его из маяка. */
        port: Int? = null,
        /** Адрес телефона вида `192.168.1.5:41234`; нужен там, где mDNS молчит. */
        peerAddr: String? = null,
        /** Поднять свой onion: работает вне общей сети, но подъём долгий. */
        useTor: Boolean = false,
    )
    fun removeCompanionPairing(deviceId: String)
}

/** Что происходит с сессией после того, как аккаунт открыт или закрыт. */
interface SessionLifecycle {
    fun startClientSession()
    fun startCompanionSession()
    fun endSession()
}

/** Аккаунты и сопряжения: выбор, создание, разблокировка, выход. */
class AccountsModel(
    session: SessionContext,
    private val lifecycle: SessionLifecycle,
) : FeatureModel(session), AccountsApi {
    private val settings = session.settings

    private val _availableAccounts = MutableStateFlow<List<FfiAccount>>(emptyList())
    override val availableAccounts = _availableAccounts.asStateFlow()

    override val companionPairings = settings.companionPairings
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val allAccounts: StateFlow<List<AccountItem>> = combine(_availableAccounts, companionPairings) { local, companion ->
        local.map { AccountItem.Local(it) } + companion.map { AccountItem.Companion(it) }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedAccount = MutableStateFlow<FfiAccount?>(null)
    override val selectedAccount = _selectedAccount.asStateFlow()

    private val _isCreatingNewAccount = MutableStateFlow(false)
    override val isCreatingNewAccount = _isCreatingNewAccount.asStateFlow()

    private val _honestNotices = MutableStateFlow<List<String>>(emptyList())
    override val honestNotices = _honestNotices.asStateFlow()

    /** Есть ли системное хранилище секретов; `null` — ещё проверяем. */
    private val _secretStoreAvailable = MutableStateFlow<Boolean?>(null)
    override val secretStoreAvailable = _secretStoreAvailable.asStateFlow()

    private val _isFindingHidden = MutableStateFlow(false)
    override val isFindingHidden = _isFindingHidden.asStateFlow()

    private val _accountExists = MutableStateFlow(false)
    override val accountExists: StateFlow<Boolean> = _accountExists.asStateFlow()

    init {
        // Хранилище секретов может спросить пароль связки ключей — не на UI-потоке.
        scope.launch(Dispatchers.IO) {
            val store = SecretStore.system
            _secretStoreAvailable.value = store.isAvailable
            val remaining = settings.migratePairingSecrets(store)
            if (remaining > 0) Log.w(TAG, "$remaining companion pairing(s) kept in settings: no secret store")
        }

        scope.launch {
            try {
                // Журнал ядра — до открытия хранилища: подписчик ставится один раз на процесс.
                chat.ratatosk.desktop.util.CoreLog.start(settings.coreLogEnabled.first())
                RatatoskCore.initializeRegistry()
                refreshAccounts()
            } catch (e: Exception) {
                session._error.value = "Failed to open registry: ${e.message}"
            }
        }

        RatatoskCore.safeCall { honestNotices() }
            .onSuccess { _honestNotices.value = it }
            .onFailure { session._error.value = "Core unavailable: ${it.message}" }
    }

    override fun refreshAccounts() {
        _availableAccounts.value = RatatoskCore.listAccounts()
    }

    override fun findHiddenAccount(pin: String, onFound: (ByteArray) -> Unit, onNotFound: () -> Unit) {
        scope.launch(Dispatchers.IO) {
            _isFindingHidden.value = true
            try {
                val id = RatatoskCore.findHidden(pin)
                scope.launch {
                    if (id != null) onFound(id) else onNotFound()
                }
            } catch (e: Exception) {
                scope.launch { onNotFound() }
            } finally {
                _isFindingHidden.value = false
            }
        }
    }

    /**
     * Заводит аккаунт и открывает его.
     *
     * [bindToDevice] — открывать базу ещё и секретом устройства: 32 случайных
     * байта в хранилище ОС. Файл базы без этой машины тогда не открывается
     * ни с PIN, ни без; но и потеря хранилища (переустановка системы, сброс
     * связки ключей) — потеря переписки (FFI.md, «Чем открывается база»).
     * Выбирается один раз, при создании.
     */
    override fun initialize(label: String, pin: String?, displayName: String, bindToDevice: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                val account = RatatoskCore.createAccount(label)
                val idHex = account.id.toHexString()
                val deviceKey = if (bindToDevice) {
                    val secret = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
                    val store = SecretStore.system
                    // Сначала сохранить, потом открыть: база, заведённая ключом,
                    // который не удалось записать, не откроется больше никогда.
                    if (!store.isAvailable || !store.put(SecretStore.DEVICE_KEY_PREFIX + idHex, secret)) {
                        throw IllegalStateException("System secret store is unavailable")
                    }
                    settings.setDeviceBound(idHex, true)
                    secret
                } else null
                RatatoskCore.initialize(account.id, pin, deviceKey, displayName)
                settings.registerAccount(idHex, displayName)
                withContext(Dispatchers.Main) {
                    session._isInitialized.value = true
                    session._activeAccountId.value = idHex
                    lifecycle.startClientSession()
                    refreshAccounts()
                    session._error.value = null
                }
            } catch (e: Exception) {
                session._error.value = "Failed to initialize: ${e.message}"
            }
        }
    }

    override fun unlock(account: FfiAccount, pin: String?) {
        scope.launch(Dispatchers.IO) {
            try {
                val idHex = account.id.toHexString()
                val savedName = settings.getDisplayName(idHex).firstOrNull() ?: account.label
                val deviceKey = if (settings.isDeviceBound(idHex).first()) {
                    SecretStore.system.get(SecretStore.DEVICE_KEY_PREFIX + idHex)
                        ?: throw IllegalStateException(
                            "This account is bound to this computer, but its secret is not available in the system secret store"
                        )
                } else null
                RatatoskCore.initialize(account.id, pin, deviceKey, savedName)
                withContext(Dispatchers.Main) {
                    session._isInitialized.value = true
                    session._activeAccountId.value = idHex
                    lifecycle.startClientSession()
                    session._error.value = null
                }
            } catch (e: Exception) {
                session._error.value = "Failed to unlock: ${e.message}"
            }
        }
    }

    override fun logout() {
        lifecycle.endSession()
    }

    override fun selectAccount(account: FfiAccount?) {
        _selectedAccount.value = account
        if (account == null) {
            _isCreatingNewAccount.value = false
        }
    }

    override fun setCreatingNewAccount(creating: Boolean) {
        _isCreatingNewAccount.value = creating
        if (creating) {
            _selectedAccount.value = null
        }
    }

    /** Открыть сохранённое сопряжение: ссылка — из хранилища секретов. */
    override fun openCompanionPairing(pairing: SettingsRepository.CompanionPairing) {
        scope.launch(Dispatchers.IO) {
            val uri = pairing.legacyInviteUri
                ?: SecretStore.system.get(SecretStore.PAIRING_PREFIX + pairing.deviceId)?.toString(Charsets.UTF_8)
            if (uri == null) {
                session._error.value = "Pairing link is not available in the system secret store"
                return@launch
            }
            initializeCompanion(uri, pairing.useCache, pairing.deviceId, useTor = pairing.useTor)
        }
    }

    override fun initializeCompanion(
        uri: String,
        useCache: Boolean,
        deviceId: String?,
        port: Int?,
        peerAddr: String?,
        useTor: Boolean,
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val baseDir = File(AppDirs.getBaseDir(), "companions")
                baseDir.mkdirs()

                // Кэш сопряжения, которое уже знаем, — в его собственном файле.
                val initialCachePath = if (useCache && deviceId != null) {
                    File(baseDir, "$deviceId.db").absolutePath
                } else null

                // Каталог состояния onion — свой у каждой ссылки и постоянный:
                // адрес выводится из секрета сопряжения, а arti держит там
                // состояние сети; одноразовый каталог означал бы полный
                // bootstrap на каждый запуск (FFI, `tor_dir`).
                val torDir = if (useTor) {
                    File(baseDir, "tor-" + digestOf(uri)).apply { mkdirs() }.absolutePath
                } else null

                val companion = RatatoskCore.initializeCompanion(
                    uri,
                    (port ?: 0).toUShort(),
                    peerAddr?.trim()?.takeIf { it.isNotEmpty() },
                    initialCachePath,
                    torDir,
                )

                val actualDeviceId = companion.deviceId().toHexString()
                val phoneName = companion.phoneName()

                // Запомнить сопряжение можно только вместе со ссылкой, а ссылке
                // место в хранилище ОС. Без него — не запоминаем вовсе (и кэш не
                // пишем: без ссылки открыть его в следующий раз будет нечем).
                val store = SecretStore.system
                if (useCache && store.isAvailable && store.put(SecretStore.PAIRING_PREFIX + actualDeviceId, uri.toByteArray())) {
                    val finalCachePath = File(baseDir, "$actualDeviceId.db").absolutePath
                    if (initialCachePath == null) {
                        companion.setCachePath(finalCachePath)
                    }
                    settings.saveCompanionPairing(
                        SettingsRepository.CompanionPairing(actualDeviceId, phoneName, true, useTor = useTor)
                    )
                }

                withContext(Dispatchers.Main) {
                    session._activeAccountId.value = actualDeviceId
                    session._isInitialized.value = true
                    session._isCompanionMode.value = true
                    lifecycle.startCompanionSession()
                    session._error.value = null
                }
            } catch (e: Exception) {
                session._error.value = "Failed to link companion: ${e.message}"
            }
        }
    }

    /** Короткая метка ссылки — только для имени каталога: одна ссылка, один каталог. */
    private fun digestOf(uri: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(uri.toByteArray())
            .toHexString()
            .take(16)

    override fun removeCompanionPairing(deviceId: String) {
        scope.launch(Dispatchers.IO) {
            settings.removeCompanionPairing(deviceId)
            SecretStore.system.delete(SecretStore.PAIRING_PREFIX + deviceId)
        }
    }

    override fun reset() {
        _selectedAccount.value = null
        _isCreatingNewAccount.value = false
    }

    private companion object {
        const val TAG = "AccountsModel"
    }
}
