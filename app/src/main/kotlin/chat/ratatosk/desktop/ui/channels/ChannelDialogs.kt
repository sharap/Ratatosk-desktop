package chat.ratatosk.desktop.ui.channels

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.ui.components.Avatar
import chat.ratatosk.desktop.util.ClipboardUtils
import chat.ratatosk.desktop.util.toHexString
import qrcode.QRCode

/**
 * Заведение канала.
 *
 * Порода задаётся один раз и не меняется: «открытый» и «по приглашению» —
 * два разных обещания (§6.1). Перед открытым — текст §15: ключ чтения
 * лежит в самой ссылке, и закрыть доступ обратно нельзя никогда.
 * Заводящему канал по приглашению не говорится ничего — такого текста
 * в §15 нет, и придумывать его клиенту нельзя.
 */
@Composable
fun CreateChannelDialog(viewModel: RatatoskViewModel, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.CHANNEL_CREATE) },
        text = {
            Column(Modifier.widthIn(max = 520.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(Strings.CHANNEL_TITLE_HINT) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(Strings.CHANNEL_KIND, style = MaterialTheme.typography.labelLarge)
                Column(Modifier.selectableGroup()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !open, onClick = { open = false })
                        Text(Strings.CHANNEL_KIND_PRIVATE)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = open, onClick = { open = true })
                        Text(Strings.CHANNEL_KIND_OPEN)
                    }
                }
                if (open) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = viewModel.channelNotices.open,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.createChannel(title.trim(), open); onDismiss() },
                enabled = title.isNotBlank(),
            ) { Text(Strings.CHANNEL_CREATE_ACTION) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } },
    )
}

/**
 * Подписка по ссылке.
 *
 * Оба текста §15 — и с условием: порода до подписки неизвестна. Ссылка
 * ничем не подписана (§10.2), выдать её обещание за установленное нельзя,
 * а разбирать её тело у себя — значит повторять формат ядра в клиенте.
 */
@Composable
fun SubscribeChannelDialog(
    viewModel: RatatoskViewModel,
    uri: String? = null,
    onDismiss: () -> Unit,
) {
    var link by remember { mutableStateOf(uri.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.CHANNEL_SUBSCRIBE) },
        text = {
            Column(Modifier.widthIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                if (uri == null) {
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        label = { Text(Strings.CHANNEL_SUBSCRIBE_HINT) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Text(Strings.CHANNEL_SUBSCRIBE_OPEN_IF, style = MaterialTheme.typography.labelMedium)
                Text(viewModel.channelNotices.open, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Text(Strings.CHANNEL_SUBSCRIBE_PRIVATE_IF, style = MaterialTheme.typography.labelMedium)
                Text(viewModel.channelNotices.private, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.subscribeToChannel(link.trim()); onDismiss() },
                enabled = link.isNotBlank(),
            ) { Text(Strings.CHANNEL_SUBSCRIBE_ACTION) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } },
    )
}

/** Отписка уносит и архив, и сказано это до кнопки (§10.6). */
@Composable
fun UnsubscribeChannelDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.CHANNEL_UNSUBSCRIBE) },
        text = { Text(Strings.CHANNEL_UNSUBSCRIBE_WARNING, modifier = Modifier.widthIn(max = 520.dp)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(Strings.CHANNEL_UNSUBSCRIBE) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } },
    )
}

/**
 * Ссылка на канал.
 *
 * Собирается в момент показа и запросом: в неё едут нынешняя версия
 * представления и наши адреса (§10.2). Перед показом — текст §15, а
 * у открытого канала сказано и второе: в ссылке едет ключ чтения.
 * Сокращать её сторонним сервисом нельзя — ключ уедет сокращателю.
 */
