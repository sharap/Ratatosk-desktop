package chat.ratatosk.desktop.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.image.BufferedImage
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * Просмотр видеосообщения на компьютере.
 *
 * Своего проигрывателя у Compose Desktop нет, а тащить его сюда ради
 * кружков — сотня мегабайт нативных библиотек. Зато `ffmpeg` уже нужен
 * голосовым ([VoicePlayer]), и он же годится здесь: два потока из одного
 * файла — кадры и звук, каждый своим процессом.
 *
 * Кадры просим сырыми и квадратными: тогда каждый ровно
 * `SIDE * SIDE * 3` байта, и разбирать формат картинки не нужно вовсе.
 * Звук — тот же PCM, что у голосового.
 *
 * Время ведёт показ кадров, а не звуковая карта: за минуту кружка два
 * этих счётчика расходятся на считанные миллисекунды, а вот звук без
 * `ffmpeg` не заиграет вообще — и тогда о просмотре надо сказать словами,
 * а не показывать кнопку, которая не делает ничего.
 */
class VideoPlayer(private val scope: CoroutineScope) {
    companion object {
        /** Сторона кадра: кружок на экране меньше, запас на масштаб. */
        const val SIDE = 240

        /** Кадров в секунду: кружок — это лицо, а не футбол. */
        const val FPS = 15

        private const val RATE = 48_000
        private const val TOOL = "ffmpeg"

        /** Есть ли чем смотреть: тот же `ffmpeg`, что и у голосовых. */
        val available: Boolean get() = VoicePlayer.available
    }

    private val _frame = MutableStateFlow<ImageBitmap?>(null)

    /** Последний показанный кадр; читает экран. */
    val frame: StateFlow<ImageBitmap?> = _frame.asStateFlow()

    @Volatile
    var positionMs: Long = 0
        private set

    @Volatile
    var isPlaying: Boolean = false
        private set

    private var job: Job? = null
    private var videoProcess: Process? = null
    private var audioProcess: Process? = null
    private var line: SourceDataLine? = null

    /**
     * Играет [file] с [fromMs].
     *
     * @param onFinished `true` — запись доиграла до конца, `false` — её
     *   остановили. Разница не косметическая: доигравшую надо начинать
     *   сначала, а остановленную — с места, где бросили.
     */
    fun play(file: File, fromMs: Long, onFinished: (completed: Boolean) -> Unit) {
        stop()
        if (!available || !VideoFile.looksLikeMp4(file)) {
            onFinished(false)
            return
        }

        val from = fromMs.coerceAtLeast(0)
        positionMs = from
        isPlaying = true
        job = scope.launch(Dispatchers.IO) {
            var reachedEnd = false
            try {
                // Звук — отдельным процессом: у ffmpeg один выход, и класть
                // в него и кадры, и PCM нечем.
                val sound = launch { playSound(file, from) }

                val video = ProcessBuilder(
                    TOOL,
                    "-hide_banner", "-loglevel", "error",
                    "-ss", "%.3f".format(java.util.Locale.ROOT, from / 1000.0),
                    "-i", file.absolutePath,
                    // Квадрат нужен показу: кружок вырезается из середины,
                    // и обрезать его здесь дешевле, чем в каждом кадре.
                    "-vf", "fps=$FPS,scale=$SIDE:$SIDE:force_original_aspect_ratio=increase,crop=$SIDE:$SIDE",
                    "-f", "rawvideo", "-pix_fmt", "rgb24",
                    "-an",
                    "-",
                ).redirectErrorStream(false).start()
                videoProcess = video

                val bytesPerFrame = SIDE * SIDE * 3
                val buffer = ByteArray(bytesPerFrame)
                val startedAtNs = System.nanoTime()
                var index = 0L
                video.inputStream.use { input ->
                    while (isActive) {
                        var read = 0
                        while (read < bytesPerFrame) {
                            val got = input.read(buffer, read, bytesPerFrame - read)
                            if (got <= 0) break
                            read += got
                        }
                        if (read < bytesPerFrame) {
                            // Поток кончился сам — значит запись доиграла.
                            reachedEnd = true
                            break
                        }

                        val dueMs = index * 1000 / FPS
                        val aheadMs = dueMs - (System.nanoTime() - startedAtNs) / 1_000_000
                        index++
                        // Отстали — кадр пропускаем: догонять его показом
                        // значит расходиться со звуком навсегда.
                        if (aheadMs < -(1000 / FPS)) continue
                        if (aheadMs > 0) kotlinx.coroutines.delay(aheadMs)

                        _frame.value = imageOf(buffer)
                        positionMs = from + dueMs
                    }
                }
                sound.cancel()
            } catch (t: Throwable) {
                Log.w("VideoPlayer", "Failed to play a video message", t)
            } finally {
                close()
                isPlaying = false
                onFinished(reachedEnd && isActive)
            }
        }
    }

    /** Останавливает просмотр; место в записи сохраняется. */
    fun stop() {
        job?.cancel()
        job = null
        close()
        isPlaying = false
    }

    /** Перемотка: место запоминаем, играть начнём с него. */
    fun seekTo(ms: Long) {
        positionMs = ms.coerceAtLeast(0)
    }

    /** Забыть показанное: пузырь ушёл с экрана. */
    fun clearFrame() {
        _frame.value = null
    }

    private suspend fun playSound(file: File, fromMs: Long) {
        try {
            val built = ProcessBuilder(
                TOOL,
                "-hide_banner", "-loglevel", "error",
                "-ss", "%.3f".format(java.util.Locale.ROOT, fromMs / 1000.0),
                "-i", file.absolutePath,
                "-vn",
                "-f", "s16le", "-acodec", "pcm_s16le",
                "-ar", RATE.toString(), "-ac", "1",
                "-",
            ).redirectErrorStream(false).start()
            audioProcess = built

            val format = AudioFormat(RATE.toFloat(), 16, 1, true, false)
            val out = AudioSystem.getSourceDataLine(format).apply {
                open(format)
                start()
            }
            line = out

            val buffer = ByteArray(4096)
            built.inputStream.use { input ->
                while (kotlinx.coroutines.currentCoroutineContext()[Job]?.isActive != false) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                }
            }
        } catch (t: Throwable) {
            // Немое видео — не беда: кадры важнее, и о них речь.
            Log.w("VideoPlayer", "No sound for the video message", t)
        }
    }

    /** Сырой RGB в картинку: раскладка кадра известна заранее. */
    private fun imageOf(rgb: ByteArray): ImageBitmap {
        val image = BufferedImage(SIDE, SIDE, BufferedImage.TYPE_INT_RGB)
        var at = 0
        for (y in 0 until SIDE) {
            for (x in 0 until SIDE) {
                val r = rgb[at].toInt() and 0xFF
                val g = rgb[at + 1].toInt() and 0xFF
                val b = rgb[at + 2].toInt() and 0xFF
                at += 3
                image.setRGB(x, y, (r shl 16) or (g shl 8) or b)
            }
        }
        return image.toComposeImageBitmap()
    }

    private fun close() {
        try {
            line?.stop()
            line?.close()
        } catch (t: Throwable) {
            // Закрытие не должно мешать ответу
        }
        line = null
        videoProcess?.destroy()
        videoProcess = null
        audioProcess?.destroy()
        audioProcess = null
    }
}
