package chat.ratatosk.desktop.util

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

object FilePicker {
    fun pickImage(): File? {
        val dialog = FileDialog(null as Frame?, "Выберите изображение", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name ->
            val lowercase = name.lowercase()
            lowercase.endsWith(".jpg") || lowercase.endsWith(".jpeg") || lowercase.endsWith(".png")
        }
        dialog.isVisible = true
        return if (dialog.file != null) File(dialog.directory, dialog.file) else null
    }

    fun pickFiles(): List<File> {
        val chooser = JFileChooser()
        chooser.isMultiSelectionEnabled = true
        chooser.dialogTitle = "Выберите файлы"
        val result = chooser.showOpenDialog(null)
        return if (result == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFiles.toList()
        } else {
            emptyList()
        }
    }

    fun pickDirectory(): File? {
        val chooser = JFileChooser()
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.dialogTitle = "Выберите папку для загрузок"
        val result = chooser.showOpenDialog(null)
        return if (result == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile
        } else {
            null
        }
    }
}
