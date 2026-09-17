package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.backend.AppEvent
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.util.AppDirs
import chat.ratatosk.desktop.util.FileUtils
import chat.ratatosk.desktop.util.Log
import chat.ratatosk.desktop.util.toHexString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import org.ratatosk.core.FfiFile
import org.ratatosk.core.FfiSwept
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Что открыто в просмотрщике: уже сохранённый на диск файл. */
class ViewerMedia(val name: String, val file: File)

interface FilesApi {
    val fileProgress: StateFlow<Map<String, Float>>
    val filePreviews: StateFlow<Map<String, ByteArray>>
    val activeJobsFlow: StateFlow<Set<String>>
    /** Исходящие: доля, отданная собеседнику, по `fileId` в hex. */
    val fileSending: StateFlow<Map<String, Float>>
    /** Стоящие передачи: слова ядра о причине, по `fileId` в hex. */
    val fileWaiting: StateFlow<Map<String, String>>
    /**
     * Сколько вложения уже легло на этот компьютер, по `fileId` в hex.
     * У компаньона это отдельный ход: телефон давно собрал файл целиком,
     * а сюда он ещё едет — [fileProgress] про это ничего не знает.
     */
    val saveProgress: StateFlow<Map<String, Float>>
    val autoAcceptLimit: StateFlow<ULong?>
    val downloadDirPath: StateFlow<String?>
    fun acceptFile(chatId: ByteArray, fileId: ByteArray)
    fun declineFile(chatId: ByteArray, fileId: ByteArray)
    /** Остановить приём, не отказываясь: продолжит [acceptFile] с того же места. */
    fun pauseFile(chatId: ByteArray, fileId: ByteArray)
    fun getFilePreview(fileId: ByteArray): ByteArray?
    fun saveFile(file: FfiFile, destination: File, onFailure: () -> Unit = {}, onComplete: (File) -> Unit)
    fun downloadFile(file: FfiFile, onComplete: (String) -> Unit)
    fun openFile(file: FfiFile)
    /** Что показывает просмотрщик; `null` — окно закрыто. */
    val viewerMedia: StateFlow<ViewerMedia?>
    /** Картинку — своим просмотрщиком, остальное — системным приложением. */
    fun openMedia(file: FfiFile)
    fun closeViewer()
    fun cancelFileJob(fileId: ByteArray)
    fun sweepOrphanFiles(onResult: (FfiSwept) -> Unit)
    fun setAutoAcceptLimit(limit: ULong?)
    fun setDownloadDirPath(path: String?)
}

/** Вложения: приём, сохранение, открытие, превью, автоприём. */
@OptIn(ExperimentalCoroutinesApi::class)
class FilesModel(session: SessionContext) : FeatureModel(session), FilesApi {
    private val _fileProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    override val fileProgress = _fileProgress.asStateFlow()

    private val _filePreviews = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    override val filePreviews = _filePreviews.asStateFlow()

    /** Какие превью уже запрошены: экран спрашивает на каждой перерисовке. */
    private val previewRequests = ConcurrentHashMap.newKeySet<String>()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val _activeJobsFlow = MutableStateFlow<Set<String>>(emptySet())
    override val activeJobsFlow = _activeJobsFlow.asStateFlow()

    private val _fileSending = MutableStateFlow<Map<String, Float>>(emptyMap())
    override val fileSending = _fileSending.asStateFlow()

    private val _fileWaiting = MutableStateFlow<Map<String, String>>(emptyMap())
    override val fileWaiting = _fileWaiting.asStateFlow()

    private val _saveProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    override val saveProgress = _saveProgress.asStateFlow()

    private val _viewerMedia = MutableStateFlow<ViewerMedia?>(null)
    override val viewerMedia = _viewerMedia.asStateFlow()

    private val _autoAcceptLimit = MutableStateFlow<ULong?>(null)
    override val autoAcceptLimit = _autoAcceptLimit.asStateFlow()

