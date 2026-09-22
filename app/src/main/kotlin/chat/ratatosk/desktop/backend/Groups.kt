package chat.ratatosk.desktop.backend

/**
 * Группа (§11) — одинаково для полного клиента и компаньона.
 *
 * Своих типов, а не `FfiGroup`: у компаньона нет ключей участников и даты
 * создания, и подставлять вместо них выдумки — значит показать неправду.
 */
class Group(
    val chatId: ByteArray,
    val title: String,
    /** Мы в группе. Вышедший видит переписку, но писать не может. */
    val joined: Boolean,
    /**
     * Можно исключать, переименовывать, менять картинку. `null` — ещё не
     * известно: компаньон узнаёт это из состава, который приходит отдельно.
     */
    val canManage: Boolean?,
    /** Когда последний раз менялась картинка; `0` — не ставилась. */
    val avatarMs: ULong,
    /** Когда создана; `null` — неизвестно (компаньон). */
    val createdMs: ULong?,
    /**
     * Канальная часть; `null` — обычная группа (или второй экран, который
     * о каналах не знает).
     *
     * Канал — это группа со вторым профилем, а не третий вид чата (§3.2):
     * приходит тем же списком, читается так же. Отличает его **наличие
     * записи**; отдельного признака нет нарочно — рисовать по нему нечего,
     * если записи нет, и два источника одного ответа однажды разошлись бы.
     */
    val channel: Channel? = null,
)

/**
 * Канал с нашей стороны (фаза 2, §6, §10).
 *
 * @param open порода; `null` — подписанного представления ещё нет, и
 *   обещание ссылки за установленную породу выдавать нельзя (§10.2).
 * @param mine мы владелец: считает ядро, а не мы сравнением ключей (§13.3).
 * @param canWrite вправе ли мы говорить прямо сейчас — с учётом срока
 *   и правила «владельцу всё» (§6.2, §6.3).
 * @param rightsUntilMs когда истекает наша выдача; `0` — срока нет.
 * @param powBits цена слова (§11); `0` — работа не требуется.
 * @param awaiting ждём впуска владельцем (§10.4).
 * @param readable есть ли чем читать: хоть одно поколение ключа.
 * @param mayRotate показывать ли кнопку поворота (§6.4).
 * @param ownerUnseen от владельца давно ничего не приходило (§6.3) —
 *   именно про наш приём, а не про то, где владелец.
 * @param grantsExpiring выдач, которым меньше месяца (только владельцу).
 */
class Channel(
    val open: Boolean?,
    val mine: Boolean,
    val canWrite: Boolean,
    val canAdmit: Boolean,
    val rightsUntilMs: ULong,
    val powBits: UInt,
    val awaiting: Boolean,
    val readable: Boolean,
    val mayRotate: Boolean,
    val ownerUnseen: Boolean,
    val grantsExpiring: UInt,
)

/** Кто просится в канал (§10.4): заявка ждёт владельца. */
class ChannelRequest(val peerIk: ByteArray, val name: String)

/** Кого впустили (§6.5). */
class ChannelAdmit(val peerIk: ByteArray, val name: String, val admittedByName: String)

/** Что кому выдано (§6.2, §6.3). */
class ChannelGrant(
    val peerIk: ByteArray,
    val name: String,
    val write: Boolean,
    val admit: Boolean,
    val evict: Boolean,
    val edit: Boolean,
    val untilMs: ULong,
    val live: Boolean,
)

/** Кто ещё раздаёт (§7.5). */
class ChannelSeed(val peerIk: ByteArray, val validUntilMs: ULong, val verified: Boolean)

/** Участник группы. Назван личным чатом с ним — другого имени у компаньона нет. */
class GroupMember(
    val chatId: ByteArray,
    val name: String,
    val isMe: Boolean,
    /** Создатель ли; `null` — ядро этого режима не говорит. */
    val isOwner: Boolean?,
)
