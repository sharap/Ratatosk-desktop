package chat.ratatosk.desktop.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.util.AppDirs
import chat.ratatosk.desktop.util.VideoFile
import chat.ratatosk.desktop.util.VideoPlayer
import chat.ratatosk.desktop.util.toHexString
import kotlinx.coroutines.delay
import org.ratatosk.core.FfiFile

/**
 * Видеосообщение в пузыре: кружок, который играет по нажатию.
 *
 * Записывать кружки на компьютере пока нечем, и это сказано прямо:
 * здесь только просмотр. Обложка берётся из превью вложения — оно
 * приезжает **до** самого файла, так что кружок виден ещё не принятым.
 *
 * Смотрит [VideoPlayer] через `ffmpeg`: своего проигрывателя у Compose
 * Desktop нет. Нет `ffmpeg` — так и сказано, а запись можно сохранить
 * и открыть чем угодно.
 *
 * Круглым кадр делает показ, а не файл: внутри обычный MP4.
 */
@Composable
fun VideoBubble(
    viewModel: RatatoskViewModel,
    file: FfiFile,
    durationMs: Long,
    preview: ByteArray?,
    available: Boolean,
    onSave: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val player = remember { VideoPlayer(scope) }
    val frame by player.frame.collectAsState()
    var playing by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var decrypted by remember { mutableStateOf<java.io.File?>(null) }

    DisposableEffect(Unit) { onDispose { player.stop() } }

    // Пока играет — тянем место из проигрывателя: он считает по
    // показанным кадрам, и это точнее нашего таймера.
    LaunchedEffect(playing) {
        while (playing) {
            positionMs = player.positionMs
            playing = player.isPlaying
            delay(100)
        }
    }

    /** Готовит расшифрованную копию и делает с ней то, о чём просили. */
    fun withFile(action: (java.io.File) -> Unit) {
        decrypted?.let { action(it); return }
        preparing = true
        val dest = java.io.File(AppDirs.getMediaCacheDir(), file.fileId.toHexString() + ".mp4")
        if (dest.exists() && dest.length() == file.sizeBytes.toLong()) {
            preparing = false
            decrypted = dest
            action(dest)
            return
        }
        viewModel.saveFile(file, dest, onFailure = { preparing = false }) { saved ->
            preparing = false
            decrypted = saved
            action(saved)
        }
    }

    fun start(fromMs: Long) {
        withFile { path ->
            // Доигравшее начинаем сначала: иначе «смотреть» ведёт в самый
            // конец и не показывает ничего.
            val from = if (fromMs >= durationMs - 200) 0 else fromMs
            positionMs = from
            playing = true
            player.play(path, from) { completed ->
                playing = false
                if (completed) {
                    positionMs = durationMs
                    // Последний кадр убираем: дальше кружок — обложка,
                    // а не застывшее лицо на полуслове.
                    player.clearFrame()
                }
            }
        }
    }

    if (!VideoPlayer.available) {
        // Смотреть нечем — но сохранить можно, и это честнее молчащей кнопки.
        Column(Modifier.padding(8.dp)) {
            Text(
                Strings.VIDEO_MESSAGE + ", " + formatVoiceDuration(durationMs),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                Strings.VIDEO_NO_PLAYER,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            if (available) {
                IconButton(onClick = onSave) {
                    Icon(Icons.Default.PlayArrow, contentDescription = Strings.SAVE)
                }
            }
        }
        return
    }

    val poster = rememberPreview(preview)
    val fraction = (positionMs.toFloat() / durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(Color.Black)
                .clickable(enabled = available) {
                    if (playing) {
                        player.stop()
                        playing = false
                    } else {
                        start(positionMs)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val shown = frame ?: poster
            if (shown != null) {
                Image(
                    bitmap = shown,
                    contentDescription = Strings.VIDEO_MESSAGE,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            when {
                preparing -> CircularProgressIndicator(Modifier.size(44.dp), color = Color.White)
                !playing && available -> Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = Strings.VIDEO_PLAY,
                    tint = Color.White,
                    modifier = Modifier.size(52.dp),
                )
            }

            // Сколько проиграно — кольцом по краю: полоса под круглым
            // кадром выглядела бы приделанной сбоку.
            if (playing || positionMs > 0) {
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                    strokeWidth = 3.dp,
                )
            }
        }

        Text(
            // Пока играет или промотано — остаток: важно, сколько ещё.
            text = if (playing || positionMs > 0) {
                "−" + formatVoiceDuration((durationMs - positionMs).coerceAtLeast(0))
            } else {
                formatVoiceDuration(durationMs)
            },
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
