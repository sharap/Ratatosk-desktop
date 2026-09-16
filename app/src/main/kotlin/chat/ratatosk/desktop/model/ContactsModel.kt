package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.backend.AppEvent
import chat.ratatosk.desktop.util.ClipboardUtils
import chat.ratatosk.desktop.util.Log
import chat.ratatosk.desktop.util.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ratatosk.core.FfiContact
import org.ratatosk.core.maxAvatarBytes
import java.util.concurrent.ConcurrentHashMap

interface ContactsApi {
    val contacts: StateFlow<List<FfiContact>>
    val contactAvatars: StateFlow<Map<String, ByteArray>>
    val fingerprint: StateFlow<String?>
    val maxAvatarBytes: StateFlow<Int>
    val userName: StateFlow<String?>
    val myAvatar: StateFlow<ByteArray?>
    val myContactUri: StateFlow<String?>
    fun refreshContacts()
    fun addContact(uri: String, metInPerson: Boolean)
    fun addSharedContact(msgId: ByteArray)
    fun shareContact(chatId: ByteArray, peerIk: ByteArray)
    fun getMyContactUri()
    fun copyMyContactUri(onDone: (Boolean) -> Unit)
    fun setDisplayName(name: String)
    fun setLocalName(peerIk: ByteArray, name: String?)
    fun deleteContact(peerIk: ByteArray, purgeHistory: Boolean)
    fun getContactByChatId(chatId: ByteArray): FfiContact?
    fun markVerified(peerIk: ByteArray)
    fun revokeVerification(peerIk: ByteArray)
    fun getAvatarOf(peerIk: ByteArray): ByteArray?
    fun setAvatar(bytes: ByteArray?)
}

