package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.core.RatatoskCore
import chat.ratatosk.desktop.util.AppDirs
import chat.ratatosk.desktop.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.ratatosk.core.FfiArchivePeek
import org.ratatosk.core.FfiArchiveUnlock
import org.ratatosk.core.FfiExportScope
import org.ratatosk.core.FfiExported
import org.ratatosk.core.FfiImported
import org.ratatosk.core.FfiMerged
import org.ratatosk.core.peekArchive
import java.io.File

/**
 * Долгая операция с архивом. Любой исход, кроме [Working], отпускает окно:
 * ошибка — такое же завершение, как успех (в Android окно экспорта при
 * ошибке запиралось навсегда, ревью 2.3).
 */
sealed interface BackupOp {
    data object Idle : BackupOp
    data object Working : BackupOp
    data class Exported(val result: FfiExported) : BackupOp
    data class Imported(val result: FfiImported, val label: String) : BackupOp
    data class Merged(val result: FfiMerged) : BackupOp
    data class Failed(val message: String) : BackupOp
}

interface BackupApi {
    val backupOp: StateFlow<BackupOp>

    /** Архив открытого аккаунта в [destination]; `phrase == null` — только ключ. */
    fun exportArchive(destination: File, scope: FfiExportScope, phrase: String?)
    /** Чем открывается архив и что в нём; ни ключа, ни фразы не нужно. */
    suspend fun peekArchiveFile(archive: File): Result<FfiArchivePeek>
    /** Новый аккаунт из архива; зовётся до открытия какого-либо аккаунта. */
    fun importArchive(archive: File, unlock: FfiArchiveUnlock, label: String)
    /** Контакты из архива — в открытый аккаунт, поверх живой переписки. */
    fun mergeContacts(archive: File, unlock: FfiArchiveUnlock)
    fun clearBackupOp()
    /** Открытый аккаунт привязан к этой машине — его архив восстановится только здесь. */
    suspend fun isActiveAccountDeviceBound(): Boolean
}

/** Резервная копия (§12): вывоз, ввоз, слияние знакомств. */
class BackupModel(
    session: SessionContext,
    private val onAccountsChanged: () -> Unit,
) : FeatureModel(session), BackupApi {
    private val _backupOp = MutableStateFlow<BackupOp>(BackupOp.Idle)
    override val backupOp = _backupOp.asStateFlow()

    private fun run(what: String, block: suspend () -> BackupOp) {
        if (_backupOp.value == BackupOp.Working) return
        _backupOp.value = BackupOp.Working
        scope.launch(Dispatchers.IO) {
            _backupOp.value = try {
                block()
            } catch (e: Exception) {
                Log.w(TAG, "$what failed", e)
                BackupOp.Failed(e.message ?: e::class.simpleName.orEmpty())
            }
        }
    }

    override fun exportArchive(destination: File, scope: FfiExportScope, phrase: String?) {
        run("Export") {
            val client = session.client ?: error("No account is open")
            // Ядро и само откажет, но сказать «файл уже есть» понятнее, чем пересказывать отказ.
            if (destination.exists()) error("File already exists: ${destination.name}")
            destination.parentFile?.mkdirs()
            BackupOp.Exported(client.exportHistory(destination.absolutePath, scope, phrase?.takeIf { it.isNotEmpty() }))
        }
    }

    override suspend fun peekArchiveFile(archive: File): Result<FfiArchivePeek> =
        kotlinx.coroutines.withContext(Dispatchers.IO) { runCatching { peekArchive(archive.absolutePath) } }

    override fun importArchive(archive: File, unlock: FfiArchiveUnlock, label: String) {
        run("Import") {
            val imported = RatatoskCore.importArchive(archive.absolutePath, unlock, label)
            kotlinx.coroutines.withContext(Dispatchers.Main) { onAccountsChanged() }
            BackupOp.Imported(imported, label)
        }
    }

    override fun mergeContacts(archive: File, unlock: FfiArchiveUnlock) {
        run("Merge") {
            val client = session.client ?: error("No account is open")
            // Черновик — расшифрованная копия чужой базы: только в своём каталоге.
            val scratch = File(AppDirs.getMediaCacheDir().parentFile, "merge-${System.nanoTime()}").apply { mkdirs() }
            try {
                val merged = client.mergeContacts(archive.absolutePath, unlock, scratch.absolutePath)
                session.backend?.requestChats()
                BackupOp.Merged(merged)
            } finally {
                scratch.deleteRecursively()
            }
        }
    }

    override suspend fun isActiveAccountDeviceBound(): Boolean {
        val id = session.activeAccountId.value ?: return false
        return session.settings.isDeviceBound(id).first()
    }

    override fun clearBackupOp() {
        if (_backupOp.value != BackupOp.Working) _backupOp.value = BackupOp.Idle
    }

    override fun reset() {
        clearBackupOp()
    }

    private companion object {
        const val TAG = "BackupModel"
    }
}
