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
    onSaved: (String) -> Unit,
) {
    if (files.isEmpty()) return
    val progress by viewModel.fileProgress.collectAsState()
    val sending by viewModel.fileSending.collectAsState()
    val waiting by viewModel.fileWaiting.collectAsState()
    val jobs by viewModel.activeJobsFlow.collectAsState()
    val saveFractions by viewModel.saveProgress.collectAsState()
    val previews by viewModel.filePreviews.collectAsState()

    Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        files.forEach { file ->
            val hex = file.fileId.toHexString()
            // Кружок узнаётся по имени — как и голосовое. Записывать
            // их здесь пока нечем, а смотреть — тем же ffmpeg.
            val videoMs = chat.ratatosk.desktop.util.VideoFile.durationMsOf(file.name)
            if (videoMs != null) {
                val fractionVideo = progress[hex]
                    ?: if (file.chunkTotal > 0UL) (file.receivedChunks.toFloat() / file.chunkTotal.toFloat()).coerceIn(0f, 1f) else 0f
                Column(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    VideoBubble(
                        viewModel = viewModel,
                        file = file,
                        durationMs = videoMs,
                        preview = previews[hex] ?: if (file.hasPreview) viewModel.getFilePreview(file.fileId) else null,
                        // Смотреть можно принятое: до этого файла на диске нет.
                        available = file.complete || fractionVideo >= 1f || !file.incoming,
                        onSave = { viewModel.downloadFile(file, onSaved) },
                    )
                    if (file.incoming && !file.accepted && !file.complete && fractionVideo < 1f) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            TextButton(onClick = { viewModel.acceptFile(chatId, file.fileId) }) {
                                Text(Strings.FILE_ACCEPT)
                            }
                            TextButton(onClick = { viewModel.declineFile(chatId, file.fileId) }) {
                                Text(Strings.FILE_DECLINE)
                            }
                        }
                    }
                }
                return@forEach
            }

            // Голосовое узнаётся по имени — отдельного поля у ядра нет.
            // Показываем его записью, а не файлом: «12345.voice.ogg»
            // человеку не говорит ничего.
            val voiceMs = chat.ratatosk.desktop.util.VoiceFile.durationMsOf(file.name)
            if (voiceMs != null) {
                val fractionVoice = progress[hex]
                    ?: if (file.chunkTotal > 0UL) (file.receivedChunks.toFloat() / file.chunkTotal.toFloat()).coerceIn(0f, 1f) else 0f
                Column(
                    Modifier
                        .widthIn(min = 240.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    VoiceBubble(
                        viewModel = viewModel,
                        file = file,
                        durationMs = voiceMs,
                        preview = previews[hex] ?: if (file.hasPreview) viewModel.getFilePreview(file.fileId) else null,
                        // Слушать можно принятое: до этого файла на диске нет.
                        available = file.complete || fractionVoice >= 1f || !file.incoming,
                        onSave = { viewModel.downloadFile(file, onSaved) },
                    )
                    if (file.incoming && !file.accepted && !file.complete && fractionVoice < 1f) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            TextButton(onClick = { viewModel.acceptFile(chatId, file.fileId) }) {
                                Text(Strings.FILE_ACCEPT)
                            }
                            TextButton(onClick = { viewModel.declineFile(chatId, file.fileId) }) {
                                Text(Strings.FILE_DECLINE)
                            }
                        }
                    }
                }
                return@forEach
            }
            val fraction = progress[hex] ?: if (file.chunkTotal > 0UL) (file.receivedChunks.toFloat() / file.chunkTotal.toFloat()).coerceIn(0f, 1f) else 0f
            AttachmentCard(
                file = file,
                // Сообщение перечитывается чуть позже события — не ждём его,
                // чтобы «Открыть» и «Сохранить» появились сразу по готовности.
                doneByProgress = fraction >= 1f,
                // Превью — через поток: пришло позже — перерисуется (ревью Android 2.5).
                preview = previews[hex] ?: if (file.hasPreview) viewModel.getFilePreview(file.fileId) else null,
                receiveFraction = fraction,
                sendFraction = sending[hex],
                waitingText = waiting[hex],
                saving = hex in jobs,
                // Ход выкладывания на этот компьютер — свой: у компаньона телефон
                // давно собрал файл целиком, а сюда он ещё едет.
                saveFraction = saveFractions[hex],
                onAccept = { viewModel.acceptFile(chatId, file.fileId) },
                onDecline = { viewModel.declineFile(chatId, file.fileId) },
                onPause = { viewModel.pauseFile(chatId, file.fileId) },
                // Картинку — своим просмотрщиком, остальное — системным приложением.
                onOpen = { viewModel.openMedia(file) },
                onSave = { viewModel.downloadFile(file, onSaved) },
                onCancel = { viewModel.cancelFileJob(file.fileId) },
            )
        }
    }
}

