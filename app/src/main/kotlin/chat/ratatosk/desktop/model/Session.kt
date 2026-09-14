package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.core.RatatoskCore
import chat.ratatosk.desktop.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.ratatosk.core.FfiCompanionEvent
import org.ratatosk.core.FfiEvent

/** Состояние сессии, которое видят экраны. */
interface SessionApi {
    val error: StateFlow<String?>
    val isInitialized: StateFlow<Boolean>
    val activeAccountId: StateFlow<String?>
    val isCompanionMode: StateFlow<Boolean>
    val isCompanionLinked: StateFlow<Boolean>
    val isCompanionFresh: StateFlow<Boolean>
    val events: StateFlow<List<FfiEvent>>
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

    private val _events = MutableStateFlow<List<FfiEvent>>(emptyList())
    override val events: StateFlow<List<FfiEvent>> = _events.asStateFlow()

    override fun clearError() {
        _error.value = null
    }

    internal fun recordEvent(event: FfiEvent) {
        _events.update { (it + event).takeLast(100) }
    }

    /** Сессия закрыта: всё, что её описывало, — к исходному. */
    internal fun reset() {
        _isInitialized.value = false
        _isCompanionMode.value = false
        _isCompanionLinked.value = false
        _isCompanionFresh.value = false
        _activeAccountId.value = null
        _events.value = emptyList()
    }
}

/**
 * Модель одной функции приложения. События ядра до неё доносит [AppModels];
 * [reset] зовётся при выходе из аккаунта и обязан вернуть модель к пустому
 * состоянию, отменив свою фоновую работу.
 */
abstract class FeatureModel(protected val session: SessionContext) {
    protected val scope: CoroutineScope get() = session.scope

    open fun onEvent(event: FfiEvent) {}
    open fun onCompanionEvent(event: FfiCompanionEvent) {}
    abstract fun reset()
}
