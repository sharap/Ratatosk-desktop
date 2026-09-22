package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.backend.AppEvent
import chat.ratatosk.desktop.backend.Channel
import chat.ratatosk.desktop.backend.ChannelAdmit
import chat.ratatosk.desktop.backend.ChannelGrant
import chat.ratatosk.desktop.backend.ChannelRequest
import chat.ratatosk.desktop.backend.ChannelSeed
import chat.ratatosk.desktop.backend.Group
import chat.ratatosk.desktop.util.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.ratatosk.core.FfiChannelRights
import org.ratatosk.core.FfiSeeding
import org.ratatosk.core.FfiSharingLevel
import org.ratatosk.core.admitterGrantNotice
import org.ratatosk.core.keyRotationNotice
import org.ratatosk.core.openChannelNotice
import org.ratatosk.core.privateChannelNotice
import org.ratatosk.core.seedingNotice
import org.ratatosk.core.sharingLevelNotice
import org.ratatosk.core.sharingNotice

/**
 * Обязательные тексты §15: каждый показывается **до** действия и своему
 * человеку. Константы биндингов, а не сведения о сессии.
 */
class ChannelNotices(
    /** До заведения открытого канала, до подписки на него и до показа ссылки. */
    val open: String,
    /** Только подписывающемуся, до подписки. */
    val private: String,
    /** При выдаче права «впускать» (§6.5), а не при снятии. */
    val admitterGrant: String,
    /** До поворота: кнопка называется последствием (§6.4). */
    val keyRotation: String,
    /** До показа ссылки: в неё попадает наш адрес (§10.2). */
    val sharing: String,
    /** До объявления себя раздающим (§7.5.1). */
    val seeding: String,
    /** До сужения круга отдачи (§12): платит не только тот, кто настраивал. */
    val sharingLevel: String,
)

/** Почему в канале закрыто поле ввода. */
enum class ChannelInput {
    /** Не канал или писать можно. */
    ALLOWED,

    /** Права писать нет (§6.2): состоять в канале и мочь говорить — разное. */
    NO_RIGHT,

    /** Ждём, пока владелец впустит (§10.4). Отказа как ответа не бывает. */
    AWAITING,

    /** Читать пока нечем: ни одного поколения ключа. */
    NOT_READABLE,
}

/**
 * Что показывать вместо поля ввода.
 *
 * Спрашивается право, а не состав: в канале состоять и мочь говорить —
 * разные вещи (§6.2). Право уже посчитано ядром с учётом срока и правила
 * «владельцу всё».
 */
fun channelInput(group: Group?): ChannelInput {
    val channel = group?.channel ?: return ChannelInput.ALLOWED
    return when {
        channel.awaiting -> ChannelInput.AWAITING
        !channel.readable -> ChannelInput.NOT_READABLE
        channel.canWrite -> ChannelInput.ALLOWED
        else -> ChannelInput.NO_RIGHT
    }
}

interface ChannelsApi {
    val channelNotices: ChannelNotices

    /** Заявки на впуск, по каналам в hex; §10.4 обещает, что они ждут. */
    val channelRequests: StateFlow<Map<String, List<ChannelRequest>>>
    /** Кого впустили: видно владельцу. */
    val channelAdmits: StateFlow<Map<String, List<ChannelAdmit>>>
    /** Кому что выдано (§6.2, §6.3). */
    val channelGrants: StateFlow<Map<String, List<ChannelGrant>>>
    /** Как мы раздаём канал (§7.5.1). */
    val seedingMode: StateFlow<Map<String, FfiSeeding>>
    /** Кто ещё вызвался раздавать. */
    val channelSeeds: StateFlow<Map<String, List<ChannelSeed>>>
    /** Кому отдаём блоки этого канала (§12). */
    val sharingLevel: StateFlow<Map<String, FfiSharingLevel>>