@Composable
private fun AttachmentCard(
    file: FfiFile,
    doneByProgress: Boolean,
    preview: ByteArray?,
    receiveFraction: Float,
    sendFraction: Float?,
    waitingText: String?,
    saving: Boolean,
    saveFraction: Float?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onPause: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val complete = file.complete || doneByProgress
    val available = complete || !file.incoming
    // Приём остановлен: приехавшее на месте, предложение живёт (FFI, pause_file).
    val paused = file.incoming && !file.accepted && !complete && file.receivedChunks > 0UL
    Column(
        Modifier
            .widthIn(min = 240.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
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
        // Подпись — на своей поверхности и своими цветами: раньше она брала
        // полупрозрачный цвет пузыря, и на светлой картинке её было не разобрать.
        val labelColor = MaterialTheme.colorScheme.onSurface
        val subLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        val actionColor = MaterialTheme.colorScheme.primary
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(iconFor(file.name), null, tint = actionColor, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = labelColor)
                Text(
                    stateLine(file, receiveFraction, sendFraction, paused, complete, saving, saveFraction),
                    style = MaterialTheme.typography.labelSmall,
                    color = subLabelColor,
                )
            }
            when {
                saving -> {
                    // Доли ещё нет — честнее крутилка без числа, чем чужие сто процентов.
                    if (saveFraction == null) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = actionColor)
                    } else {
                        CircularProgressIndicator(progress = { saveFraction }, modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = actionColor)
                    }
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, Strings.FILE_CANCEL, tint = MaterialTheme.colorScheme.error) }
                }
                file.incoming && !file.accepted && !complete -> {
                    IconButton(onClick = onDecline) { Icon(Icons.Default.Close, Strings.FILE_DECLINE, tint = MaterialTheme.colorScheme.error) }
                    // Остановленное продолжаем тем же `acceptFile` — с того же места.
                    IconButton(onClick = onAccept) {
                        if (paused) Icon(Icons.Default.PlayArrow, Strings.FILE_RESUME, tint = actionColor)
                        else Icon(Icons.Default.Download, Strings.FILE_ACCEPT, tint = actionColor)
                    }
                }
                available -> {
                    IconButton(onClick = onOpen) { Icon(Icons.AutoMirrored.Filled.OpenInNew, Strings.FILE_OPEN, tint = actionColor) }
                    IconButton(onClick = onSave) { Icon(Icons.Default.SaveAlt, Strings.FILE_SAVE, tint = actionColor) }
                }
                // Качается: «остановить» в середине кольца. Прервать приём было
                // нечем, а «Отклонить» выбросило бы уже приехавшее.
                file.incoming -> Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { receiveFraction }, modifier = Modifier.size(32.dp), strokeWidth = 2.dp, color = actionColor)
                    IconButton(onClick = onPause, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Pause, Strings.FILE_PAUSE, tint = actionColor, modifier = Modifier.size(16.dp))
                    }
                }
                // Своё исходящее остановить нечем: отказа от своей отправки
                // на границе ядра нет.
                else -> CircularProgressIndicator(progress = { receiveFraction }, modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = actionColor)
            }
        }
        // Стоит — словами ядра, почему: «ошибка» и «загрузка» здесь одинаково неправда.
        if (waitingText != null && !complete) {
            Text(
                waitingText,
                style = MaterialTheme.typography.labelSmall,
                color = subLabelColor,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            )
        }
    }
}

private fun stateLine(
    file: FfiFile,
    receive: Float,
    send: Float?,
    paused: Boolean,
    complete: Boolean,
    saving: Boolean,
    saveFraction: Float?,
): String {
    val size = formatFileSize(file.sizeBytes)
    return when {
        // Пока файл едет на этот компьютер, показываем именно это.
        saving && saveFraction != null -> "$size · " + Strings.FILE_FETCHING.format((saveFraction * 100).toInt())
        saving -> "$size · " + Strings.FILE_FETCHING_UNKNOWN
        !file.incoming && send != null && send < 1f -> "$size · " + Strings.FILE_SENDING.format((send * 100).toInt())
        // «Остановлено», а не «отменено»: прочитавший «отменено» не станет
        // продолжать то, что считает потерянным (FFI, pause_file).
        paused -> "$size · " + Strings.FILE_PAUSED.format((receive * 100).toInt())
        file.incoming && file.accepted && !complete -> "$size · " + Strings.FILE_RECEIVING.format((receive * 100).toInt())
        else -> size
    }
}

@Composable
/** Превью байтами — картинкой; годится и волне голосового. */
internal fun rememberPreview(bytes: ByteArray?): ImageBitmap? = remember(bytes) {
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
        b < 1024 -> Strings.SIZE_BYTES.format(b)
        b < 1024 * 1024 -> Strings.SIZE_KILOBYTES.format(b / 1024)
        b < 1024 * 1024 * 1024 -> Strings.SIZE_MEGABYTES.format(b / (1024 * 1024))
        else -> Strings.SIZE_GIGABYTES.format(b / (1024 * 1024 * 1024))
    }
}
