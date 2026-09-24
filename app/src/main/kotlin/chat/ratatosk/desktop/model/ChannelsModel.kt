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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    /**
     * Метка «до владельца канала не доехало» (§15).
     *
     * `false` у `FfiMessage::in_the_channel` значит ровно это — не
     * «удалили» (удаления у канала нет вовсе) и не «подделка» (подпись
     * проверена, иначе сообщение не показалось бы).
     */
    val messageNotInTheChannel: String,
    /** До предпросмотра: владелец узнает, что кем-то интересуются (§15). */
    val preview: String,
    /** Когда ожидание ушло на медленный путь (§10.5). */
    val slowPath: String,
)

/**
 * Что рассказал предпросмотр канала.
 *
 * Всё из документа, подписанного владельцем и проверенного ключом
 * из ссылки: обещанию самой ссылки верить нельзя (§10.2), а этому можно.
 */
class ChannelPreview(
    val chatId: ByteArray,
    val title: String,
    val open: Boolean,
    val version: ULong,
    val powBits: UInt,
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

    /**
     * Слова к признаку канала (§15).
     *
     * На границе, а не в клиенте: признак обязан говорить то, что
     * протокол на самом деле знает, а своя строка разошлась бы
     * с поведением на первой же правке.
     */
    fun channelSignalText(signal: org.ratatosk.core.FfiChannelSignal): String

    /** Что показывать, пока канал не открылся (§10.5) — словами ядра. */
    fun channelWaitingText(waiting: org.ratatosk.core.FfiWaiting): String

    /** Предлагать ли кнопку «сообщить, когда откроется». */
    fun channelWaitingOffersNotification(waiting: org.ratatosk.core.FfiWaiting): Boolean

    /**
     * Каналы (chatId в hex), об открытии которых просили сказать.
     *
     * Просьба живёт на диске: ожидание меряется часами, и переживать
     * перезапуск она обязана.
     */
    val channelsToAnnounce: StateFlow<Set<String>>

    /** Просить или отменить просьбу сказать, когда канал откроется. */
    fun announceChannelWhenOpen(chatId: ByteArray, announce: Boolean)

    /**
     * Предпросмотр канала по ссылке (§10.3, шаг 5).
     *
     * Перед вызовом обязателен текст §15: владелец узнает, что кем-то
     * интересуются, даже если человек потом откажется. Ответа может
     * и не быть — это не отказ (§10.5).
     */
    fun previewChannel(uri: String)

    /** Что рассказал предпросмотр; `null` — не спрашивали или ответа нет. */
    val channelPreview: StateFlow<ChannelPreview?>

    /** Забыть предпросмотр: окно закрыли. */
    fun clearChannelPreview()

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

    /**
     * Тянем ли сейчас более раннюю историю канала (§7.4); по каналам.
     *
     * У команды нет ответа: блоки приедут обычной дорогой, а «глубже
     * ничего нет» скажет событие — и только оно снимает полоску.
     */
    val historyPulling: StateFlow<Map<String, Boolean>>

    /** Последняя просьба уткнулась в дно: у спрошенных глубже нет. */
    val historyEnded: StateFlow<Map<String, Boolean>>

    /**
     * Просит более раннюю историю канала (§7.4).
     *
     * Вступление историю не тянет, и это решение: иначе подписавшийся
     * оплачивал бы год чужой переписки, которого не просил.
     */
    fun pullOlderHistory(chatId: ByteArray)

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
        messageNotInTheChannel = org.ratatosk.core.messageNotInTheChannelText(),
        preview = org.ratatosk.core.channelPreviewNotice(),
        slowPath = org.ratatosk.core.channelSlowPathNotice(),
    )

    override fun channelSignalText(signal: org.ratatosk.core.FfiChannelSignal): String =
        org.ratatosk.core.channelSignalText(signal)

    override fun channelWaitingText(waiting: org.ratatosk.core.FfiWaiting): String =
        org.ratatosk.core.channelWaitingText(waiting)

    override fun channelWaitingOffersNotification(waiting: org.ratatosk.core.FfiWaiting): Boolean =
        org.ratatosk.core.channelWaitingOffersANotification(waiting)

    override val channelsToAnnounce: StateFlow<Set<String>> = session.settings.channelsToAnnounce
        .stateIn(session.scope, SharingStarted.WhileSubscribed(5000), emptySet())

    override fun announceChannelWhenOpen(chatId: ByteArray, announce: Boolean) {
        val hex = chatId.toHexString()
        session.scope.launch { session.settings.announceChannelWhenOpen(hex, announce) }
    }

    private val _channelPreview = MutableStateFlow<ChannelPreview?>(null)
    override val channelPreview = _channelPreview.asStateFlow()

    /**
     * Ждём ли ответа прямо сейчас.
     *
     * Ответа может и не быть вовремя: человек закрыл окно, а владелец
     * ответил через минуту. Без этого признака ответ ложился в состояние
     * и всплывал в следующем окне — с чужой ссылкой и чужим названием.
     */
    @Volatile
    private var awaitingPreview = false

    override fun previewChannel(uri: String) {
        _channelPreview.value = null
        awaitingPreview = true
        session.io("Failed to preview a channel") { it.previewChannel(uri) }
    }

    override fun clearChannelPreview() {
        awaitingPreview = false
        _channelPreview.value = null
    }

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

    private val _historyPulling = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    override val historyPulling = _historyPulling.asStateFlow()

    private val _historyEnded = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    override val historyEnded = _historyEnded.asStateFlow()

    override fun pullOlderHistory(chatId: ByteArray) {
        val hex = chatId.toHexString()
        _historyPulling.update { it + (hex to true) }
        _historyEnded.update { it - hex }
        session.clientIo("Failed to pull older history") { it.pullOlderHistory(chatId) }
    }

    override fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.ChannelHistoryEnd -> {
                val hex = event.chatId.toHexString()
                _historyPulling.update { it - hex }
                _historyEnded.update { it + (hex to true) }
            }
            is AppEvent.ChannelPreviewed -> {
                // Ответ на предпросмотр: породу больше не надо угадывать.
                // Но только если его ещё ждут: опоздавший ответ всплыл бы
                // в следующем окне, рассказывая про чужую ссылку.
                if (!awaitingPreview) return
                awaitingPreview = false
                _channelPreview.value = ChannelPreview(
                    chatId = event.chatId,
                    title = event.title,
                    open = event.open,
                    version = event.version,
                    powBits = event.powBits,
                )
            }
            is AppEvent.ChannelRequested -> loadChannel(event.chatId)
            is AppEvent.ChannelPeopleChanged -> loadChannel(event.chatId)
            is AppEvent.SeedingChanged -> loadChannel(event.chatId)
            else -> {}
        }
    }

    override fun createChannel(title: String, open: Boolean) {
        // Глубина истории (§5.4) появилась в ядре только что и своего
        // экрана ещё не имеет. Пока просим «всё» — так канал ведёт себя
        // как прежде; «ничего» молча отрезало бы от пришедших завтра
        // всё сказанное сегодня.
        session.clientIo("Failed to create channel") { it.createChannel(title, open, true) }
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
        _historyPulling.value = emptyMap()
        _historyEnded.value = emptyMap()
        _channelPreview.value = null
        awaitingPreview = false
    }
}
