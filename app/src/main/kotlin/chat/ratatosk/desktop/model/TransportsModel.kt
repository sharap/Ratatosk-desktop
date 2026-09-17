package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.backend.AppEvent
import chat.ratatosk.desktop.ui.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.ratatosk.core.FfiMailAccount
import org.ratatosk.core.FfiMailStatus
import org.ratatosk.core.FfiTorStatus
import org.ratatosk.core.FfiTransport

interface TransportsApi {
    val torStatus: StateFlow<FfiTorStatus?>
    val mailStatus: StateFlow<FfiMailStatus?>
    val mailAccount: StateFlow<FfiMailAccount?>
    val transportsEnabled: StateFlow<Map<FfiTransport, Boolean>>
    val transportsReady: StateFlow<Map<FfiTransport, Boolean>>
    val lanEnabled: StateFlow<Boolean>
    val torEnabled: StateFlow<Boolean>
    val mailEnabled: StateFlow<Boolean>
    val btEnabled: StateFlow<Boolean>
    /** Есть ли у ядра чем поднять эфир Bluetooth; нет — раздела не показываем. */
    val btHasRadio: StateFlow<Boolean>
    val onionAddress: StateFlow<String?>
    val cardVersion: StateFlow<ULong?>
    fun refreshTransportStatus()
    fun setTransportEnabled(transport: FfiTransport, enabled: Boolean)
    fun setMailAccount(address: String, password: String, imapHost: String, imapPort: Int, smtpHost: String, smtpPort: Int, viaTor: Boolean)
    fun createMailAccount(url: String, viaTor: Boolean)
    fun clearMailAccount()
    fun setLanEnabled(enabled: Boolean)
    /** Пересмотреть сеть: адреса собеседников могли поменяться. */
    fun networkChanged()
}

/** Ступени доставки: Tor, почта, локальная сеть — и объявление своих адресов. */
class TransportsModel(session: SessionContext) : FeatureModel(session), TransportsApi {
    private val _torStatus = MutableStateFlow<FfiTorStatus?>(null)
    override val torStatus = _torStatus.asStateFlow()

    private val _mailStatus = MutableStateFlow<FfiMailStatus?>(null)
    override val mailStatus = _mailStatus.asStateFlow()

    private val _mailAccount = MutableStateFlow<FfiMailAccount?>(null)
    override val mailAccount = _mailAccount.asStateFlow()

    private val _transportsEnabled = MutableStateFlow<Map<FfiTransport, Boolean>>(emptyMap())
    override val transportsEnabled = _transportsEnabled.asStateFlow()

    private val _transportsReady = MutableStateFlow<Map<FfiTransport, Boolean>>(emptyMap())
    override val transportsReady = _transportsReady.asStateFlow()