@Composable
fun ChannelLinkDialog(
    viewModel: RatatoskViewModel,
    chatId: ByteArray,
    open: Boolean?,
    onDismiss: () -> Unit,
) {
    var link by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(chatId.contentHashCode()) {
        viewModel.channelLink(chatId) { result ->
            result.onSuccess { link = it }.onFailure { failed = true }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.CHANNEL_LINK) },
        text = {
            Column(Modifier.widthIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Text(viewModel.channelNotices.sharing, style = MaterialTheme.typography.bodySmall)
                if (open == true) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = Strings.CHANNEL_LINK_OPEN_WARNING,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(12.dp))
                val shown = link
                when {
                    failed -> Text(Strings.CHANNEL_LINK_FAILED, color = MaterialTheme.colorScheme.error)
                    shown == null -> CircularProgressIndicator()
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Код — чтобы снять его телефоном: ссылка длинная,
                        // руками её не переписывают.
                        val code = remember(shown) {
                            runCatching {
                                (QRCode(shown).render().nativeImage() as java.awt.image.BufferedImage)
                                    .toComposeImageBitmap()
                            }.getOrNull()
                        }
                        if (code != null) {
                            Image(
                                bitmap = code,
                                contentDescription = Strings.CHANNEL_LINK,
                                modifier = Modifier.size(240.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        SelectionContainer {
                            Text(shown, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        },
        confirmButton = {
            val shown = link
            if (shown != null) {
                TextButton(onClick = { ClipboardUtils.copyToClipboard(shown); onDismiss() }) {
                    Text(Strings.COPY)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CLOSE) } },
    )
}

/** Обязательный текст §15 перед действием, у которого есть цена. */
@Composable
fun ChannelNoticeDialog(
    title: String,
    notice: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(notice, modifier = Modifier.widthIn(max = 520.dp)) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } },
    )
}

/** Сроки выдачи: «без срока» здесь быть не может (§6.3). */
private enum class Term(val days: Long, val label: String) {
    MONTH(30, Strings.CHANNEL_RIGHT_TERM_MONTH),
    QUARTER(90, Strings.CHANNEL_RIGHT_TERM_QUARTER),
    YEAR(365, Strings.CHANNEL_RIGHT_TERM_YEAR),
}

/**
 * Выдача и снятие прав (§6.2, §6.3).
 *
 * Снятие — это выдача с пустым набором: список в новой версии
 * представления и есть всё, что действует. Срок спрашивается всегда:
 * непродлённое право истекает само, а право без срока означало бы отзыв.
 */
@Composable
fun ChannelRightDialog(
    viewModel: RatatoskViewModel,
    chatId: ByteArray,
    peerIk: ByteArray,
    name: String,
    currentWrite: Boolean = false,
    currentAdmit: Boolean = false,
    currentEvict: Boolean = false,
    currentEdit: Boolean = false,
    onDismiss: () -> Unit,
) {
    var write by remember { mutableStateOf(currentWrite) }
    var admit by remember { mutableStateOf(currentAdmit) }
    var evict by remember { mutableStateOf(currentEvict) }
    var edit by remember { mutableStateOf(currentEdit) }
    var term by remember { mutableStateOf(Term.MONTH) }
    val nothing = !write && !admit && !evict && !edit

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column(Modifier.widthIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                CheckRow(write, { write = it }, Strings.CHANNEL_RIGHT_WRITE)
                CheckRow(admit, { admit = it }, Strings.CHANNEL_RIGHT_ADMIT)
                CheckRow(evict, { evict = it }, Strings.CHANNEL_RIGHT_EVICT)
                CheckRow(edit, { edit = it }, Strings.CHANNEL_RIGHT_EDIT)
                if (admit) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = viewModel.channelNotices.admitterGrant,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (nothing) {
                    Text(Strings.CHANNEL_RIGHT_NONE)
                } else {
                    Text(Strings.CHANNEL_RIGHT_TERM, style = MaterialTheme.typography.labelLarge)
                    Column(Modifier.selectableGroup()) {
                        Term.entries.forEach { option ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = term == option, onClick = { term = option })
                                Text(option.label)
                            }
                        }
                    }
                    Text(
                        text = Strings.CHANNEL_RIGHT_TERM_REQUIRED,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val until = if (nothing) {
                    0UL
                } else {
                    (System.currentTimeMillis() + term.days * 24L * 60 * 60 * 1000).toULong()
                }
                viewModel.setChannelRight(chatId, peerIk, write, admit, evict, edit, until)
                onDismiss()
            }) { Text(Strings.SAVE) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } },
    )
}

@Composable
private fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

/** Цена слова (§11): фильтр первого уровня, и сказано это прямо. */
@Composable
fun ChannelPowDialog(current: UInt, onConfirm: (UInt) -> Unit, onDismiss: () -> Unit) {
    var bits by remember { mutableStateOf(current.toString()) }
    val parsed = bits.toUIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.CHANNEL_POW_TITLE) },
        text = {
            Column(Modifier.widthIn(max = 520.dp)) {
                OutlinedTextField(
                    value = bits,
                    onValueChange = { bits = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text(Strings.CHANNEL_POW_HINT) },
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = Strings.CHANNEL_POW_EXPLAIN,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = { parsed?.let(onConfirm); onDismiss() }, enabled = parsed != null) {
                Text(Strings.SAVE)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } },
    )
}

/**
 * Выбор контакта для канала.
 *
 * Годится и впуску, и выдаче права: и то и другое адресовано человеку,
 * чья карточка у нас есть — ключ чтения запечатывается на неё, а право
 * выдаётся на его ключ. Список здесь из контактов именно поэтому.
 *
 * @param exclude кого не предлагать: уже впущенных или уже одарённых.
 * @param onPick что сделать с выбранным.
 */
@Composable
fun PickChannelContactDialog(
    viewModel: RatatoskViewModel,
    title: String,
    desc: String,
    exclude: List<ByteArray>,
    onPick: (peerIk: ByteArray, name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val contacts by viewModel.contacts.collectAsState()
    val avatars by viewModel.contactAvatars.collectAsState()
    val available = remember(contacts, exclude) {
        contacts.filterNot { c -> exclude.any { it.contentEquals(c.peerIk) } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.widthIn(max = 520.dp)) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (available.isEmpty()) {
                    Text(Strings.CHANNEL_ADMIT_NONE_LEFT)
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(available) { contact ->
                            val name = contact.localName ?: contact.displayName
                            ListItem(
                                headlineContent = { Text(name) },
                                leadingContent = {
                                    Avatar(
                                        avatarBytes = avatars[contact.peerIk.toHexString()]
                                            ?: viewModel.getAvatarOf(contact.peerIk),
                                        name = name,
                                    )
                                },
                                modifier = Modifier.clickable {
                                    onPick(contact.peerIk, name)
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } },
    )
}
