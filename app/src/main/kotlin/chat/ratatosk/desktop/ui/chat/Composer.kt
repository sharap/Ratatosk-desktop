package chat.ratatosk.desktop.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.util.AppDirs
import chat.ratatosk.desktop.util.ImageUtils
import chat.ratatosk.desktop.util.Log
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Над полем ввода: на что отвечаем или что правим. */
sealed interface ComposerBanner {
    data class Reply(val author: String, val preview: String) : ComposerBanner
    data class Edit(val notice: String) : ComposerBanner
}

/**
 * Поле ввода. Клавиши:
 * * Enter / Ctrl+Enter — отправить (что именно — настройка); другая — новая строка;
 * * ↑ в пустом поле — править последнее своё;
 * * Esc — снять ответ или правку (не поглощается, если снимать нечего: тогда это «назад»);
 * * Ctrl+V с картинкой или файлами в буфере — прикрепить.
 */
@Composable
fun Composer(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    banner: ComposerBanner?,
    onCancelBanner: () -> Unit,
    attachments: List<File>,
    onRemoveAttachment: (File) -> Unit,
    onAttach: () -> Unit,
    onAttachFiles: (List<File>) -> Unit,
    sendWithCtrlEnter: Boolean,
    onSend: () -> Unit,
    onEditLast: () -> Boolean,
    focusRequester: FocusRequester,
) {
    val canSend = value.text.isNotBlank() || attachments.isNotEmpty()

    Surface(tonalElevation = 2.dp) {
        Column {
            banner?.let { b ->
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (b is ComposerBanner.Edit) Icons.Default.Edit else Icons.AutoMirrored.Filled.Reply, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        when (b) {
                            is ComposerBanner.Reply -> {
                                Text(Strings.CHAT_REPLYING_TO.format(b.author), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(b.preview, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            // Текст ядра о правке — до отправки: прежние слова могли уже прочитать.
                            is ComposerBanner.Edit -> {
                                Text(Strings.CHAT_EDITING, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                if (b.notice.isNotBlank()) Text(b.notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                    IconButton(onClick = onCancelBanner) { Icon(Icons.Default.Close, Strings.CANCEL) }
                }
            }

            if (attachments.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    attachments.forEach { file -> AttachmentThumb(file, onRemove = { onRemoveAttachment(file) }) }
                }
            }

            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = onAttach) { Icon(Icons.Default.AttachFile, Strings.ATTACH_FILES) }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when {
                                e.key == Key.Enter || e.key == Key.NumPadEnter -> {
                                    val sendChord = if (sendWithCtrlEnter) e.isCtrlPressed else !e.isShiftPressed && !e.isCtrlPressed && !e.isAltPressed
                                    when {
                                        sendChord -> { if (canSend) onSend(); true }
                                        // В режиме Ctrl+Enter обычный Enter переносит строку сам;
                                        // в режиме Enter перенос — Shift+Enter, вставляем явно.
                                        !sendWithCtrlEnter && e.isShiftPressed -> {
                                            onValueChange(insert(value, "\n")); true
                                        }
                                        else -> false
                                    }
                                }
                                e.key == Key.DirectionUp && value.text.isEmpty() && banner !is ComposerBanner.Edit -> onEditLast()
                                e.key == Key.Escape && banner != null -> { onCancelBanner(); true }
                                e.key == Key.V && e.isCtrlPressed -> {
                                    val pasted = filesFromClipboard()
                                    if (pasted.isNotEmpty()) { onAttachFiles(pasted); true } else false
                                }
                                else -> false
                            }
                        },
                    placeholder = { Text(if (sendWithCtrlEnter) Strings.CHAT_SEND_HINT_CTRL else Strings.CHAT_SEND_HINT_ENTER) },
                    maxLines = 8,
                )
                Spacer(Modifier.width(4.dp))
                FilledIconButton(onClick = onSend, enabled = canSend) {
                    if (banner is ComposerBanner.Edit) Icon(Icons.Default.Check, Strings.SAVE)
                    else Icon(Icons.AutoMirrored.Filled.Send, Strings.SEND)
                }
            }
        }
    }
}

@Composable
private fun AttachmentThumb(file: File, onRemove: () -> Unit) {
    val bitmap = remember(file) {
        if (file.extension.lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp"))
            ImageUtils.readImageBounded(file, maxSide = 128)?.toComposeImageBitmap()
        else null
    }
    Box(Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (bitmap != null) {
            Image(bitmap, file.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, modifier = Modifier.size(24.dp))
                Text(file.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).size(22.dp)) {
            Icon(Icons.Default.Cancel, Strings.CANCEL, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        }
    }
}

private fun insert(value: TextFieldValue, text: String): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val newText = value.text.replaceRange(start, end, text)
    return TextFieldValue(newText, TextRange(start + text.length))
}

/**
 * Файлы из буфера: скопированные в файловом менеджере — как есть; картинка
 * (снимок экрана) — PNG во временный каталог приложения (0700, чистится при
 * выходе). Ничего подходящего — пустой список, и Ctrl+V вставляет текст.
 */
internal fun filesFromClipboard(): List<File> = try {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    when {
        clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor) ->
            (clipboard.getData(DataFlavor.javaFileListFlavor) as? List<*>)?.filterIsInstance<File>()?.filter { it.isFile } ?: emptyList()
        clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor) -> {
            val image = clipboard.getData(DataFlavor.imageFlavor) as? java.awt.Image
            if (image == null) emptyList() else {
                val buffered = image as? BufferedImage ?: BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB).also {
                    it.createGraphics().apply { drawImage(image, 0, 0, null); dispose() }
                }
                val dir = File(AppDirs.getMediaCacheDir(), "outgoing").apply { mkdirs() }
                val file = File(dir, "screenshot-${System.currentTimeMillis()}.png")
                ImageIO.write(buffered, "png", file)
                listOf(file)
            }
        }
        else -> emptyList()
    }
} catch (e: Exception) {
    Log.w("Composer", "Clipboard read failed", e)
    emptyList()
}
