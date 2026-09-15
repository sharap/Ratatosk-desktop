package chat.ratatosk.desktop.core

import chat.ratatosk.desktop.util.toHexString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ratatosk.core.AccountRegistry
import org.ratatosk.core.FfiArchiveUnlock
import org.ratatosk.core.FfiExportScope
import org.ratatosk.core.importArchive
import org.ratatosk.core.peekArchive
import java.io.File
import java.nio.file.Files

/**
 * Вывоз и ввоз архива на настоящем ядре, во временных каталогах — не в
 * `~/.ratatosk`. Проверяет то, на что опирается приложение: раскладку
 * `<id>.db` + `<id>.files`, внесение в реестр, открытие прежним PIN.
 */
class ArchiveRoundTripTest {
    private val tmp = Files.createTempDirectory("archive").toFile()

    @After
    fun tearDown() {
        tmp.deleteRecursively()
    }

    private fun exportFrom(pin: String?, deviceKey: ByteArray?, phrase: String?): Pair<File, String> {
        val root = File(tmp, "old").apply { mkdirs() }
        val registry = AccountRegistry.open(root.absolutePath)
        val account = registry.create("old")
        val client = registry.openAccount(account.id, pin, deviceKey, "Old")
        try {
            val archive = File(tmp, "backup.ratatosk")
            val exported = client.exportHistory(archive.absolutePath, FfiExportScope.EVERYTHING, phrase)
            assertEquals(phrase != null, exported.lockedByPhrase)
            assertTrue(archive.exists())
            return archive to exported.keyText
        } finally {
            client.destroy()
            registry.destroy()
        }
    }

    private fun importInto(archive: File, unlock: FfiArchiveUnlock): Pair<AccountRegistry, ByteArray> {
        val root = File(tmp, "new").apply { mkdirs() }
        val registry = AccountRegistry.open(root.absolutePath)
        val id = ByteArray(16) { (it + 7).toByte() }
        val hex = id.toHexString()
        importArchive(archive.absolutePath, unlock, File(root, "$hex.db").absolutePath, File(root, "$hex.files").absolutePath)
        registry.adopt(id, "restored")
        return registry to id
    }

    @Test
    fun restoredAccountOpensWithOldPin() {
        val (archive, key) = exportFrom(pin = "1234", deviceKey = null, phrase = "long phrase")
        assertTrue(peekArchive(archive.absolutePath).takesPassphrase)

        val (registry, id) = importInto(archive, FfiArchiveUnlock.Key(key))
        try {
            assertTrue(registry.list().any { it.id.contentEquals(id) })
            registry.openAccount(id, "1234", null, "New").destroy()
            assertFalse(runCatching { registry.openAccount(id, "0000", null, "New").destroy() }.isSuccess)
        } finally {
            registry.destroy()
        }
    }

    @Test
    fun deviceBoundAccountNeedsItsDeviceKeyAfterImport() {
        val deviceKey = ByteArray(32) { 5 }
        val (archive, _) = exportFrom(pin = "1234", deviceKey = deviceKey, phrase = "phrase")
        val (registry, id) = importInto(archive, FfiArchiveUnlock.Passphrase("phrase"))
        try {
            // Секрет устройства в архив не едет: на другой машине такой аккаунт не открыть.
            assertFalse(runCatching { registry.openAccount(id, "1234", null, "New").destroy() }.isSuccess)
            registry.openAccount(id, "1234", deviceKey, "New").destroy()
        } finally {
            registry.destroy()
        }
    }
}
