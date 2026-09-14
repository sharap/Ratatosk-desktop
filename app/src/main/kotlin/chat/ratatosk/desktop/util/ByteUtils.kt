package chat.ratatosk.desktop.util

fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

fun String.hexToByteArray() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
