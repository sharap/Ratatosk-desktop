package chat.ratatosk.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.model.TransportInput
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.util.ClipboardUtils
import kotlinx.coroutines.launch
import org.ratatosk.core.FfiTransport
import org.ratatosk.core.FfiYggMode
import org.ratatosk.core.yggAddress

// Разделы настроек меша Yggdrasil (0.2) и ступени nostr (0.3).
// Тексты ядра о цене включения показываются в подтверждении **до**
// переключения — так требует ядро, и только так они что-то значат.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YggdrasilSection(viewModel: RatatoskViewModel) {
    val state by viewModel.yggState.collectAsState()
    val alive by viewModel.yggPeersAlive.collectAsState()
    val ready by viewModel.transportsReady.collectAsState()
    val notices = viewModel.yggNotices

    var pendingMode by remember { mutableStateOf<FfiYggMode?>(null) }
    var showKeyDialog by remember { mutableStateOf(false) }
    var showPeersDialog by remember { mutableStateOf(false) }

    val current = state ?: return
    val mode = current.mode

    // Живое состояние пиров — только пока раздел на экране и узел свой.
    if (mode == FfiYggMode.EMBEDDED) {
        DisposableEffect(Unit) {
            val watch = viewModel.watchYggLive()
            onDispose { watch.close() }
        }
    }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            TitleWithDot(Strings.YGG_TRANSPORT, ready[FfiTransport.YGG] == true)
            Text(Strings.YGG_DESC, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            val modes = listOf(FfiYggMode.OFF to Strings.YGG_MODE_OFF, FfiYggMode.EMBEDDED to Strings.YGG_MODE_EMBEDDED, FfiYggMode.EXTERNAL to Strings.YGG_MODE_EXTERNAL)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, (m, label) ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = {
                            if (m == mode) return@SegmentedButton
                            if (yggTexts(mode, m, notices.warning, notices.nodeNotice, notices.nodeStopNotice).isEmpty()) {
                                viewModel.setYggMode(m)
                            } else {
                                pendingMode = m
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size)
                    ) { Text(label, maxLines = 1) }
                }
            }

            if (mode != FfiYggMode.OFF) {
                Spacer(Modifier.height(12.dp))
                if (current.keyHex == null) {
                    Text(
                        Strings.YGG_KEY_NOT_SET,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (mode == FfiYggMode.EXTERNAL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    )
                } else {
                    current.address?.let { CopyableLine(Strings.YGG_ADDRESS.format(it), it) }
                    CopyableLine(Strings.YGG_KEY.format(current.keyHex.take(16) + "…"), current.keyHex)
                }
            }

            if (mode == FfiYggMode.EXTERNAL) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showKeyDialog = true }) { Text(Strings.YGG_SET_KEY) }
            }

            if (mode == FfiYggMode.EMBEDDED) {
                Spacer(Modifier.height(8.dp))
                val peers = current.peers
                if (peers.isEmpty()) {
                    Text(Strings.YGG_NO_PEERS, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                } else {
                    val live = alive
                    Text(
                        if (live != null) Strings.YGG_PEERS_ALIVE.format(live.count { it.up }, live.size) else Strings.YGG_PEERS.format(peers.size),
                        style = MaterialTheme.typography.labelMedium
                    )
                    // Мёртвый пир из живого списка не исчезает — сшивать с настроенным не нужно.
                    (live?.map { Triple(it.uri, it.up, it) } ?: peers.map { Triple(it, null, null) }).forEach { (uri, up, peer) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            StatusDot(up)
                            Spacer(Modifier.width(6.dp))
                            Text(uri, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (peer != null) {
                                if (peer.inbound) Text(" · ${Strings.YGG_PEER_INBOUND}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                if (peer.up && peer.latencyMs > 0) Text(" · ${Strings.YGG_LATENCY.format(peer.latencyMs)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showPeersDialog = true }) { Text(Strings.YGG_EDIT_PEERS) }
            }
        }
    }

    pendingMode?.let { target ->
        ConfirmTexts(
            title = Strings.YGG_TRANSPORT,
            texts = yggTexts(mode, target, notices.warning, notices.nodeNotice, notices.nodeStopNotice),
            confirmLabel = Strings.CONTINUE,
            onDismiss = { pendingMode = null },
            onConfirm = {
                viewModel.setYggMode(target)
                pendingMode = null
            }
        )
    }

    if (showKeyDialog) {
        YggKeyDialog(
            viewModel = viewModel,
            initial = current.keyHex.orEmpty(),
            onDismiss = { showKeyDialog = false }
        )
    }

    if (showPeersDialog) {
        ListEditDialog(
            title = Strings.YGG_PEERS_TITLE,
            hint = Strings.YGG_PEERS_HINT,
            initial = current.peers,
            onDismiss = { showPeersDialog = false },
            onSave = { lines ->
                val invalid = viewModel.setYggPeers(lines)
                if (invalid.isEmpty()) showPeersDialog = false
                invalid
            }
        )
    }
}

/**
 * Какие тексты ядра показать при переходе между режимами меша:
 * включение — `ygg_warning`; к своему узлу — вдобавок `ygg_node_notice`;
 * уход со своего узла — `ygg_node_stop_notice`.
 */
internal fun yggTexts(from: FfiYggMode, to: FfiYggMode, warning: String, nodeNotice: String, stopNotice: String): List<String> =
    buildList {
        if (from == FfiYggMode.EMBEDDED && to != FfiYggMode.EMBEDDED) add(stopNotice)
        if (from == FfiYggMode.OFF && to != FfiYggMode.OFF) add(warning)
        if (to == FfiYggMode.EMBEDDED) add(nodeNotice)
    }.filter { it.isNotBlank() }

@Composable
private fun YggKeyDialog(viewModel: RatatoskViewModel, initial: String, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var fetching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val parsed = remember(text) { TransportInput.parseYggKey(text) }
    // Адрес считает ядро: человек сверяет его с `yggdrasilctl getSelf` до сохранения.
    val address = remember(parsed) { parsed?.let { runCatching { yggAddress(it) }.getOrNull() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.YGG_KEY_TITLE) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Strings.YGG_KEY_HINT, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    label = { Text(Strings.YGG_KEY_FIELD) },
                    singleLine = true,
                    isError = text.isNotBlank() && parsed == null,
                    supportingText = {
                        when {
                            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                            text.isNotBlank() && parsed == null -> Text(Strings.YGG_KEY_INVALID)
                            address != null -> Text(Strings.YGG_ADDRESS.format(address))
                        }
                    },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        enabled = !fetching,
                        onClick = {
                            fetching = true
                            scope.launch {
                                viewModel.fetchYggKeyFromDaemon()
                                    .onSuccess { text = it; error = null }
                                    .onFailure { error = Strings.YGG_KEY_DAEMON_FAILED.format(it.message ?: "") }
                                fetching = false
                            }
                        }
                    ) { Text(Strings.YGG_KEY_FROM_DAEMON) }
                    if (fetching) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = text.isBlank() || parsed != null,
                onClick = { if (viewModel.setYggKey(text)) onDismiss() }
            ) { Text(if (text.isBlank()) Strings.YGG_KEY_CLEAR else Strings.SAVE) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } }
    )
}

