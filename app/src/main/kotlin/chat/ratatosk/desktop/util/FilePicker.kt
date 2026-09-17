package chat.ratatosk.desktop.util

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import chat.ratatosk.desktop.ui.Strings
import javax.swing.JFileChooser

object FilePicker {
    fun pickImage(): File? {
        val dialog = FileDialog(null as Frame?, Strings.PICK_IMAGE, FileDialog.LOAD)
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
        chooser.dialogTitle = Strings.PICK_FILES
        val result = chooser.showOpenDialog(null)
        return if (result == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFiles.toList()
        } else {
            emptyList()
        }
    }

    /**
     * «Сохранить как». Существующий файл не выбирается молча: вызывающий
     * получает путь и сам решает, что делать (архив поверх файла ядро
     * не запишет — там может лежать чья-то единственная копия).
     */
    fun saveFile(title: String, defaultName: String, directory: File? = null): File? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
        directory?.let { dialog.directory = it.absolutePath }
        dialog.file = defaultName
        dialog.isVisible = true
        return if (dialog.file != null) File(dialog.directory, dialog.file) else null
    }

    fun pickFile(title: String): File? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.isVisible = true
        return if (dialog.file != null) File(dialog.directory, dialog.file) else null
    }

    fun pickDirectory(): File? {
        val chooser = JFileChooser()
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.dialogTitle = Strings.PICK_DOWNLOAD_DIR
        val result = chooser.showOpenDialog(null)
        return if (result == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile
        } else {
            null
        }
    }
}
