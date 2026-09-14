package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.core.RatatoskCore
import chat.ratatosk.desktop.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ratatosk.core.FfiCompanionEvent
import org.ratatosk.core.FfiEvent
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
    val onionAddress: StateFlow<String?>
    val cardVersion: StateFlow<ULong?>
    fun refreshTransportStatus()
    fun setTransportEnabled(transport: FfiTransport, enabled: Boolean)
    fun setMailAccount(address: String, password: String, imapHost: String, imapPort: Int, smtpHost: String, smtpPort: Int, viaTor: Boolean)
    fun createMailAccount(url: String, viaTor: Boolean)
    fun clearMailAccount()
    fun setLanEnabled(enabled: Boolean)
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

    private val _onionAddress = MutableStateFlow<String?>(null)
    override val onionAddress = _onionAddress.asStateFlow()

    private val _cardVersion = MutableStateFlow<ULong?>(null)
    override val cardVersion = _cardVersion.asStateFlow()

    private var isAnnouncingTor = false

    override fun refreshTransportStatus() {
        scope.launch(Dispatchers.IO) {
            try {
                val client = RatatoskCore.getClient()
                val en = FfiTransport.entries.associateWith { client.transportEnabled(it) }
                val re = FfiTransport.entries.associateWith { client.transportReady(it) }
                val ts = client.torStatus()
                val ms = client.mailStatus()
                val ma = client.mailAccount()

                withContext(Dispatchers.Main) {
                    _transportsEnabled.value = en
                    _transportsReady.value = re
                    _torStatus.value = ts
                    _mailStatus.value = ms
                    _mailAccount.value = ma
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to refresh transport status", e)
            }
        }
    }

    override fun setTransportEnabled(transport: FfiTransport, enabled: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().setTransportEnabled(transport, enabled)
                refreshTransportStatus()
            } catch (e: Exception) {
                session._error.value = "Failed to toggle transport: ${e.message}"
            }
        }
    }

    override fun setMailAccount(address: String, password: String, imapHost: String, imapPort: Int, smtpHost: String, smtpPort: Int, viaTor: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                val client = RatatoskCore.getClient()
                client.setMailAccount(address.trim(), password, imapHost.trim(), imapPort.toUShort(), smtpHost.trim(), smtpPort.toUShort(), viaTor)
                client.networkChanged()
                refreshTransportStatus()
            } catch (e: Exception) {
                session._error.value = "Failed to set mail account: ${e.message}"
            }
        }
    }

    override fun createMailAccount(url: String, viaTor: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                val client = RatatoskCore.getClient()
                if (viaTor && !client.transportEnabled(FfiTransport.ONION)) {
                    session._error.value = "Tor must be enabled to register via Tor"
                    return@launch
                }
                client.createMailAccount(url.trim(), viaTor)
                client.networkChanged()
                refreshTransportStatus()
            } catch (e: Exception) {
                session._error.value = "Failed to create mail account: ${e.message}"
            }
        }
    }

    override fun clearMailAccount() {
        scope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().clearMailAccount()
                refreshTransportStatus()
            } catch (e: Exception) { }
        }
    }

    override fun setLanEnabled(enabled: Boolean) {
        setTransportEnabled(FfiTransport.LAN, enabled)
    }

    override fun onEvent(event: FfiEvent) {
        when (event) {
            is FfiEvent.TorStatus -> {
                _torStatus.value = FfiTorStatus(event.fraction, event.note, event.blocked)
                if (event.fraction >= 1.0f && _onionAddress.value == null && !isAnnouncingTor) {
                    isAnnouncingTor = true
                    scope.launch(Dispatchers.IO) {
                        try {
                            val client = RatatoskCore.getClient()
                            val card = client.myAddresses()
                            withContext(Dispatchers.Main) {
                                _onionAddress.value = card.onion.takeIf { it.isNotEmpty() }
                                _cardVersion.value = card.version
                            }
                            if (client.transportEnabled(FfiTransport.ONION) && card.onion.isNotEmpty()) {
                                client.announceAddresses(card.onion, null)
                                val updatedCard = client.myAddresses()
                                withContext(Dispatchers.Main) {
                                    _cardVersion.value = updatedCard.version
                                }
                            }
                        } catch (e: Exception) {
                        } finally {
                            isAnnouncingTor = false
                        }
                    }
                }
                refreshTransportStatus()
            }
            is FfiEvent.MailAccountReady -> {
                val mailAddress = event.address
                scope.launch(Dispatchers.IO) {
                    try {
                        val client = RatatoskCore.getClient()
                        client.setTransportEnabled(FfiTransport.MAIL, true)
                        refreshTransportStatus()
                        client.announceAddresses(null, mailAddress)
                    } catch (e: Exception) { }
                }
            }
            is FfiEvent.MailAccountFailed -> {
                session._error.value = "Mail setup failed: ${event.reason}"
                refreshTransportStatus()
            }
            is FfiEvent.MailLoginFailed -> {
                session._error.value = "Mail login failed: ${event.reason}"
                refreshTransportStatus()
            }
            is FfiEvent.CommandRefused -> {
                session._error.value = event.reason
            }
            else -> {}
        }
    }

    override fun onCompanionEvent(event: FfiCompanionEvent) {
        when (event) {
            is FfiCompanionEvent.Linked -> session._isCompanionLinked.value = true
            is FfiCompanionEvent.Unlinked -> session._isCompanionLinked.value = false
            is FfiCompanionEvent.Refused -> session._error.value = event.reason
            else -> {}
        }
    }

    override fun reset() {
        _torStatus.value = null
        _mailStatus.value = null
        _mailAccount.value = null
        _transportsEnabled.value = emptyMap()
        _transportsReady.value = emptyMap()
        // Непустой адрес от прошлого аккаунта не дал бы новому объявить свои.
        _onionAddress.value = null
        _cardVersion.value = null
        isAnnouncingTor = false
    }

    private companion object {
        const val TAG = "TransportsModel"
    }
}
