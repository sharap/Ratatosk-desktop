package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.util.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.ratatosk.core.FfiTransport
import org.ratatosk.core.FfiYggMode
import org.ratatosk.core.FfiYggPeer
import org.ratatosk.core.yggAddress
import org.ratatosk.core.yggNodeNotice
import org.ratatosk.core.yggNodeStopNotice
import org.ratatosk.core.yggWarning
import java.util.concurrent.TimeUnit

/** Настройки меша, как их видит ядро. */
class YggState(
    val mode: FfiYggMode,
    /** Действующий ключ в hex — тот, что уехал собеседникам; `null` — не назван. */
    val keyHex: String?,
    /** Адрес `200::/7`, выведенный ядром из ключа, — для сверки с `yggdrasilctl getSelf`. */
    val address: String?,
    val peers: List<String>,
)

/** Тексты, которые окно обязано показать до переключения (0.2). */
class YggNotices(val warning: String, val nodeNotice: String, val nodeStopNotice: String)

interface YggdrasilApi {
    /** `null` — не прочитано или режим компаньона (меша у второго экрана нет). */
    val yggState: StateFlow<YggState?>
    /** Пиры своего узла прямо сейчас; `null` — узла нет или он поднимается. */
    val yggPeersAlive: StateFlow<List<FfiYggPeer>?>
    val yggNotices: YggNotices

    fun setYggMode(mode: FfiYggMode)
    /** `false` — ключ не 64 hex-символа; ничего не сохранено. */
    fun setYggKey(input: String): Boolean
    /** Негодные строки; если список не пуст — ничего не сохранено. */
    fun setYggPeers(peers: List<String>): List<String>
    /** Ключ локального демона: `yggdrasilctl -json getSelf`. */
    suspend fun fetchYggKeyFromDaemon(): Result<String>
    /** Держит опрос живых пиров, пока не закрыт. */
    fun watchYggLive(): AutoCloseable
}

/**
 * Меш Yggdrasil (0.2): один выбор на три положения — выключен, свой узел,
 * внешний демон. Режим и включённость ступени меняются вместе: «узел
 * встроенный, но выключен» ядро объяснять не предлагает, и мы не будем.
 */
class YggdrasilModel(
    session: SessionContext,
    private val transports: TransportsModel,
) : FeatureModel(session), YggdrasilApi {
    private val _yggState = MutableStateFlow<YggState?>(null)
    override val yggState = _yggState.asStateFlow()

    private val _yggPeersAlive = MutableStateFlow<List<FfiYggPeer>?>(null)
    override val yggPeersAlive = _yggPeersAlive.asStateFlow()

    override val yggNotices: YggNotices by lazy {
        YggNotices(
            warning = runCatching { yggWarning() }.getOrDefault(""),
            nodeNotice = runCatching { yggNodeNotice() }.getOrDefault(""),
            nodeStopNotice = runCatching { yggNodeStopNotice() }.getOrDefault(""),
        )
    }

    private val poller = LivePoller(scope, 3_000) {
        val alive = session.client?.yggPeersAlive()
        _yggPeersAlive.value = alive
    }

    internal fun refresh() {
        session.clientIo { client ->
            val key = client.yggKey().takeIf { it.isNotEmpty() }
            val state = YggState(
                mode = client.yggMode(),
                keyHex = key?.toHexString(),
                address = key?.let { runCatching { yggAddress(it) }.getOrNull() },
                peers = client.yggPeers(),
            )
            withContext(Dispatchers.Main) { _yggState.value = state }
        }
    }

    override fun setYggMode(mode: FfiYggMode) {
        session.clientIo("Failed to set Yggdrasil mode") { client ->
            client.setYggMode(mode)
            client.setTransportEnabled(FfiTransport.YGG, mode != FfiYggMode.OFF)
            client.networkChanged()
            refresh()
            transports.refreshTransportStatus()
        }
    }

    override fun setYggKey(input: String): Boolean {
        val bytes = if (input.isBlank()) ByteArray(0) else TransportInput.parseYggKey(input) ?: return false
        session.clientIo("Failed to set Yggdrasil key") { client ->
            client.setYggKey(bytes)
            client.networkChanged()
            refresh()
            transports.refreshTransportStatus()
        }
        return true
    }

    override fun setYggPeers(peers: List<String>): List<String> {
        val clean = peers.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val invalid = clean.filterNot { TransportInput.isValidYggPeer(it) }
        if (invalid.isNotEmpty()) return invalid
        session.clientIo("Failed to set Yggdrasil peers") { client ->
            client.setYggPeers(clean)
            client.networkChanged()
            refresh()
        }
        return emptyList()
    }

    override suspend fun fetchYggKeyFromDaemon(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val process = ProcessBuilder("yggdrasilctl", "-json", "getSelf")
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                error("yggdrasilctl did not answer")
            }
            val output = process.inputStream.bufferedReader().readText()
            TransportInput.yggKeyFromGetSelf(output)
                ?: error(output.lines().lastOrNull { it.isNotBlank() }?.take(200) ?: "No key in yggdrasilctl output")
        }
    }

    override fun watchYggLive(): AutoCloseable = poller.watch()

    override fun reset() {
        poller.stop()
        _yggState.value = null
        _yggPeersAlive.value = null
    }
}
