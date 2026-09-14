package chat.ratatosk.desktop.util

import java.awt.Desktop
import java.io.File

object FileUtils {
    private const val TAG = "FileUtils"

    fun openFile(file: File) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open file", e)
        }
    }

    fun openDirectory(file: File) {
        try {
            if (Desktop.isDesktopSupported()) {
                if (file.isDirectory) {
                    Desktop.getDesktop().open(file)
                } else {
                    Desktop.getDesktop().open(file.parentFile)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open directory", e)
        }
    }

    fun getDownloadsDir(): File {
        val userHome = System.getProperty("user.home")
        val downloads = File(userHome, "Downloads")
        val ratatoskDir = File(downloads, "Ratatosk")
        if (!ratatoskDir.exists()) {
            ratatoskDir.mkdirs()
        }
        return ratatoskDir
    }

    private val WINDOWS_RESERVED = Regex("^(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])(\\..*)?$", RegexOption.IGNORE_CASE)
    private val FORBIDDEN_CHARS = Regex("[<>:\"/\\\\|?*\\u0000-\\u001F]")

    /**
     * Приводит имя файла, пришедшее от собеседника, к одному безопасному
     * сегменту пути.
     *
     * Имя вида `../../.bashrc` в `File(dir, name)` разрешается **за пределы**
     * каталога загрузок. Поэтому берём последний сегмент и выбрасываем всё,
     * что ФС понимает особым образом: разделители, `:` (на Windows это
     * альтернативный поток, `имя:поток`), управляющие символы, зарезервированные
     * имена устройств (`CON`, `NUL.txt`) и точки/пробелы в конце, которые
     * Windows молча отрезает.
     */
    fun safeName(raw: String?): String {
        var name = raw?.substringAfterLast('/')?.substringAfterLast('\\')?.trim().orEmpty()
        name = name.replace(FORBIDDEN_CHARS, "_").trimEnd('.', ' ')
        if (name.isEmpty() || name.all { it == '.' }) return "file"
        if (WINDOWS_RESERVED.matches(name)) name = "_$name"
        return name.take(200)
    }

    /**
     * Файл с этим именем в каталоге, не затирающий существующий:
     * `photo.jpg`, `photo (1).jpg`, `photo (2).jpg`…
     */
    fun uniqueFile(dir: File, name: String): File {
        val candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.').takeIf { it > 0 }
        val base = if (dot != null) name.substring(0, dot) else name
        val ext = if (dot != null) name.substring(dot) else ""
        var i = 1
        while (true) {
            val next = File(dir, "$base ($i)$ext")
            if (!next.exists()) return next
            i++
        }
    }
}
