package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.backend.AppEvent
import chat.ratatosk.desktop.backend.Backend
import chat.ratatosk.desktop.backend.ClientBackend
import chat.ratatosk.desktop.core.RatatoskCore
import chat.ratatosk.desktop.data.SettingsRepository
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import chat.ratatosk.desktop.backend.CompanionEndpoint
import kotlinx.coroutines.flow.asStateFlow
import org.ratatosk.core.RatatoskClient

/** Состояние сессии, которое видят экраны. */
interface SessionApi {
    val error: StateFlow<String?>
    val isInitialized: StateFlow<Boolean>
    val activeAccountId: StateFlow<String?>
    val isCompanionMode: StateFlow<Boolean>
    val isCompanionLinked: StateFlow<Boolean>
    val isCompanionFresh: StateFlow<Boolean>
    /** Порт и ключ этого второго экрана; `null` — полный клиент. */
    val companionEndpoint: StateFlow<CompanionEndpoint?>
    fun clearError()
}

/**
 * Общее для всех моделей: область корутин, настройки и то, что описывает
 * открытую сессию, — какой аккаунт, клиент это или компаньон, есть ли связь.
 *
 * Изменяемые потоки открыты моделям пакета (`internal`), экранам — только
 * на чтение через [SessionApi].
 */
class SessionContext(
    val scope: CoroutineScope,
    val settings: SettingsRepository,
) : SessionApi {
    internal val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    internal val _isInitialized = MutableStateFlow(RatatoskCore.isInitialized())
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    internal val _activeAccountId = MutableStateFlow<String?>(RatatoskCore.getActiveAccountId())
    override val activeAccountId: StateFlow<String?> = _activeAccountId.asStateFlow()

    internal val _isCompanionMode = MutableStateFlow(RatatoskCore.isCompanionMode())
    override val isCompanionMode: StateFlow<Boolean> = _isCompanionMode.asStateFlow()

    internal val _isCompanionLinked = MutableStateFlow(false)
    override val isCompanionLinked: StateFlow<Boolean> = _isCompanionLinked.asStateFlow()

    internal val _isCompanionFresh = MutableStateFlow(false)
    override val isCompanionFresh: StateFlow<Boolean> = _isCompanionFresh.asStateFlow()

    internal val _companionEndpoint = MutableStateFlow<CompanionEndpoint?>(null)
    override val companionEndpoint: StateFlow<CompanionEndpoint?> = _companionEndpoint.asStateFlow()

    /** Открытый аккаунт; `null` — ничего не открыто. */
    @Volatile
    internal var backend: Backend? = null

    /** Возможности полного клиента; у компаньона их нет. */
    internal val client: RatatoskClient? get() = (backend as? ClientBackend)?.client

    /**
     * Команда в фоне. Нет открытого аккаунта — ничего не делает.
     *
     * [logLabel] — только для журнала. Человеку показываются **слова ядра**:
     * они точнее нашей догадки о причине, а склеивать их с английским
     * «Failed to…» значит показывать полфразы на чужом языке.
     */
    internal fun io(logLabel: String? = null, block: suspend CoroutineScope.(Backend) -> Unit): Job =
        scope.launch(Dispatchers.IO) {
            val b = backend ?: return@launch
            try {
                block(b)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Session", logLabel ?: "Backend call failed", e)
                if (logLabel != null) _error.value = errorText(e)
            }
        }

    /** То же для возможностей полного клиента; в режиме компаньона — ничего. */
    internal fun clientIo(logLabel: String? = null, block: suspend CoroutineScope.(RatatoskClient) -> Unit): Job =
        io(logLabel) { b -> (b as? ClientBackend)?.let { block(it.client) } }

    /**
     * Слова к ошибке ядра.
     *
     * У отказа канала свой текст — и только он: `RatatoskException.Channel`
     * несёт причину значением, а её `message` выглядит как `reason=NO_RIGHT`.
     * Показать такое человеку нельзя, а писать свой текст к двенадцати
     * причинам — тем более (§15).
     */
    internal fun errorText(e: Throwable): String = when {
        e is org.ratatosk.core.RatatoskException.Channel ->
            org.ratatosk.core.channelRefusalText(e.reason)
        !e.message.isNullOrBlank() -> e.message!!
        else -> Strings.CORE_CALL_FAILED
    }

    override fun clearError() {
        _error.value = null
    }

    /** Сессия закрыта: всё, что её описывало, — к исходному. */
    internal fun reset() {
        backend = null
        _isInitialized.value = false
        _isCompanionMode.value = false
        _isCompanionLinked.value = false
        _isCompanionFresh.value = false
        _companionEndpoint.value = null
        _activeAccountId.value = null
    }
}

/**
 * Модель одной функции приложения. События аккаунта до неё доносит [AppModels];
 * [reset] зовётся при выходе из аккаунта и обязан вернуть модель к пустому
 * состоянию, отменив свою фоновую работу.
 */
abstract class FeatureModel(protected val session: SessionContext) {
    protected val scope: CoroutineScope get() = session.scope

    open fun onEvent(event: AppEvent) {}
    abstract fun reset()
}
