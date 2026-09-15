package chat.ratatosk.desktop.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import org.ratatosk.core.FfiPairedDevice
import qrcode.QRCode
import java.text.DateFormat
import java.util.Date

/**
 * Сопряжённые устройства — сторона полного клиента (§13.4, FFI.md
 * «Компаньон: сопряжение десктопа»).
 */
@Composable
fun PairedDevicesSection(viewModel: RatatoskViewModel) {
    val devices by viewModel.pairedDevices.collectAsState()
    val pending by viewModel.pendingPairing.collectAsState()

    var askLabel by remember { mutableStateOf(false) }
    var revoking by remember { mutableStateOf<FfiPairedDevice?>(null) }

    LaunchedEffect(Unit) { viewModel.refreshDevices() }

    Column {
        Text(Strings.PAIRED_DEVICES, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(Strings.PAIRED_DEVICES_DESC, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        if (devices.isEmpty()) {
            Text(Strings.PAIRED_NONE, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        }
        devices.forEach { device ->
            ListItem(
                leadingContent = { Icon(Icons.Default.Devices, null) },
                headlineContent = { Text(device.label) },
                supportingContent = { Text(deviceStatus(device), style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    TextButton(onClick = { revoking = device }) {
                        Text(Strings.PAIR_REVOKE, color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { askLabel = true }, enabled = pending == null) { Text(Strings.PAIR_DEVICE) }
    }

    if (askLabel) {
        var label by remember { mutableStateOf(Strings.PAIR_LABEL_DEFAULT) }
        AlertDialog(
            onDismissRequest = { askLabel = false },
            title = { Text(Strings.PAIR_DEVICE) },
            text = {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(Strings.PAIR_LABEL) }, singleLine = true)
            },
            confirmButton = {
                Button(enabled = label.isNotBlank(), onClick = {
                    viewModel.startPairing(label)
                    askLabel = false
                }) { Text(Strings.CONTINUE) }
            },
            dismissButton = { TextButton(onClick = { askLabel = false }) { Text(Strings.CANCEL) } }
        )
    }

    pending?.let { PairingDialog(viewModel, it) }

    revoking?.let { device ->
        AlertDialog(
            onDismissRequest = { revoking = null },
            title = { Text(Strings.PAIR_REVOKE_TITLE.format(device.label)) },
            text = { Text(Strings.PAIR_REVOKE_TEXT) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.revokeDevice(device.deviceId)
                    revoking = null
                }) { Text(Strings.PAIR_REVOKE, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { Button(onClick = { revoking = null }) { Text(Strings.CANCEL) } }
        )
    }
}

@Composable
private fun PairingDialog(viewModel: RatatoskViewModel, pending: chat.ratatosk.desktop.model.PendingPairing) {
    var confirmClose by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val uri = pending.uri

    val close = {
        // Подключилось или ссылки ещё нет — закрываем без вопросов;
        // иначе ссылка пропадёт навсегда, и об этом надо спросить.
        when {
            pending.connected -> viewModel.finishPairing(revoke = false)
            uri == null -> viewModel.finishPairing(revoke = true)
            else -> confirmClose = true
        }
    }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
        title = { Text(Strings.PAIR_DEVICE + ": " + pending.label) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    pending.connected -> {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                        Text(Strings.PAIR_CONNECTED.format(pending.label))
                    }
                    uri == null -> {
                        CircularProgressIndicator()
                        Text(Strings.PAIR_WAITING)
                    }
                    else -> {
                        val qr = remember(uri) {
                            (QRCode(uri).render().nativeImage() as java.awt.image.BufferedImage).toComposeImageBitmap()
                        }
                        Image(bitmap = qr, contentDescription = null, modifier = Modifier.size(240.dp))
                        Text(Strings.PAIR_SCAN, style = MaterialTheme.typography.bodySmall)
                        Text(Strings.PAIR_ONCE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = {
                            viewModel.copyPairingUri()
                            copied = true
                        }) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(Strings.PAIR_COPY)
                        }
                        if (copied) Text(Strings.PAIR_COPIED, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        },
        confirmButton = {
            if (pending.connected) {
                Button(onClick = close) { Text(Strings.BACKUP_DONE_BUTTON) }
            } else {
                TextButton(onClick = close) { Text(Strings.CLOSE) }
            }
        }
    )

    if (confirmClose && !pending.connected) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text(Strings.PAIR_CLOSE_TITLE) },
            text = { Text(Strings.PAIR_CLOSE_TEXT) },
            confirmButton = {
                TextButton(onClick = { confirmClose = false; viewModel.finishPairing(revoke = true) }) {
                    Text(Strings.PAIR_CANCEL, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClose = false; viewModel.finishPairing(revoke = false) }) {
                    Text(Strings.PAIR_KEEP)
                }
            }
        )
    }
}

private fun deviceStatus(device: FfiPairedDevice): String {
    val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    return buildList {
        add(if (device.connected) Strings.DEVICE_CONNECTED
            else if (device.lastSeenMs == 0UL) Strings.DEVICE_NEVER_SEEN
            else Strings.DEVICE_LAST_SEEN.format(fmt.format(Date(device.lastSeenMs.toLong()))))
        // «Только дома» — законное и самое частое состояние, а не поломка.
        add(if (device.reachableAnywhere) Strings.DEVICE_ANYWHERE else Strings.DEVICE_LOCAL_ONLY)
        if (device.cacheExpired) add(Strings.DEVICE_CACHE_EXPIRED)
        add(Strings.DEVICE_PAIRED_AT.format(fmt.format(Date(device.pairedMs.toLong()))))
    }.joinToString(" · ")
}
