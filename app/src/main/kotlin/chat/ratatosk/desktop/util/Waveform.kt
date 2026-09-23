package chat.ratatosk.desktop.util

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Волна голосового — картинкой в превью вложения.
 *
 * Превью приезжает **до** самого файла: по нему видно, что это запись
 * и какой длины, ещё до приёма. Рисуем из громкости, снятой с готового
 * файла — декодировать Opus умеет только `ffmpeg`, и он это уже сделал.
 */
object Waveform {
    private const val WIDTH = 240
    private const val HEIGHT = 48
    private const val BARS = 48

    /** Рисует волну по столбикам громкости (0..1); `null` — рисовать нечего. */
    fun png(peaks: List<Float>): ByteArray? {
        if (peaks.isEmpty()) return null
        val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.WHITE
        g.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

        val step = WIDTH.toFloat() / BARS
        for (i in 0 until BARS) {
            // Каждому столбику — своя доля записи; у короткой они
            // повторяются, и это честнее, чем рисовать пустоту.
            val from = i * peaks.size / BARS
            val to = ((i + 1) * peaks.size / BARS).coerceAtLeast(from + 1)
            val level = peaks.subList(from.coerceAtMost(peaks.size - 1), to.coerceAtMost(peaks.size))
                .maxOrNull() ?: 0f
            val height = (HEIGHT - 6) * level.coerceIn(0.05f, 1f)
            val x = (step * i + step / 2).toInt()
            g.drawLine(x, ((HEIGHT - height) / 2).toInt(), x, ((HEIGHT + height) / 2).toInt())
        }
        g.dispose()

        val out = ByteArrayOutputStream()
        return if (ImageIO.write(image, "png", out)) out.toByteArray() else null
    }
}
