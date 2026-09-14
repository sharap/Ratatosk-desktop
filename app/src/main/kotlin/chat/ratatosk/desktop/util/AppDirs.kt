package chat.ratatosk.desktop.util

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

object AppDirs {
    fun getBaseDir(): File {
        val userHome = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()
        val dir = when {
            os.contains("win") -> File(System.getenv("APPDATA"), "Ratatosk")
            os.contains("mac") -> File(userHome, "Library/Application Support/Ratatosk")
            else -> File(userHome, ".ratatosk")
        }
        dir.mkdirs()
        restrictToOwner(dir)
        return dir
    }

    /**
     * Расшифрованные копии вложений, открытые системным приложением.
     *
     * Лежат открытым текстом, поэтому не в общем `java.io.tmpdir`, где их
     * видят все пользователи машины, а внутри каталога приложения с правами
     * только владельца; чистятся при выходе из аккаунта и при старте.
     */
    fun getMediaCacheDir(): File {
        val dir = File(getBaseDir(), "cache/media")
        dir.mkdirs()
        restrictToOwner(dir)
        return dir
    }

    fun clearMediaCache() {
        val dir = File(getBaseDir(), "cache/media")
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { file ->
            if (!file.deleteRecursively()) Log.w("AppDirs", "Failed to remove cached copy")
        }
    }

    /** `rwx------` там, где это есть; на Windows права наследует профиль пользователя. */
    private fun restrictToOwner(dir: File) {
        try {
            Files.setPosixFilePermissions(dir.toPath(), PosixFilePermissions.fromString("rwx------"))
        } catch (_: UnsupportedOperationException) {
        } catch (e: Exception) {
            Log.w("AppDirs", "Failed to restrict directory permissions", e)
        }
    }
}
