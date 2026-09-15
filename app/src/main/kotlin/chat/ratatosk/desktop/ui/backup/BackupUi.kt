package chat.ratatosk.desktop.ui.backup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import chat.ratatosk.desktop.model.BackupOp
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.ui.components.SecretTextField
import chat.ratatosk.desktop.util.ClipboardUtils
import chat.ratatosk.desktop.util.FilePicker
import chat.ratatosk.desktop.util.FileUtils
import kotlinx.coroutines.launch
import org.ratatosk.core.FfiArchivePeek
import org.ratatosk.core.FfiArchiveUnlock
import org.ratatosk.core.FfiExportScope
import java.io.File
import java.time.LocalDate

// Резервная копия (§12): вывоз из настроек, ввоз с экрана выбора аккаунта,
// слияние знакомств. Что обязано быть сказано человеку — по FFI.md.

/** Раздел настроек полного клиента. */
@Composable
fun BackupSection(viewModel: RatatoskViewModel) {
    var showExport by remember { mutableStateOf(false) }
    var mergeArchive by remember { mutableStateOf<Pair<File, FfiArchivePeek>?>(null) }
    var pickError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column {
        Text(Strings.BACKUP, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(Strings.BACKUP_DESC, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showExport = true }) { Text(Strings.BACKUP_EXPORT) }
            OutlinedButton(onClick = {
                val file = FilePicker.pickFile(Strings.IMPORT_PICK_TITLE) ?: return@OutlinedButton
                scope.launch {
                    viewModel.peekArchiveFile(file)
                        .onSuccess { mergeArchive = file to it }
                        .onFailure { pickError = Strings.IMPORT_NOT_ARCHIVE.format(it.message ?: "") }
                }
            }) { Text(Strings.BACKUP_MERGE) }
        }
        pickError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }

    if (showExport) {
        ExportDialog(viewModel, onDismiss = { showExport = false })
    }

    mergeArchive?.let { (file, peek) ->
        ArchiveUnlockDialog(
            title = Strings.MERGE_UNLOCK_TITLE,
            peek = peek,
            confirmLabel = Strings.MERGE_BUTTON,
            onDismiss = { mergeArchive = null },
            onUnlock = { unlock ->
                viewModel.mergeContacts(file, unlock)
                mergeArchive = null
            }
        )
    }

    BackupProgressAndResult(viewModel)
}

