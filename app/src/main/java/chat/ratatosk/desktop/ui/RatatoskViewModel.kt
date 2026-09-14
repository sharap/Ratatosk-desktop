package chat.ratatosk.desktop.ui

import chat.ratatosk.desktop.core.RatatoskCore
import chat.ratatosk.desktop.data.SettingsRepository
import chat.ratatosk.desktop.ui.theme.ChatThemeData
import chat.ratatosk.desktop.util.toHexString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import org.ratatosk.core.*
import java.util.concurrent.ConcurrentHashMap
import java.io.File
import javax.imageio.ImageIO
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RatatoskViewModel(
    private val settingsRepository: SettingsRepository,
    private val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    private val _events = MutableStateFlow<List<FfiEvent>>(emptyList())
    val events = _events.asStateFlow()

    private val _contacts = MutableStateFlow<List<FfiContact>>(emptyList())
    val contacts = _contacts.asStateFlow()

    private val _messages = MutableStateFlow<Map<String, List<FfiMessage>>>(emptyMap())
    val messages = _messages.asStateFlow()

    private val _messageStatuses = MutableStateFlow<Map<String, FfiDeliveryStatus>>(emptyMap())
    val messageStatuses = _messageStatuses.asStateFlow()

    private val _repliedMessages = MutableStateFlow<Map<String, FfiMessage?>>(emptyMap())
    val repliedMessages = _repliedMessages.asStateFlow()

    private val _unreadCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCounts = _unreadCounts.asStateFlow()

    private val _fileProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val fileProgress = _fileProgress.asStateFlow()

    private val _filePreviews = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val filePreviews = _filePreviews.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val _activeJobsFlow = MutableStateFlow<Set<String>>(emptySet())
    val activeJobsFlow = _activeJobsFlow.asStateFlow()

    private val pendingCompanionSaves = ConcurrentHashMap<String, (File) -> Unit>()

    private val _searchResults = MutableStateFlow<List<FfiMessage>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _torStatus = MutableStateFlow<FfiTorStatus?>(null)
    val torStatus = _torStatus.asStateFlow()

    private val _mailStatus = MutableStateFlow<FfiMailStatus?>(null)
    val mailStatus = _mailStatus.asStateFlow()

    private val _mailAccount = MutableStateFlow<FfiMailAccount?>(null)
    val mailAccount = _mailAccount.asStateFlow()

    private val _transportsEnabled = MutableStateFlow<Map<FfiTransport, Boolean>>(emptyMap())
    val transportsEnabled = _transportsEnabled.asStateFlow()

    private val _transportsReady = MutableStateFlow<Map<FfiTransport, Boolean>>(emptyMap())
    val transportsReady = _transportsReady.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _isFindingHidden = MutableStateFlow(false)
    val isFindingHidden = _isFindingHidden.asStateFlow()

    private val _activeAccountId = MutableStateFlow<String?>(RatatoskCore.getActiveAccountId())
    val activeAccountId = _activeAccountId.asStateFlow()

    private val _activeChatId = MutableStateFlow<ByteArray?>(null)
    val activeChatId = _activeChatId.asSharedFlow() // Use shared flow to trigger events

    private val _activeChatIdFlow = MutableStateFlow<ByteArray?>(null)
    val activeChatIdFlow = _activeChatIdFlow.asStateFlow()

    private val _activeContactIdFlow = MutableStateFlow<ByteArray?>(null)
    val activeContactIdFlow = _activeContactIdFlow.asStateFlow()

    private val _availableAccounts = MutableStateFlow<List<FfiAccount>>(emptyList())
    val availableAccounts = _availableAccounts.asStateFlow()

    private val _companionPairings = settingsRepository.companionPairings
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val companionPairings = _companionPairings

    val allAccounts: StateFlow<List<AccountItem>> = combine(_availableAccounts, _companionPairings) { local, companion ->
        local.map { AccountItem.Local(it) } + companion.map { AccountItem.Companion(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedAccount = MutableStateFlow<FfiAccount?>(null)
    val selectedAccount = _selectedAccount.asStateFlow()

    private val _isCreatingNewAccount = MutableStateFlow(false)
    val isCreatingNewAccount = _isCreatingNewAccount.asStateFlow()

    private val _isCompanionMode = MutableStateFlow<Boolean>(RatatoskCore.isCompanionMode())
    val isCompanionMode: StateFlow<Boolean> = _isCompanionMode.asStateFlow()

    private val _isCompanionLinked = MutableStateFlow<Boolean>(false)
    val isCompanionLinked: StateFlow<Boolean> = _isCompanionLinked.asStateFlow()

    private val _isCompanionFresh = MutableStateFlow<Boolean>(false)
    val isCompanionFresh: StateFlow<Boolean> = _isCompanionFresh.asStateFlow()

    val totalUnreadCount: StateFlow<Int> = _unreadCounts
        .map { it.values.sum() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _honestNotices = MutableStateFlow<List<String>>(emptyList())
    val honestNotices = _honestNotices.asStateFlow()
    
    private val _isInitialized = MutableStateFlow(RatatoskCore.isInitialized())
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _accountExists = MutableStateFlow(false)
    val accountExists: StateFlow<Boolean> = _accountExists.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _fingerprint = MutableStateFlow<String?>(null)
    val fingerprint = _fingerprint.asStateFlow()

    private val _maxAvatarBytes = MutableStateFlow(128 * 1024)
    val maxAvatarBytes = _maxAvatarBytes.asStateFlow()

    val userName = activeAccountId.flatMapLatest { id ->
        if (id == null) flowOf(null) else settingsRepository.getDisplayName(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _myAvatar = MutableStateFlow<ByteArray?>(null)
    val myAvatar = _myAvatar.asStateFlow()

    private val _contactAvatars = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val contactAvatars = _contactAvatars.asStateFlow()

    private val _autoAcceptLimit = MutableStateFlow<ULong?>(null)
    val autoAcceptLimit = _autoAcceptLimit.asStateFlow()

    val chatTheme = settingsRepository.chatTheme.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatThemeData()
    )

    val notificationsShowName = activeAccountId.flatMapLatest { id ->
        if (id == null) flowOf(true) else settingsRepository.getNotificationsShowName(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val notificationsShowText = activeAccountId.flatMapLatest { id ->
        if (id == null) flowOf(true) else settingsRepository.getNotificationsShowText(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val downloadDirPath = activeAccountId.flatMapLatest { id ->
        if (id == null) flowOf(null) else settingsRepository.getDownloadDirPath(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val lanEnabled = transportsEnabled.map { it[FfiTransport.LAN] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val torEnabled = transportsEnabled.map { it[FfiTransport.ONION] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
        
    val mailEnabled = transportsEnabled.map { it[FfiTransport.MAIL] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _onionAddress = MutableStateFlow<String?>(null)
    val onionAddress = _onionAddress.asStateFlow()

    private val _myContactUri = MutableStateFlow<String?>(null)
    val myContactUri = _myContactUri.asStateFlow()

    private val _cardVersion = MutableStateFlow<ULong?>(null)
    val cardVersion = _cardVersion.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                RatatoskCore.initializeRegistry()
                refreshAccounts()
            } catch (e: Exception) {
                _error.value = "Failed to open registry: ${e.message}"
            }
        }

        viewModelScope.launch {
            while (true) {
                if (!_isInitialized.value && RatatoskCore.isInitialized()) {
                    _isInitialized.value = true
                    _activeAccountId.value = RatatoskCore.getActiveAccountId()
                    setupEngine()
                }
                kotlinx.coroutines.delay(1000)
            }
        }

        val noticesResult = RatatoskCore.safeCall { honestNotices() }
        noticesResult.onSuccess {
            _honestNotices.value = it
        }.onFailure {
            _error.value = "Core unavailable: ${it.message}"
        }

        if (RatatoskCore.isInitialized()) {
            if (RatatoskCore.isCompanionMode()) {
                setupCompanion()
            } else {
                setupEngine()
            }
        } else {
            viewModelScope.launch {
                RatatoskCore.tryAutoInitialize()?.let {
                    _isInitialized.value = true
                    _isCompanionMode.value = RatatoskCore.isCompanionMode()
                    _activeAccountId.value = RatatoskCore.getActiveAccountId()
                    setupEngine()
                }
            }
        }
        
        viewModelScope.launch {
            while (true) {
                if (RatatoskCore.isInitialized()) {
                    if (RatatoskCore.isCompanionMode()) {
                        try {
                            RatatoskCore.getCompanion().chats()
                        } catch (e: Exception) {}
                    } else {
                        refreshContacts()
                        refreshTransportStatus()
                    }
                }
                kotlinx.coroutines.delay(30000)
            }
        }
    }

    private var eventsJob: Job? = null
    private var companionEventsJob: Job? = null

    private fun setupCompanion() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                if (companionEventsJob == null) {
                    companionEventsJob = RatatoskCore.companionEvents
                        .onEach { handleCompanionEvent(it) }
                        .launchIn(viewModelScope)
                }
            }
            try {
                RatatoskCore.getCompanion().chats()
            } catch (e: Exception) {}
        }
    }

    private fun setupEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = RatatoskCore.getClient()
                val fingerprint = client.fingerprint()
                val maxAvatar = try { maxAvatarBytes().toInt() } catch (e: Exception) { 32768 }
                
                withContext(Dispatchers.Main) {
                    _fingerprint.value = fingerprint
                    _maxAvatarBytes.value = maxAvatar
                }

                withContext(Dispatchers.Main) {
                    if (eventsJob == null) {
                        eventsJob = RatatoskCore.events
                            .onEach { handleEvent(it) }
                            .launchIn(viewModelScope)
                    }
                }

                launch(Dispatchers.IO) {
                    try {
                        val avatar = client.myAvatar()
                        withContext(Dispatchers.Main) { _myAvatar.value = avatar }
                    } catch (e: Exception) { }
                }

                launch(Dispatchers.IO) {
                    try {
                        val limit = client.autoAcceptBytes()
                        withContext(Dispatchers.Main) { _autoAcceptLimit.value = limit }
                    } catch (e: Exception) { }
                }
                
                launch(Dispatchers.IO) {
                    try {
                        val currentContacts = client.contacts()
                        withContext(Dispatchers.Main) { _contacts.value = currentContacts }
                        refreshTransportStatus()
                        currentContacts.forEach { contact ->
                            loadMessages(contact.chatId)
                        }
                    } catch (e: Exception) { }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _error.value = "Failed to load identity: ${e.message}"
                }
            }
        }
    }

    fun refreshContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = RatatoskCore.getClient().contacts()
                _contacts.value = list
            } catch (e: Exception) {
                viewModelScope.launch {
                    _error.value = "Failed to refresh contacts: ${e.message}"
                }
            }
        }
    }

    fun loadMessages(chatId: ByteArray, limit: Int? = null) {
        if (RatatoskCore.isCompanionMode()) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    RatatoskCore.getCompanion().history(chatId, limit?.toUInt() ?: 100u, null)
                } catch (e: Exception) { }
            }
            return
        }
        val chatIdHex = chatId.toHexString()
        val currentSize = _messages.value[chatIdHex]?.size ?: 0
        val targetLimit = when {
            limit != null -> limit
            currentSize > 0 -> currentSize + 5
            else -> 100
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!RatatoskCore.isInitialized()) return@launch
                val msgs = RatatoskCore.getClient().messages(chatId, maxOf(targetLimit, 1).toUInt())
                _messages.update { currentMap ->
                    val existing = currentMap[chatIdHex] ?: emptyList()
                    if (limit == null && msgs.size < existing.size) {
                        currentMap
                    } else {
                        currentMap + (chatIdHex to msgs)
                    }
                }
            } catch (e: Exception) { }
        }
    }

    fun refreshAccounts() {
        _availableAccounts.value = RatatoskCore.listAccounts()
    }

    fun findHiddenAccount(pin: String, onFound: (ByteArray) -> Unit, onNotFound: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isFindingHidden.value = true
            try {
                val id = RatatoskCore.findHidden(pin)
                viewModelScope.launch {
                    if (id != null) onFound(id) else onNotFound()
                }
            } catch (e: Exception) {
                viewModelScope.launch { onNotFound() }
            } finally {
                _isFindingHidden.value = false
            }
        }
    }

    fun initialize(label: String, pin: String?, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val account = RatatoskCore.createAccount(label)
                RatatoskCore.initialize(account.id, pin, null, displayName)
                val idHex = account.id.toHexString()
                settingsRepository.registerAccount(idHex, displayName)
                withContext(Dispatchers.Main) {
                    _isInitialized.value = true
                    _activeAccountId.value = idHex
                    setupEngine()
                    refreshAccounts()
                    _error.value = null
                }
            } catch (e: Exception) {
                _error.value = "Failed to initialize: ${e.message}"
            }
        }
    }

    fun unlock(account: FfiAccount, pin: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val idHex = account.id.toHexString()
                val savedName = settingsRepository.getDisplayName(idHex).firstOrNull() ?: account.label
                RatatoskCore.initialize(account.id, pin, null, savedName)
                withContext(Dispatchers.Main) {
                    _isInitialized.value = true
                    _activeAccountId.value = idHex
                    setupEngine()
                    _error.value = null
                }
            } catch (e: Exception) {
                _error.value = "Failed to unlock: ${e.message}"
            }
        }
    }

    fun logout() {
        RatatoskCore.logout()
        _isInitialized.value = false
        _isCompanionMode.value = false
        _isCompanionLinked.value = false
        _activeAccountId.value = null
        _selectedAccount.value = null
        _isCreatingNewAccount.value = false
        _contacts.value = emptyList()
        _messages.value = emptyMap()
        _messageStatuses.value = emptyMap()
        _unreadCounts.value = emptyMap()
    }

    fun clearError() {
        _error.value = null
    }

    fun selectAccount(account: FfiAccount?) {
        _selectedAccount.value = account
        if (account == null) {
            _isCreatingNewAccount.value = false
        }
    }

    fun setActiveChat(chatId: ByteArray?) {
        _activeChatId.value = chatId
        _activeChatIdFlow.value = chatId
        if (chatId != null) {
            _activeContactIdFlow.value = null
            if (_isCompanionMode.value) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        RatatoskCore.getCompanion().history(chatId, 50u, null)
                        RatatoskCore.getCompanion().markRead(chatId, chatId)
                    } catch (e: Exception) {}
                }
            } else {
                _unreadCounts.update { it + (chatId.toHexString() to 0) }
            }
        }
    }

    fun setActiveContact(chatId: ByteArray?) {
        _activeContactIdFlow.value = chatId
        if (chatId != null) {
            _activeChatId.value = null
            _activeChatIdFlow.value = null
        }
    }

    fun setCreatingNewAccount(creating: Boolean) {
        _isCreatingNewAccount.value = creating
        if (creating) {
            _selectedAccount.value = null
        }
    }

    fun initializeCompanion(uri: String, useCache: Boolean, deviceId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val baseDir = File(chat.ratatosk.desktop.util.AppDirs.getBaseDir(), "companions")
                baseDir.mkdirs()
                
                // If we know the deviceId, use its specific cache file
                val initialCachePath = if (useCache && deviceId != null) {
                    File(baseDir, "$deviceId.db").absolutePath
                } else null
                
                val companion = RatatoskCore.initializeCompanion(uri, 0u, null, initialCachePath)
                
                val actualDeviceId = companion.deviceId().toHexString()
                val phoneName = companion.phoneName()
                
                if (useCache) {
                    val finalCachePath = File(baseDir, "$actualDeviceId.db").absolutePath
                    if (initialCachePath == null) {
                        companion.setCachePath(finalCachePath)
                    }
                    settingsRepository.saveCompanionPairing(
                        SettingsRepository.CompanionPairing(actualDeviceId, uri, phoneName, true)
                    )
                }
                
                withContext(Dispatchers.Main) {
                    _activeAccountId.value = actualDeviceId
                    _isInitialized.value = true
                    _isCompanionMode.value = true
                    setupCompanion()
                    _error.value = null
                }
            } catch (e: Exception) {
                _error.value = "Failed to link companion: ${e.message}"
            }
        }
    }

    fun companionSendText(chatId: ByteArray, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getCompanion().sendText(chatId, text)
            } catch (e: Exception) {
                _error.value = "Failed to send: ${e.message}"
            }
        }
    }

    fun companionSetReaction(chatId: ByteArray, msgId: ByteArray, emoji: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getCompanion().setReaction(chatId, msgId, emoji ?: "")
            } catch (e: Exception) { }
        }
    }

    fun companionEditMessage(chatId: ByteArray, msgId: ByteArray, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getCompanion().editMessage(chatId, msgId, text)
            } catch (e: Exception) {
                _error.value = "Failed to edit: ${e.message}"
            }
        }
    }

    fun companionDeleteMessages(chatId: ByteArray, msgIds: List<ByteArray>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getCompanion().deleteMessages(chatId, msgIds)
            } catch (e: Exception) { }
        }
    }

    fun companionRetractMessages(chatId: ByteArray, msgIds: List<ByteArray>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getCompanion().retractMessages(chatId, msgIds)
            } catch (e: Exception) { }
        }
    }

    fun companionReply(chatId: ByteArray, replyTo: ByteArray, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getCompanion().sendReply(chatId, replyTo, text)
            } catch (e: Exception) {
                _error.value = "Failed to reply: ${e.message}"
            }
        }
    }

    fun companionAcceptFile(fileId: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getCompanion().acceptFile(fileId)
            } catch (e: Exception) { }
        }
    }

    fun companionDeclineFile(fileId: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getCompanion().declineFile(fileId)
            } catch (e: Exception) { }
        }
    }

    fun companionSaveFile(fileId: ByteArray, chunkTotal: ULong, name: String) {
        val destDir = downloadDirPath.value?.let { java.io.File(it) } ?: chat.ratatosk.desktop.util.FileUtils.getDownloadsDir()
        val destination = java.io.File(destDir, name)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getCompanion().saveFile(fileId, chunkTotal, destination.absolutePath)
            } catch (e: Exception) {
                _error.value = "Failed to save file: ${e.message}"
            }
        }
    }

    fun companionSendFile(chatId: ByteArray, file: java.io.File, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val preview = if (file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp")) {
                    generatePreview(file)
                } else null
                val outgoing = FfiCompanionOutgoing(file.absolutePath, preview)
                RatatoskCore.getCompanion().sendFiles(chatId, listOf(outgoing), text)
            } catch (e: Exception) {
                _error.value = "Failed to send file: ${e.message}"
            }
        }
    }

    fun companionGetFilePreview(fileId: ByteArray): ByteArray? {
        val hex = fileId.toHexString()
        _filePreviews.value[hex]?.let { return it }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getCompanion().preview(fileId)
            } catch (e: Exception) { }
        }
        return null
    }
    
    fun companionLoadHistory(chatId: ByteArray, limit: UInt, before: ByteArray?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getCompanion().history(chatId, limit, before)
            } catch (e: Exception) {}
        }
    }

    fun removeCompanionPairing(deviceId: String) {
        viewModelScope.launch {
            settingsRepository.removeCompanionPairing(deviceId)
        }
    }

    private var searchJob: Job? = null
    fun searchMessages(chatId: ByteArray?, query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        _isSearching.value = true
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val results = RatatoskCore.getClient().search(chatId, query, 50u)
                _searchResults.value = results
            } catch (e: Exception) {
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    fun sendText(chatId: ByteArray, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().sendText(chatId, text)
                loadMessages(chatId)
            } catch (e: Exception) {
                _error.value = "Failed to send: ${e.message}"
            }
        }
    }

    fun sendFiles(chatId: ByteArray, files: List<File>, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val outgoingFiles = files.map { file ->
                    val preview = if (file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp")) {
                        generatePreview(file)
                    } else null
                    FfiOutgoingFile(file.absolutePath, preview)
                }
                RatatoskCore.getClient().sendFiles(chatId, outgoingFiles, text)
                loadMessages(chatId)
            } catch (e: Exception) {
                _error.value = "Failed to send files: ${e.message}"
            }
        }
    }

    private fun generatePreview(file: File): ByteArray? {
        return try {
            val originalImage = ImageIO.read(file) ?: return null
            val width = 320
            val height = 320
            val ratio = Math.min(width.toDouble() / originalImage.width, height.toDouble() / originalImage.height)
            if (ratio >= 1.0) return null

            val targetWidth = (originalImage.width * ratio).toInt()
            val targetHeight = (originalImage.height * ratio).toInt()

            val resultingImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH)
            val outputImage = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
            outputImage.getGraphics().drawImage(resultingImage, 0, 0, null)

            val baos = ByteArrayOutputStream()
            ImageIO.write(outputImage, "jpg", baos)
            val bytes = baos.toByteArray()
            if (bytes.size > maxPreviewBytes().toInt()) null else bytes
        } catch (e: Exception) {
            null
        }
    }

    fun acceptFile(chatId: ByteArray, fileId: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (RatatoskCore.isCompanionMode()) {
                    RatatoskCore.getCompanion().acceptFile(fileId)
                } else {
                    RatatoskCore.getClient().acceptFile(fileId)
                    loadMessages(chatId)
                }
            } catch (e: Exception) { }
        }
    }

    fun declineFile(chatId: ByteArray, fileId: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (RatatoskCore.isCompanionMode()) {
                    RatatoskCore.getCompanion().declineFile(fileId)
                } else {
                    RatatoskCore.getClient().declineFile(fileId)
                    loadMessages(chatId)
                }
            } catch (e: Exception) { }
        }
    }

    fun getFilePreview(fileId: ByteArray): ByteArray? {
        val hex = fileId.toHexString()
        _filePreviews.value[hex]?.let { return it }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (RatatoskCore.isCompanionMode()) {
                    RatatoskCore.getCompanion().preview(fileId)
                } else {
                    val bytes = RatatoskCore.getClient().previewOf(fileId)
                    if (bytes != null) {
                        _filePreviews.update { it + (hex to bytes) }
                    }
                }
            } catch (e: Exception) { }
        }
        return null
    }

    fun saveFile(file: FfiFile, destination: File, onComplete: (File) -> Unit) {
        val fileIdHex = file.fileId.toHexString()
        
        if (RatatoskCore.isCompanionMode()) {
            pendingCompanionSaves[fileIdHex] = onComplete
            _activeJobsFlow.update { it + fileIdHex }
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    destination.parentFile?.mkdirs()
                    RatatoskCore.getCompanion().saveFile(file.fileId, file.chunkTotal, destination.absolutePath)
                } catch (e: Exception) {
                    pendingCompanionSaves.remove(fileIdHex)
                    _activeJobsFlow.update { it - fileIdHex }
                }
            }
            return
        }

        val job = viewModelScope.launch(Dispatchers.IO) {
            var reader: FfiFileReader? = null
            try {
                destination.parentFile?.mkdirs()
                reader = RatatoskCore.getClient().openFile(file.fileId)
                if (reader == null) return@launch

                destination.outputStream().use { output ->
                    val total = reader.chunkTotal()
                    for (i in 0UL until total) {
                        ensureActive()
                        val chunk = reader.chunk(i)
                        if (chunk != null) {
                            output.write(chunk)
                            output.flush()
                            _fileProgress.update { it + (fileIdHex to (i.toFloat() / total.toFloat())) }
                        }
                    }
                }
                _fileProgress.update { it + (fileIdHex to 1f) }
                viewModelScope.launch { onComplete(destination) }
            } catch (e: Exception) {
            } finally {
                reader?.destroy()
                activeJobs.remove(fileIdHex)
                _activeJobsFlow.update { it - fileIdHex }
            }
        }
        activeJobs[fileIdHex]?.cancel()
        activeJobs[fileIdHex] = job
        _activeJobsFlow.update { it + fileIdHex }
    }

    fun downloadFile(file: FfiFile, onComplete: (String) -> Unit) {
        val destDir = downloadDirPath.value?.let { File(it) } ?: chat.ratatosk.desktop.util.FileUtils.getDownloadsDir()
        val destination = File(destDir, file.name)
        saveFile(file, destination) { onComplete(it.absolutePath) }
    }

    fun openFile(file: FfiFile) {
        val baseDir = File(System.getProperty("java.io.tmpdir"), "ratatosk_media")
        val destination = File(baseDir, "${file.fileId.toHexString()}_${file.name}")
        
        if (destination.exists() && destination.length() == file.sizeBytes.toLong()) {
            chat.ratatosk.desktop.util.FileUtils.openFile(destination)
            return
        }
        
        saveFile(file, destination) {
            chat.ratatosk.desktop.util.FileUtils.openFile(it)
        }
    }

    fun cancelFileJob(fileId: ByteArray) {
        val hex = fileId.toHexString()
        if (RatatoskCore.isCompanionMode()) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    RatatoskCore.getCompanion().cancelSave()
                } catch (e: Exception) { }
            }
            _activeJobsFlow.update { it - hex }
            return
        }
        activeJobs[hex]?.cancel()
        activeJobs.remove(hex)
        _activeJobsFlow.update { it - hex }
    }

    fun sweepOrphanFiles(onResult: (FfiSwept) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = RatatoskCore.getClient().sweepOrphanFiles()
                viewModelScope.launch { onResult(result) }
            } catch (e: Exception) { }
        }
    }

    fun resendMessage(chatId: ByteArray, body: String) {
        sendText(chatId, body)
    }

    fun setNotificationsShowName(show: Boolean) {
        val id = activeAccountId.value ?: return
        viewModelScope.launch {
            settingsRepository.setNotificationsShowName(id, show)
        }
    }

    fun setNotificationsShowText(show: Boolean) {
        val id = activeAccountId.value ?: return
        viewModelScope.launch {
            settingsRepository.setNotificationsShowText(id, show)
        }
    }

    fun setDownloadDirPath(path: String?) {
        val id = activeAccountId.value ?: return
        viewModelScope.launch {
            settingsRepository.setDownloadDirPath(id, path)
        }
    }
    
    fun refreshTransportStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = RatatoskCore.getClient()
                val en = FfiTransport.entries.associateWith { client.transportEnabled(it) }
                val re = FfiTransport.entries.associateWith { client.transportReady(it) }
                val ts = client.torStatus()
                val ms = client.mailStatus()
                val ma = client.mailAccount()
                
                withContext(Dispatchers.Main) {
                    _transportsEnabled.value = en
                    _transportsReady.value = re
                    _torStatus.value = ts
                    _mailStatus.value = ms
                    _mailAccount.value = ma
                }
            } catch (e: Exception) {
                println("RatatoskViewModel: Error refreshing transport status: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun setTransportEnabled(transport: FfiTransport, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                println("RatatoskViewModel: Setting transport $transport to $enabled")
                RatatoskCore.getClient().setTransportEnabled(transport, enabled)
                refreshTransportStatus()
            } catch (e: Exception) {
                _error.value = "Failed to toggle transport: ${e.message}"
                println("RatatoskViewModel: Error setting transport: ${e.message}")
            }
        }
    }

    fun setMailAccount(address: String, password: String, imapHost: String, imapPort: Int, smtpHost: String, smtpPort: Int, viaTor: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = RatatoskCore.getClient()
                client.setMailAccount(address.trim(), password, imapHost.trim(), imapPort.toUShort(), smtpHost.trim(), smtpPort.toUShort(), viaTor)
                client.networkChanged()
                refreshTransportStatus()
            } catch (e: Exception) {
                _error.value = "Failed to set mail account: ${e.message}"
            }
        }
    }

    fun createMailAccount(url: String, viaTor: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = RatatoskCore.getClient()
                if (viaTor && !client.transportEnabled(FfiTransport.ONION)) {
                    _error.value = "Tor must be enabled to register via Tor"
                    return@launch
                }
                client.createMailAccount(url.trim(), viaTor)
                client.networkChanged()
                refreshTransportStatus()
            } catch (e: Exception) {
                _error.value = "Failed to create mail account: ${e.message}"
            }
        }
    }

    fun clearMailAccount() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().clearMailAccount()
                refreshTransportStatus()
            } catch (e: Exception) { }
        }
    }

    fun setLanEnabled(enabled: Boolean) {
        setTransportEnabled(FfiTransport.LAN, enabled)
    }

    fun addContact(uri: String, metInPerson: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().addContact(uri, metInPerson)
            } catch (e: Exception) {
                _error.value = "Failed to add contact: ${e.message}"
            }
        }
    }

    fun addSharedContact(msgId: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().addSharedContact(msgId)
            } catch (e: Exception) {
                _error.value = "Failed to add shared contact: ${e.message}"
            }
        }
    }

    fun shareContact(chatId: ByteArray, peerIk: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().shareContact(chatId, peerIk)
            } catch (e: Exception) {
                _error.value = "Failed to share contact: ${e.message}"
            }
        }
    }

    fun getMyContactUri() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = RatatoskCore.getClient().myContactUri()
                withContext(Dispatchers.Main) {
                    _myContactUri.value = uri
                }
            } catch (e: Exception) { }
        }
    }

    fun setDisplayName(name: String) {
        val id = activeAccountId.value ?: return
        viewModelScope.launch {
            settingsRepository.setDisplayName(id, name)
        }
    }

    fun setLocalName(peerIk: ByteArray, name: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().setLocalName(peerIk, name)
                refreshContacts()
            } catch (e: Exception) { }
        }
    }

    fun deleteContact(peerIk: ByteArray, purgeHistory: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().deleteContact(peerIk, purgeHistory)
                refreshContacts()
            } catch (e: Exception) { }
        }
    }

    fun getContactByChatId(chatId: ByteArray): FfiContact? {
        return _contacts.value.find { it.chatId.contentEquals(chatId) }
    }

    fun clearChat(chatId: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().clearChat(chatId)
                loadMessages(chatId)
            } catch (e: Exception) { }
        }
    }

    fun deleteMessages(chatId: ByteArray, msgIds: List<ByteArray>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().deleteMessages(chatId, msgIds)
                loadMessages(chatId)
            } catch (e: Exception) { }
        }
    }

    fun retractMessages(chatId: ByteArray, msgIds: List<ByteArray>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().retractMessages(chatId, msgIds)
                loadMessages(chatId)
            } catch (e: Exception) { }
        }
    }

    fun editMessage(chatId: ByteArray, msgId: ByteArray, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().editMessage(chatId, msgId, text)
                loadMessages(chatId)
            } catch (e: Exception) { }
        }
    }

    fun reply(chatId: ByteArray, replyTo: ByteArray, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().reply(chatId, replyTo, text)
                loadMessages(chatId)
            } catch (e: Exception) { }
        }
    }

    fun forwardMessages(chatId: ByteArray, msgIds: List<ByteArray>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().forwardMessages(chatId, msgIds)
                loadMessages(chatId)
            } catch (e: Exception) { }
        }
    }

    fun markRead(chatId: ByteArray, upTo: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().markRead(chatId, upTo)
                _unreadCounts.update { it + (chatId.toHexString() to 0) }
            } catch (e: Exception) { }
        }
    }

    fun markVerified(peerIk: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().markVerified(peerIk)
                refreshContacts()
            } catch (e: Exception) {
                _error.value = "Failed to mark as verified: ${e.message}"
            }
        }
    }

    fun revokeVerification(peerIk: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().revokeVerification(peerIk)
                refreshContacts()
            } catch (e: Exception) {
                _error.value = "Failed to revoke verification: ${e.message}"
            }
        }
    }

    fun setReaction(chatId: ByteArray, msgId: ByteArray, emoji: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().setReaction(chatId, msgId, emoji)
                loadMessages(chatId)
            } catch (e: Exception) { }
        }
    }

    fun getAvatarOf(peerIk: ByteArray): ByteArray? {
        val hex = peerIk.toHexString()
        _contactAvatars.value[hex]?.let { return it }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = RatatoskCore.getClient().avatarOf(peerIk)
                if (bytes != null) {
                    _contactAvatars.update { it + (hex to bytes) }
                }
            } catch (e: Exception) { }
        }
        return null
    }

    fun setAvatar(bytes: ByteArray?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().setAvatar(bytes)
                _myAvatar.value = bytes
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getMessage(msgId: ByteArray): FfiMessage? {
        val hex = msgId.toHexString()
        _repliedMessages.value[hex]?.let { return it }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val msg = RatatoskCore.getClient().message(msgId)
                _repliedMessages.update { it + (hex to msg) }
            } catch (e: Exception) { }
        }
        return null
    }

    fun setAutoAcceptLimit(limit: ULong?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().setAutoAcceptBytes(limit)
                _autoAcceptLimit.value = limit
            } catch (e: Exception) { }
        }
    }

    fun updateChatTheme(update: (ChatThemeData) -> ChatThemeData) {
        viewModelScope.launch {
            val newData = update(chatTheme.value)
            settingsRepository.updateChatTheme(newData)
        }
    }

    fun getRetractionNotice(): String {
        return try {
            retractionNotice()
        } catch (e: Exception) {
            "Retract selected messages?"
        }
    }

    private var isAnnouncingTor = false

    private fun handleEvent(event: FfiEvent) {
        _events.update { (it + event).takeLast(100) }
        when (event) {
            is FfiEvent.MessageReceived -> {
                val hexId = event.chatId.toHexString()
                loadMessages(event.chatId)
                if (_activeChatId.value?.contentEquals(event.chatId) != true) {
                    _unreadCounts.update { current -> 
                        val newCount = (current[hexId] ?: 0) + 1
                        current + (hexId to newCount)
                    }
                }
                refreshContacts()
            }
            is FfiEvent.StatusChanged -> {
                val msgIdHex = event.msgId.toHexString()
                _messageStatuses.update { it + (msgIdHex to event.status) }
            }
            is FfiEvent.ContactAdded, is FfiEvent.ContactChanged, is FfiEvent.ContactRemoved -> {
                refreshContacts()
            }
            is FfiEvent.AvatarChanged -> {
                val ikHex = event.peerIk.toHexString()
                viewModelScope.launch(Dispatchers.IO) {
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
            is FfiEvent.FileProgress -> {
                val fileIdHex = event.fileId.toHexString()
                val progress = if (event.total > 0UL) event.received.toFloat() / event.total.toFloat() else 0f
                _fileProgress.update { it + (fileIdHex to progress) }
            }
            is FfiEvent.GroupMembershipChanged -> {
                refreshContacts()
            }
            is FfiEvent.MessageEdited -> {
                loadMessages(event.chatId)
            }
            is FfiEvent.ReactionChanged -> {
                loadMessages(event.chatId)
            }
            is FfiEvent.MessagesDeleted -> {
                loadMessages(event.chatId)
            }
            is FfiEvent.TorStatus -> {
                _torStatus.value = FfiTorStatus(event.fraction, event.note, event.blocked)
                if (event.fraction >= 1.0f && _onionAddress.value == null && !isAnnouncingTor) {
                    isAnnouncingTor = true
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val client = RatatoskCore.getClient()
                            val card = client.myAddresses()
                            withContext(Dispatchers.Main) {
                                _onionAddress.value = card.onion.takeIf { it.isNotEmpty() }
                                _cardVersion.value = card.version
                            }
                            if (client.transportEnabled(FfiTransport.ONION) && card.onion.isNotEmpty()) {
                                client.announceAddresses(card.onion, null)
                                val updatedCard = client.myAddresses()
                                withContext(Dispatchers.Main) {
                                    _cardVersion.value = updatedCard.version
                                }
                            }
                        } catch (e: Exception) {
                        } finally {
                            isAnnouncingTor = false
                        }
                    }
                }
                refreshTransportStatus()
            }
            is FfiEvent.MailAccountReady -> {
                val mailAddress = event.address
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val client = RatatoskCore.getClient()
                        client.setTransportEnabled(FfiTransport.MAIL, true)
                        refreshTransportStatus()
                        client.announceAddresses(null, mailAddress)
                    } catch (e: Exception) { }
                }
            }
            is FfiEvent.MailAccountFailed -> {
                _error.value = "Mail setup failed: ${event.reason}"
                refreshTransportStatus()
            }
            is FfiEvent.MailLoginFailed -> {
                _error.value = "Mail login failed: ${event.reason}"
                refreshTransportStatus()
            }
            is FfiEvent.CommandRefused -> {
                _error.value = event.reason
            }
            else -> { }
        }
    }

    private fun handleCompanionEvent(event: FfiCompanionEvent) {
        when (event) {
            is FfiCompanionEvent.Linked -> {
                _isCompanionLinked.value = true
                viewModelScope.launch(Dispatchers.IO) {
                    try { RatatoskCore.getCompanion().chats() } catch (e: Exception) {}
                }
            }
            is FfiCompanionEvent.Unlinked -> {
                _isCompanionLinked.value = false
            }
            is FfiCompanionEvent.Chats -> {
                val mappedContacts = event.chats.map { mapCompanionChat(it) }
                _contacts.value = mappedContacts
                
                _isCompanionFresh.value = event.fresh
                mappedContacts.forEach { loadMessages(it.chatId) }
            }
            is FfiCompanionEvent.History -> {
                val mappedMessages = event.page.map { mapCompanionMessage(it) }
                val chatIdHex = event.chatId.toHexString()
                _messages.update { it + (chatIdHex to mappedMessages) }
                _isCompanionFresh.value = event.fresh
            }
            is FfiCompanionEvent.Arrived -> {
                loadMessages(event.message.chatId)
                // Trigger chats refresh to update last message/unread
                viewModelScope.launch(Dispatchers.IO) {
                    try { RatatoskCore.getCompanion().chats() } catch (e: Exception) {}
                }
            }
            is FfiCompanionEvent.StatusChanged -> {
                // Update status in messages
                val msgIdHex = event.msgId.toHexString()
                _messageStatuses.update { it + (msgIdHex to event.status) }
                _activeChatIdFlow.value?.let { loadMessages(it) }
            }
            is FfiCompanionEvent.Gone -> {
                _activeChatIdFlow.value?.let { loadMessages(it) }
            }
            is FfiCompanionEvent.Edited -> {
                loadMessages(event.message.chatId)
            }
            is FfiCompanionEvent.Reacted -> {
                loadMessages(event.chatId)
            }
            is FfiCompanionEvent.FileProgress -> {
                val hexId = event.fileId.toHexString()
                val progress = if (event.chunkTotal > 0UL) event.haveChunks.toFloat() / event.chunkTotal.toFloat() else 0f
                _fileProgress.update { it + (hexId to progress) }
            }
            is FfiCompanionEvent.FilePreview -> {
                val hexId = event.fileId.toHexString()
                if (event.bytes != null) {
                    _filePreviews.update { it + (hexId to event.bytes) }
                }
            }
            is FfiCompanionEvent.FileSaved -> {
                val hexId = event.fileId.toHexString()
                _fileProgress.update { it + (hexId to 1f) }
                _activeJobsFlow.update { it - hexId }
                pendingCompanionSaves.remove(hexId)?.let { callback ->
                    viewModelScope.launch(Dispatchers.Main) {
                        callback(File(event.path))
                    }
                }
            }
            is FfiCompanionEvent.FilesSent -> {
                event.fileIds.forEach { fileId ->
                    val hexId = fileId.toHexString()
                    _fileProgress.update { it + (hexId to 1f) }
                }
                viewModelScope.launch(Dispatchers.IO) {
                    try { RatatoskCore.getCompanion().chats() } catch (e: Exception) {}
                }
            }
            is FfiCompanionEvent.Refused -> {
                _error.value = event.reason
            }
            is FfiCompanionEvent.ChatsChanged -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try { RatatoskCore.getCompanion().chats() } catch (e: Exception) {}
                }
            }
            else -> {}
        }
    }

    private fun mapCompanionChat(chat: FfiCompanionChat): FfiContact {
        return FfiContact(
            peerIk = chat.chatId,
            chatId = chat.chatId,
            fingerprint = "",
            displayName = chat.title,
            localName = null,
            verified = chat.verified,
            seenOnLan = false,
            hasAvatar = false,
            onion = null,
            chatmail = null,
            cardVersion = 0UL,
            addedMs = 0UL,
            reachability = FfiReachability(emptyList(), null, null),
            directChannel = null,
            anomalies = FfiAnomalies(0UL, 0UL, 0UL, 0UL, 0UL)
        )
    }

    private fun mapCompanionMessage(msg: FfiCompanionMessage): FfiMessage {
        return FfiMessage(
            msgId = msg.msgId,
            body = msg.body,
            mine = msg.mine,
            wallMs = msg.wallMs,
            status = msg.status,
            editedAtMs = msg.editedAtMs,
            forwarded = msg.forwarded,
            reactions = msg.reactions.map { FfiReaction(it.emoji, if (it.mine) _fingerprint.value?.hexToByteArray() ?: ByteArray(0) else ByteArray(0), it.mine) },
            files = msg.files.map { mapCompanionAttachment(it, msg.mine) },
            replyTo = msg.replyTo,
            sharedContact = null
        )
    }

    private fun mapCompanionAttachment(att: FfiCompanionAttachment, mine: Boolean): FfiFile {
        return FfiFile(
            fileId = att.fileId,
            name = att.name,
            sizeBytes = att.sizeBytes,
            incoming = !mine,
            accepted = att.accepted,
            complete = att.haveChunks == att.chunkTotal,
            receivedChunks = att.haveChunks,
            chunkTotal = att.chunkTotal,
            hasPreview = att.hasPreview
        )
    }

    private fun String.hexToByteArray() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

sealed class AccountItem {
    data class Local(val account: FfiAccount) : AccountItem()
    data class Companion(val pairing: SettingsRepository.CompanionPairing) : AccountItem()
}
