package chat.ratatosk.desktop.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * Проигрывание голосового на компьютере.
 *
 * Opus декодировать нечем: чистого Java-декодера в Maven Central нет
 * (Concentus не опубликован), а тащить ради голосовых сотню мегабайт
 * нативных библиотек — плохая сделка. Зато `ffmpeg` есть почти в любой
 * системе, и он же умеет начать с нужной секунды — то есть даёт и
 * перемотку.
 *
 * Если `ffmpeg` не найден, слушать нечем, и об этом надо сказать
 * словами: [available] отвечает на это до нажатия, а не после.
 */
class VoicePlayer(private val scope: CoroutineScope) {
    companion object {
        private const val RATE = 48_000
        private const val TOOL = "ffmpeg"

        /** Есть ли чем проигрывать: `ffmpeg` в `PATH`. */
        val available: Boolean by lazy {
            try {
                val probe = ProcessBuilder(TOOL, "-version")
                    .redirectErrorStream(true)
                    .start()
                probe.inputStream.use { it.readBytes() }
                probe.waitFor() == 0
            } catch (t: Throwable) {
                false
            }
        }
    }

    private var job: Job? = null
    private var process: Process? = null
    private var line: SourceDataLine? = null

    /** Где мы в записи, в миллисекундах; читает экран. */
    @Volatile
    var positionMs: Long = 0
        private set

    @Volatile
    var isPlaying: Boolean = false
        private set

    /**
     * Играет [file], начиная с [fromMs].
     *
     * @param onFinished конец проигрывания; `true` — запись доиграла
     *   до конца, `false` — её остановили. Разница не косметическая:
     *   доигравшую надо начинать сначала, а остановленную — с места,
     *   где бросили. Свалив их в одно, получаем кнопку «слушать»,
     *   которая после конца записи не делает ничего.
     */
    fun play(file: File, fromMs: Long, onFinished: (completed: Boolean) -> Unit) {
        stop()
        if (!available || !VoiceFile.looksLikeOgg(file)) {
            onFinished(false)
            return
        }

        positionMs = fromMs
        isPlaying = true
        job = scope.launch(Dispatchers.IO) {
            val started = fromMs
            var played = 0L
            var reachedEnd = false
            try {
                // Просим у ffmpeg сырой поток: с ним JavaSound умеет всё,
                // а разбирать Ogg самим было бы нечем.
                val built = ProcessBuilder(
                    TOOL,
                    "-hide_banner", "-loglevel", "error",
                    "-ss", "%.3f".format(java.util.Locale.ROOT, started / 1000.0),
                    "-i", file.absolutePath,
                    "-f", "s16le", "-acodec", "pcm_s16le",
                    "-ar", RATE.toString(), "-ac", "1",
                    "-",
                ).redirectErrorStream(false).start()
                process = built

                val format = AudioFormat(RATE.toFloat(), 16, 1, true, false)
                val out = AudioSystem.getSourceDataLine(format).apply {
                    open(format)
                    start()
                }
                line = out

                val buffer = ByteArray(4096)
                built.inputStream.use { input ->
                    while (isActive) {
                        val read = input.read(buffer)
                        if (read <= 0) {
                            // Поток кончился сам — значит запись доиграла.
                            reachedEnd = true
                            break
                        }
                        out.write(buffer, 0, read)
                        // Две байты на кадр, кадров в секунде — RATE.
                        played += read / 2
                        positionMs = started + played * 1000 / RATE
                    }
                }
                if (isActive) out.drain()
            } catch (t: Throwable) {
                Log.w("VoicePlayer", "Failed to play a voice message", t)
            } finally {
                closeAudio()
                isPlaying = false
                onFinished(reachedEnd && isActive)
            }
        }
    }

    /** Останавливает проигрывание; место в записи сохраняется. */
    fun stop() {
        job?.cancel()
        job = null
        closeAudio()
        isPlaying = false
    }

    /** Перемотка: место запоминаем, играть начнём с него. */
    fun seekTo(ms: Long) {
        positionMs = ms.coerceAtLeast(0)
    }

    private fun closeAudio() {
        try {
            line?.stop()
            line?.close()
        } catch (t: Throwable) {
            // Закрытие не должно мешать ответу
        }
        line = null
        process?.destroy()
        process = null
    }
}
