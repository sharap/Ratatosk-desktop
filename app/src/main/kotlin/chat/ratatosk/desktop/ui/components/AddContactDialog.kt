package chat.ratatosk.desktop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AddContactDialog(onDismiss: () -> Unit, onAdd: (String, Boolean) -> Unit) {
    var uri by remember { mutableStateOf("") }
    var inPerson by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Contact") },
        text = {
            Column {
                OutlinedTextField(
                    value = uri,
                    onValueChange = { uri = it },
                    label = { Text("Ratatosk URI") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("ratatosk:v0:...") }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = inPerson, onCheckedChange = { inPerson = it })
                    Text("Met in person", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = "If not met in person, you must verify the fingerprint later.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(uri, inPerson) },
                enabled = uri.startsWith("ratatosk:")
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
