package chat.ratatosk.desktop.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.model.ChatMessage
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.ui.components.Avatar
import chat.ratatosk.desktop.util.MarkdownUtils
import chat.ratatosk.desktop.util.MessagePreview
import org.ratatosk.core.FfiDeliveryStatus
import org.ratatosk.core.FfiMessage
import java.text.SimpleDateFormat
import java.util.Date

/** Что можно сделать с сообщением; `null` — пункта нет. */
class MessageActions(
    val onReply: () -> Unit,
    val onReact: (String?) -> Unit,
    val onCopy: () -> Unit,
    val onForward: () -> Unit,
    val onDeleteForMe: () -> Unit,
    val onRetractForAll: (() -> Unit)?,
    val onEdit: (() -> Unit)?,
    val onRetry: (() -> Unit)?,
)

private val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")
private const val COLLAPSE_CHARS = 1500

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun MessageRow(
    message: ChatMessage,
    isGroup: Boolean,
    outgoingColor: Color,
    highlighted: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    revealedSpoilers: Set<Int>,
    onRevealSpoiler: (Int) -> Unit,
    authorAvatar: ByteArray?,
    onAuthorClick: (() -> Unit)?,
    reply: FfiMessage?,
    replyAuthor: String?,
    onReplyClick: (() -> Unit)?,
    waitingNotice: String,
    actions: MessageActions,
    attachments: @Composable (contentColor: Color, accent: Color) -> Unit,
) {
    val raw = message.raw
    val mine = raw.mine
    var hovered by remember { mutableStateOf(false) }
    var menuAt by remember { mutableStateOf<Offset?>(null) }

    val bubbleColor = if (mine) outgoingColor else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (mine) (if (bubbleColor.luminance() > 0.5f) Color.Black else Color.White) else MaterialTheme.colorScheme.onSurfaceVariant
    val accent = if (mine) contentColor else MaterialTheme.colorScheme.primary
    val highlightColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlighted) highlightColor else Color.Transparent, RoundedCornerShape(8.dp))
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .padding(horizontal = 4.dp, vertical = if (message.startsRun) 4.dp else 1.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        // Лицо автора в группе — у первого сообщения серии, дальше отступ той же ширины.
        if (isGroup && !mine) {
            Box(Modifier.width(40.dp)) {
                if (message.startsRun) {
                    Avatar(
                        avatarBytes = authorAvatar,
                        name = message.author?.name ?: "?",
                        size = 32.dp,
                        modifier = if (onAuthorClick != null) Modifier.clickable(onClick = onAuthorClick) else Modifier,
                    )
                }
            }
        }

        if (mine) HoverActions(visible = hovered, actions = actions, mine = true)

        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 520.dp)) {
            if (isGroup && !mine && message.startsRun) {
                Text(
                    text = message.author?.name ?: Strings.CHAT_UNKNOWN_AUTHOR,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                        .then(if (onAuthorClick != null) Modifier.clickable(onClick = onAuthorClick) else Modifier),
                )
            }

            Box {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (mine) 16.dp else 4.dp,
                        bottomEnd = if (mine) 4.dp else 16.dp,
                    ),
                    color = bubbleColor,
                    contentColor = contentColor,
                    modifier = Modifier
                        // Правый щелчок — меню у курсора; долгое нажатие — для сенсорных экранов.
                        .onPointerEvent(PointerEventType.Press) { e ->
                            if (e.buttons.isSecondaryPressed) menuAt = e.changes.first().position
                        }
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onLongClick = { menuAt = Offset.Zero },
                        ),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        if (raw.replyTo != null) {
                            ReplyQuote(reply, replyAuthor, contentColor, accent, onReplyClick)
                        }
                        if (raw.forwarded) {
                            Text(Strings.FORWARDED, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.7f))
                        }
                        attachments(contentColor, accent)
                        if (raw.body.isNotBlank()) {
                            MessageText(raw.body, contentColor, accent, expanded, onToggleExpanded, revealedSpoilers, onRevealSpoiler)
                        }
                        MetaLine(raw, message.status, contentColor, waitingNotice, actions.onRetry)
                    }
                }

                val density = LocalDensity.current
                DropdownMenu(
                    expanded = menuAt != null,
                    onDismissRequest = { menuAt = null },
                    offset = menuAt?.let { with(density) { DpOffset(it.x.toDp(), it.y.toDp()) } } ?: DpOffset.Zero,
                ) {
                    MessageMenu(message, actions, close = { menuAt = null })
                }
            }

            if (message.reactions.isNotEmpty()) {
                Row(Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    message.reactions.forEach { chip ->
                        Surface(
                            color = if (chip.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { actions.onReact(if (chip.mine) null else chip.emoji) },
                        ) {
                            Text(
                                if (chip.count > 1) "${chip.emoji} ${chip.count}" else chip.emoji,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        if (!mine) HoverActions(visible = hovered, actions = actions, mine = false)
    }
}

/** Кнопки при наведении: ответить и поставить реакцию — без меню. */
@Composable
private fun HoverActions(visible: Boolean, actions: MessageActions, mine: Boolean) {
    var picker by remember { mutableStateOf(false) }
    Box(Modifier.width(72.dp).padding(horizontal = 4.dp), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        if (visible || picker) {
            Row {
                IconButton(onClick = actions.onReply, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = Strings.REPLY, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                }
                Box {
                    IconButton(onClick = { picker = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.AddReaction, contentDescription = Strings.CHAT_REACT, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                    DropdownMenu(expanded = picker, onDismissRequest = { picker = false }) {
                        Row(Modifier.padding(horizontal = 8.dp)) {
                            QUICK_REACTIONS.forEach { emoji ->
                                TextButton(onClick = { actions.onReact(emoji); picker = false }, contentPadding = PaddingValues(4.dp)) {
                                    Text(emoji, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageMenu(message: ChatMessage, actions: MessageActions, close: () -> Unit) {
    val myReactions = message.reactions.filter { it.mine }.map { it.emoji }.toSet()
    Row(Modifier.padding(horizontal = 8.dp)) {
        QUICK_REACTIONS.forEach { emoji ->
            val selected = emoji in myReactions
            TextButton(onClick = { actions.onReact(if (selected) null else emoji); close() }, contentPadding = PaddingValues(4.dp)) {
                Text(emoji, style = MaterialTheme.typography.titleMedium, color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified)
            }
        }
    }
    HorizontalDivider()
    DropdownMenuItem(text = { Text(Strings.REPLY) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, null) }, onClick = { close(); actions.onReply() })
    if (message.raw.body.isNotBlank()) {
        DropdownMenuItem(text = { Text(Strings.CHAT_COPY_TEXT) }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { close(); actions.onCopy() })
    }
    actions.onEdit?.let { edit ->
        DropdownMenuItem(text = { Text(Strings.EDIT) }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { close(); edit() })
    }
    DropdownMenuItem(text = { Text(Strings.FORWARD) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }, onClick = { close(); actions.onForward() })
    actions.onRetry?.let { retry ->
        DropdownMenuItem(text = { Text(Strings.CHAT_RETRY) }, leadingIcon = { Icon(Icons.Default.Refresh, null) }, onClick = { close(); retry() })
    }
    HorizontalDivider()
    actions.onRetractForAll?.let { retract ->
        DropdownMenuItem(
            text = { Text(Strings.CHAT_RETRACT_FOR_ALL, color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, null, tint = MaterialTheme.colorScheme.error) },
            onClick = { close(); retract() },
        )
    }
    DropdownMenuItem(
        text = { Text(Strings.CHAT_DELETE_FOR_ME, color = MaterialTheme.colorScheme.error) },
        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
        onClick = { close(); actions.onDeleteForMe() },
    )
}

@Composable
private fun ReplyQuote(reply: FfiMessage?, author: String?, contentColor: Color, accent: Color, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(contentColor.copy(alpha = 0.08f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .height(IntrinsicSize.Min)
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            if (author != null) Text(author, style = MaterialTheme.typography.labelMedium, color = accent)
            Text(
                text = reply?.let { MessagePreview.of(it) } ?: Strings.MESSAGE_UNAVAILABLE,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = contentColor.copy(alpha = 0.8f),
            )
        }
    }
}

/**
 * Текст с разметкой. Выделяется мышью; ссылки открываются щелчком (только
 * веб и почта); спойлер открывается щелчком и остаётся открытым, пока чат
 * на экране, — состояние держит экран, а не пузырь, который пропадает при прокрутке.
 */
@Composable
private fun MessageText(
    body: String,
    contentColor: Color,
    accent: Color,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    revealed: Set<Int>,
    onReveal: (Int) -> Unit,
) {
    val parsed = remember(body, accent) { MarkdownUtils.parseMarkdown(body, accent) }
    val long = parsed.length > COLLAPSE_CHARS
    val shown = remember(parsed, expanded, revealed, contentColor) {
        val base = if (long && !expanded) parsed.subSequence(0, COLLAPSE_CHARS) else parsed
        withSpoilers(base, revealed, contentColor, onReveal)
    }
    SelectionContainer {
        Text(shown, style = MaterialTheme.typography.bodyMedium.copy(color = contentColor))
    }
    if (long) {
        Text(
            if (expanded) Strings.CHAT_COLLAPSE else Strings.CHAT_SHOW_ALL,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            modifier = Modifier.padding(top = 4.dp).clickable(onClick = onToggleExpanded),
        )
    }
}

private fun withSpoilers(base: AnnotatedString, revealed: Set<Int>, contentColor: Color, onReveal: (Int) -> Unit): AnnotatedString {
    val spoilers = base.getStringAnnotations("SPOILER", 0, base.length)
    if (spoilers.isEmpty()) return base
    return buildAnnotatedString {
        append(base)
        spoilers.forEachIndexed { index, span ->
            if (index in revealed) {
                addStyle(SpanStyle(background = contentColor.copy(alpha = 0.12f)), span.start, span.end)
            } else {
                addStyle(SpanStyle(background = Color.DarkGray, color = Color.Transparent), span.start, span.end)
                addLink(LinkAnnotation.Clickable("spoiler-$index") { onReveal(index) }, span.start, span.end)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MetaLine(raw: FfiMessage, status: FfiDeliveryStatus?, contentColor: Color, waitingNotice: String, onRetry: (() -> Unit)?) {
    Row(
        modifier = Modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Spacer(Modifier.weight(1f, fill = false))
        if (raw.editedAtMs != null) {
            Text(Strings.CHAT_EDITED, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.6f))
        }
        Text(
            SimpleDateFormat("HH:mm").format(Date(raw.wallMs.toLong())),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.7f),
        )
        if (raw.mine && status != null) {
            val (icon, label) = statusIcon(status)
            val tint = when (status) {
                FfiDeliveryStatus.UNDELIVERABLE -> MaterialTheme.colorScheme.error
                FfiDeliveryStatus.READ -> if (contentColor == Color.White) Color(0xFF9FE3FF) else Color(0xFF0277BD)
                else -> contentColor.copy(alpha = 0.7f)
            }
            // Подсказка словами: «ждёт собеседника» — не ошибка (FFI.md, WAITING).
            TooltipArea(tooltip = {
                Surface(shape = RoundedCornerShape(6.dp), tonalElevation = 4.dp, shadowElevation = 2.dp) {
                    Text(
                        if (status == FfiDeliveryStatus.WAITING && waitingNotice.isNotBlank()) "$label. $waitingNotice" else label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp).widthIn(max = 320.dp),
                    )
                }
            }) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(14.dp), tint = tint)
            }
            if (status == FfiDeliveryStatus.UNDELIVERABLE && onRetry != null) {
                Text(
                    Strings.CHAT_RETRY,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(onClick = onRetry),
                )
            }
        }
    }
}

private fun statusIcon(status: FfiDeliveryStatus) = when (status) {
    FfiDeliveryStatus.PENDING -> Icons.Default.Schedule to Strings.STATUS_PENDING
    FfiDeliveryStatus.WAITING -> Icons.Default.HourglassEmpty to Strings.STATUS_WAITING
    FfiDeliveryStatus.SENT -> Icons.Default.Done to Strings.STATUS_SENT
    FfiDeliveryStatus.DELIVERED -> Icons.Default.DoneAll to Strings.STATUS_DELIVERED
    FfiDeliveryStatus.READ -> Icons.Default.DoneAll to Strings.STATUS_READ
    FfiDeliveryStatus.UNDELIVERABLE -> Icons.Default.ErrorOutline to Strings.STATUS_UNDELIVERABLE
}
