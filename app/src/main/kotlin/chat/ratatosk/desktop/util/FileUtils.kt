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

    /**
     * Расширения, которые система по двойному щелчку **выполняет**, а не
     * показывает. Список намеренно широкий: лишняя строка стоит человеку
     * одного щелчка «показать в папке», пропущенная — чужого кода на машине.
     */
    private val EXECUTABLE_EXTENSIONS = setOf(
        // Windows
        "exe", "com", "scr", "pif", "bat", "cmd", "msi", "msix", "msp", "appx", "appxbundle",
        "lnk", "url", "hta", "cpl", "msc", "reg", "inf", "scf", "vb", "vbs", "vbe", "js", "jse",
        "ws", "wsf", "wsh", "ps1", "psm1", "application", "gadget", "chm", "iso", "img", "vhd", "vhdx",
        // Linux и общее
        "sh", "bash", "zsh", "csh", "ksh", "fish", "run", "bin", "desktop", "appimage", "deb", "rpm",
        "snap", "flatpakref", "jar", "jnlp", "py", "pyw", "pl", "rb", "php",
        // macOS
        "app", "command", "tool", "pkg", "mpkg", "dmg", "workflow", "scpt", "terminal",
    )

    fun isExecutable(name: String): Boolean {
        val ext = safeName(name).substringAfterLast('.', "").lowercase()
        return ext in EXECUTABLE_EXTENSIONS
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
    /** Картинка ли — по имени: только такие показываем своим просмотрщиком. */
    fun isImage(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

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
