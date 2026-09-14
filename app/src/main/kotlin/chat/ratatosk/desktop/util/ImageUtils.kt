package chat.ratatosk.desktop.util

import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import javax.imageio.IIOImage
import javax.imageio.ImageWriteParam

object ImageUtils {

    fun processAvatar(file: File, maxBytes: Int): ByteArray? {
        try {
            val original = ImageIO.read(file) ?: return null
            
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
        } catch (e: Exception) {
            System.err.println("ImageUtils: Error processing avatar: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    private fun compressToJpeg(image: BufferedImage, maxBytes: Int): ByteArray? {
        var quality = 0.85f
        val writers = ImageIO.getImageWritersByFormatName("jpeg")
        if (!writers.hasNext()) return null
        val writer = writers.next()
        
        var result: ByteArray? = null
        
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
        
        writer.dispose()
        return result
    }
}
