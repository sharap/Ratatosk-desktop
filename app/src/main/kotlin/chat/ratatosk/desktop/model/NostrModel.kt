package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.ui.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.ratatosk.core.FfiNostrRelay
import org.ratatosk.core.FfiTransport
import org.ratatosk.core.nostrDirectWarning
import org.ratatosk.core.nostrNoFilesNotice
import org.ratatosk.core.nostrWarning

/** Настройки ступени nostr, как их видит ядро. */
class NostrState(
    val enabled: Boolean,
    /** Мимо Tor. */
    val direct: Boolean,
    /** Откуда читаем — все названные. */
    val relays: List<String>,
    /** Куда собеседник будет класть — первые три, уехавшие в карточку. */
    val advertised: List<String>,
    /** Свой ключ `npub1…`; пусто — ступень не включали. */
    val npub: String,
)

/** Тексты, которые окно обязано показать до переключения (0.3). */
class NostrNotices(val warning: String, val noFiles: String, val directWarning: String)

interface NostrApi {
    /** `null` — не прочитано или режим компаньона. */
    val nostrState: StateFlow<NostrState?>
    /** Свои реле прямо сейчас; `null` — ступень ничего о себе не сказала. */
    val nostrRelaysAlive: StateFlow<List<FfiNostrRelay>?>
    val nostrNotices: NostrNotices

    fun setNostrEnabled(enabled: Boolean)
    fun setNostrDirect(direct: Boolean)
    /** Негодные адреса; если список не пуст — ничего не сохранено. */
    fun setNostrRelays(relays: List<String>): List<String>
    fun watchNostrLive(): AutoCloseable
}

/** Ступень поверх реле nostr (0.3). */
class NostrModel(
    session: SessionContext,
    private val transports: TransportsModel,
) : FeatureModel(session), NostrApi {
    private val _nostrState = MutableStateFlow<NostrState?>(null)
    override val nostrState = _nostrState.asStateFlow()

    private val _nostrRelaysAlive = MutableStateFlow<List<FfiNostrRelay>?>(null)
    override val nostrRelaysAlive = _nostrRelaysAlive.asStateFlow()

    override val nostrNotices: NostrNotices by lazy {
        NostrNotices(
            warning = runCatching { nostrWarning() }.getOrDefault(""),
            noFiles = runCatching { nostrNoFilesNotice() }.getOrDefault(""),
            directWarning = runCatching { nostrDirectWarning() }.getOrDefault(""),
        )
    }

    private val poller = LivePoller(scope, 3_000) {
        _nostrRelaysAlive.value = session.client?.nostrRelaysAlive()
    }

    internal fun refresh() {
        session.clientIo { client ->
            val state = NostrState(
                enabled = client.transportEnabled(FfiTransport.NOSTR),
                direct = client.nostrDirect(),
                relays = client.nostrRelays(),
                advertised = client.nostrAdvertisedRelays(),
                npub = client.nostrNpub(),
            )
            withContext(Dispatchers.Main) { _nostrState.value = state }
        }
    }

    override fun setNostrEnabled(enabled: Boolean) {
        session.clientIo("Failed to toggle Nostr") { client ->
            client.setTransportEnabled(FfiTransport.NOSTR, enabled)
            client.networkChanged()
            refresh()
            transports.refreshTransportStatus()
        }
    }

    override fun setNostrDirect(direct: Boolean) {
        session.clientIo("Failed to change Nostr route") { client ->
            client.setNostrDirect(direct)
            client.networkChanged()
            refresh()
        }
    }

    override fun setNostrRelays(relays: List<String>): List<String> {
        val clean = relays.map { TransportInput.normalizeNostrRelay(it) }.filter { it.isNotEmpty() }.distinct()
        val invalid = clean.filterNot { TransportInput.isValidNostrRelay(it) }
        if (invalid.isNotEmpty()) return invalid
        session.clientIo("Failed to set Nostr relays") { client ->
            client.setNostrRelays(clean)
            client.networkChanged()
            // Негодное ядро отбрасывает молча и оставляет прежний список —
            // сверяем, чтобы сказать об этом, а не показать старое как новое.
            if (client.nostrRelays() != clean) {
                session._error.value = Strings.NOSTR_RELAYS_REJECTED
            }
            refresh()
        }
        return emptyList()
    }

    override fun watchNostrLive(): AutoCloseable = poller.watch()

    override fun reset() {
        poller.stop()
        _nostrState.value = null
        _nostrRelaysAlive.value = null
    }
}
