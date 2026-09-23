package chat.ratatosk.desktop.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.util.AppDirs
import chat.ratatosk.desktop.util.VoiceFile
import chat.ratatosk.desktop.util.VoicePlayer
import chat.ratatosk.desktop.util.toHexString
import kotlinx.coroutines.delay
import org.ratatosk.core.FfiFile

/** Длительность словами: 0:07, 1:23. */
fun formatVoiceDuration(ms: Long): String {
    val total = (ms / 1000).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * Голосовое в пузыре: волна, остаток и перемотка.
 *
 * Волна берётся из превью вложения — оно приезжает **до** самого файла,
 * так что запись видно ещё до приёма. Слушать можно принятое: до этого
 * файла на диске нет, его готовит ядро.
 *
 * Проигрывает [VoicePlayer] через `ffmpeg`: своего декодера Opus на JVM
 * нет. Нет `ffmpeg` — так и сказано, а запись можно сохранить и открыть
 * чем угодно.
 */
@Composable
fun VoiceBubble(
    viewModel: RatatoskViewModel,
    file: FfiFile,
    durationMs: Long,
    preview: ByteArray?,
    available: Boolean,
    onSave: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val player = remember { VoicePlayer(scope) }
    var playing by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var decrypted by remember { mutableStateOf<java.io.File?>(null) }

    DisposableEffect(Unit) { onDispose { player.stop() } }

    // Пока играет — тянем место из проигрывателя: он считает по отданным
    // кадрам, и это точнее, чем наш таймер.
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
        val dest = java.io.File(AppDirs.getMediaCacheDir(), file.fileId.toHexString() + ".ogg")
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
            // Доигравшую запись начинаем сначала: иначе «слушать» ведёт
            // в самый конец и не играет ничего.
            val from = if (fromMs >= durationMs - 200) 0 else fromMs
            positionMs = from
            playing = true
            player.play(path, from) { completed ->
                playing = false
                if (completed) positionMs = 0
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
        when {
            !VoicePlayer.available -> {
                // Слушать нечем — но сохранить можно, и это честнее
                // молчащей кнопки.
                Column(Modifier.weight(1f)) {
                    Text(Strings.VOICE_MESSAGE + ", " + formatVoiceDuration(durationMs), style = MaterialTheme.typography.bodyMedium)
                    Text(Strings.VOICE_NO_PLAYER, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                if (available) {
                    IconButton(onClick = onSave) {
                        Icon(Icons.Default.PlayArrow, contentDescription = Strings.SAVE)
                    }
                }
                return@Row
            }
            preparing -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            playing -> IconButton(onClick = {
                player.stop()
                playing = false
            }) {
                Icon(Icons.Default.Stop, contentDescription = Strings.VOICE_PAUSE)
            }
            else -> IconButton(onClick = { if (available) start(positionMs) }) {
                Icon(Icons.Default.PlayArrow, contentDescription = Strings.VOICE_PLAY)
            }
        }

        Spacer(Modifier.width(4.dp))

        val wave = rememberPreview(preview)
        // Возить можно и по волне, и по пустому месту: перемотка нужна
        // и записям без превью.
        Column(
            modifier = Modifier
                .width(160.dp)
                .pointerInput(file.fileId.contentHashCode(), available) {
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    detectTapGestures { offset ->
                        if (available) start((durationMs * (offset.x / width)).toLong().coerceAtLeast(0))
                    }
                }
                .pointerInput(file.fileId.contentHashCode(), available) {
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    detectHorizontalDragGestures { change, _ ->
                        if (available) {
                            val target = (durationMs * (change.position.x / width)).toLong().coerceAtLeast(0)
                            positionMs = target
                            player.seekTo(target)
                        }
                    }
                }
        ) {
            if (wave != null) {
                Image(
                    bitmap = wave,
                    contentDescription = Strings.VOICE_MESSAGE,
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                )
            } else {
                Spacer(Modifier.fillMaxWidth().height(28.dp))
            }
            LinearProgressIndicator(
                progress = { (positionMs.toFloat() / durationMs.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp),
            )
        }

        Spacer(Modifier.width(8.dp))
        Text(
            // Пока играет или промотано — остаток: важно, сколько ещё.
            text = if (playing || positionMs > 0) {
                "−" + formatVoiceDuration((durationMs - positionMs).coerceAtLeast(0))
            } else {
                formatVoiceDuration(durationMs)
            },
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
