package chat.ratatosk.desktop.util

import java.io.File

/**
 * Запись голосового на компьютере.
 *
 * Пишет `ffmpeg`: он же единственное, чем мы умеем декодировать Opus
 * (своего декодера на JVM нет), и он же умеет взять звук с микрофона.
 * Значит и тут, и там зависимость одна — а не два разных механизма,
 * расходящихся по возможностям.
 *
 * Громкость снимается **после** записи, вторым проходом того же
 * `ffmpeg`: он декодирует готовый файл в сырой поток, а мы считаем
 * по нему столбики. Так честнее, чем гадать по ходу, и заодно оттуда
 * же берётся точная длительность.
 */
class VoiceRecorder {
    companion object {
        private const val TOOL = "ffmpeg"
        private const val RATE = 48_000
        private const val BARS = 48

        /** Есть ли чем писать: тот же `ffmpeg`, что и для проигрывания. */
        val available: Boolean get() = VoicePlayer.available
    }

    private var process: Process? = null
    private var target: File? = null
    private var startedAtMs = 0L

    val isRecording: Boolean get() = process != null

    fun elapsedMs(): Long = if (process == null) 0 else System.currentTimeMillis() - startedAtMs

    /**
     * Начинает запись.
     *
     * Пробует `pulse`, затем `alsa`: первое есть почти всегда, второе —
     * когда звуковой сервер не поднят. Не вышло ни то ни другое —
     * `false`, и человеку надо сказать словами.
     */
    fun start(): Boolean {
        if (process != null || !available) return false
        val dir = File(AppDirs.getMediaCacheDir(), "voice").apply { mkdirs() }
        val file = File(dir, "recording-${System.currentTimeMillis()}.ogg")

        for (device in listOf("pulse" to "default", "alsa" to "default")) {
            val started = try {
                ProcessBuilder(
                    TOOL,
                    "-hide_banner", "-loglevel", "error",
                    "-f", device.first, "-i", device.second,
                    "-ac", "1", "-ar", RATE.toString(),
                    "-c:a", "libopus", "-b:a", "24k",
                    "-y", file.absolutePath,
                ).redirectErrorStream(true).start()
            } catch (t: Throwable) {
                Log.w("VoiceRecorder", "Failed to start ${device.first}", t)
                null
            } ?: continue

            // Если микрофона нет, ffmpeg умирает сразу — тогда пробуем
            // следующий способ, а не показываем идущую запись впустую.
            if (started.waitFor(400, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                Log.w("VoiceRecorder", "Device ${device.first} refused the microphone")
                continue
            }
            process = started
            target = file
            startedAtMs = System.currentTimeMillis()
            return true
        }
        file.delete()
        return false
    }

    /**
     * Останавливает запись и готовит её к отправке.
     *
     * @return `null`, если записать ничего не удалось: слишком коротко,
     *   пусто или оборвалось. Пустое отправлять нельзя — собеседник
     *   увидит полосу, за которой ничего нет.
     */
    fun stop(): Recording? {
        val running = process ?: return null
        val file = target
        process = null
        target = null

        // «q» в стандартный ввод — просьба закончить по-хорошему: иначе
        // Ogg останется недописанным, и получится файл без конца.
        try {
            running.outputStream.write("q\n".toByteArray())
            running.outputStream.flush()
        } catch (t: Throwable) {
            Log.w("VoiceRecorder", "Failed to ask ffmpeg to stop", t)
        }
        if (!running.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
            running.destroy()
        }

        if (file == null || !file.exists() || file.length() == 0L || !VoiceFile.looksLikeOgg(file)) {
            file?.delete()
            return null
        }

        val measured = measure(file)
        if (measured == null || measured.durationMs < 500) {
            file.delete()
            return null
        }

        val named = File(file.parentFile, VoiceFile.name(measured.durationMs))
        file.renameTo(named)
        return Recording(named, measured.durationMs, measured.peaks)
    }

    /** Бросает запись и стирает файл: человек передумал. */
    fun cancel() {
        val running = process ?: return
        process = null
        running.destroy()
        target?.delete()
        target = null
    }

    /**
     * Считает длительность и столбики громкости по готовому файлу.
     *
     * Тем же `ffmpeg`: он декодирует Opus в сырой поток, а по нему
     * и длительность точна (кадры делим на частоту), и пики честные.
     */
    private fun measure(file: File): Measured? = try {
        val decoder = ProcessBuilder(
            TOOL,
            "-hide_banner", "-loglevel", "error",
            "-i", file.absolutePath,
            "-f", "s16le", "-acodec", "pcm_s16le",
            "-ar", RATE.toString(), "-ac", "1",
            "-",
        ).start()

        val pcm = decoder.inputStream.use { it.readBytes() }
        decoder.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)

        val frames = pcm.size / 2
        if (frames == 0) return null

        val peaks = ArrayList<Float>(BARS)
        val perBar = (frames / BARS).coerceAtLeast(1)
        var index = 0
        while (index < frames && peaks.size < BARS) {
            var peak = 0
            var i = index
            val end = (index + perBar).coerceAtMost(frames)
            while (i < end) {
                val sample = ((pcm[i * 2 + 1].toInt() shl 8) or (pcm[i * 2].toInt() and 0xff)).toShort().toInt()
                val abs = if (sample < 0) -sample else sample
                if (abs > peak) peak = abs
                i++
            }
            peaks += peak / 32767f
            index = end
        }

        Measured(frames * 1000L / RATE, peaks)
    } catch (t: Throwable) {
        Log.w("VoiceRecorder", "Failed to measure a recording", t)
        null
    }

    private class Measured(val durationMs: Long, val peaks: List<Float>)

    /** Готовая запись: файл, длительность и столбики громкости. */
    class Recording(val file: File, val durationMs: Long, val peaks: List<Float>)
}
