package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.ui.settings.yggTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ratatosk.core.FfiYggMode

class TransportInputTest {
    private val key = "a".repeat(64)

    @Test
    fun yggKeyAcceptsOnly32Bytes() {
        assertNotNull(TransportInput.parseYggKey(key))
        assertNotNull(TransportInput.parseYggKey(key.chunked(2).joinToString(":")))
        assertNotNull(TransportInput.parseYggKey("  ${key.uppercase()}\n"))
        assertNull(TransportInput.parseYggKey(key.dropLast(1)))
        assertNull(TransportInput.parseYggKey("g" + key.drop(1)))
        assertNull(TransportInput.parseYggKey(""))
    }

    @Test
    fun yggPeers() {
        listOf("tcp://1.2.3.4:5678", "tls://node.example:443", "quic://[2001:db8::1]:9000", "tcp://h:1?key=abc")
            .forEach { assertTrue(it, TransportInput.isValidYggPeer(it)) }
        listOf("1.2.3.4:5678", "http://h:80", "tcp://h", "tcp://h:0", "tcp://h:70000", "tcp://a b:1")
            .forEach { assertFalse(it, TransportInput.isValidYggPeer(it)) }
    }

    @Test
    fun nostrRelaysRequireTlsExceptLocal() {
        listOf("wss://relay.example", "wss://relay.example:4443/path", "ws://127.0.0.1:7777", "ws://localhost", "ws://[::1]:80")
            .forEach { assertTrue(it, TransportInput.isValidNostrRelay(it)) }
        listOf("ws://relay.example", "https://relay.example", "relay.example", "wss://", "wss://h:99999")
            .forEach { assertFalse(it, TransportInput.isValidNostrRelay(it)) }
        assertEquals("wss://relay.example", TransportInput.normalizeNostrRelay(" relay.example "))
        assertEquals("ws://localhost", TransportInput.normalizeNostrRelay("ws://localhost"))
    }

    @Test
    fun keyFromYggdrasilctlBothFormats() {
        val v05 = """{"build_name":"yggdrasil","key":"$key","address":"200::1"}"""
        val v04 = """{"self":{"200::1":{"build_name":"yggdrasil","key":"${key.uppercase()}"}}}"""
        assertEquals(key, TransportInput.yggKeyFromGetSelf(v05))
        assertEquals(key, TransportInput.yggKeyFromGetSelf(v04))
        assertNull(TransportInput.yggKeyFromGetSelf("Fatal error: dial unix /var/run/yggdrasil/yggdrasil.sock"))
    }

    @Test
    fun yggNoticesPerTransition() {
        val t = { from: FfiYggMode, to: FfiYggMode -> yggTexts(from, to, "W", "N", "S") }
        assertEquals(listOf("W", "N"), t(FfiYggMode.OFF, FfiYggMode.EMBEDDED))
        assertEquals(listOf("W"), t(FfiYggMode.OFF, FfiYggMode.EXTERNAL))
        assertEquals(listOf("S"), t(FfiYggMode.EMBEDDED, FfiYggMode.OFF))
        assertEquals(listOf("S"), t(FfiYggMode.EMBEDDED, FfiYggMode.EXTERNAL))
        assertEquals(listOf("N"), t(FfiYggMode.EXTERNAL, FfiYggMode.EMBEDDED))
        assertEquals(emptyList<String>(), t(FfiYggMode.EXTERNAL, FfiYggMode.OFF))
    }
}
