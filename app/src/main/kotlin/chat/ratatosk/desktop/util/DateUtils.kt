package chat.ratatosk.desktop.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Время и даты одним видом во всём приложении. */
object DateUtils {
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dayMonthFormat = SimpleDateFormat("d MMM", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val fullFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    /** Отметка в списке чатов: сегодня — время, вчера — словом, дальше — дата. */
    fun formatChatTime(wallMs: ULong, now: Long = System.currentTimeMillis()): String {
        if (wallMs == 0UL) return ""
        val ms = wallMs.toLong()
        val date = Date(ms)
        return when {
            isSameDay(ms, now) -> timeFormat.format(date)
            isSameDay(ms, now - DAY_MS) -> chat.ratatosk.desktop.ui.Strings.DATE_YESTERDAY
            isSameYear(ms, now) -> dayMonthFormat.format(date)
            else -> fullDateFormat.format(date)
        }
    }

    /** Дата и время полностью — для карточек и сведений. */
    fun formatDateTime(wallMs: ULong): String =
        if (wallMs == 0UL) "" else fullFormat.format(Date(wallMs.toLong()))

    private const val DAY_MS = 24L * 60 * 60 * 1000

    private fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    private fun isSameYear(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
    }
}