    /** Порода задаётся один раз: «открытый» и «по приглашению» — разные обещания. */
    fun createChannel(title: String, open: Boolean)
    /** Подписка по ссылке `ratatosk:v0:channel:…` (§10.3, §10.4). */
    fun subscribeToChannel(uri: String)
    /** Отписка (§10.6): уносит и архив — сказать об этом надо **до**. */
    fun unsubscribeFromChannel(chatId: ByteArray)
    /** Ссылка — запросом и в момент показа: иначе устаревала бы молча (§10.2). */
    fun channelLink(chatId: ByteArray, onResult: (Result<String>) -> Unit)

    /** Впускает человека (§6.5, §10.4); впускаемый обязан быть контактом. */
    fun admitToChannel(chatId: ByteArray, peerIk: ByteArray)
    /** Выдаёт или снимает право; снятие — выдача с пустым набором. */
    fun setChannelRight(
        chatId: ByteArray,
        peerIk: ByteArray,
        write: Boolean,
        admit: Boolean,
        evict: Boolean,
        edit: Boolean,
        untilMs: ULong,
    )
    fun rotateChannelKey(chatId: ByteArray)
    fun setChannelPow(chatId: ByteArray, bits: UInt)
    fun setSeeding(chatId: ByteArray, mode: FfiSeeding)
    fun setSharingLevel(chatId: ByteArray, level: FfiSharingLevel)

    /** Перечитать всё канальное: заявки, впущенных, выдачи, раздачу. */
    fun loadChannel(chatId: ByteArray)
}

/**
 * Каналы (фаза 2, §6, §10).
 *
 * Состояния канала здесь нет: оно приезжает внутри [Group] — канал это
 * группа со вторым профилем. Здесь то, чего в группе не бывает: заявки,
 * впущенные, выдачи и раздача, — и команды, которых у второго экрана нет
 * вовсе, поэтому все они идут через `clientIo`.
 */
class ChannelsModel(session: SessionContext) : FeatureModel(session), ChannelsApi {
    override val channelNotices = ChannelNotices(
        open = openChannelNotice(),
        private = privateChannelNotice(),
        admitterGrant = admitterGrantNotice(),
        keyRotation = keyRotationNotice(),
        sharing = sharingNotice(),
        seeding = seedingNotice(),
        sharingLevel = sharingLevelNotice(),
    )

    private val _channelRequests = MutableStateFlow<Map<String, List<ChannelRequest>>>(emptyMap())
    override val channelRequests = _channelRequests.asStateFlow()

    private val _channelAdmits = MutableStateFlow<Map<String, List<ChannelAdmit>>>(emptyMap())
    override val channelAdmits = _channelAdmits.asStateFlow()

    private val _channelGrants = MutableStateFlow<Map<String, List<ChannelGrant>>>(emptyMap())
    override val channelGrants = _channelGrants.asStateFlow()

    private val _seedingMode = MutableStateFlow<Map<String, FfiSeeding>>(emptyMap())
    override val seedingMode = _seedingMode.asStateFlow()

    private val _channelSeeds = MutableStateFlow<Map<String, List<ChannelSeed>>>(emptyMap())
    override val channelSeeds = _channelSeeds.asStateFlow()

    private val _sharingLevel = MutableStateFlow<Map<String, FfiSharingLevel>>(emptyMap())
    override val sharingLevel = _sharingLevel.asStateFlow()

