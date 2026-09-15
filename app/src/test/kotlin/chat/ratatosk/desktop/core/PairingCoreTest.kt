package chat.ratatosk.desktop.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ratatosk.core.AccountRegistry
import org.ratatosk.core.EventObserver
import org.ratatosk.core.FfiEvent
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Сопряжение со стороны полного клиента на настоящем ядре: ссылка приходит
 * событием с префиксом сопряжения, запись появляется в списке, отзыв её
 * убирает. Сама ссылка в вывод теста не печатается — это секрет.
 */
class PairingCoreTest {
    private val tmp = Files.createTempDirectory("pairing").toFile()

    @After
    fun tearDown() {
        tmp.deleteRecursively()
    }

    @Test
    fun linuxBuildHasItsOwnBluetoothRadio() {
        org.junit.Assume.assumeTrue(System.getProperty("os.name").lowercase().contains("linux"))
        val registry = AccountRegistry.open(tmp.absolutePath)
        val client = registry.openAccount(registry.create("bt").id, "1234", null, "Me")
        try {
            // Сборка с `bt` берёт радио у BlueZ: вручать нечего, раздел настроек показывается.
            assertTrue(client.bluetooth().use { it.hasRadio() })
        } finally {
            client.destroy()
            registry.destroy()
        }
    }

    @Test
    fun pairListRevoke() {
        val registry = AccountRegistry.open(tmp.absolutePath)
        val account = registry.create("acc")
        val client = registry.openAccount(account.id, "1234", null, "Me")
        val events = LinkedBlockingQueue<FfiEvent>()
        client.setObserver(object : EventObserver {
            override fun onEvent(event: FfiEvent) { events.add(event) }
        })
        try {
            client.pairDevice("Laptop")
            var ready: FfiEvent.PairingReady? = null
            val deadline = System.currentTimeMillis() + 10_000
            while (ready == null && System.currentTimeMillis() < deadline) {
                ready = events.poll(200, TimeUnit.MILLISECONDS) as? FfiEvent.PairingReady
            }
            assertNotNull("PairingReady did not arrive", ready)
            assertTrue(ready!!.uri.startsWith("ratatosk:v0:pair:"))

            val device = client.devices().single()
            assertTrue(device.deviceId.contentEquals(ready.deviceId))
            assertEquals("Laptop", device.label)
            assertEquals(0UL, device.lastSeenMs)

            client.revokePairing(ready.deviceId)
            assertTrue(client.devices().isEmpty())
        } finally {
            client.destroy()
            registry.destroy()
        }
    }
}
