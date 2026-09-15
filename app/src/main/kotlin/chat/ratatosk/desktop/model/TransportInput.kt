package chat.ratatosk.desktop.model

/**
 * Проверка того, что человек вводит в настройках транспортов.
 *
 * Ядро негодное **отбрасывает молча** (реле nostr) или отказывает без
 * объяснения, понятного человеку (ключ меша). Поэтому проверяем до вызова
 * и говорим, что именно не так, — строкой рядом с полем, а не тишиной.
 */
object TransportInput {
    /** Ключ узла меша: 64 hex-символа; пробелы и двоеточия из копирования допустимы. */
    fun parseYggKey(input: String): ByteArray? {
        val clean = input.filterNot { it.isWhitespace() || it == ':' }
        if (clean.length != 64 || !clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return ByteArray(32) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    private val YGG_PEER = Regex("""^(tcp|tls|quic|ws|wss|socks|sockstls)://[^\s/?#]+:(\d{1,5})(/[^\s]*)?(\?[^\s]*)?$""", RegexOption.IGNORE_CASE)

    /** Пир меша: `tcp://узел:порт` и родня; порт в пределах. */
    fun isValidYggPeer(peer: String): Boolean {
        val m = YGG_PEER.matchEntire(peer.trim()) ?: return false
        return m.groupValues[2].toInt() in 1..65535
    }

    private val NOSTR_RELAY = Regex("""^(wss|ws)://([^\s/:?#]+|\[[0-9a-fA-F:]+])(:(\d{1,5}))?(/[^\s]*)?$""", RegexOption.IGNORE_CASE)
    private val LOCAL_HOSTS = setOf("127.0.0.1", "localhost", "[::1]")

    /**
     * Реле nostr: `wss://…`. Открытый `ws://` — только до самого устройства:
     * наружу он показал бы наблюдателю по дороге граф переписки (так же
     * решает ядро, `set_nostr_relays`).
     */
    fun isValidNostrRelay(relay: String): Boolean {
        val m = NOSTR_RELAY.matchEntire(relay.trim()) ?: return false
        val port = m.groupValues[4].takeIf { it.isNotEmpty() }?.toInt()
        if (port != null && port !in 1..65535) return false
        val scheme = m.groupValues[1].lowercase()
        val host = m.groupValues[2].lowercase()
        return scheme == "wss" || host in LOCAL_HOSTS
    }

    /** `relay.example` → `wss://relay.example`; уже со схемой — как есть. */
    fun normalizeNostrRelay(input: String): String {
        val t = input.trim()
        return if (t.isEmpty() || t.contains("://")) t else "wss://$t"
    }

    /** Строки списка: по одной на строку, без пустых и повторов. */
    fun splitLines(text: String): List<String> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private val JSON_KEY = Regex(""""key"\s*:\s*"([0-9a-fA-F]{64})"""")

    /**
     * Ключ из ответа `yggdrasilctl -json getSelf`. Формат менялся между
     * версиями (в 0.4 ключ лежал глубже, в 0.5 — на верхнем уровне), поэтому
     * ищем поле `key` из 64 hex-символов где угодно, а не по пути.
     */
    fun yggKeyFromGetSelf(json: String): String? = JSON_KEY.find(json)?.groupValues?.get(1)?.lowercase()
}
