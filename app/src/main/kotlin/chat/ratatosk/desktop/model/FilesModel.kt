package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.core.RatatoskCore
import chat.ratatosk.desktop.util.AppDirs
import chat.ratatosk.desktop.util.FileUtils
import chat.ratatosk.desktop.util.Log
import chat.ratatosk.desktop.util.toHexString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
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
import org.ratatosk.core.FfiEvent
import org.ratatosk.core.FfiFile
import org.ratatosk.core.FfiFileReader
import org.ratatosk.core.FfiSwept
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

interface FilesApi {
    val fileProgress: StateFlow<Map<String, Float>>
    val filePreviews: StateFlow<Map<String, ByteArray>>
    val activeJobsFlow: StateFlow<Set<String>>
    val autoAcceptLimit: StateFlow<ULong?>
    val downloadDirPath: StateFlow<String?>
    fun acceptFile(chatId: ByteArray, fileId: ByteArray)
    fun declineFile(chatId: ByteArray, fileId: ByteArray)
    fun getFilePreview(fileId: ByteArray): ByteArray?
    fun saveFile(file: FfiFile, destination: File, onFailure: () -> Unit = {}, onComplete: (File) -> Unit)
    fun downloadFile(file: FfiFile, onComplete: (String) -> Unit)
    fun openFile(file: FfiFile)
    fun cancelFileJob(fileId: ByteArray)
    fun sweepOrphanFiles(onResult: (FfiSwept) -> Unit)
    fun setAutoAcceptLimit(limit: ULong?)
    fun setDownloadDirPath(path: String?)
    fun companionAcceptFile(fileId: ByteArray)
    fun companionDeclineFile(fileId: ByteArray)
    fun companionSaveFile(fileId: ByteArray, chunkTotal: ULong, name: String)
    fun companionGetFilePreview(fileId: ByteArray): ByteArray?
}

