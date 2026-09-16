package chat.ratatosk.desktop.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.util.toHexString

/**
 * Выбор получателя: группы, в которых мы состоим, и контакты. Один диалог
 * и для пересылки сообщений, и для карточки контакта — раньше карточку можно
 * было отправить только человеку, но не в группу.
 *
 * @param notice текст ядра о последствиях (пересылка), если он есть.
 * @param excludeChatId себя в список не показывать.
 */
@Composable
fun PickChatDialog(
    viewModel: RatatoskViewModel,
    title: String,
    notice: String = "",
    excludeChatId: ByteArray? = null,
    onDismiss: () -> Unit,
    onPick: (ByteArray) -> Unit,
) {
    val contacts by viewModel.contacts.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val avatars by viewModel.contactAvatars.collectAsState()
    val groupAvatars by viewModel.groupAvatars.collectAsState()

    fun excluded(chatId: ByteArray) = excludeChatId?.contentEquals(chatId) == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (notice.isNotBlank()) {
                    Text(notice, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    val joined = groups.filter { it.joined && !excluded(it.chatId) }
                    if (joined.isNotEmpty()) {
                        item {
                            Text(Strings.FORWARD_GROUPS, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        items(joined, key = { "g" + it.chatId.toHexString() }) { group ->
                            val avatar = groupAvatars[group.chatId.toHexString()] ?: viewModel.getGroupAvatar(group.chatId)
                            ListItem(
                                headlineContent = { Text(group.title) },
                                leadingContent = {
                                    if (avatar != null) Avatar(avatarBytes = avatar, name = group.title)
                                    else Icon(Icons.Default.Groups, null)
                                },
                                modifier = Modifier.clickable { onPick(group.chatId) },
                            )
                        }
                    }
                    val people = contacts.filter { !excluded(it.chatId) }
                    if (people.isNotEmpty()) {
                        item {
                            Text(Strings.FORWARD_CONTACTS, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        items(people, key = { "c" + it.chatId.toHexString() }) { contact ->
                            val name = contact.localName ?: contact.displayName
                            ListItem(
                                headlineContent = { Text(name) },
                                leadingContent = {
                                    Avatar(avatarBytes = avatars[contact.peerIk.toHexString()] ?: viewModel.getAvatarOf(contact.peerIk), name = name)
                                },
                                modifier = Modifier.clickable { onPick(contact.chatId) },
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
