package chat.ratatosk.desktop.util

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.ptr.PointerByReference
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * Секреты, которым не место в файле настроек: секрет устройства аккаунта
 * и ссылка сопряжения компаньона (DESKTOP.md, «Ссылку сопряжения хранит
 * приложение»).
 *
 * Хранилище — системное: Secret Service через libsecret на Linux
 * (gnome-keyring, KWallet/ksecretd), DPAPI на Windows. На macOS и там, где
 * хранилища нет, [isAvailable] ложно, и вызывающий обязан **не сохранять**
 * секрет вовсе, а сказать об этом человеку, — а не складывать его в файл.
 *
 * Все вызовы блокирующие (связка ключей может спросить пароль) — только не
 * на UI-потоке.
 */
interface SecretStore {
    val isAvailable: Boolean
    fun get(key: String): ByteArray?
    fun put(key: String, value: ByteArray): Boolean
    fun delete(key: String)

    companion object {
        const val DEVICE_KEY_PREFIX = "device-key:"
        const val PAIRING_PREFIX = "pairing:"

        val system: SecretStore by lazy {
            val os = System.getProperty("os.name").lowercase()
            val store = when {
                os.contains("linux") -> LibSecretStore.openOrNull()
                os.contains("win") -> DpapiSecretStore(File(AppDirs.getBaseDir(), "secrets"))
                else -> null
            }
            store ?: UnavailableSecretStore
        }
    }
}

private object UnavailableSecretStore : SecretStore {
    override val isAvailable = false
    override fun get(key: String): ByteArray? = null
    override fun put(key: String, value: ByteArray) = false
    override fun delete(key: String) {}
}

/**
 * Secret Service через libsecret. Вызовы `*v_sync` принимают атрибуты
 * хэш-таблицей GLib, а не varargs: вариативные вызовы через JNA зависят
 * от ABI, и ошибка в них — порча стека, а не исключение.
 */