@Composable
private fun ExportDialog(viewModel: RatatoskViewModel, onDismiss: () -> Unit) {
    var scope by remember { mutableStateOf(FfiExportScope.EVERYTHING) }
    var noPhrase by remember { mutableStateOf(false) }
    var phrase by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var fileError by remember { mutableStateOf<String?>(null) }
    val deviceBound by produceState(false) { value = viewModel.isActiveAccountDeviceBound() }

    val phraseOk = noPhrase || (phrase.isNotEmpty() && phrase == repeat)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.BACKUP_EXPORT) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Strings.BACKUP_SCOPE, style = MaterialTheme.typography.labelLarge)
                listOf(
                    Triple(FfiExportScope.EVERYTHING, Strings.BACKUP_SCOPE_EVERYTHING, Strings.BACKUP_SCOPE_EVERYTHING_DESC),
                    Triple(FfiExportScope.WITHOUT_ATTACHMENTS, Strings.BACKUP_SCOPE_NO_FILES, Strings.BACKUP_SCOPE_NO_FILES_DESC),
                    Triple(FfiExportScope.SOCIAL_GRAPH, Strings.BACKUP_SCOPE_GRAPH, Strings.BACKUP_SCOPE_GRAPH_DESC),
                ).forEach { (value, label, desc) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().selectable(selected = scope == value, onClick = { scope = value })
                    ) {
                        RadioButton(selected = scope == value, onClick = { scope = value })
                        Column {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                if (deviceBound) {
                    Text(Strings.BACKUP_DEVICE_BOUND, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
                if (!noPhrase) {
                    Text(Strings.BACKUP_PHRASE_DESC, style = MaterialTheme.typography.bodySmall)
                    SecretTextField(value = phrase, onValueChange = { phrase = it }, label = Strings.BACKUP_PHRASE, modifier = Modifier.fillMaxWidth())
                    SecretTextField(value = repeat, onValueChange = { repeat = it }, label = Strings.BACKUP_PHRASE_REPEAT, modifier = Modifier.fillMaxWidth())
                    if (repeat.isNotEmpty() && phrase != repeat) {
                        Text(Strings.BACKUP_PHRASE_MISMATCH, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(checked = noPhrase, onCheckedChange = { noPhrase = it })
                    Column(Modifier.padding(top = 12.dp)) {
                        Text(Strings.BACKUP_NO_PHRASE, style = MaterialTheme.typography.bodyMedium)
                        Text(Strings.BACKUP_NO_PHRASE_DESC, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                fileError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(enabled = phraseOk, onClick = {
                val file = FilePicker.saveFile(
                    title = Strings.BACKUP_SAVE_TITLE,
                    defaultName = "ratatosk-backup-${LocalDate.now()}.ratatosk",
                    directory = FileUtils.getDownloadsDir().parentFile,
                ) ?: return@Button
                // Ядро поверх файла не пишет — там может лежать единственная копия.
                if (file.exists()) {
                    fileError = Strings.BACKUP_FILE_EXISTS
                    return@Button
                }
                viewModel.exportArchive(file, scope, if (noPhrase) null else phrase)
                onDismiss()
            }) { Text(Strings.BACKUP_CHOOSE_FILE) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } }
    )
}

/**
 * Чем открыть архив. Спрашиваем только то, что в архиве есть ([FfiArchivePeek]):
 * у архива без фразы — только ключ; у архива с фразой — оба входа.
 */
@Composable
fun ArchiveUnlockDialog(
    title: String,
    peek: FfiArchivePeek,
    confirmLabel: String,
    onDismiss: () -> Unit,
    extraContent: @Composable () -> Unit = {},
    confirmEnabled: Boolean = true,
    onUnlock: (FfiArchiveUnlock) -> Unit,
) {
    var byKey by remember { mutableStateOf(!peek.takesPassphrase) }
    var phrase by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Strings.IMPORT_CONTENTS.format(scopeLabel(peek.scope)), style = MaterialTheme.typography.bodySmall)
                if (peek.takesPassphrase) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !byKey, onClick = { byKey = false }, label = { Text(Strings.IMPORT_BY_PHRASE) })
                        FilterChip(selected = byKey, onClick = { byKey = true }, label = { Text(Strings.IMPORT_BY_KEY) })
                    }
                }
                if (byKey) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text(Strings.IMPORT_KEY_FIELD) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    SecretTextField(value = phrase, onValueChange = { phrase = it }, label = Strings.BACKUP_PHRASE, modifier = Modifier.fillMaxWidth())
                }
                extraContent()
            }
        },
        confirmButton = {
            Button(
                enabled = confirmEnabled && (if (byKey) key.isNotBlank() else phrase.isNotEmpty()),
                onClick = {
                    onUnlock(if (byKey) FfiArchiveUnlock.Key(key.trim()) else FfiArchiveUnlock.Passphrase(phrase))
                }
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } }
    )
}

/** Ввоз архива: выбрать файл → узнать, чем он открывается → открыть → итог. */
@Composable
fun ImportArchiveFlow(viewModel: RatatoskViewModel, onClose: () -> Unit) {
    var picked by remember { mutableStateOf<Pair<File, FfiArchivePeek>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var label by remember { mutableStateOf(Strings.IMPORT_LABEL_DEFAULT) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val file = FilePicker.pickFile(Strings.IMPORT_PICK_TITLE)
        if (file == null) {
            onClose()
            return@LaunchedEffect
        }
        scope.launch {
            viewModel.peekArchiveFile(file)
                .onSuccess { picked = file to it }
                .onFailure { error = Strings.IMPORT_NOT_ARCHIVE.format(it.message ?: "") }
        }
    }

    error?.let {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(Strings.IMPORT_ARCHIVE) },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = onClose) { Text(Strings.CLOSE) } }
        )
    }

    picked?.let { (file, peek) ->
        ArchiveUnlockDialog(
            title = Strings.IMPORT_ARCHIVE,
            peek = peek,
            confirmLabel = Strings.IMPORT_BUTTON,
            onDismiss = onClose,
            confirmEnabled = label.isNotBlank(),
            extraContent = {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(Strings.IMPORT_LABEL) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            onUnlock = { unlock ->
                viewModel.importArchive(file, unlock, label.trim())
                onClose()
            }
        )
    }
}

