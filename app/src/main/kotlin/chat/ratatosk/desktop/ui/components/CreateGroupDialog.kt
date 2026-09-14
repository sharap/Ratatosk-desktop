package chat.ratatosk.desktop.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.ui.Strings

/**
 * Создание группы. Текст [joinNotice] (§11.5: участники увидят адреса друг
 * друга) стоит в диалоге **до** кнопки — сказать после было бы поздно.
 */
@Composable
fun CreateGroupDialog(
    joinNotice: String,
    maxTitleChars: Int,
    initialTitle: String = "",
    confirmLabel: String = Strings.CREATE_GROUP,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    val trimmed = title.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(confirmLabel) },
        text = {
            Column {
                if (joinNotice.isNotBlank()) {
                    Text(joinNotice, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= maxTitleChars) title = it.replace("\n", " ") },
                    label = { Text(Strings.GROUP_TITLE) },
                    singleLine = true,
                    supportingText = { Text("${title.length} / $maxTitleChars") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(trimmed) }, enabled = trimmed.isNotEmpty()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.CANCEL) }
        },
    )
}
