package chat.ratatosk.desktop.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Записи сопряжений переживают перезапуск.
 *
 * Формат хранится строкой и уже менялся (добавился признак Tor), а имя телефона
 * задаёт его владелец — разделители формата в нём не редкость.
 */
class CompanionPairingsTest {
    private val dir = Files.createTempDirectory("settings").toFile()
    private val settings = SettingsRepository(File(dir, "s.preferences_pb"))

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun pairingSurvivesSaveAndRead() = runBlocking {
        settings.saveCompanionPairing(
            SettingsRepository.CompanionPairing("aa11", "Телефон Пети", useCache = true, useTor = true)
        )
        settings.saveCompanionPairing(
            SettingsRepository.CompanionPairing("bb22", "Второй", useCache = false, useTor = false)
        )

        val saved = settings.companionPairings.first()
        assertEquals(2, saved.size)
        val first = saved.first { it.deviceId == "aa11" }
        assertEquals("Телефон Пети", first.phoneName)
        assertTrue(first.useCache)
        assertTrue("признак Tor должен сохраняться", first.useTor)
        val second = saved.first { it.deviceId == "bb22" }
        assertFalse(second.useCache)
        assertFalse(second.useTor)
    }

    @Test
    fun savingAgainReplacesTheSameDevice() = runBlocking {
        settings.saveCompanionPairing(SettingsRepository.CompanionPairing("aa11", "Старое имя", useCache = true))
        settings.saveCompanionPairing(SettingsRepository.CompanionPairing("aa11", "Новое имя", useCache = false, useTor = true))

        val saved = settings.companionPairings.first()
        assertEquals(1, saved.size)
        assertEquals("Новое имя", saved.single().phoneName)
        assertTrue(saved.single().useTor)
    }

    @Test
    fun separatorsInPhoneNameDoNotBreakTheRecord() = runBlocking {
        settings.saveCompanionPairing(SettingsRepository.CompanionPairing("cc33", "A||B;;C", useCache = true))
        settings.saveCompanionPairing(SettingsRepository.CompanionPairing("dd44", "Обычное", useCache = true))

        val saved = settings.companionPairings.first()
        assertEquals(2, saved.size)
        assertEquals(setOf("cc33", "dd44"), saved.map { it.deviceId }.toSet())
    }

    @Test
    fun removingForgetsOnlyThatDevice() = runBlocking {
        settings.saveCompanionPairing(SettingsRepository.CompanionPairing("aa11", "Один", useCache = true))
        settings.saveCompanionPairing(SettingsRepository.CompanionPairing("bb22", "Два", useCache = true))
        settings.removeCompanionPairing("aa11")

        assertEquals(listOf("bb22"), settings.companionPairings.first().map { it.deviceId })
    }
}