/** Ожидание и итог долгой операции — один на всё окно. */
@Composable
fun BackupProgressAndResult(viewModel: RatatoskViewModel) {
    val op by viewModel.backupOp.collectAsState()

    when (val current = op) {
        BackupOp.Idle -> {}
        BackupOp.Working -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(Strings.BACKUP) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(Strings.BACKUP_WORKING)
                }
            },
            confirmButton = {}
        )
        is BackupOp.Failed -> AlertDialog(
            onDismissRequest = { viewModel.clearBackupOp() },
            title = { Text(Strings.BACKUP) },
            text = { Text(Strings.BACKUP_FAILED.format(current.message)) },
            confirmButton = { TextButton(onClick = { viewModel.clearBackupOp() }) { Text(Strings.CLOSE) } }
        )
        is BackupOp.Exported -> ExportResultDialog(current, onClose = { viewModel.clearBackupOp() })
        is BackupOp.Imported -> {
            val r = current.result
            AlertDialog(
                onDismissRequest = {},
                title = { Text(Strings.IMPORT_DONE) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(current.label, fontWeight = FontWeight.Bold)
                        Text(Strings.IMPORT_STATS.format(r.contacts.toLong(), r.messages.toLong(), r.files.toLong()), style = MaterialTheme.typography.bodySmall)
                        // Три вещи, которые FFI.md требует сказать после ввоза.
                        Text(Strings.IMPORT_OLD_DEVICE)
                        Text(Strings.IMPORT_OLD_PIN)
                        Text(Strings.IMPORT_DEVICE_BOUND)
                        if (r.wholeFiles < r.files) {
                            Text(Strings.IMPORT_PARTIAL_FILES.format(r.wholeFiles.toLong(), r.files.toLong()))
                        }
                    }
                },
                confirmButton = { Button(onClick = { viewModel.clearBackupOp() }) { Text(Strings.BACKUP_DONE_BUTTON) } }
            )
        }
        is BackupOp.Merged -> {
            val r = current.result
            AlertDialog(
                onDismissRequest = { viewModel.clearBackupOp() },
                title = { Text(Strings.MERGE_DONE) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(Strings.MERGE_ADDED.format(r.added.toLong(), r.known.toLong()))
                        if (!r.ownGraph) Text(Strings.MERGE_FOREIGN)
                        if (r.refused > 0UL) Text(Strings.MERGE_REFUSED.format(r.refused.toLong()), color = MaterialTheme.colorScheme.error)
                    }
                },
                confirmButton = { Button(onClick = { viewModel.clearBackupOp() }) { Text(Strings.BACKUP_DONE_BUTTON) } }
            )
        }
    }
}

@Composable
private fun ExportResultDialog(exported: BackupOp.Exported, onClose: () -> Unit) {
    val r = exported.result
    // Ключ — единственный вход в архив без фразы: закрыть, не отметив, что сохранён, нельзя.
    var saved by remember { mutableStateOf(r.lockedByPhrase) }

    AlertDialog(
        onDismissRequest = { if (saved) onClose() },
        properties = DialogProperties(dismissOnBackPress = saved, dismissOnClickOutside = saved),
        title = { Text(Strings.BACKUP_DONE) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Strings.BACKUP_SAVED_TO.format(r.path), style = MaterialTheme.typography.bodySmall)
                if (r.files > 0UL) {
                    Text(Strings.BACKUP_SIZE.format(r.files.toLong(), formatBytes(r.bytes.toLong())), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { FileUtils.openDirectory(File(r.path)) }) { Text(Strings.BACKUP_SHOW_IN_FOLDER) }

                HorizontalDivider()
                Text(Strings.BACKUP_KEY, style = MaterialTheme.typography.labelLarge)
                Text(
                    if (r.lockedByPhrase) Strings.BACKUP_KEY_SPARE else Strings.BACKUP_KEY_ONLY,
                    color = if (r.lockedByPhrase) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectionContainer(Modifier.weight(1f)) {
                        Text(r.keyText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { ClipboardUtils.copyToClipboard(r.keyText) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = Strings.COPY)
                    }
                }
                if (!r.lockedByPhrase) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = saved, onCheckedChange = { saved = it })
                        Text(Strings.BACKUP_KEY_SAVED)
                    }
                }
            }
        },
        confirmButton = { Button(enabled = saved, onClick = onClose) { Text(Strings.BACKUP_DONE_BUTTON) } }
    )
}

private fun scopeLabel(scope: FfiExportScope) = when (scope) {
    FfiExportScope.EVERYTHING -> Strings.BACKUP_SCOPE_EVERYTHING_DESC
    FfiExportScope.WITHOUT_ATTACHMENTS -> Strings.BACKUP_SCOPE_NO_FILES_DESC
    FfiExportScope.SOCIAL_GRAPH -> Strings.BACKUP_SCOPE_GRAPH_DESC
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f ГБ".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f МБ".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.0f КБ".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes Б"
}
