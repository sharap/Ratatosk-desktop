package chat.ratatosk.desktop.core

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Библиотека ядра находится JNA из ресурсов, а биндинги от той же сборки.
 *
 * Первый же вызов грузит библиотеку и сверяет версию контракта и контрольные
 * суммы всех функций: разойдись биндинги с `.so` — упадёт здесь, а не у
 * человека при первом нажатии.
 */
class NativeLibraryTest {
    @Test
    fun libraryLoadsFromResourcesAndMatchesBindings() {
        println("JNA resource prefix: ${com.sun.jna.Platform.RESOURCE_PREFIX}")
        assertTrue(org.ratatosk.core.noPinWarning().isNotBlank())
    }
}
