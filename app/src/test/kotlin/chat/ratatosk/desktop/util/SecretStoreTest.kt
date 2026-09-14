package chat.ratatosk.desktop.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Живое хранилище ОС: кладёт, читает и удаляет пробную запись.
 * Трогает связку ключей человека, поэтому только по явной просьбе:
 * `./gradlew :app:test -Pratatosk.test.keyring=true`.
 */
class SecretStoreTest {
    @Test
    fun roundTripInSystemStore() {
        assumeTrue(System.getProperty("ratatosk.test.keyring") == "true")
        val store = SecretStore.system
        assertTrue("system secret store is not available", store.isAvailable)

        val key = "test:roundtrip"
        val value = ByteArray(32) { it.toByte() }
        try {
            assertTrue(store.put(key, value))
            assertArrayEquals(value, store.get(key))
            // Повторная запись заменяет, а не дублирует.
            val other = ByteArray(32) { (31 - it).toByte() }
            assertTrue(store.put(key, other))
            assertArrayEquals(other, store.get(key))
        } finally {
            store.delete(key)
        }
        assertNull(store.get(key))
    }
}
