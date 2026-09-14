package chat.ratatosk.desktop.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileUtilsTest {
    @Test
    fun pathTraversalIsReducedToLastSegment() {
        assertEquals("passwd", FileUtils.safeName("../../etc/passwd"))
        assertEquals("evil.exe", FileUtils.safeName("..\\..\\Windows\\evil.exe"))
        assertEquals("file", FileUtils.safeName(".."))
        assertEquals("file", FileUtils.safeName("../"))
        assertEquals("file", FileUtils.safeName(null))
        assertEquals("file", FileUtils.safeName("   "))
    }

    @Test
    fun specialCharactersAndWindowsQuirksAreNeutralised() {
        assertEquals("a_b_c", FileUtils.safeName("a:b*c"))
        assertEquals("report", FileUtils.safeName("report. . "))
        assertEquals("_CON", FileUtils.safeName("CON"))
        assertEquals("_nul.txt", FileUtils.safeName("nul.txt"))
        assertEquals("x_y", FileUtils.safeName("x\u0000y"))
        assertEquals(200, FileUtils.safeName("a".repeat(500)).length)
    }

    @Test
    fun resultNeverEscapesTargetDirectory() {
        val dir = Files.createTempDirectory("safe").toFile()
        try {
            listOf("../x", "..\\x", "/abs/x", "a/../../x", "C:\\x", "....", "x/..").forEach { raw ->
                val target = File(dir, FileUtils.safeName(raw)).canonicalFile
                assertEquals("escaped for $raw", dir.canonicalFile, target.parentFile)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun executablesAreRecognisedDespiteTricks() {
        listOf("setup.exe", "SETUP.EXE", "run.sh", "x.desktop", "a.lnk", "b.jar", "c.exe.", "d.bat ", "../e.msi", "f.tar.gz.exe")
            .forEach { assertTrue("not flagged: $it", FileUtils.isExecutable(it)) }
        listOf("photo.jpg", "doc.pdf", "archive.zip", "notes.txt", "exe", "README", "movie.mp4")
            .forEach { assertFalse("flagged: $it", FileUtils.isExecutable(it)) }
    }

    @Test
    fun uniqueFileDoesNotOverwrite() {
        val dir = Files.createTempDirectory("unique").toFile()
        try {
            assertEquals("photo.jpg", FileUtils.uniqueFile(dir, "photo.jpg").name)
            File(dir, "photo.jpg").writeText("1")
            assertEquals("photo (1).jpg", FileUtils.uniqueFile(dir, "photo.jpg").name)
            File(dir, "photo (1).jpg").writeText("2")
            assertEquals("photo (2).jpg", FileUtils.uniqueFile(dir, "photo.jpg").name)
            File(dir, "README").writeText("3")
            val next = FileUtils.uniqueFile(dir, "README")
            assertEquals("README (1)", next.name)
            assertFalse(next.exists())
            assertTrue(File(dir, ".bashrc").let { FileUtils.uniqueFile(dir, it.name).name == ".bashrc" })
        } finally {
            dir.deleteRecursively()
        }
    }
}
