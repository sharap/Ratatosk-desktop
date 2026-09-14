package chat.ratatosk.desktop.util

import java.io.File

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
        return dir
    }
}
