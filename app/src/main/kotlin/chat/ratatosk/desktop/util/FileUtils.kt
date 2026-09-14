package chat.ratatosk.desktop.util

import java.awt.Desktop
import java.io.File

object FileUtils {
    fun openFile(file: File) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file)
            }
        } catch (e: Exception) {
            System.err.println("FileUtils: Failed to open file: ${e.message}")
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
            System.err.println("FileUtils: Failed to open directory: ${e.message}")
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
}
