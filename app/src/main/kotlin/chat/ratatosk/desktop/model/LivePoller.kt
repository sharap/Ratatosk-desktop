package chat.ratatosk.desktop.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Опрос того, о чём ядро событий не шлёт (живые пиры, реле), — **только
 * пока на него смотрят**. Каждый [watch] держит опрос включённым, пока не
 * закрыт; последний закрытый останавливает его. В фоне ядро не дёргается.
 */
internal class LivePoller(
    private val scope: CoroutineScope,
    private val intervalMs: Long,
    private val poll: suspend () -> Unit,
) {
    private var watchers = 0
    private var job: Job? = null

    @Synchronized
    fun watch(): AutoCloseable {
        watchers++
        if (job == null) {
            job = scope.launch(Dispatchers.IO) {
                while (isActive) {
                    runCatching { poll() }
                    delay(intervalMs)
                }
            }
        }
        var closed = false
        return AutoCloseable {
            synchronized(this) {
                if (closed) return@AutoCloseable
                closed = true
                watchers--
                if (watchers == 0) {
                    job?.cancel()
                    job = null
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        watchers = 0
        job?.cancel()
        job = null
    }
}
