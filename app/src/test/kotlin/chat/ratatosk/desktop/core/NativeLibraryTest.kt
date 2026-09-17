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

    @Test
    fun libraryLiesWhereJnaLooksForIt() {
        // `DESKTOP.md` просит сверять это на каждой платформе: библиотека
        // кладётся в каталог с префиксом JNA, и разойдись они — дистрибутив
        // соберётся, а приложение не запустится.
        val prefix = com.sun.jna.Platform.RESOURCE_PREFIX
        val onClasspath = javaClass.getResource("/$prefix/libratatosk_ffi.so")
            ?: javaClass.getResource("/$prefix/ratatosk_ffi.dll")
            ?: javaClass.getResource("/$prefix/libratatosk_ffi.dylib")
        assertTrue("нет библиотеки в ресурсах для префикса $prefix", onClasspath != null)
    }

    @Test
    fun yggAddressIsDerivedByCore() {
        val address = org.ratatosk.core.yggAddress(ByteArray(32) { 0x11 })
        println("ygg address for 0x11…: $address")
        assertTrue(address != null && address.startsWith("2"))
        assertTrue(org.ratatosk.core.yggAddress(ByteArray(0)) == null)
    }
}