private class LibSecretStore private constructor(
    private val secret: NativeLibrary,
    private val glib: NativeLibrary,
) : SecretStore {

    private val schema = SecretSchema()

    override val isAvailable: Boolean by lazy {
        // Поиск несуществующего ключа: библиотека есть, но службы нет или
        // она недоступна — это ошибка, а не пустой ответ.
        runCatching { lookup("probe").let { true } }.getOrElse {
            Log.w(TAG, "Secret Service unavailable", it)
            false
        }
    }

    override fun get(key: String): ByteArray? = try {
        lookup(key)?.let { Base64.getDecoder().decode(it) }
    } catch (e: Exception) {
        Log.w(TAG, "Secret lookup failed", e)
        null
    }

    override fun put(key: String, value: ByteArray): Boolean = try {
        withAttributes(key) { table ->
            val error = PointerByReference()
            val ok = fn(secret, "secret_password_storev_sync").invokeInt(
                arrayOf(schema.pointer, table, null, "Ratatosk: $key", Base64.getEncoder().encodeToString(value), null, error)
            )
            checkError(error)
            ok != 0
        }
    } catch (e: Exception) {
        Log.w(TAG, "Secret store failed", e)
        false
    }

    override fun delete(key: String) {
        try {
            withAttributes(key) { table ->
                val error = PointerByReference()
                fn(secret, "secret_password_clearv_sync").invokeInt(arrayOf(schema.pointer, table, null, error))
                checkError(error)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Secret delete failed", e)
        }
    }

    private fun lookup(key: String): String? = withAttributes(key) { table ->
        val error = PointerByReference()
        val result = fn(secret, "secret_password_lookupv_sync").invokePointer(arrayOf(schema.pointer, table, null, error))
        checkError(error)
        result?.let { ptr ->
            try {
                ptr.getString(0, "UTF-8")
            } finally {
                fn(secret, "secret_password_free").invokeVoid(arrayOf(ptr))
            }
        }
    }

    /** Хэш-таблица `{ATTR_KEY: key}`; строки живут в [Memory] до конца вызова. */
    private fun <T> withAttributes(key: String, block: (Pointer) -> T): T {
        val name = utf8(ATTR_KEY)
        val value = utf8(key)
        val table = fn(glib, "g_hash_table_new").invokePointer(
            arrayOf(glib.getFunction("g_str_hash"), glib.getFunction("g_str_equal"))
        ) ?: error("g_hash_table_new returned NULL")
        try {
            fn(glib, "g_hash_table_insert").invokeInt(arrayOf(table, name, value))
            return block(table)
        } finally {
            fn(glib, "g_hash_table_unref").invokeVoid(arrayOf(table))
            // Держим ссылки до конца: таблица хранила указатели на эту память.
            name.size(); value.size()
        }
    }

    private fun checkError(error: PointerByReference) {
        val err = error.value ?: return
        // struct GError { GQuark domain; gint code; gchar *message; }
        val message = err.getPointer(8)?.getString(0, "UTF-8")
        fn(glib, "g_error_free").invokeVoid(arrayOf(err))
        throw IllegalStateException(message ?: "libsecret error")
    }

    private fun fn(lib: NativeLibrary, name: String): Function = lib.getFunction(name)

    private fun utf8(s: String): Memory {
        val bytes = s.toByteArray(Charsets.UTF_8)
        return Memory(bytes.size + 1L).apply {
            write(0, bytes, 0, bytes.size)
            setByte(bytes.size.toLong(), 0)
        }
    }

    companion object {
        private const val TAG = "SecretStore"
        private const val ATTR_KEY = "key"

        fun openOrNull(): LibSecretStore? = try {
            LibSecretStore(NativeLibrary.getInstance("secret-1"), NativeLibrary.getInstance("glib-2.0"))
        } catch (e: Throwable) {
            Log.w(TAG, "libsecret not found")
            null
        }
    }
}

/**
 * DPAPI: байты шифруются ключом учётной записи Windows и лежат файлом.
 * Расшифровать их может только этот пользователь на этой машине.
 */
private class DpapiSecretStore(private val dir: File) : SecretStore {
    override val isAvailable = true

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) } + ".bin")
    }

    override fun get(key: String): ByteArray? = try {
        fileFor(key).takeIf { it.isFile }?.readBytes()?.let { Crypt32Util.cryptUnprotectData(it) }
    } catch (e: Exception) {
        Log.w("SecretStore", "DPAPI unprotect failed", e)
        null
    }

    override fun put(key: String, value: ByteArray): Boolean = try {
        dir.mkdirs()
        fileFor(key).writeBytes(Crypt32Util.cryptProtectData(value))
        true
    } catch (e: Exception) {
        Log.w("SecretStore", "DPAPI protect failed", e)
        false
    }

    override fun delete(key: String) {
        fileFor(key).delete()
    }
}

/** `SecretSchemaAttribute` из libsecret. */
@Structure.FieldOrder("name", "type")
internal class SecretSchemaAttribute : Structure() {
    @JvmField var name: String? = null
    @JvmField var type: Int = 0 // SECRET_SCHEMA_ATTRIBUTE_STRING
}

/** `SecretSchema` из libsecret: 32 атрибута и зарезервированные поля. */
@Structure.FieldOrder(
    "name", "flags", "attributes",
    "reserved", "reserved1", "reserved2", "reserved3", "reserved4", "reserved5", "reserved6", "reserved7",
)
internal class SecretSchema : Structure() {
    @JvmField var name: String? = "chat.ratatosk.desktop.Secret"
    @JvmField var flags: Int = 0 // SECRET_SCHEMA_NONE
    @JvmField var attributes: Array<SecretSchemaAttribute?> = SecretSchemaAttribute().toArray(32).map { it as SecretSchemaAttribute }.toTypedArray()
    @JvmField var reserved: Int = 0
    @JvmField var reserved1: Pointer? = null
    @JvmField var reserved2: Pointer? = null
    @JvmField var reserved3: Pointer? = null
    @JvmField var reserved4: Pointer? = null
    @JvmField var reserved5: Pointer? = null
    @JvmField var reserved6: Pointer? = null
    @JvmField var reserved7: Pointer? = null

    init {
        attributes[0]!!.name = "key"
        // Остальные 31 — нули: список атрибутов оканчивается пустым именем.
        write()
    }
}
