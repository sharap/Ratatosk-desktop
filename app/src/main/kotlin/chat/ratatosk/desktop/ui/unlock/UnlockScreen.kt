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

/**
 * Открытие аккаунта.
 *
 * PIN спрашивается только тогда, когда без него открыть не удалось: у аккаунта
 * без PIN и у привязанного к этому компьютеру спрашивать нечего. Пока идёт
 * попытка — виден индикатор: вывод ключа из PIN занимает секунды, и без него
 * экран выглядел бы застывшим.
 */
@Composable
fun UnlockScreen(viewModel: RatatoskViewModel, account: FfiAccount) {
    var pin by remember(account.id) { mutableStateOf("") }
    val error by viewModel.error.collectAsState()
    val isOpening by viewModel.isOpening.collectAsState()
    val pinRequired by viewModel.pinRequired.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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

        if (isOpening) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text(Strings.OPENING_ACCOUNT, style = MaterialTheme.typography.bodyMedium)
            if (pinRequired) {
                Text(
                    Strings.OPENING_SLOW_NOTE,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else if (pinRequired) {
            SecretTextField(
                value = pin,
                onValueChange = { pin = it },
                label = Strings.ENTER_PIN,
                modifier = Modifier.fillMaxWidth(0.8f),
                onSubmit = { viewModel.unlock(account, pin.takeIf { it.isNotEmpty() }) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(0.8f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { viewModel.selectAccount(null) },
                enabled = !isOpening,
                modifier = Modifier.weight(1f)
            ) {
                Text(Strings.CANCEL)
            }
            Button(
                onClick = { viewModel.unlock(account, pin.takeIf { it.isNotEmpty() }) },
                enabled = !isOpening && pinRequired,
                modifier = Modifier.weight(1f)
            ) {
                Text(Strings.UNLOCK)
            }
        }
    }
}
