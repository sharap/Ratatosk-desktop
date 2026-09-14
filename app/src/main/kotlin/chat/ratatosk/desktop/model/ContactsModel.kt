package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.core.RatatoskCore
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
import org.ratatosk.core.FfiCompanionEvent
import org.ratatosk.core.FfiContact
import org.ratatosk.core.FfiEvent
import org.ratatosk.core.maxAvatarBytes

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

/** Контакты, своя карточка и лица. */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactsModel(session: SessionContext) : FeatureModel(session), ContactsApi {
    private val _contacts = MutableStateFlow<List<FfiContact>>(emptyList())
    override val contacts = _contacts.asStateFlow()

    private val _contactAvatars = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    override val contactAvatars = _contactAvatars.asStateFlow()

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

    /** Отпечаток и предел аватара; бросает, если личность не читается. */
    internal suspend fun loadIdentity() {
        val client = RatatoskCore.getClient()
        val fingerprint = client.fingerprint()
        val maxAvatar = try { maxAvatarBytes().toInt() } catch (e: Exception) { 32768 }
        withContext(Dispatchers.Main) {
            _fingerprint.value = fingerprint
            _maxAvatarBytes.value = maxAvatar
        }
    }

    internal suspend fun loadMyAvatar() {
        try {
            val avatar = RatatoskCore.getClient().myAvatar()
            withContext(Dispatchers.Main) { _myAvatar.value = avatar }
        } catch (e: Exception) { }
    }

    internal suspend fun loadContacts(): List<FfiContact> {
        val currentContacts = RatatoskCore.getClient().contacts()
        withContext(Dispatchers.Main) { _contacts.value = currentContacts }
        return currentContacts
    }

    override fun refreshContacts() {
        scope.launch(Dispatchers.IO) {
            try {
                val list = RatatoskCore.getClient().contacts()
                _contacts.value = list
            } catch (e: Exception) {
                scope.launch {
                    session._error.value = "Failed to refresh contacts: ${e.message}"
                }
            }
        }
    }

    override fun addContact(uri: String, metInPerson: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().addContact(uri, metInPerson)
            } catch (e: Exception) {
                session._error.value = "Failed to add contact: ${e.message}"
            }
        }
    }

    override fun addSharedContact(msgId: ByteArray) {
        scope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().addSharedContact(msgId)
            } catch (e: Exception) {
                session._error.value = "Failed to add shared contact: ${e.message}"
            }
        }
    }

    override fun shareContact(chatId: ByteArray, peerIk: ByteArray) {
        scope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().shareContact(chatId, peerIk)
            } catch (e: Exception) {
                session._error.value = "Failed to share contact: ${e.message}"
            }
        }
    }

    override fun getMyContactUri() {
        scope.launch(Dispatchers.IO) {
            try {
                val uri = RatatoskCore.getClient().myContactUri()
                withContext(Dispatchers.Main) {
                    _myContactUri.value = uri
                }
            } catch (e: Exception) { }
        }
    }

    /** Копирует свою ссылку в буфер: только по явной просьбе человека. */
    override fun copyMyContactUri(onDone: (Boolean) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val uri = try {
                RatatoskCore.getClient().myContactUri()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get contact uri", e)
                null
            }
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
        scope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().setLocalName(peerIk, name)
                refreshContacts()
            } catch (e: Exception) { }
        }
    }

    override fun deleteContact(peerIk: ByteArray, purgeHistory: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().deleteContact(peerIk, purgeHistory)
                refreshContacts()
            } catch (e: Exception) { }
        }
    }

    override fun getContactByChatId(chatId: ByteArray): FfiContact? {
        return _contacts.value.find { it.chatId.contentEquals(chatId) }
    }

    override fun markVerified(peerIk: ByteArray) {
        scope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().markVerified(peerIk)
                refreshContacts()
            } catch (e: Exception) {
                session._error.value = "Failed to mark as verified: ${e.message}"
            }
        }
    }

    override fun revokeVerification(peerIk: ByteArray) {
        scope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().revokeVerification(peerIk)
                refreshContacts()
            } catch (e: Exception) {
                session._error.value = "Failed to revoke verification: ${e.message}"
            }
        }
    }

    override fun getAvatarOf(peerIk: ByteArray): ByteArray? {
        val hex = peerIk.toHexString()
        _contactAvatars.value[hex]?.let { return it }
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = RatatoskCore.getClient().avatarOf(peerIk)
                if (bytes != null) {
                    _contactAvatars.update { it + (hex to bytes) }
                }
            } catch (e: Exception) { }
        }
        return null
    }

    override fun setAvatar(bytes: ByteArray?) {
        scope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().setAvatar(bytes)
                _myAvatar.value = bytes
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set avatar", e)
            }
        }
    }

    override fun onEvent(event: FfiEvent) {
        when (event) {
            is FfiEvent.MessageReceived -> refreshContacts()
            is FfiEvent.ContactAdded, is FfiEvent.ContactChanged, is FfiEvent.ContactRemoved -> {
                refreshContacts()
            }
            is FfiEvent.AvatarChanged -> {
                val ikHex = event.peerIk.toHexString()
                scope.launch(Dispatchers.IO) {
                    try {
                        val bytes = RatatoskCore.getClient().avatarOf(event.peerIk)
                        if (bytes != null) {
                            _contactAvatars.update { it + (ikHex to bytes) }
                        } else {
                            _contactAvatars.update { it - ikHex }
                        }
                        refreshContacts()
                    } catch (e: Exception) { }
                }
            }
            is FfiEvent.GroupMembershipChanged -> refreshContacts()
            else -> {}
        }
    }

    override fun onCompanionEvent(event: FfiCompanionEvent) {
        when (event) {
            is FfiCompanionEvent.Chats -> {
                _contacts.value = event.chats.map { mapCompanionChat(it) }
                session._isCompanionFresh.value = event.fresh
            }
            is FfiCompanionEvent.Linked, is FfiCompanionEvent.ChatsChanged -> {
                scope.launch(Dispatchers.IO) {
                    try { RatatoskCore.getCompanion().chats() } catch (e: Exception) {}
                }
            }
            else -> {}
        }
    }

    override fun reset() {
        _contacts.value = emptyList()
        _fingerprint.value = null
        _myAvatar.value = null
        _contactAvatars.value = emptyMap()
        _myContactUri.value = null
    }

    private companion object {
        const val TAG = "ContactsModel"
    }
}
