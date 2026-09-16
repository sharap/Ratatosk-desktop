package chat.ratatosk.desktop.core

import org.ratatosk.core.AccountRegistry
import org.ratatosk.core.CompanionObserver
import org.ratatosk.core.EventObserver
import org.ratatosk.core.FfiAccount
import org.ratatosk.core.FfiCompanionEvent
import org.ratatosk.core.FfiEvent
import org.ratatosk.core.RatatoskClient
import org.ratatosk.core.RatatoskCompanion
import org.ratatosk.core.RatatoskException
import chat.ratatosk.desktop.util.AppDirs
import chat.ratatosk.desktop.util.Log
import chat.ratatosk.desktop.util.toHexString
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import java.io.File

object RatatoskCore : EventObserver, CompanionObserver {
    private const val TAG = "RatatoskCore"

    @Volatile
    private var client: RatatoskClient? = null

    @Volatile
    private var companion: RatatoskCompanion? = null

    @Volatile
    private var registry: AccountRegistry? = null
    
    @Volatile
    private var nativeError: Throwable? = null

    private var activeAccountIdHex: String? = null
    private var isCompanionMode: Boolean = false

    private val _events = MutableSharedFlow<FfiEvent>(
        replay = 20, 
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = _events.asSharedFlow()

    private val _companionEvents = MutableSharedFlow<FfiCompanionEvent>(
        replay = 20,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val companionEvents = _companionEvents.asSharedFlow()

    @Throws(RatatoskException::class)
    fun initializeRegistry(): AccountRegistry {
        synchronized(this) {
            registry?.let { return it }
            val root = File(AppDirs.getBaseDir(), "ratatosk_root")
            root.mkdirs()
            val newRegistry = AccountRegistry.open(root.absolutePath)
            registry = newRegistry
            return newRegistry
        }
    }

    @Throws(RatatoskException::class)
    fun initialize(accountId: ByteArray, pin: String?, deviceKey: ByteArray? = null, displayName: String): RatatoskClient {
        synchronized(this) {
            val accountIdHex = accountId.toHexString()
            Log.d(TAG, "initialize: client ${if (client != null) "active" else "none"}")
            
            if (client != null && activeAccountIdHex == accountIdHex) {
                return client!!
            }

            client?.destroy()
            client = null
            
            return try {
                val reg = registry ?: throw IllegalStateException("Registry not initialized")
                val newClient = reg.openAccount(accountId, pin, deviceKey, displayName)
                Log.d(TAG, "account opened")
                
                newClient.setObserver(this)
                newClient.networkChanged() 
                
                reg.setForeground(accountId)
                
                client = newClient
            activeAccountIdHex = accountIdHex
            isCompanionMode = false
            nativeError = null 
            newClient
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to open account", t)
                nativeError = t
                throw t
            }
        }
    }

    fun setForeground(accountId: ByteArray?) {
        registry?.setForeground(accountId)
    }

    fun findHidden(pin: String): ByteArray? {
        return registry?.findHidden(pin)
    }

    fun createHidden(): ByteArray {
        val reg = registry ?: throw IllegalStateException("Registry not initialized")
        return reg.createHidden()
    }

    fun createAccount(label: String): FfiAccount {
        val reg = registry ?: throw IllegalStateException("Registry not initialized")
        return reg.create(label)
    }

    /**
     * Восстанавливает аккаунт из архива (§12) и вносит его в реестр.
     *
     * Каталог вложений — `<id>.files` рядом с `<id>.db`: ядро выводит его из
     * пути базы именно так, и под другим именем восстановленные файлы при
     * открытии не нашлись бы (ревью Android, 1.1).
     */
    @Throws(RatatoskException::class)
    fun importArchive(archivePath: String, unlock: org.ratatosk.core.FfiArchiveUnlock, label: String): org.ratatosk.core.FfiImported {
        val reg = registry ?: throw IllegalStateException("Registry not initialized")
        val root = File(AppDirs.getBaseDir(), "ratatosk_root").apply { mkdirs() }
        val accountId = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val idHex = accountId.toHexString()
        val destination = File(root, "$idHex.db")
        val filesDir = File(root, "$idHex.files")
        try {
            val imported = org.ratatosk.core.importArchive(archivePath, unlock, destination.absolutePath, filesDir.absolutePath)
            reg.adopt(accountId, label)
            return imported
        } catch (t: Throwable) {
            // Не ввезено — не оставлять полуфабрикат, который найдёт перебор скрытых.
            destination.delete()
            File(destination.path + "-wal").delete()
            File(destination.path + "-shm").delete()
            filesDir.deleteRecursively()
            throw t
        }
    }

    fun logout() {
        synchronized(this) {
            registry?.setForeground(null)
            client?.destroy()
            client = null
            companion?.destroy()
            companion = null
            activeAccountIdHex = null
            isCompanionMode = false
            // Повтор событий — для подписчика, пришедшего чуть позже открытия.
            // Следующему аккаунту события прошлого не нужны.
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            _events.resetReplayCache()
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            _companionEvents.resetReplayCache()
        }
    }

    @Throws(RatatoskException::class)
    fun initializeCompanion(
        inviteUri: String,
        port: UShort,
        peerAddr: String?,
        cachePath: String?,
        torDir: String? = null,
    ): RatatoskCompanion {
        synchronized(this) {
            
            client?.destroy()
            client = null
            companion?.destroy()
            companion = null
            
            return try {
                // `torDir` задан — поднимаем свой onion: вне общей сети с телефоном
                // без него связи нет вовсе, зато первый подъём идёт десятки секунд.
                val newCompanion = RatatoskCompanion.open(inviteUri, port, peerAddr, cachePath, torDir)
                newCompanion.setObserver(this)

                companion = newCompanion
                // Имя аккаунта — по идентификатору устройства от ядра, а не по
                // `hashCode` ссылки: тот не обещает ни постоянства, ни различий.
                activeAccountIdHex = "companion:" + newCompanion.deviceId().toHexString()
                isCompanionMode = true
                nativeError = null
                newCompanion
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to open companion", t)
                nativeError = t
                throw t
            }
        }
    }

    override fun onEvent(`event`: FfiEvent) {
        // Только имя события: поля несут тексты сообщений, имена и адреса.
        Log.d(TAG, "event ${`event`::class.simpleName}")
        if (!_events.tryEmit(`event`)) {
            Log.w(TAG, "Event buffer full")
        }
    }

    override fun onEvent(`event`: FfiCompanionEvent) {
        Log.d(TAG, "companion event ${`event`::class.simpleName}")
        _companionEvents.tryEmit(`event`)
        
        when (`event`) {
            is FfiCompanionEvent.Arrived -> {
                _events.tryEmit(FfiEvent.MessageReceived(`event`.message.chatId, `event`.message.msgId))
            }
            is FfiCompanionEvent.Edited -> {
                _events.tryEmit(FfiEvent.MessageEdited(`event`.message.chatId, `event`.message.msgId))
            }
            is FfiCompanionEvent.Reacted -> {
                _events.tryEmit(FfiEvent.ReactionChanged(`event`.chatId, `event`.msgId, ByteArray(0)))
            }
            is FfiCompanionEvent.StatusChanged -> {
                _events.tryEmit(FfiEvent.StatusChanged(`event`.msgId, `event`.status))
            }
            is FfiCompanionEvent.Gone -> {
                _events.tryEmit(FfiEvent.MessagesDeleted(`event`.chatId, `event`.msgIds))
            }
            is FfiCompanionEvent.FileProgress -> {
                _events.tryEmit(FfiEvent.FileProgress(`event`.fileId, `event`.haveChunks, `event`.chunkTotal))
            }
            is FfiCompanionEvent.ChatsChanged -> {
                _events.tryEmit(FfiEvent.ContactChanged(ByteArray(0)))
            }
            else -> {}
        }
    }

    fun getClient(): RatatoskClient {
        val error = nativeError
        if (error != null) throw RuntimeException("Native core failed to load", error)
        return client ?: throw IllegalStateException("RatatoskCore not initialized")
    }

    fun getCompanion(): RatatoskCompanion {
        val error = nativeError
        if (error != null) throw RuntimeException("Native core failed to load", error)
        return companion ?: throw IllegalStateException("RatatoskCore (companion) not initialized")
    }
    
    fun isInitialized(): Boolean = client != null || companion != null

    fun isCompanionMode(): Boolean = isCompanionMode

    fun getActiveAccountId(): String? = activeAccountIdHex

    fun listAccounts(): List<FfiAccount> {
        return try {
            registry?.list() ?: emptyList()
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun anyAccountExists(): Boolean {
        val root = File(AppDirs.getBaseDir(), "ratatosk_root")
        if (!root.exists()) return false
        return root.list()?.isNotEmpty() ?: false
    }

    fun getNativeError(): Throwable? = nativeError
    
    fun <T> safeCall(block: () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}
