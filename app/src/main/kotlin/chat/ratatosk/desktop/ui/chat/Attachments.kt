package chat.ratatosk.desktop.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.util.toHexString
import org.ratatosk.core.FfiFile

/** Вложения сообщения — одна карточка на файл. */
@Composable
fun AttachmentList(
    files: List<FfiFile>,
    chatId: ByteArray,
    viewModel: RatatoskViewModel,
    contentColor: Color,
    accent: Color,
    onSaved: (String) -> Unit,
) {
    if (files.isEmpty()) return
    val progress by viewModel.fileProgress.collectAsState()
    val sending by viewModel.fileSending.collectAsState()
    val waiting by viewModel.fileWaiting.collectAsState()
    val jobs by viewModel.activeJobsFlow.collectAsState()
    val previews by viewModel.filePreviews.collectAsState()

    Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        files.forEach { file ->
            val hex = file.fileId.toHexString()
            AttachmentCard(
                file = file,
                // Превью — через поток: пришло позже — перерисуется (ревью Android 2.5).
                preview = previews[hex] ?: if (file.hasPreview) viewModel.getFilePreview(file.fileId) else null,
                receiveFraction = progress[hex] ?: if (file.chunkTotal > 0UL) (file.receivedChunks.toFloat() / file.chunkTotal.toFloat()).coerceIn(0f, 1f) else 0f,
                sendFraction = sending[hex],
                waitingText = waiting[hex],
                saving = hex in jobs,
                contentColor = contentColor,
                accent = accent,
                onAccept = { viewModel.acceptFile(chatId, file.fileId) },
                onDecline = { viewModel.declineFile(chatId, file.fileId) },
                onOpen = { viewModel.openFile(file) },
                onSave = { viewModel.downloadFile(file, onSaved) },
                onCancel = { viewModel.cancelFileJob(file.fileId) },
            )
        }
    }
}

@Composable
private fun AttachmentCard(
    file: FfiFile,
    preview: ByteArray?,
    receiveFraction: Float,
    sendFraction: Float?,
    waitingText: String?,
    saving: Boolean,
    contentColor: Color,
    accent: Color,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val available = file.complete || !file.incoming
    Column(
        Modifier
            .widthIn(min = 240.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(contentColor.copy(alpha = 0.08f))
    ) {
        val bitmap = rememberPreview(preview)
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .aspectRatio((bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)).coerceIn(0.5f, 2.5f))
                    .then(if (available) Modifier.clickable(onClick = onOpen) else Modifier),
            )
        }
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(iconFor(file.name), null, tint = accent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = contentColor)
                Text(stateLine(file, receiveFraction, sendFraction), style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.65f))
            }
            when {
                saving -> {
                    CircularProgressIndicator(progress = { receiveFraction }, modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = accent)
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, Strings.FILE_CANCEL, tint = MaterialTheme.colorScheme.error) }
                }
                file.incoming && !file.accepted && !file.complete -> {
                    IconButton(onClick = onDecline) { Icon(Icons.Default.Close, Strings.FILE_DECLINE, tint = MaterialTheme.colorScheme.error) }
                    IconButton(onClick = onAccept) { Icon(Icons.Default.Download, Strings.FILE_ACCEPT, tint = accent) }
                }
                available -> {
                    IconButton(onClick = onOpen) { Icon(Icons.AutoMirrored.Filled.OpenInNew, Strings.FILE_OPEN, tint = accent) }
                    IconButton(onClick = onSave) { Icon(Icons.Default.SaveAlt, Strings.FILE_SAVE, tint = accent) }
                }
                else -> CircularProgressIndicator(progress = { receiveFraction }, modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = accent)
            }
        }
        // Стоит — словами ядра, почему: «ошибка» и «загрузка» здесь одинаково неправда.
        if (waitingText != null && !file.complete) {
            Text(
                waitingText,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.75f),
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            )
        }
    }
}

private fun stateLine(file: FfiFile, receive: Float, send: Float?): String {
    val size = formatFileSize(file.sizeBytes)
    return when {
        !file.incoming && send != null && send < 1f -> "$size · " + Strings.FILE_SENDING.format((send * 100).toInt())
        file.incoming && file.accepted && !file.complete -> "$size · " + Strings.FILE_RECEIVING.format((receive * 100).toInt())
        else -> size
    }
}

@Composable
private fun rememberPreview(bytes: ByteArray?): ImageBitmap? = remember(bytes) {
    bytes?.let { runCatching { org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull() }
}

private fun iconFor(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" -> Icons.Default.Image
    "mp4", "mkv", "webm", "mov", "avi" -> Icons.Default.Movie
    "mp3", "ogg", "opus", "m4a", "wav", "flac" -> Icons.Default.AudioFile
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

fun formatFileSize(bytes: ULong): String {
    val b = bytes.toDouble()
    return when {
        b < 1024 -> "%.0f Б".format(b)
        b < 1024 * 1024 -> "%.1f КБ".format(b / 1024)
        b < 1024 * 1024 * 1024 -> "%.1f МБ".format(b / (1024 * 1024))
        else -> "%.1f ГБ".format(b / (1024 * 1024 * 1024))
    }
}
