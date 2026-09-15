package chat.ratatosk.desktop.util

import java.io.File

/**
 * Журнал ядра (`FFI.md`, «Журнал ядра»).
 *
 * Подписчик у ядра **один на процесс** и ставится до открытия хранилища:
 * второй вызов ничего не делает. Поэтому решаем один раз при старте:
 * включён журнал в настройках — в файл (он же и в отладочном запуске),
 * иначе в отладочном запуске — в поток ошибок, иначе — никуда.
 */
object CoreLog {
    @Volatile
    private var started = false

    fun file(): File = File(AppDirs.getBaseDir(), "logs/core.log")

    @Synchronized
    fun start(fileEnabled: Boolean) {
        if (started) return
        started = true
        try {
            when {
                fileEnabled -> {
                    val file = file()
                    file.parentFile.mkdirs()
                    // Пустой отбор — умолчание ядра: наши крейты подробно, чужое по делу.
                    org.ratatosk.core.enableFileLogging("", file.absolutePath)
                    Log.d("CoreLog", "core log to file")
                }
                Log.isDebug -> org.ratatosk.core.enableLogging("")
            }
        } catch (t: Throwable) {
            Log.w("CoreLog", "Core logging unavailable", t)
        }
    }
}