/** Вложения: приём, сохранение, открытие, превью, автоприём. */
@OptIn(ExperimentalCoroutinesApi::class)
class FilesModel(
    session: SessionContext,
    private val chats: ChatsModel,
) : FeatureModel(session), FilesApi {
    private val _fileProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    override val fileProgress = _fileProgress.asStateFlow()

    private val _filePreviews = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    override val filePreviews = _filePreviews.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val _activeJobsFlow = MutableStateFlow<Set<String>>(emptySet())
    override val activeJobsFlow = _activeJobsFlow.asStateFlow()

    private val pendingCompanionSaves = ConcurrentHashMap<String, (File) -> Unit>()

    private val _autoAcceptLimit = MutableStateFlow<ULong?>(null)
    override val autoAcceptLimit = _autoAcceptLimit.asStateFlow()

    override val downloadDirPath = session.activeAccountId.flatMapLatest { id ->
        if (id == null) flowOf(null) else session.settings.getDownloadDirPath(id)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), null)

    internal suspend fun loadAutoAcceptLimit() {
        try {
            val limit = RatatoskCore.getClient().autoAcceptBytes()
            withContext(Dispatchers.Main) { _autoAcceptLimit.value = limit }
        } catch (e: Exception) { }
    }

    override fun acceptFile(chatId: ByteArray, fileId: ByteArray) {
        scope.launch(Dispatchers.IO) {
            try {
                if (RatatoskCore.isCompanionMode()) {
                    RatatoskCore.getCompanion().acceptFile(fileId)
                } else {
                    RatatoskCore.getClient().acceptFile(fileId)
                    chats.loadMessages(chatId)
                }
            } catch (e: Exception) { }
        }
    }

    override fun declineFile(chatId: ByteArray, fileId: ByteArray) {
        scope.launch(Dispatchers.IO) {
            try {
                if (RatatoskCore.isCompanionMode()) {
                    RatatoskCore.getCompanion().declineFile(fileId)
                } else {
                    RatatoskCore.getClient().declineFile(fileId)
                    chats.loadMessages(chatId)
                }
            } catch (e: Exception) { }
        }
    }

    override fun getFilePreview(fileId: ByteArray): ByteArray? {
        val hex = fileId.toHexString()
        _filePreviews.value[hex]?.let { return it }

        scope.launch(Dispatchers.IO) {
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

    /**
     * Расшифровывает вложение в [destination].
     *
     * Пишется во временный `.part` рядом и переносится на место одним ходом:
     * недописанный файл не выглядит готовым ни человеку, ни [openFile],
     * который сверяет размер уже лежащей копии.
     */
    override fun saveFile(file: FfiFile, destination: File, onFailure: () -> Unit, onComplete: (File) -> Unit) {
        val fileIdHex = file.fileId.toHexString()

        if (RatatoskCore.isCompanionMode()) {
            pendingCompanionSaves[fileIdHex] = onComplete
            _activeJobsFlow.update { it + fileIdHex }
            scope.launch(Dispatchers.IO) {
                try {
                    destination.parentFile?.mkdirs()
                    RatatoskCore.getCompanion().saveFile(file.fileId, file.chunkTotal, destination.absolutePath)
                } catch (e: Exception) {
                    Log.w(TAG, "Companion save failed", e)
                    pendingCompanionSaves.remove(fileIdHex)
                    _activeJobsFlow.update { it - fileIdHex }
                    session._error.value = "Failed to save file: ${e.message}"
                    withContext(Dispatchers.Main) { onFailure() }
                }
            }
            return
        }

        // Регистрация до старта: задача, завершившаяся мгновенно, иначе успела
        // бы снять отметку раньше, чем её поставили, — и спиннер висел бы вечно.
        lateinit var job: Job
        job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            val part = File(destination.parentFile, destination.name + ".part")
            var reader: FfiFileReader? = null
            var saved = false
            try {
                destination.parentFile?.mkdirs()
                reader = RatatoskCore.getClient().openFile(file.fileId)
                    ?: throw IllegalStateException("File is not available")

                part.outputStream().use { output ->
                    val total = reader.chunkTotal()
                    for (i in 0UL until total) {
                        ensureActive()
                        val chunk = reader.chunk(i)
                            ?: throw IllegalStateException("File is incomplete")
                        output.write(chunk)
                        _fileProgress.update { it + (fileIdHex to ((i + 1UL).toFloat() / total.toFloat())) }
                    }
                }
                Files.move(part.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                saved = true
                _fileProgress.update { it + (fileIdHex to 1f) }
                withContext(Dispatchers.Main) { onComplete(destination) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save file", e)
                session._error.value = "Failed to save file: ${e.message}"
            } finally {
                reader?.destroy()
                if (!saved) {
                    part.delete()
                    scope.launch { onFailure() }
                }
                // Снимаем только свою регистрацию: повторный запуск для того же
                // файла уже положил сюда новую задачу.
                if (activeJobs.remove(fileIdHex, job)) {
                    _activeJobsFlow.update { it - fileIdHex }
                }
            }
        }
        activeJobs.put(fileIdHex, job)?.cancel()
        _activeJobsFlow.update { it + fileIdHex }
        job.start()
    }

    override fun downloadFile(file: FfiFile, onComplete: (String) -> Unit) {
        val destDir = downloadDirPath.value?.let { File(it) } ?: FileUtils.getDownloadsDir()
        destDir.mkdirs()
        // Имя приходит от собеседника: только безопасный сегмент и без затирания
        // уже лежащего файла с тем же именем.
        val destination = FileUtils.uniqueFile(destDir, FileUtils.safeName(file.name))
        saveFile(file, destination) { onComplete(it.absolutePath) }
    }

    /**
     * Открывает вложение системным приложением.
     *
     * Исполняемое (по расширению) не открывается никогда: файл от собеседника,
     * запущенный двойным щелчком, — это чужой код на этой машине. Такое
     * сохраняется в загрузки и показывается в папке, решать — человеку.
     */
    override fun openFile(file: FfiFile) {
        if (FileUtils.isExecutable(file.name)) {
            downloadFile(file) { path -> FileUtils.openDirectory(File(path)) }
            return
        }
        val destination = File(AppDirs.getMediaCacheDir(), "${file.fileId.toHexString()}_${FileUtils.safeName(file.name)}")

        if (destination.exists() && destination.length() == file.sizeBytes.toLong()) {
            FileUtils.openFile(destination)
            return
        }

        saveFile(file, destination) {
            FileUtils.openFile(it)
        }
    }

    override fun cancelFileJob(fileId: ByteArray) {
        val hex = fileId.toHexString()
        if (RatatoskCore.isCompanionMode()) {
            scope.launch(Dispatchers.IO) {
                try {
                    RatatoskCore.getCompanion().cancelSave()
                } catch (e: Exception) { }
            }
            pendingCompanionSaves.remove(hex)
            _activeJobsFlow.update { it - hex }
            return
        }
        activeJobs.remove(hex)?.cancel()
        _activeJobsFlow.update { it - hex }
    }

    override fun sweepOrphanFiles(onResult: (FfiSwept) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val result = RatatoskCore.getClient().sweepOrphanFiles()
                scope.launch { onResult(result) }
            } catch (e: Exception) { }
        }
    }

    override fun setAutoAcceptLimit(limit: ULong?) {
        scope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getClient().setAutoAcceptBytes(limit)
                _autoAcceptLimit.value = limit
            } catch (e: Exception) { }
        }
    }

    override fun setDownloadDirPath(path: String?) {
        val id = session.activeAccountId.value ?: return
        scope.launch {
            session.settings.setDownloadDirPath(id, path)
        }
    }

    // --- Компаньон ---------------------------------------------------------

    override fun companionAcceptFile(fileId: ByteArray) {
        scope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getCompanion().acceptFile(fileId)
            } catch (e: Exception) { }
        }
    }

    override fun companionDeclineFile(fileId: ByteArray) {
        scope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getCompanion().declineFile(fileId)
            } catch (e: Exception) { }
        }
    }

    override fun companionSaveFile(fileId: ByteArray, chunkTotal: ULong, name: String) {
        val destDir = downloadDirPath.value?.let { File(it) } ?: FileUtils.getDownloadsDir()
        scope.launch(Dispatchers.IO) {
            try {
                destDir.mkdirs()
                val destination = FileUtils.uniqueFile(destDir, FileUtils.safeName(name))
                RatatoskCore.getCompanion().saveFile(fileId, chunkTotal, destination.absolutePath)
            } catch (e: Exception) {
                session._error.value = "Failed to save file: ${e.message}"
            }
        }
    }

    override fun companionGetFilePreview(fileId: ByteArray): ByteArray? {
        val hex = fileId.toHexString()
        _filePreviews.value[hex]?.let { return it }

        scope.launch(Dispatchers.IO) {
            try {
                RatatoskCore.getCompanion().preview(fileId)
            } catch (e: Exception) { }
        }
        return null
    }

    // --- События -----------------------------------------------------------

    override fun onEvent(event: FfiEvent) {
        if (event is FfiEvent.FileProgress) {
            val progress = if (event.total > 0UL) event.received.toFloat() / event.total.toFloat() else 0f
            _fileProgress.update { it + (event.fileId.toHexString() to progress) }
        }
    }

    override fun onCompanionEvent(event: FfiCompanionEvent) {
        when (event) {
            is FfiCompanionEvent.FileProgress -> {
                val progress = if (event.chunkTotal > 0UL) event.haveChunks.toFloat() / event.chunkTotal.toFloat() else 0f
                _fileProgress.update { it + (event.fileId.toHexString() to progress) }
            }
            is FfiCompanionEvent.FilePreview -> {
                val bytes = event.bytes
                if (bytes != null) {
                    _filePreviews.update { it + (event.fileId.toHexString() to bytes) }
                }
            }
            is FfiCompanionEvent.FileSaved -> {
                val hexId = event.fileId.toHexString()
                _fileProgress.update { it + (hexId to 1f) }
                _activeJobsFlow.update { it - hexId }
                pendingCompanionSaves.remove(hexId)?.let { callback ->
                    scope.launch(Dispatchers.Main) {
                        callback(File(event.path))
                    }
                }
            }
            is FfiCompanionEvent.FilesSent -> {
                event.fileIds.forEach { fileId ->
                    _fileProgress.update { it + (fileId.toHexString() to 1f) }
                }
                scope.launch(Dispatchers.IO) {
                    try { RatatoskCore.getCompanion().chats() } catch (e: Exception) {}
                }
            }
            else -> {}
        }
    }

    override fun reset() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        _fileProgress.value = emptyMap()
        _filePreviews.value = emptyMap()
        _activeJobsFlow.value = emptySet()
        pendingCompanionSaves.clear()
        _autoAcceptLimit.value = null
    }

    private companion object {
        const val TAG = "FilesModel"
    }
}
