package chat.ratatosk.desktop.util

import chat.ratatosk.desktop.ui.Strings
import org.ratatosk.core.FfiMessage

/**
 * Одна строка, которой сообщение представляется вне переписки: в уведомлении
 * и в списке чатов. Одно место на оба — «[файл]» в уведомлении и пустая
 * строка в списке были бы разными ответами на один вопрос.
 */
object MessagePreview {
    private val IMAGE = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg")
    private val VIDEO = setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp")
    private val AUDIO = setOf("mp3", "ogg", "oga", "opus", "m4a", "aac", "wav", "flac")

    fun of(message: FfiMessage): String =
        of(message.body, message.files.map { it.name }, message.sharedContact != null)

    fun of(body: String, fileNames: List<String>, hasSharedContact: Boolean): String {
        val text = MarkdownUtils.toPlainText(body, Strings.PREVIEW_SPOILER)
        if (text.isNotBlank()) return text
        if (hasSharedContact) return Strings.PREVIEW_CONTACT
        return when (fileNames.size) {
            0 -> Strings.PREVIEW_EMPTY
            1 -> when (fileNames.first().substringAfterLast('.', "").lowercase()) {
                in IMAGE -> Strings.PREVIEW_PHOTO
                in VIDEO -> Strings.PREVIEW_VIDEO
                in AUDIO -> Strings.PREVIEW_AUDIO
                else -> Strings.PREVIEW_FILE
            }
            else -> attachments(fileNames.size)
        }
    }

    /** «2 вложения», «5 вложений», «21 вложение» — три формы, строкой с %d не выразить. */
    internal fun attachments(n: Int): String {
        val mod100 = n % 100
        val mod10 = n % 10
        val word = when {
            mod100 in 11..14 -> "вложений"
            mod10 == 1 -> "вложение"
            mod10 in 2..4 -> "вложения"
            else -> "вложений"
        }
        return "[$n $word]"
    }
}