@Composable
fun NostrSection(viewModel: RatatoskViewModel) {
    val state by viewModel.nostrState.collectAsState()
    val alive by viewModel.nostrRelaysAlive.collectAsState()
    val ready by viewModel.transportsReady.collectAsState()
    val notices = viewModel.nostrNotices

    var confirmEnable by remember { mutableStateOf(false) }
    var confirmDirect by remember { mutableStateOf(false) }
    var showRelaysDialog by remember { mutableStateOf(false) }

    val current = state ?: return

    if (current.enabled) {
        DisposableEffect(Unit) {
            val watch = viewModel.watchNostrLive()
            onDispose { watch.close() }
        }
    }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    TitleWithDot(Strings.NOSTR_TRANSPORT, ready[FfiTransport.NOSTR] == true)
                    Text(Strings.NOSTR_DESC, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = current.enabled,
                    onCheckedChange = { on -> if (on) confirmEnable = true else viewModel.setNostrEnabled(false) }
                )
            }

            if (current.npub.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                CopyableLine(Strings.NOSTR_NPUB.format(current.npub.take(20) + "…"), current.npub)
            }

            if (current.enabled) {
                Spacer(Modifier.height(8.dp))
                if (current.relays.isEmpty()) {
                    Text(Strings.NOSTR_NO_RELAYS, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                } else {
                    val live = alive
                    Text(
                        if (live != null) Strings.NOSTR_RELAYS_ALIVE.format(live.count { it.up }, live.size) else Strings.NOSTR_RELAYS.format(current.relays.size),
                        style = MaterialTheme.typography.labelMedium
                    )
                    val byUrl = live?.associateBy { it.url }
                    current.relays.forEach { url ->
                        val relay = byUrl?.get(url)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            StatusDot(relay?.up)
                            Spacer(Modifier.width(6.dp))
                            Text(url, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            relay?.note?.takeIf { it.isNotBlank() }?.let {
                                Text(" · $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                            }
                        }
                    }
                    // «Названо пять, объявлено три» — показать глазами, а не оставить выяснять.
                    if (current.advertised.size < current.relays.size) {
                        Text(
                            Strings.NOSTR_ADVERTISED.format(current.advertised.size, current.advertised.joinToString(", ")),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showRelaysDialog = true }) { Text(Strings.NOSTR_EDIT_RELAYS) }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Strings.NOSTR_DIRECT, style = MaterialTheme.typography.bodyMedium)
                        Text(Strings.NOSTR_DIRECT_DESC, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(
                        checked = current.direct,
                        onCheckedChange = { on -> if (on) confirmDirect = true else viewModel.setNostrDirect(false) }
                    )
                }
            }
        }
    }

    if (confirmEnable) {
        ConfirmTexts(
            title = Strings.NOSTR_TRANSPORT,
            texts = listOf(notices.warning, notices.noFiles),
            confirmLabel = Strings.ENABLE,
            onDismiss = { confirmEnable = false },
            onConfirm = {
                viewModel.setNostrEnabled(true)
                confirmEnable = false
            }
        )
    }

    if (confirmDirect) {
        ConfirmTexts(
            title = Strings.NOSTR_DIRECT,
            texts = listOf(notices.directWarning),
            confirmLabel = Strings.ENABLE,
            onDismiss = { confirmDirect = false },
            onConfirm = {
                viewModel.setNostrDirect(true)
                confirmDirect = false
            }
        )
    }

    if (showRelaysDialog) {
        ListEditDialog(
            title = Strings.NOSTR_RELAYS_TITLE,
            hint = Strings.NOSTR_RELAYS_HINT,
            initial = current.relays,
            onDismiss = { showRelaysDialog = false },
            onSave = { lines ->
                val invalid = viewModel.setNostrRelays(lines)
                if (invalid.isEmpty()) showRelaysDialog = false
                invalid
            }
        )
    }
}

