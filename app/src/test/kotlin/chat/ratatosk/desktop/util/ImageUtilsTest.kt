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
    fun cropRectFollowsScaleAndDrag() {
        // Масштаб 1 — виден квадрат по меньшей стороне, по центру широкой картинки.
        val whole = ImageUtils.avatarCropRect(1000, 600, scale = 1f, offsetXpx = 0f, offsetYpx = 0f, viewportPx = 280)
        assertEquals(600, whole.size)
        assertEquals(200, whole.x)
        assertEquals(0, whole.y)

        // Увеличили вдвое — берём вдвое меньший квадрат, всё ещё по центру.
        val zoomed = ImageUtils.avatarCropRect(1000, 600, scale = 2f, offsetXpx = 0f, offsetYpx = 0f, viewportPx = 280)
        assertEquals(300, zoomed.size)
        assertEquals(350, zoomed.x)
        assertEquals(150, zoomed.y)

        // Потянули картинку вправо — кадр уехал влево, на то же число пикселей исходника.
        val dragged = ImageUtils.avatarCropRect(1000, 600, scale = 2f, offsetXpx = 140f, offsetYpx = 0f, viewportPx = 280)
        assertEquals(350 - 150, dragged.x)

        // За край не выходим ни при каком сдвиге.
        val far = ImageUtils.avatarCropRect(1000, 600, scale = 1f, offsetXpx = -100000f, offsetYpx = 100000f, viewportPx = 280)
        assertEquals(1000 - 600, far.x)
        assertEquals(0, far.y)
    }

    @Test
    fun croppedAvatarIsSquareAndWithinLimit() {
        val file = image(800, 400)
        try {
            val source = ImageUtils.readImageBounded(file, maxSide = 1024)!!
            val rect = ImageUtils.avatarCropRect(source.width, source.height, 1f, 0f, 0f, 280)
            val bytes = ImageUtils.cropAvatar(source, rect, maxBytes = 32 * 1024)!!
            assertTrue(bytes.size <= 32 * 1024)
            val decoded = ImageIO.read(bytes.inputStream())
            assertEquals(256, decoded.width)
            assertEquals(256, decoded.height)
        } finally {
            file.delete()
        }
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
