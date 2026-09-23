package chat.ratatosk.desktop.ui.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.util.VoiceRecorder
import chat.ratatosk.desktop.util.Waveform
import kotlinx.coroutines.delay

/**
 * Запись голосового: состояние, которое держит экран чата.
 *
 * Запись идёт `ffmpeg`, а волна считается по готовому файлу — тем же
 * `ffmpeg`, вторым проходом. Отправка — обычным вложением: голосовое
 * узнаётся по имени файла.
 */
class VoiceRecordingState(
    private val recorder: VoiceRecorder,
    private val onSend: (java.io.File, ByteArray?) -> Unit,
    private val onError: (String) -> Unit,
) {
    var isRecording by mutableStateOf(false)
        private set

    var elapsedMs by mutableStateOf(0L)
        private set

    fun begin() {
        if (isRecording) return
        if (!VoiceRecorder.available) {
            onError(Strings.VOICE_NO_RECORDER)
            return
        }
        if (!recorder.start()) {
            onError(Strings.VOICE_NO_MIC)
            return
        }
        isRecording = true
        elapsedMs = 0
    }

    fun tick() {
        if (isRecording) elapsedMs = recorder.elapsedMs()
    }

    /** Заканчивает запись; [send] — отправить или стереть. */
    fun finish(send: Boolean) {
        if (!isRecording) return
        isRecording = false
        if (!send) {
            recorder.cancel()
            return
        }
        val done = recorder.stop()
        if (done == null) onError(Strings.VOICE_TOO_SHORT) else onSend(done.file, Waveform.png(done.peaks))
    }

    fun cancelIfRecording() {
        if (isRecording) {
            isRecording = false
            recorder.cancel()
        }
    }
}

/** Заводит запись для этого чата и следит за её временем. */
@Composable
fun rememberVoiceRecording(
    chatId: ByteArray,
    onSend: (java.io.File, ByteArray?) -> Unit,
    onError: (String) -> Unit,
): VoiceRecordingState {
    val state = remember(chatId.contentHashCode()) {
        VoiceRecordingState(VoiceRecorder(), onSend, onError)
    }

    LaunchedEffect(state.isRecording) {
        while (state.isRecording) {
            state.tick()
            delay(100)
        }
    }

    // Уход с экрана посреди записи не должен оставлять микрофон занятым.
    DisposableEffect(Unit) { onDispose { state.cancelIfRecording() } }

    return state
}

/** Полоса записи: сколько идёт, отправить или стереть. */
@Composable
fun VoiceRecordingRow(state: VoiceRecordingState) {
    Row(
        Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Mic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            Strings.VOICE_RECORDING.format(formatVoiceDuration(state.elapsedMs)),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { state.finish(send = false) }) {
            Icon(Icons.Default.Delete, Strings.VOICE_DISCARD, tint = MaterialTheme.colorScheme.error)
        }
        IconButton(onClick = { state.finish(send = true) }) {
            Icon(Icons.Default.Send, Strings.VOICE_SEND)
        }
    }
}
