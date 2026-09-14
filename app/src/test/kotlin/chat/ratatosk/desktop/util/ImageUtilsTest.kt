package chat.ratatosk.desktop.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

class ImageUtilsTest {
    private fun image(width: Int, height: Int): File {
        val file = Files.createTempFile("img", ".png").toFile()
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", file)
        return file
    }

    @Test
    fun largeImageIsSubsampledOnRead() {
        val file = image(4000, 3000)
        try {
            val img = ImageUtils.readImageBounded(file, maxSide = 256)!!
            // Не меньше нужного по меньшей стороне, но и не полный размер.
            assertTrue(minOf(img.width, img.height) >= 256)
            assertTrue(img.width < 4000)
        } finally {
            file.delete()
        }
    }

    @Test
    fun smallImageStillGetsPreview() {
        val file = image(100, 50)
        try {
            val bytes = ImageUtils.makePreview(file, maxBytes = 64 * 1024)
            assertNotNull(bytes)
            val decoded = ImageIO.read(bytes!!.inputStream())
            assertEquals(100, decoded.width)
            assertEquals(50, decoded.height)
        } finally {
            file.delete()
        }
    }

    @Test
    fun notAnImageGivesNull() {
        val file = Files.createTempFile("txt", ".png").toFile()
        try {
            file.writeText("not an image")
            assertNull(ImageUtils.readImageBounded(file, maxSide = 256))
            assertNull(ImageUtils.processAvatar(file, maxBytes = 64 * 1024))
        } finally {
            file.delete()
        }
    }
}