// --- Общие кусочки ------------------------------------------------------

@Composable
private fun TitleWithDot(title: String, ready: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(8.dp))
        StatusDot(ready)
    }
}

/** Зелёная — на связи, красная — нет, серая — неизвестно. */
@Composable
private fun StatusDot(up: Boolean?) {
    val color = when (up) {
        true -> Color(0xFF4CAF50)
        false -> MaterialTheme.colorScheme.error
        null -> Color.Gray
    }
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
private fun CopyableLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SelectionContainer(Modifier.weight(1f, fill = false)) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { ClipboardUtils.copyToClipboard(value) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.ContentCopy, contentDescription = Strings.COPY, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ConfirmTexts(title: String, texts: List<String>, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                texts.filter { it.isNotBlank() }.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { Button(onClick = onDismiss) { Text(Strings.CANCEL) } }
    )
}

/** Список по строке на элемент. [onSave] возвращает негодные строки; пусто — сохранено. */
@Composable
private fun ListEditDialog(
    title: String,
    hint: String,
    initial: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> List<String>,
) {
    var text by remember { mutableStateOf(initial.joinToString("\n")) }
    var invalid by remember { mutableStateOf<List<String>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hint, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; invalid = emptyList() },
                    minLines = 4,
                    maxLines = 10,
                    isError = invalid.isNotEmpty(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    supportingText = if (invalid.isNotEmpty()) {
                        { Text(Strings.INVALID_LINES.format(invalid.joinToString(", "))) }
                    } else null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = { invalid = onSave(TransportInput.splitLines(text)) }) { Text(Strings.SAVE) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } }
    )
}
