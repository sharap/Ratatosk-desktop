package chat.ratatosk.desktop.util

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

object ClipboardUtils {
    fun copyToClipboard(text: String) {
        try {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        } catch (e: Exception) {
            System.err.println("Failed to copy to clipboard: ${e.message}")
        }
    }
}