    override val lanEnabled = transportsEnabled.map { it[FfiTransport.LAN] ?: false }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), false)

    override val torEnabled = transportsEnabled.map { it[FfiTransport.ONION] ?: false }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), false)

    override val mailEnabled = transportsEnabled.map { it[FfiTransport.MAIL] ?: false }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), false)

    override val btEnabled = transportsEnabled.map { it[FfiTransport.BT] ?: false }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), false)

    private val _btHasRadio = MutableStateFlow(false)
    override val btHasRadio = _btHasRadio.asStateFlow()

    private val _onionAddress = MutableStateFlow<String?>(null)
    override val onionAddress = _onionAddress.asStateFlow()

    private val _cardVersion = MutableStateFlow<ULong?>(null)
    override val cardVersion = _cardVersion.asStateFlow()

    private var isAnnouncingTor = false

    override fun refreshTransportStatus() {
        session.clientIo { client ->
            val en = FfiTransport.entries.associateWith { client.transportEnabled(it) }
            val re = FfiTransport.entries.associateWith { client.transportReady(it) }
            val ts = client.torStatus()
            val ms = client.mailStatus()
            val ma = client.mailAccount()
            // На Linux со сборкой `bt` радио у ядра своё (BlueZ), вручать его не нужно.
            val radio = runCatching { client.bluetooth().use { it.hasRadio() } }.getOrDefault(false)

            withContext(Dispatchers.Main) {
                _transportsEnabled.value = en
                _transportsReady.value = re
                _torStatus.value = ts
                _mailStatus.value = ms
                _mailAccount.value = ma
                _btHasRadio.value = radio
            }
        }
    }

    override fun networkChanged() {
        session.clientIo("Failed to refresh network") { it.networkChanged() }
    }

    override fun setTransportEnabled(transport: FfiTransport, enabled: Boolean) {
        session.clientIo("Failed to toggle transport") { client ->
            client.setTransportEnabled(transport, enabled)
            refreshTransportStatus()
        }
    }

    override fun setMailAccount(address: String, password: String, imapHost: String, imapPort: Int, smtpHost: String, smtpPort: Int, viaTor: Boolean) {
        session.clientIo("Failed to set mail account") { client ->
            client.setMailAccount(address.trim(), password, imapHost.trim(), imapPort.toUShort(), smtpHost.trim(), smtpPort.toUShort(), viaTor)
            client.networkChanged()
            refreshTransportStatus()
        }
    }

    override fun createMailAccount(url: String, viaTor: Boolean) {
        session.clientIo("Failed to create mail account") { client ->
            if (viaTor && !client.transportEnabled(FfiTransport.ONION)) {
                session._error.value = Strings.MAIL_NEEDS_TOR
                return@clientIo
            }
            client.createMailAccount(url.trim(), viaTor)
            client.networkChanged()
            refreshTransportStatus()
        }
    }

    override fun clearMailAccount() {
        session.clientIo { client ->
            client.clearMailAccount()
            refreshTransportStatus()
        }
    }

    override fun setLanEnabled(enabled: Boolean) {
        setTransportEnabled(FfiTransport.LAN, enabled)
    }

    override fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.TorStatus -> {
                _torStatus.value = FfiTorStatus(event.fraction, event.note, event.blocked)
                if (event.fraction >= 1.0f && _onionAddress.value == null && !isAnnouncingTor) {
                    isAnnouncingTor = true
                    session.clientIo { client ->
                        try {
                            val card = client.myAddresses()
                            withContext(Dispatchers.Main) {
                                _onionAddress.value = card.onion.takeIf { it.isNotEmpty() }
                                _cardVersion.value = card.version
                            }
                            if (client.transportEnabled(FfiTransport.ONION) && card.onion.isNotEmpty()) {
                                // null — «почтовый адрес не трогать».
                                client.announceAddresses(card.onion, null)
                                val updatedCard = client.myAddresses()
                                withContext(Dispatchers.Main) {
                                    _cardVersion.value = updatedCard.version
                                }
                            }
                        } finally {
                            isAnnouncingTor = false
                        }
                    }
                }
                refreshTransportStatus()
            }
            is AppEvent.MailAccountReady -> {
                session.clientIo { client ->
                    client.setTransportEnabled(FfiTransport.MAIL, true)
                    refreshTransportStatus()
                    client.announceAddresses(null, event.address)
                }
            }
            is AppEvent.MailAccountFailed -> {
                session._error.value = event.reason
                refreshTransportStatus()
            }
            is AppEvent.MailLoginFailed -> {
                session._error.value = event.reason
                refreshTransportStatus()
            }
            else -> {}
        }
    }

    override fun reset() {
        _torStatus.value = null
        _mailStatus.value = null
        _mailAccount.value = null
        _transportsEnabled.value = emptyMap()
        _transportsReady.value = emptyMap()
        _btHasRadio.value = false
        // Непустой адрес от прошлого аккаунта не дал бы новому объявить свои.
        _onionAddress.value = null
        _cardVersion.value = null
        isAnnouncingTor = false
    }
}
