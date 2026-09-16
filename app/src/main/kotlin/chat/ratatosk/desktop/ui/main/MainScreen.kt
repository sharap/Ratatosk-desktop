package chat.ratatosk.desktop.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.model.ChatItem
import chat.ratatosk.desktop.model.NavState
import chat.ratatosk.desktop.model.Pane
import chat.ratatosk.desktop.model.Section
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.ui.chat.ChatScreen
import chat.ratatosk.desktop.ui.chatlist.ChatListScreen
import chat.ratatosk.desktop.ui.components.Avatar
import chat.ratatosk.desktop.ui.contacts.ContactDetailsScreen
import chat.ratatosk.desktop.ui.contacts.ContactsScreen
import chat.ratatosk.desktop.ui.groups.GroupDetailsScreen
import chat.ratatosk.desktop.ui.profile.ProfileScreen
import chat.ratatosk.desktop.ui.settings.SettingsScreen

/** Уже этого — одна панель за раз, с «назад». */
private val WIDE_MIN = 720.dp
private val LIST_WIDTH = 340.dp
/** Правая часть шире этого — настройкам и профилю хватает на две колонки. */
private val TWO_COLUMN_MIN = 900.dp

/**
 * Главный экран. Раскладка рисуется прямо по [NavState] модели навигации:
 * слева раздел (чаты или контакты), справа — верх стопки. Своего состояния
 * навигации у экрана нет (прежде его держали ещё навигатор панелей и пейджер
 * вкладок, и расхождение между тремя было видно человеку).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MainScreen(viewModel: RatatoskViewModel) {
    val nav by viewModel.navState.collectAsState()
    val isCompanionMode by viewModel.isCompanionMode.collectAsState()
    val focus = remember { FocusRequester() }

    // «Назад» — Esc, Alt+← и боковая кнопка мыши. Клавиши ловятся после
    // дочерних: поле ввода или диалог, которым они нужны, заберут их раньше.
    val backModifier = Modifier
        .focusRequester(focus)
        .focusable()
        .onKeyEvent { e ->
            val isBack = e.type == KeyEventType.KeyDown &&
                (e.key == Key.Escape || (e.key == Key.DirectionLeft && e.isAltPressed))
            isBack && viewModel.back()
        }
        .onPointerEvent(PointerEventType.Press) { if (it.buttons.isBackPressed) viewModel.back() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    BoxWithConstraints(Modifier.fillMaxSize().then(backModifier)) {
        val wide = maxWidth >= WIDE_MIN
        Row(Modifier.fillMaxSize()) {
            NavRail(viewModel, nav, isCompanionMode, compact = !wide)
            VerticalDivider()
            if (wide) {
                Box(Modifier.width(LIST_WIDTH).fillMaxHeight()) {
                    SectionList(viewModel, nav, isCompanionMode)
                }
                VerticalDivider()
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                    val top = nav.top
                    if (top == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(Strings.NO_CHAT_SELECTED, color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        PaneContent(viewModel, top, isCompanionMode, showBack = nav.stack.size > 1, twoColumn = maxWidth >= TWO_COLUMN_MIN)
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val top = nav.top
                    if (top == null) SectionList(viewModel, nav, isCompanionMode)
                    else PaneContent(viewModel, top, isCompanionMode, showBack = true, twoColumn = false)
                }
            }
        }
    }
}

@Composable
private fun SectionList(viewModel: RatatoskViewModel, nav: NavState, isCompanionMode: Boolean) {
    // Что выделено в списке — то, что сейчас показано справа, чем бы оно ни было.
    val selected = when (val top = nav.top) {
        is Pane.Chat -> top.chatId
        is Pane.Contact -> top.chatId
        is Pane.GroupInfo -> top.chatId
        else -> null
    }
    when (nav.section) {
        Section.CHATS -> ChatListScreen(
            viewModel = viewModel,
            onChatClick = { viewModel.openChat(it) },
            onOpenCard = { item ->
                when (item) {
                    is ChatItem.GroupChat -> viewModel.openGroupInfo(item.chatId)
                    is ChatItem.Direct -> viewModel.openContact(item.chatId, fromChat = false)
                }
            },
            selectedChatId = selected,
        )
        // У компаньона карточки контакта нет (ключей и сверки на втором экране
        // не бывает), поэтому строка сразу открывает чат, а завести контакт
        // может только телефон.
        Section.CONTACTS -> ContactsScreen(
            viewModel = viewModel,
            onContactClick = { if (isCompanionMode) viewModel.openChat(it) else viewModel.openContact(it, fromChat = false) },
            onChatClick = { viewModel.openChat(it) },
            selectedChatId = selected,
            showFab = !isCompanionMode,
        )
    }
}

@Composable
private fun PaneContent(viewModel: RatatoskViewModel, pane: Pane, isCompanionMode: Boolean, showBack: Boolean, twoColumn: Boolean) {
    // Ключ по содержимому: черновик, ответ и прокрутка одного чата не
    // перетекают в другой (ревью Android 5.1).
    key(pane) {
        when (pane) {
            is Pane.Chat -> ChatScreen(
                viewModel = viewModel,
                chatId = pane.chatId,
                onBack = { viewModel.back() },
                onHeaderClick = {
                    when {
                        viewModel.getGroup(pane.chatId) != null -> viewModel.openGroupInfo(pane.chatId)
                        // У компаньона карточки контакта нет: ключей и сверки у второго экрана не бывает.
                        !isCompanionMode -> viewModel.openContact(pane.chatId, fromChat = true)
                    }
                },
                showBackButton = showBack,
                isCompact = !twoColumn,
            )
            is Pane.Contact -> ContactDetailsScreen(
                viewModel = viewModel,
                chatId = pane.chatId,
                onBack = { viewModel.back() },
                onChatClick = { viewModel.openChat(it) },
                showBackButton = showBack,
                isCompact = !twoColumn,
            )
            is Pane.GroupInfo -> GroupDetailsScreen(
                viewModel = viewModel,
                chatId = pane.chatId,
                onBack = { viewModel.back() },
                onOpenChat = { viewModel.openChat(pane.chatId) },
                showBackButton = showBack,
            )
            Pane.Settings -> SettingsScreen(viewModel = viewModel, isTwoColumn = twoColumn)
            Pane.Profile -> ProfileScreen(viewModel = viewModel, isTwoColumn = twoColumn)
        }
    }
}

@Composable
private fun NavRail(viewModel: RatatoskViewModel, nav: NavState, isCompanionMode: Boolean, compact: Boolean) {
    val myAvatar by viewModel.myAvatar.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val totalUnread by viewModel.totalUnreadCount.collectAsState()
    val top = nav.top
    val paneOverList = top == Pane.Settings || top == Pane.Profile

    // Раздел выбран, когда его список виден: в узком окне — только если справа пусто.
    fun sectionSelected(section: Section) = nav.section == section && !paneOverList && (!compact || top == null)

    fun goToSection(section: Section) {
        viewModel.selectSection(section)
        if (compact || paneOverList) while (viewModel.back()) Unit
    }

    NavigationRail(modifier = Modifier.width(72.dp)) {
        Spacer(Modifier.height(8.dp))
        if (!isCompanionMode) {
            val selected = top == Pane.Profile
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { viewModel.openProfile() },
                contentAlignment = Alignment.Center
            ) {
                Avatar(avatarBytes = myAvatar, name = userName ?: Strings.PROFILE, size = 40.dp)
                if (selected) Box(Modifier.fillMaxSize().border(2.dp, MaterialTheme.colorScheme.primary, CircleShape))
            }
            Spacer(Modifier.height(12.dp))
        }

        NavigationRailItem(
            selected = sectionSelected(Section.CHATS),
            onClick = { goToSection(Section.CHATS) },
            icon = {
                BadgedBox(badge = { if (totalUnread > 0) Badge { Text(totalUnread.toString()) } }) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                }
            },
            label = { Text(Strings.CHATS) }
        )
        NavigationRailItem(
            selected = sectionSelected(Section.CONTACTS),
            onClick = { goToSection(Section.CONTACTS) },
            icon = { Icon(Icons.Default.AccountBox, contentDescription = null) },
            label = { Text(Strings.CONTACTS) }
        )
        Spacer(Modifier.weight(1f))
        NavigationRailItem(
            selected = top == Pane.Settings,
            onClick = { viewModel.openSettings() },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text(Strings.SETTINGS) }
        )
        Spacer(Modifier.height(16.dp))
    }
}
