package chat.ratatosk.desktop.util

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.util.Timer
import kotlin.concurrent.schedule

object ClipboardUtils {
    /**
     * Скопировать секрет и стереть его из буфера через [clearAfterMs] —
     * только если там всё ещё он: скопированное человеком позже не трогаем.
     */
    fun copySecret(text: String, clearAfterMs: Long = 60_000) {
        copyToClipboard(text)
        Timer("clipboard-clear", true).schedule(clearAfterMs) { clearIfHolds(text) }
    }

    fun clearIfHolds(text: String) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val current = runCatching { clipboard.getData(DataFlavor.stringFlavor) as? String }.getOrNull()
            if (current == text) clipboard.setContents(StringSelection(""), null)
        } catch (e: Exception) {
            Log.w("ClipboardUtils", "Failed to clear clipboard", e)
        }
    }

    fun copyToClipboard(text: String) {
        try {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        } catch (e: Exception) {
            Log.w("ClipboardUtils", "Failed to copy to clipboard", e)
        }
    }
}