    override val downloadDirPath = session.activeAccountId.flatMapLatest { id ->
        if (id == null) flowOf(null) else session.settings.getDownloadDirPath(id)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), null)

    internal fun onSessionStarted() {
        session.clientIo { client ->
            val limit = client.autoAcceptBytes()
            withContext(Dispatchers.Main) { _autoAcceptLimit.value = limit }
        }
    }

    override fun acceptFile(chatId: ByteArray, fileId: ByteArray) {
        session.io("Failed to accept file") { it.acceptFile(chatId, fileId) }
    }

    override fun declineFile(chatId: ByteArray, fileId: ByteArray) {
        session.io("Failed to decline file") { it.declineFile(chatId, fileId) }
    }

    override fun pauseFile(chatId: ByteArray, fileId: ByteArray) {
        session.io("Failed to pause file") { it.pauseFile(chatId, fileId) }
    }

    override fun getFilePreview(fileId: ByteArray): ByteArray? {
        val hex = fileId.toHexString()
        _filePreviews.value[hex]?.let { return it }
        if (previewRequests.add(hex)) {
            session.io { backend ->
                try {
                    backend.requestPreview(fileId)
                } catch (e: Exception) {
                    // Не вышло — пусть следующий показ спросит снова.
                    previewRequests.remove(hex)
                    throw e
                }
            }
        }
        return null
    }

    /**
     * Сохраняет вложение в [destination] — одинаково у клиента и компаньона;
     * как именно пишется файл, решает [chat.ratatosk.desktop.backend.Backend.saveFile].
     */
    override fun saveFile(file: FfiFile, destination: File, onFailure: () -> Unit, onComplete: (File) -> Unit) {
        val fileIdHex = file.fileId.toHexString()
        val backend = session.backend ?: return onFailure()

        // Регистрация до старта: задача, завершившаяся мгновенно, иначе успела
        // бы снять отметку раньше, чем её поставили, — и спиннер висел бы вечно.
        lateinit var job: Job
        job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            var saved = false
            // Прошлая доля того же файла ввела бы в заблуждение: начинаем с чистого.
            _saveProgress.update { it - fileIdHex }
            val watcher = if (backend.isCompanion) launchPartWatcher(file, destination) else null
            try {
                backend.saveFile(file, destination)
                saved = true
                _saveProgress.update { it + (fileIdHex to 1f) }
                withContext(Dispatchers.Main) { onComplete(destination) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save file", e)
                session._error.value = e.message?.takeIf { it.isNotBlank() } ?: Strings.FILE_SAVE_FAILED
            } finally {
                watcher?.cancel()
                if (!saved) scope.launch { onFailure() }
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

    /**
     * Следит за растущим файлом и переводит его размер в долю.
     *
     * Ядро пишет вложение в файл с припиской `.part` и до конца приёма
     * ничего о ходе не сообщает: события компаньона говорят только о том,
     * сколько собрал **телефон**, — а это давно сто процентов.
     */
    private fun CoroutineScope.launchPartWatcher(file: FfiFile, destination: File): Job {
        val hex = file.fileId.toHexString()
        val total = file.sizeBytes.toLong()
        val part = File(destination.parentFile, destination.name + ".part")
        return launch(Dispatchers.IO) {
            Log.d(TAG, "watching ${part.name} in ${part.parentFile?.name}")
            while (isActive) {
                // Имя недописанного файла задаёт ядро: берём и «.part», и любой
                // другой временный сосед с тем же началом имени.
                val written = when {
                    destination.isFile -> destination.length()
                    part.isFile -> part.length()
                    else -> destination.parentFile
                        ?.listFiles { f -> f.name.startsWith(destination.name) && f != destination }
                        ?.maxOfOrNull { it.length() } ?: 0L
                }
                if (total > 0) {
                    _saveProgress.update { it + (hex to (written.toFloat() / total).coerceIn(0f, 1f)) }
                }
                delay(400)
            }
        }
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
        val destination = cacheFileFor(file)

        if (destination.exists() && destination.length() == file.sizeBytes.toLong()) {
            FileUtils.openFile(destination)
            return
        }

        saveFile(file, destination) {
            FileUtils.openFile(it)
        }
    }

    override fun openMedia(file: FfiFile) {
        // Не картинка — дело системного приложения; исполняемое `openFile`
        // не откроет вовсе, а положит в загрузки и покажет папку.
        if (!FileUtils.isImage(file.name)) return openFile(file)
        val destination = cacheFileFor(file)
        if (destination.exists() && destination.length() == file.sizeBytes.toLong()) {
            _viewerMedia.value = ViewerMedia(file.name, destination)
            return
        }
        saveFile(file, destination) { _viewerMedia.value = ViewerMedia(file.name, it) }
    }

    override fun closeViewer() {
        _viewerMedia.value = null
    }

    override fun cancelFileJob(fileId: ByteArray) {
        val hex = fileId.toHexString()
        activeJobs.remove(hex)?.cancel()
        _activeJobsFlow.update { it - hex }
    }

    override fun sweepOrphanFiles(onResult: (FfiSwept) -> Unit) {
        session.clientIo { client ->
            val result = client.sweepOrphanFiles()
            scope.launch { onResult(result) }
        }
    }

    override fun setAutoAcceptLimit(limit: ULong?) {
        session.clientIo { client ->
            client.setAutoAcceptBytes(limit)
            _autoAcceptLimit.value = limit
        }
    }

    override fun setDownloadDirPath(path: String?) {
        val id = session.activeAccountId.value ?: return
        scope.launch {
            session.settings.setDownloadDirPath(id, path)
        }
    }

    override fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.FileProgress -> {
                _fileProgress.update { it + (event.fileId.toHexString() to event.fraction) }
            }
            is AppEvent.SaveProgress -> {
                _saveProgress.update { it + (event.fileId.toHexString() to event.fraction) }
            }
            is AppEvent.FileSending -> {
                val hex = event.fileId.toHexString()
                _fileSending.update { it + (hex to event.fraction) }
                _fileWaiting.update { it - hex }
            }
            is AppEvent.FileWaiting -> {
                val hex = event.fileId.toHexString()
                val text = event.text
                _fileWaiting.update { if (text != null) it + (hex to text) else it - hex }
            }
            is AppEvent.PreviewLoaded -> {
                val hex = event.fileId.toHexString()
                val bytes = event.bytes
                if (bytes != null) _filePreviews.update { it + (hex to bytes) }
            }
            else -> {}
        }
    }

    override fun reset() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        previewRequests.clear()
        _fileProgress.value = emptyMap()
        _fileSending.value = emptyMap()
        _fileWaiting.value = emptyMap()
        _saveProgress.value = emptyMap()
        _filePreviews.value = emptyMap()
        _viewerMedia.value = null
        _activeJobsFlow.value = emptySet()
        _autoAcceptLimit.value = null
    }

    /** Имя во временном каталоге: по `fileId`, чтобы разные файлы не сталкивались. */
    private fun cacheFileFor(file: FfiFile) =
        File(AppDirs.getMediaCacheDir(), "${file.fileId.toHexString()}_${FileUtils.safeName(file.name)}")

    private companion object {
        const val TAG = "FilesModel"
    }
}
