package chat.ratatosk.desktop.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.util.ClipboardUtils
import chat.ratatosk.desktop.util.inspectLink

/**
 * Куда ведёт ссылка — до перехода, а не после.
 *
 * Ссылку и её подпись пишет собеседник: `[сбербанк.рф](http://зло.example)`
 * читается как банк. Здесь показывается адрес, а не подпись, и названо,
 * чем эта пара странна. Решает человек: перейти или скопировать
 * и посмотреть спокойно.
 *
 * @param shownText текст ссылки, каким его видно в сообщении.
 */
@Composable
fun LinkConfirmDialog(
    url: String,
    shownText: String,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val looks = remember(url, shownText) { inspectLink(url, shownText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.LINK_OPEN_TITLE) },
        text = {
            Column(Modifier.widthIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = Strings.LINK_LEADS_TO,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = looks.host ?: Strings.LINK_UNKNOWN_HOST,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                // Адрес целиком, и его можно выделить: в нём и прячут подмену.
                SelectionContainer {
                    Text(
                        text = looks.url,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                val warnings = buildList {
                    looks.shownHost?.let { add(Strings.LINK_WARN_SHOWN_HOST.format(it)) }
                    if (looks.hasUserInfo) add(Strings.LINK_WARN_USERINFO.format(looks.host ?: ""))
                    if (looks.punycode) add(Strings.LINK_WARN_PUNYCODE)
                    if (!looks.web) add(Strings.LINK_WARN_SCHEME.format(looks.scheme ?: "—"))
                }
                if (warnings.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    warnings.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onOpen(looks.url) }) { Text(Strings.LINK_OPEN) }
        },
        dismissButton = {
            TextButton(onClick = { ClipboardUtils.copyToClipboard(looks.url); onDismiss() }) { Text(Strings.COPY) }
        },
    )
}
