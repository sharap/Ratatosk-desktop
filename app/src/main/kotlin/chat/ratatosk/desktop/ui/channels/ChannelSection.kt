package chat.ratatosk.desktop.ui.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.backend.Channel
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.ui.components.Avatar
import chat.ratatosk.desktop.util.toHexString
import org.ratatosk.core.FfiSeeding
import org.ratatosk.core.FfiSharingLevel
import java.text.DateFormat
import java.util.Date

/**
 * Канальная часть карточки: порода, раздача, заявки, впущенные, выдачи.
 *
 * Порода — только из подписанного представления: пока его нет, сказано
 * «неизвестна», а не обещание ссылки (§10.2). Заявки, впущенных и выдачи
 * ядро держит **у владельца** — у читателя они пусты, и это свойство,
 * а не пропуск (§3.2). Раздача же — дело каждого читателя: канал на том
 * и держится, что читатели раздают друг другу (§7.5).
 */
@Composable
fun ChannelSection(
    viewModel: RatatoskViewModel,
    chatId: ByteArray,
    channel: Channel,
) {
    val hex = remember(chatId) { chatId.toHexString() }
    val requests by viewModel.channelRequests.collectAsState()
    val admits by viewModel.channelAdmits.collectAsState()
    val grants by viewModel.channelGrants.collectAsState()
    val seeding by viewModel.seedingMode.collectAsState()
    val seeds by viewModel.channelSeeds.collectAsState()
    val sharing by viewModel.sharingLevel.collectAsState()

    var editingRight by remember { mutableStateOf<Pair<ByteArray, String>?>(null) }
    var showRotate by remember { mutableStateOf(false) }
    var showPow by remember { mutableStateOf(false) }
    var showLink by remember { mutableStateOf(false) }
    var showAdmitContact by remember { mutableStateOf(false) }
    var confirmAnnounce by remember { mutableStateOf(false) }
    var confirmNarrow by remember { mutableStateOf<FfiSharingLevel?>(null) }

    LaunchedEffect(hex) { viewModel.loadChannel(chatId) }

    Column(Modifier.fillMaxWidth()) {
        Text(Strings.CHANNEL_KIND, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text = when (channel.open) {
                true -> Strings.CHANNEL_KIND_OPEN
                false -> Strings.CHANNEL_KIND_PRIVATE
                null -> Strings.CHANNEL_KIND_UNKNOWN
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showLink = true }) {
                Icon(Icons.Default.Link, null)
                Spacer(Modifier.width(8.dp))
                Text(Strings.CHANNEL_LINK_SHOW)
            }
            // Впускать может и делегат с правом «впускать»: такой блок едет
            // адресату и ничьего состава не требует. В открытом канале
            // впускать некого — ключ и так в ссылке.
            if (channel.canAdmit && channel.open != true) {
                OutlinedButton(onClick = { showAdmitContact = true }) {
                    Icon(Icons.Default.PersonAdd, null)
                    Spacer(Modifier.width(8.dp))
                    Text(Strings.CHANNEL_ADMIT_CONTACT)
                }
            }
            // Кнопка поворота — по `may_rotate`: там уже учтены порода,
            // право и нижний предел в неделю (§6.4).
            if (channel.mayRotate) {
                OutlinedButton(onClick = { showRotate = true }) { Text(Strings.CHANNEL_ROTATE) }
            }
        }

        // Своё право со сроком (§6.3): отказ по сроку не должен наступать
        // внезапно, и предупредить его больше нечем.
        if (!channel.mine && channel.rightsUntilMs > 0UL) {
            Spacer(Modifier.height(8.dp))
            Text(
                Strings.CHANNEL_MY_RIGHT_UNTIL.format(
                    DateFormat.getDateInstance().format(Date(channel.rightsUntilMs.toLong()))
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        // «От владельца ничего не приходило» — про наш приём, а не про то,
        // где владелец: каталога пиров в ядре нет.
        if (channel.ownerUnseen) {
            Text(
                Strings.CHANNEL_OWNER_QUIET,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        // --- Раздача: дело каждого читателя ------------------------------
        Spacer(Modifier.height(20.dp))
        Text(Strings.CHANNEL_SEEDING, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val mode = seeding[hex] ?: FfiSeeding.QUIET
        SeedingOption(
            selected = mode == FfiSeeding.QUIET,
            title = Strings.CHANNEL_SEEDING_QUIET,
            desc = Strings.CHANNEL_SEEDING_QUIET_DESC,
            onClick = { viewModel.setSeeding(chatId, FfiSeeding.QUIET) },
        )
        SeedingOption(
            selected = mode == FfiSeeding.ANNOUNCED,
            title = Strings.CHANNEL_SEEDING_ANNOUNCED,
            desc = Strings.CHANNEL_SEEDING_ANNOUNCED_DESC,
            onClick = { if (mode != FfiSeeding.ANNOUNCED) confirmAnnounce = true },
        )
        SeedingOption(
            selected = mode == FfiSeeding.OFF,
            title = Strings.CHANNEL_SEEDING_OFF,
            desc = Strings.CHANNEL_SEEDING_OFF_DESC,
            onClick = { viewModel.setSeeding(chatId, FfiSeeding.OFF) },
        )

        if (mode != FfiSeeding.OFF) {
            Spacer(Modifier.height(12.dp))
            Text(Strings.CHANNEL_SHARING_LEVEL, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val level = sharing[hex] ?: FfiSharingLevel.EVERYONE
            SeedingOption(
                selected = level == FfiSharingLevel.EVERYONE,
                title = Strings.CHANNEL_SHARING_EVERYONE,
                desc = null,
                onClick = { viewModel.setSharingLevel(chatId, FfiSharingLevel.EVERYONE) },
            )
            SeedingOption(
                selected = level == FfiSharingLevel.CONTACTS,
                title = Strings.CHANNEL_SHARING_CONTACTS,
                desc = null,
                onClick = { if (level != FfiSharingLevel.CONTACTS) confirmNarrow = FfiSharingLevel.CONTACTS },
            )
            SeedingOption(
                selected = level == FfiSharingLevel.VERIFIED,
                title = Strings.CHANNEL_SHARING_VERIFIED,
                desc = null,
                onClick = { if (level != FfiSharingLevel.VERIFIED) confirmNarrow = FfiSharingLevel.VERIFIED },
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(Strings.CHANNEL_SEEDS, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val others = seeds[hex].orEmpty()
        if (others.isEmpty()) {
            Text(Strings.CHANNEL_SEEDS_NONE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        } else {
            others.forEach { seed ->
                // «Раздаёт» с истекающим сроком означает «раздавал», и
                // разница видна только по сроку.
                Text(
                    text = seed.peerIk.toHexString().take(16) + " — " +
                        Strings.CHANNEL_SEED_UNTIL.format(
                            DateFormat.getDateInstance().format(Date(seed.validUntilMs.toLong()))
                        ) +
                        if (!seed.verified) ", " + Strings.CHANNEL_SEED_UNVERIFIED else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (!channel.mine) return@Column

        // --- Владельцу: цена слова, заявки, впущенные, выдачи -------------
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Strings.CHANNEL_POW_TITLE, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = if (channel.powBits > 0u) {
                        Strings.CHANNEL_POW_CURRENT.format(channel.powBits.toInt())
                    } else {
                        Strings.CHANNEL_POW_FREE
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = { showPow = true }) { Text(Strings.EDIT) }
        }

        if (channel.open != true) {
            Spacer(Modifier.height(20.dp))
            Text(Strings.CHANNEL_REQUESTS, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(Strings.CHANNEL_NO_REFUSAL, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            val waiting = requests[hex].orEmpty()
            if (waiting.isEmpty()) {
                Text(Strings.CHANNEL_REQUESTS_NONE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            } else {
                waiting.forEach { request ->
                    ListItem(
                        headlineContent = { Text(request.name) },
                        supportingContent = { Text(request.peerIk.toHexString().take(16)) },
                        leadingContent = { Avatar(avatarBytes = null, name = request.name) },
                        // Одна кнопка, а не пара: отказа как ответа §10.4
                        // не знает, и вторая обещала бы ответ, которого
                        // просящий не получит.
                        trailingContent = {
                            Button(onClick = { viewModel.admitToChannel(chatId, request.peerIk) }) {
                                Text(Strings.CHANNEL_ADMIT)
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(Strings.CHANNEL_ADMITTED, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val letIn = admits[hex].orEmpty()
            if (letIn.isEmpty()) {
                Text(Strings.CHANNEL_ADMITTED_NONE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            } else {
                letIn.forEach { admit ->
                    ListItem(
                        headlineContent = { Text(admit.name) },
                        supportingContent = { Text(Strings.CHANNEL_ADMITTED_BY.format(admit.admittedByName)) },
                        leadingContent = { Avatar(avatarBytes = null, name = admit.name) },
                        trailingContent = {
                            TextButton(onClick = { editingRight = admit.peerIk to admit.name }) {
                                Text(Strings.CHANNEL_GRANT_EDIT)
                            }
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(Strings.CHANNEL_GRANTS, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (channel.grantsExpiring > 0u) {
            Text(
                Strings.CHANNEL_GRANTS_EXPIRING.format(channel.grantsExpiring.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        val given = grants[hex].orEmpty()
        if (given.isEmpty()) {
            Text(Strings.CHANNEL_GRANTS_NONE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        } else {
            given.forEach { grant ->
                val what = listOfNotNull(
                    Strings.CHANNEL_RIGHT_WRITE.takeIf { grant.write },
                    Strings.CHANNEL_RIGHT_ADMIT.takeIf { grant.admit },
                    Strings.CHANNEL_RIGHT_EVICT.takeIf { grant.evict },
                    Strings.CHANNEL_RIGHT_EDIT.takeIf { grant.edit },
                ).joinToString(", ").ifEmpty { Strings.CHANNEL_RIGHT_NONE }
                ListItem(
                    headlineContent = { Text(grant.name) },
                    supportingContent = {
                        Text(
                            what + ", " + if (grant.live) {
                                Strings.CHANNEL_RIGHT_UNTIL.format(
                                    DateFormat.getDateInstance().format(Date(grant.untilMs.toLong()))
                                )
                            } else {
                                Strings.CHANNEL_RIGHT_EXPIRED
                            }
                        )
                    },
                    leadingContent = { Avatar(avatarBytes = null, name = grant.name) },
                    trailingContent = {
                        TextButton(onClick = { editingRight = grant.peerIk to grant.name }) {
                            Text(Strings.CHANNEL_GRANT_EDIT)
                        }
                    },
                )
            }
        }
    }

    if (showLink) {
        ChannelLinkDialog(viewModel, chatId, channel.open) { showLink = false }
    }

    if (showAdmitContact) {
        AdmitContactDialog(viewModel, chatId, admits[hex].orEmpty()) { showAdmitContact = false }
    }

    if (showRotate) {
        ChannelNoticeDialog(
            title = Strings.CHANNEL_ROTATE,
            notice = viewModel.channelNotices.keyRotation,
            confirmLabel = Strings.CHANNEL_ROTATE,
            onConfirm = { viewModel.rotateChannelKey(chatId) },
            onDismiss = { showRotate = false },
        )
    }

    if (showPow) {
        ChannelPowDialog(
            current = channel.powBits,
            onConfirm = { viewModel.setChannelPow(chatId, it) },
            onDismiss = { showPow = false },
        )
    }

    if (confirmAnnounce) {
        ChannelNoticeDialog(
            title = Strings.CHANNEL_SEEDING_ANNOUNCED,
            notice = viewModel.channelNotices.seeding,
            confirmLabel = Strings.CHANNEL_SEEDING_ANNOUNCED,
            onConfirm = { viewModel.setSeeding(chatId, FfiSeeding.ANNOUNCED) },
            onDismiss = { confirmAnnounce = false },
        )
    }

    confirmNarrow?.let { level ->
        ChannelNoticeDialog(
            title = Strings.CHANNEL_SHARING_LEVEL,
            notice = viewModel.channelNotices.sharingLevel,
            confirmLabel = Strings.SAVE,
            onConfirm = { viewModel.setSharingLevel(chatId, level) },
            onDismiss = { confirmNarrow = null },
        )
    }

    editingRight?.let { (who, name) ->
        val grant = grants[hex]?.firstOrNull { it.peerIk.contentEquals(who) }
        ChannelRightDialog(
            viewModel = viewModel,
            chatId = chatId,
            peerIk = who,
            name = name,
            currentWrite = grant?.write == true,
            currentAdmit = grant?.admit == true,
            currentEvict = grant?.evict == true,
            currentEdit = grant?.edit == true,
            onDismiss = { editingRight = null },
        )
    }
}

/** Строка выбора: кружок, название и, если есть, что это значит. */
@Composable
private fun SeedingOption(selected: Boolean, title: String, desc: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.padding(top = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (desc != null) {
                Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
