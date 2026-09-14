package chat.ratatosk.desktop.util

/**
 * Журнал приложения.
 *
 * Отладочные строки печатаются только при `-Dratatosk.debug=true` или
 * `RATATOSK_DEBUG=1`. Предупреждения и ошибки — всегда, но **без содержимого**:
 * сюда нельзя класть тексты сообщений, имена файлов, ссылки сопряжения и
 * всё, что пришло от собеседника, — stdout десктопного приложения часто
 * оседает в журнале сессии (journald, `~/.xsession-errors`), и читать его
 * может кто угодно с доступом к машине.
 */
object Log {
    val isDebug: Boolean =
        System.getProperty("ratatosk.debug") == "true" || System.getenv("RATATOSK_DEBUG") == "1"

    fun d(tag: String, message: String) {
        if (isDebug) println("D/$tag: $message")
    }

    fun w(tag: String, message: String, t: Throwable? = null) {
        System.err.println("W/$tag: $message${t.describe()}")
        if (isDebug) t?.printStackTrace()
    }

    fun e(tag: String, message: String, t: Throwable? = null) {
        System.err.println("E/$tag: $message${t.describe()}")
        if (isDebug) t?.printStackTrace()
    }

    // Только класс исключения: текст ошибки ядра может содержать то, что
    // пришло проводом. Подробности — в отладочном режиме, вместе со стеком.
    private fun Throwable?.describe(): String = if (this == null) "" else " (${this::class.simpleName})"
}
