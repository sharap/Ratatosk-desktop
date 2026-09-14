package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.backend.AppEvent
import chat.ratatosk.desktop.backend.Group
import chat.ratatosk.desktop.backend.GroupMember
import chat.ratatosk.desktop.util.toHexString
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.ratatosk.core.evictionNotice
import org.ratatosk.core.groupJoinNotice
import org.ratatosk.core.leaveNotice
import org.ratatosk.core.maxGroupTitleChars
import org.ratatosk.core.ownerLeaveNotice
import java.util.concurrent.ConcurrentHashMap

/**
 * Тексты, которые окно **обязано** показать до действия (§11.4, §11.5).
 * Константы биндингов, а не сведения о телефоне: проводом не едут.
 */
class GroupNotices(
    val join: String,
    val eviction: String,
    val leave: String,
    val ownerLeave: String,
    val maxTitleChars: Int,
)

interface GroupsApi {
    val groups: StateFlow<List<Group>>
    /** Состав по `chatId` группы в hex; появляется после [loadMembers]. */
    val groupMembers: StateFlow<Map<String, List<GroupMember>>>
    val groupAvatars: StateFlow<Map<String, ByteArray>>
    /** Только что заведённая нами группа — чтобы экран мог её открыть. */
    val createdGroups: SharedFlow<ByteArray>
    val groupNotices: GroupNotices

    fun getGroup(chatId: ByteArray): Group?
    /** Можно ли распоряжаться группой: исключать, переименовывать, менять картинку. */
    fun canManageGroup(chatId: ByteArray): Boolean
    fun loadMembers(chatId: ByteArray)
    fun getGroupAvatar(chatId: ByteArray): ByteArray?

    fun createGroup(title: String)
    fun renameGroup(chatId: ByteArray, title: String)
    fun inviteToGroup(chatId: ByteArray, memberChatId: ByteArray)
    fun evictFromGroup(chatId: ByteArray, memberChatId: ByteArray)
    fun leaveGroup(chatId: ByteArray)
    fun setGroupAvatar(chatId: ByteArray, bytes: ByteArray?)
}

/** Группы (§11): список, состав, права и распоряжение. */
class GroupsModel(session: SessionContext) : FeatureModel(session), GroupsApi {
    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    override val groups = _groups.asStateFlow()

    private val _groupMembers = MutableStateFlow<Map<String, List<GroupMember>>>(emptyMap())
    override val groupMembers = _groupMembers.asStateFlow()

    private val _groupAvatars = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    override val groupAvatars = _groupAvatars.asStateFlow()
    private val avatarRequests = ConcurrentHashMap.newKeySet<String>()

    private val _createdGroups = MutableSharedFlow<ByteArray>(extraBufferCapacity = 4)
    override val createdGroups = _createdGroups.asSharedFlow()

    override val groupNotices: GroupNotices by lazy {
        GroupNotices(
            join = runCatching { groupJoinNotice() }.getOrDefault(""),
            eviction = runCatching { evictionNotice() }.getOrDefault(""),
            leave = runCatching { leaveNotice() }.getOrDefault(""),
            ownerLeave = runCatching { ownerLeaveNotice() }.getOrDefault(""),
            maxTitleChars = runCatching { maxGroupTitleChars().toInt() }.getOrDefault(64),
        )
    }

    override fun getGroup(chatId: ByteArray): Group? = _groups.value.find { it.chatId.contentEquals(chatId) }

    override fun canManageGroup(chatId: ByteArray): Boolean {
        val group = getGroup(chatId) ?: return false
        if (!group.joined) return false
        // Клиент знает права сразу; компаньон — из состава (`mine && owner`).
        return group.canManage
            ?: (_groupMembers.value[chatId.toHexString()]?.any { it.isMe && it.isOwner == true } == true)
    }

    override fun loadMembers(chatId: ByteArray) {
        session.io { it.requestMembers(chatId) }
    }

    override fun getGroupAvatar(chatId: ByteArray): ByteArray? {
        val hex = chatId.toHexString()
        _groupAvatars.value[hex]?.let { return it }
        val group = getGroup(chatId) ?: return null
        if (group.avatarMs > 0UL && avatarRequests.add(hex)) {
            session.io { it.requestAvatar(chatId) }
        }
        return null
    }

    override fun createGroup(title: String) {
        session.io("Failed to create group") { it.createGroup(title.trim()) }
    }

    override fun renameGroup(chatId: ByteArray, title: String) {
        session.io("Failed to rename group") { it.renameGroup(chatId, title.trim()) }
    }

    override fun inviteToGroup(chatId: ByteArray, memberChatId: ByteArray) {
        session.io("Failed to invite") { backend ->
            backend.inviteToGroup(chatId, memberChatId)
            backend.requestMembers(chatId)
        }
    }

    override fun evictFromGroup(chatId: ByteArray, memberChatId: ByteArray) {
        session.io("Failed to remove member") { backend ->
            backend.evictFromGroup(chatId, memberChatId)
            backend.requestMembers(chatId)
        }
    }

    override fun leaveGroup(chatId: ByteArray) {
        session.io("Failed to leave group") { backend ->
            backend.leaveGroup(chatId)
            backend.requestChats()
        }
    }

    override fun setGroupAvatar(chatId: ByteArray, bytes: ByteArray?) {
        session.io("Failed to set group picture") { it.setGroupAvatar(chatId, bytes) }
    }

    override fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.ChatsLoaded -> _groups.value = event.groups
            is AppEvent.MembersLoaded -> {
                _groupMembers.update { it + (event.chatId.toHexString() to event.members) }
            }
            is AppEvent.GroupChanged -> {
                // Состав перечитываем, только если его уже смотрели.
                if (_groupMembers.value.containsKey(event.chatId.toHexString())) loadMembers(event.chatId)
            }
            is AppEvent.GroupCreated -> _createdGroups.tryEmit(event.chatId)
            is AppEvent.AvatarLoaded -> {
                val chatId = event.chatId ?: return
                if (getGroup(chatId) == null) return
                val hex = chatId.toHexString()
                val bytes = event.bytes
                _groupAvatars.update { if (bytes != null) it + (hex to bytes) else it - hex }
            }
            is AppEvent.AvatarChanged -> {
                val chatId = event.chatId ?: return
                if (getGroup(chatId) == null) return
                avatarRequests.remove(chatId.toHexString())
                session.io { it.requestAvatar(chatId) }
            }
            else -> {}
        }
    }

    override fun reset() {
        _groups.value = emptyList()
        _groupMembers.value = emptyMap()
        _groupAvatars.value = emptyMap()
        avatarRequests.clear()
    }
}