/**
 * Список чатов, своя карточка и лица.
 *
 * Экраны пока называют человека `peerIk` (типы полного клиента); у компаньона
 * это тот же `chatId`. Модель переводит в `chatId` для [chat.ratatosk.desktop.backend.Backend].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactsModel(session: SessionContext) : FeatureModel(session), ContactsApi {
    private val _contacts = MutableStateFlow<List<FfiContact>>(emptyList())
    override val contacts = _contacts.asStateFlow()

    /** Лица по `peerIk` в hex — так их ищут экраны. */
    private val _contactAvatars = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    override val contactAvatars = _contactAvatars.asStateFlow()

    /** Чьи лица уже запрошены: без этого экран спрашивал бы ядро на каждой перерисовке. */
    private val avatarRequests = ConcurrentHashMap.newKeySet<String>()

    /** Последние отметки лиц по `chatId`: сменилась — запрос выше больше не действителен. */
    private var avatarStamps: Map<String, String> = emptyMap()

    private val _fingerprint = MutableStateFlow<String?>(null)
    override val fingerprint = _fingerprint.asStateFlow()

    private val _maxAvatarBytes = MutableStateFlow(128 * 1024)
    override val maxAvatarBytes = _maxAvatarBytes.asStateFlow()

    override val userName = session.activeAccountId.flatMapLatest { id ->
        if (id == null) flowOf(null) else session.settings.getDisplayName(id)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), null)

    private val _myAvatar = MutableStateFlow<ByteArray?>(null)
    override val myAvatar = _myAvatar.asStateFlow()

    private val _myContactUri = MutableStateFlow<String?>(null)
    override val myContactUri = _myContactUri.asStateFlow()

    /** Первое, что нужно открытому аккаунту: список, своё лицо, отпечаток. */
    internal fun onSessionStarted() {
        session.io("Failed to load identity") { backend ->
            val maxAvatar = try { maxAvatarBytes().toInt() } catch (e: Exception) { 32768 }
            val fingerprint = session.client?.fingerprint()
            withContext(Dispatchers.Main) {
                _maxAvatarBytes.value = maxAvatar
                _fingerprint.value = fingerprint
            }
            backend.requestChats()
            // Компаньон спросит своё лицо, когда телефон окажется на линии:
            // до `Linked` спрашивать некого, и запрос уходил в пустоту.
            if (!backend.isCompanion) backend.requestAvatar(null)
        }
    }

    override fun refreshContacts() {
        session.io("Failed to refresh contacts") { it.requestChats() }
    }

    override fun addContact(uri: String, metInPerson: Boolean) {
        session.clientIo("Failed to add contact") { it.addContact(uri, metInPerson) }
    }

    override fun addSharedContact(msgId: ByteArray) {
        session.io("Failed to add shared contact") { it.addSharedContact(msgId) }
    }

    override fun shareContact(chatId: ByteArray, peerIk: ByteArray) {
        val whoChatId = chatIdOf(peerIk)
        session.io("Failed to share contact") { backend ->
            backend.shareContact(chatId, whoChatId ?: throw IllegalArgumentException("Unknown contact"))
        }
    }

    override fun getMyContactUri() {
        session.clientIo { client ->
            val uri = client.myContactUri()
            withContext(Dispatchers.Main) { _myContactUri.value = uri }
        }
    }

    /** Копирует свою ссылку в буфер: только по явной просьбе человека. */
    override fun copyMyContactUri(onDone: (Boolean) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val uri = runCatching { session.client?.myContactUri() }.getOrNull()
            withContext(Dispatchers.Main) {
                if (uri != null) {
                    _myContactUri.value = uri
                    ClipboardUtils.copyToClipboard(uri)
                }
                onDone(uri != null)
            }
        }
    }

    override fun setDisplayName(name: String) {
        val id = session.activeAccountId.value ?: return
        scope.launch {
            session.settings.setDisplayName(id, name)
        }
    }

    override fun setLocalName(peerIk: ByteArray, name: String?) {
        session.clientIo { client ->
            client.setLocalName(peerIk, name)
            session.backend?.requestChats()
        }
    }

    override fun deleteContact(peerIk: ByteArray, purgeHistory: Boolean) {
        session.clientIo { client ->
            client.deleteContact(peerIk, purgeHistory)
            session.backend?.requestChats()
        }
    }

    override fun getContactByChatId(chatId: ByteArray): FfiContact? {
        return _contacts.value.find { it.chatId.contentEquals(chatId) }
    }

    override fun markVerified(peerIk: ByteArray) {
        session.clientIo("Failed to mark as verified") { client ->
            client.markVerified(peerIk)
            session.backend?.requestChats()
        }
    }

    override fun revokeVerification(peerIk: ByteArray) {
        session.clientIo("Failed to revoke verification") { client ->
            client.revokeVerification(peerIk)
            session.backend?.requestChats()
        }
    }

    override fun getAvatarOf(peerIk: ByteArray): ByteArray? {
        val hex = peerIk.toHexString()
        _contactAvatars.value[hex]?.let { return it }
        val chatId = chatIdOf(peerIk) ?: return null
        if (avatarRequests.add(hex)) {
            session.io { it.requestAvatar(chatId) }
        }
        return null
    }

    override fun setAvatar(bytes: ByteArray?) {
        session.io("Failed to set avatar") { it.setMyAvatar(bytes) }
    }

    private fun chatIdOf(peerIk: ByteArray): ByteArray? =
        _contacts.value.find { it.peerIk.contentEquals(peerIk) }?.chatId

    private fun peerIkHexOf(chatId: ByteArray): String? =
        _contacts.value.find { it.chatId.contentEquals(chatId) }?.peerIk?.toHexString()

    override fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.ChatsLoaded -> {
                _contacts.value = event.chats
                val changed = event.avatarStamps.filter { (chatHex, stamp) -> avatarStamps[chatHex] != stamp }.keys
                avatarStamps = event.avatarStamps
                event.chats.filter { it.chatId.toHexString() in changed }.forEach { contact ->
                    val ikHex = contact.peerIk.toHexString()
                    // Спрашивали раньше — спросить снова: старый ответ устарел.
                    if (avatarRequests.remove(ikHex) || _contactAvatars.value.containsKey(ikHex)) {
                        Log.d(TAG, "avatar stamp changed, re-requesting")
                        avatarRequests.add(ikHex)
                        val chatId = contact.chatId
                        session.io { it.requestAvatar(chatId) }
                    }
                }
                if (session.backend?.isCompanion == true) session._isCompanionFresh.value = event.fresh
            }
            is AppEvent.ChatsChanged, is AppEvent.MessageArrived -> refreshContacts()
            is AppEvent.AvatarLoaded -> {
                Log.d(TAG, "avatar loaded: ${if (event.chatId == null) "own" else "chat"}, ${event.bytes?.size ?: 0} bytes")
                val chatId = event.chatId
                if (chatId == null) {
                    _myAvatar.value = event.bytes
                } else {
                    val hex = peerIkHexOf(chatId) ?: return
                    val bytes = event.bytes
                    _contactAvatars.update { if (bytes != null) it + (hex to bytes) else it - hex }
                }
            }
            // «Свою — раз за подключение: метки для сравнения у неё нет» (FFI, `avatar`).
            is AppEvent.Linked -> session.io("Failed to load own avatar") { it.requestAvatar(null) }
            is AppEvent.AvatarChanged -> {
                Log.d(TAG, "avatar changed: ${if (event.chatId == null) "own" else "chat"}")
                val chatId = event.chatId
                if (chatId != null) peerIkHexOf(chatId)?.let { avatarRequests.remove(it) }
                session.io { it.requestAvatar(chatId) }
            }
            else -> {}
        }
    }

    override fun reset() {
        _contacts.value = emptyList()
        _fingerprint.value = null
        _myAvatar.value = null
        _contactAvatars.value = emptyMap()
        avatarRequests.clear()
        avatarStamps = emptyMap()
        _myContactUri.value = null
    }

    private companion object {
        const val TAG = "ContactsModel"
    }
}
