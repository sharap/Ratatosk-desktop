package chat.ratatosk.desktop.util

import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

object ImageUtils {
    private const val TAG = "ImageUtils"

    /**
     * Сколько пикселей декодируем самое большее. Дальше — прореживание при
     * чтении: фото на 50 Мп в ARGB — это 200 МБ кучи, а нам из него нужен
     * квадрат 256×256 или превью 320×320.
     */
    private const val MAX_DECODE_PIXELS = 16_000_000L

    /**
     * Читает картинку, не раздувая память: размеры — из заголовка, затем
     * декодирование с шагом, при котором результат не больше [maxSide] по
     * меньшей стороне (но не меньше нужного) и не больше [MAX_DECODE_PIXELS].
     *
     * `null` — не картинка, неизвестный формат или не хватило памяти.
     */
    fun readImageBounded(file: File, maxSide: Int): BufferedImage? {
        return try {
            ImageIO.createImageInputStream(file)?.use { input ->
                val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return null
                try {
                    reader.input = input
                    val width = reader.getWidth(0).toLong()
                    val height = reader.getHeight(0).toLong()
                    val bySide = (minOf(width, height) / maxSide).coerceAtLeast(1)
                    var step = bySide
                    while ((width / step) * (height / step) > MAX_DECODE_PIXELS) step++
                    val param = reader.defaultReadParam
                    if (step > 1) param.setSourceSubsampling(step.toInt(), step.toInt(), 0, 0)
                    reader.read(0, param)
                } finally {
                    reader.dispose()
                }
            }
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Image too large to decode")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read image", e)
            null
        }
    }

    fun processAvatar(file: File, maxBytes: Int): ByteArray? {
        try {
            val original = readImageBounded(file, maxSide = 256) ?: return null

            // 1. Center crop to square
            val size = minOf(original.width, original.height)
            val x = (original.width - size) / 2
            val y = (original.height - size) / 2
            val cropped = original.getSubimage(x, y, size, size)

            // 2. Resize to 256x256
            val targetSize = 256
            val resized = BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB)
            val g: Graphics2D = resized.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.drawImage(cropped, 0, 0, targetSize, targetSize, null)
            g.dispose()

            // 3. Compress to JPEG under maxBytes
            return compressToJpeg(resized, maxBytes)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Out of memory while processing avatar")
            return null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to process avatar", e)
            return null
        }
    }

    /**
     * Превью вложения: вписано в 320×320, JPEG не больше [maxBytes].
     * Маленькая картинка идёт как есть по размеру, но всё равно перекодируется:
     * отправлять собеседнику исходный файл вместо превью — не то же самое.
     */
    fun makePreview(file: File, maxBytes: Int): ByteArray? {
        return try {
            val original = readImageBounded(file, maxSide = 320) ?: return null
            val ratio = minOf(1.0, minOf(320.0 / original.width, 320.0 / original.height))
            val targetWidth = (original.width * ratio).toInt().coerceAtLeast(1)
            val targetHeight = (original.height * ratio).toInt().coerceAtLeast(1)

            val output = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
            val g = output.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(original, 0, 0, targetWidth, targetHeight, null)
            g.dispose()

            compressToJpeg(output, maxBytes)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Out of memory while making preview")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to make preview", e)
            null
        }
    }

    private fun compressToJpeg(image: BufferedImage, maxBytes: Int): ByteArray? {
        var quality = 0.85f
        val writers = ImageIO.getImageWritersByFormatName("jpeg")
        if (!writers.hasNext()) return null
        val writer = writers.next()

        var result: ByteArray? = null

        try {
            while (quality > 0.1f) {
                val baos = ByteArrayOutputStream()
                val output = ImageIO.createImageOutputStream(baos)
                writer.output = output

                val param = writer.defaultWriteParam
                if (param.canWriteCompressed()) {
                    param.compressionMode = ImageWriteParam.MODE_EXPLICIT
                    param.compressionType = "JPEG"
                    param.compressionQuality = quality
                }

                writer.write(null, IIOImage(image, null, null), param)
                output.flush()
                val currentBytes = baos.toByteArray()
                output.close()

                if (currentBytes.size <= maxBytes) {
                    result = currentBytes
                    break
                }
                quality -= 0.1f
            }
        } finally {
            writer.dispose()
        }
        return result
    }
}
