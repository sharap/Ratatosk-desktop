package chat.ratatosk.desktop.ui.unlock

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.ui.components.SecretTextField
import org.ratatosk.core.FfiAccount

@Composable
fun UnlockScreen(viewModel: RatatoskViewModel, account: FfiAccount) {
    var pin by remember { mutableStateOf("") }
    val error by viewModel.error.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        
        Text(
            text = Strings.WELCOME_BACK,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = account.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        error?.let {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        SecretTextField(
            value = pin,
            onValueChange = { pin = it },
            label = Strings.ENTER_PIN,
            modifier = Modifier.fillMaxWidth(0.8f),
            onSubmit = { viewModel.unlock(account, pin.takeIf { it.isNotEmpty() }) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(0.8f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { viewModel.selectAccount(null) },
                modifier = Modifier.weight(1f)
            ) {
                Text(Strings.CANCEL)
            }
            Button(
                onClick = { viewModel.unlock(account, pin.takeIf { it.isNotEmpty() }) },
                modifier = Modifier.weight(1f)
            ) {
                Text(Strings.UNLOCK)
            }
        }
    }
}