    override fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.ChannelRequested -> loadChannel(event.chatId)
            is AppEvent.ChannelPeopleChanged -> loadChannel(event.chatId)
            is AppEvent.SeedingChanged -> loadChannel(event.chatId)
            else -> {}
        }
    }

    override fun createChannel(title: String, open: Boolean) {
        session.clientIo("Failed to create channel") { it.createChannel(title, open) }
    }

    override fun subscribeToChannel(uri: String) {
        session.clientIo("Failed to subscribe to channel") { it.subscribeToChannel(uri) }
    }

    override fun unsubscribeFromChannel(chatId: ByteArray) {
        session.clientIo("Failed to unsubscribe from channel") { it.unsubscribeFromChannel(chatId) }
    }

    override fun channelLink(chatId: ByteArray, onResult: (Result<String>) -> Unit) {
        session.clientIo("Failed to build a channel link") { client ->
            val link = runCatching { client.channelLink(chatId) }
            withContext(Dispatchers.Main) { onResult(link) }
            link.getOrThrow()
        }
    }

    override fun admitToChannel(chatId: ByteArray, peerIk: ByteArray) {
        session.clientIo("Failed to admit to channel") { client ->
            client.admitToChannel(chatId, peerIk)
            load(client, chatId)
        }
    }

    override fun setChannelRight(
        chatId: ByteArray,
        peerIk: ByteArray,
        write: Boolean,
        admit: Boolean,
        evict: Boolean,
        edit: Boolean,
        untilMs: ULong,
    ) {
        session.clientIo("Failed to set a channel right") { client ->
            client.setChannelRight(
                chatId,
                peerIk,
                FfiChannelRights(write = write, admit = admit, evict = evict, edit = edit),
                untilMs,
            )
            load(client, chatId)
        }
    }

    override fun rotateChannelKey(chatId: ByteArray) {
        session.clientIo("Failed to rotate a channel key") { it.rotateChannelKey(chatId) }
    }

    override fun setChannelPow(chatId: ByteArray, bits: UInt) {
        session.clientIo("Failed to set channel pow") { it.setChannelPow(chatId, bits) }
    }

    override fun setSeeding(chatId: ByteArray, mode: FfiSeeding) {
        session.clientIo("Failed to change seeding") { client ->
            client.setSeeding(chatId, mode)
            load(client, chatId)
        }
    }

    override fun setSharingLevel(chatId: ByteArray, level: FfiSharingLevel) {
        session.clientIo("Failed to set a sharing level") { client ->
            client.setSharingLevel(chatId, level)
            load(client, chatId)
        }
    }

    override fun loadChannel(chatId: ByteArray) {
        session.clientIo("Failed to read channel state") { client -> load(client, chatId) }
    }

    /**
     * Читает всё канальное разом.
     *
     * Заявки, впущенных и выдачи ядро держит **у владельца**: у читателя
     * они пусты, и это не пропуск. Раздача же — дело каждого читателя.
     */
    private suspend fun load(client: org.ratatosk.core.RatatoskClient, chatId: ByteArray) {
        val hex = chatId.toHexString()
        val requests = runCatching { client.channelRequests(chatId) }.getOrDefault(emptyList())
        val admits = runCatching { client.channelAdmits(chatId) }.getOrDefault(emptyList())
        val grants = runCatching { client.channelGrants(chatId) }.getOrDefault(emptyList())
        val mode = runCatching { client.seedingMode(chatId) }.getOrDefault(FfiSeeding.QUIET)
        val seeds = runCatching { client.channelSeeds(chatId) }.getOrDefault(emptyList())
        val level = runCatching { client.sharingLevel(chatId) }.getOrDefault(FfiSharingLevel.EVERYONE)

        withContext(Dispatchers.Main) {
            _channelRequests.update {
                it + (hex to requests.map { r -> ChannelRequest(r.who, r.name) })
            }
            _channelAdmits.update {
                it + (hex to admits.map { a -> ChannelAdmit(a.who, a.name, a.admittedByName) })
            }
            _channelGrants.update {
                it + (hex to grants.map { g ->
                    ChannelGrant(
                        peerIk = g.who,
                        name = g.name,
                        write = g.rights.write,
                        admit = g.rights.admit,
                        evict = g.rights.evict,
                        edit = g.rights.edit,
                        untilMs = g.untilMs,
                        live = g.live,
                    )
                })
            }
            _seedingMode.update { it + (hex to mode) }
            _channelSeeds.update {
                it + (hex to seeds.map { s -> ChannelSeed(s.who, s.validUntilMs, s.verified) })
            }
            _sharingLevel.update { it + (hex to level) }
        }
    }

    /** Сессия закрыта: чужих заявок и выдач мы не храним. */
    override fun reset() {
        _channelRequests.value = emptyMap()
        _channelAdmits.value = emptyMap()
        _channelGrants.value = emptyMap()
        _seedingMode.value = emptyMap()
        _channelSeeds.value = emptyMap()
        _sharingLevel.value = emptyMap()
    }
}
