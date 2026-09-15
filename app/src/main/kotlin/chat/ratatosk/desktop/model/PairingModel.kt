package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.backend.AppEvent
import chat.ratatosk.desktop.util.ClipboardUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.ratatosk.core.FfiPairedDevice

/**
 * Идущее сопряжение. [uri] живёт **только здесь и только до закрытия окна**:
 * ядро его не хранит и второй раз не покажет, в журнал он не пишется.
 */
data class PendingPairing(
    val label: String,
    val deviceId: ByteArray? = null,
    val uri: String? = null,
    val connected: Boolean = false,
)

interface PairingApi {
    val pairedDevices: StateFlow<List<FfiPairedDevice>>
    val pendingPairing: StateFlow<PendingPairing?>

    fun refreshDevices()
    /** Завести сопряжение; ссылка придёт в [pendingPairing]. */
    fun startPairing(label: String)
    fun copyPairingUri()
    /**
     * Закрыть окно сопряжения. [revoke] — отменить его: запись без
     * подключившегося устройства иначе остаётся в списке мёртвой.
     */
    fun finishPairing(revoke: Boolean)
    fun revokeDevice(deviceId: ByteArray)
}

/** Сопряжение второго экрана со стороны полного клиента (§13.4). */
class PairingModel(session: SessionContext) : FeatureModel(session), PairingApi {
    private val _pairedDevices = MutableStateFlow<List<FfiPairedDevice>>(emptyList())
    override val pairedDevices = _pairedDevices.asStateFlow()

    private val _pendingPairing = MutableStateFlow<PendingPairing?>(null)
    override val pendingPairing = _pendingPairing.asStateFlow()

    /** Окно закрыли с отменой раньше, чем пришла ссылка: отозвать, когда придёт. */
    @Volatile
    private var revokeWhenReady = false

    override fun refreshDevices() {
        session.clientIo { client ->
            val devices = client.devices()
            withContext(Dispatchers.Main) { _pairedDevices.value = devices }
        }
    }

    override fun startPairing(label: String) {
        // Сопрягает только полный клиент; у второго экрана своих сопряжений нет.
        if (_pendingPairing.value != null || session.client == null) return
        revokeWhenReady = false
        _pendingPairing.value = PendingPairing(label = label.trim())
        session.clientIo("Failed to start pairing") { client ->
            try {
                client.pairDevice(label.trim())
            } catch (e: Exception) {
                _pendingPairing.value = null
                throw e
            }
        }
    }

    override fun copyPairingUri() {
        _pendingPairing.value?.uri?.let { ClipboardUtils.copySecret(it) }
    }

    override fun finishPairing(revoke: Boolean) {
        val pending = _pendingPairing.value ?: return
        _pendingPairing.value = null
        pending.uri?.let { ClipboardUtils.clearIfHolds(it) }
        if (revoke && !pending.connected) {
            val id = pending.deviceId
            if (id != null) revokeDevice(id) else revokeWhenReady = true
        }
        refreshDevices()
    }

    override fun revokeDevice(deviceId: ByteArray) {
        session.clientIo("Failed to revoke pairing") { client ->
            client.revokePairing(deviceId)
            refreshDevices()
        }
    }

    override fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.PairingReady -> {
                val pending = _pendingPairing.value
                when {
                    pending != null && pending.uri == null ->
                        _pendingPairing.value = pending.copy(deviceId = event.deviceId, uri = event.uri)
                    revokeWhenReady -> {
                        revokeWhenReady = false
                        revokeDevice(event.deviceId)
                    }
                }
                refreshDevices()
            }
            is AppEvent.DeviceLink -> {
                _pendingPairing.update { pending ->
                    if (pending?.deviceId?.contentEquals(event.deviceId) == true && event.connected) {
                        // Подключилось — ссылка своё отработала, в буфере ей больше не место.
                        pending.uri?.let { ClipboardUtils.clearIfHolds(it) }
                        pending.copy(connected = true)
                    } else pending
                }
                refreshDevices()
            }
            is AppEvent.PairingRevoked -> refreshDevices()
            else -> {}
        }
    }

    override fun reset() {
        _pendingPairing.value?.uri?.let { ClipboardUtils.clearIfHolds(it) }
        _pendingPairing.value = null
        _pairedDevices.value = emptyList()
        revokeWhenReady = false
    }
}
