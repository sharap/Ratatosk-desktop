package chat.ratatosk.desktop.ui.main

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.*
import androidx.compose.material3.adaptive.navigation.*
import androidx.compose.runtime.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.ui.chat.ChatScreen
import chat.ratatosk.desktop.ui.chatlist.ChatListScreen
import chat.ratatosk.desktop.ui.components.Avatar
import chat.ratatosk.desktop.ui.components.AddContactDialog
import chat.ratatosk.desktop.ui.contacts.ContactDetailsScreen
import chat.ratatosk.desktop.ui.contacts.ContactsScreen
import chat.ratatosk.desktop.ui.profile.ProfileScreen
import chat.ratatosk.desktop.ui.settings.SettingsScreen
import chat.ratatosk.desktop.util.toHexString
import org.ratatosk.core.FfiAccount
import kotlinx.coroutines.launch
import androidx.window.core.layout.WindowWidthSizeClass

enum class MainTab(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String) {
    CHATS(Icons.AutoMirrored.Filled.Chat, Strings.CHATS),
    CONTACTS(Icons.Default.AccountBox, Strings.CONTACTS),
    SETTINGS(Icons.Default.Settings, Strings.SETTINGS),
    PROFILE(Icons.Default.AccountCircle, Strings.PROFILE)
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MainScreen(
    viewModel: RatatoskViewModel,
    onChatClick: (ByteArray) -> Unit,
    onContactClick: (ByteArray) -> Unit
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val activeChatId by viewModel.activeChatIdFlow.collectAsState()
    val activeContactId by viewModel.activeContactIdFlow.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val contactAvatars by viewModel.contactAvatars.collectAsState()

    val selectedAccount by viewModel.selectedAccount.collectAsState()
    val totalUnreadCount by viewModel.totalUnreadCount.collectAsState()
    val isCompanionMode by viewModel.isCompanionMode.collectAsState()
    var showAddContactDialog by remember { mutableStateOf(false) }

    val visibleTabs = remember(isCompanionMode) {
        if (isCompanionMode) listOf(MainTab.CHATS, MainTab.SETTINGS)
        else MainTab.entries
    }

    val pagerState = rememberPagerState(pageCount = { visibleTabs.size })
    val scope = rememberCoroutineScope()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    
    val currentTab by remember(selectedTabIndex) { derivedStateOf { visibleTabs[selectedTabIndex] } }

    val isDetailOpen by remember(selectedTabIndex, activeChatId, activeContactId) {
        derivedStateOf {
            val tab = visibleTabs[selectedTabIndex]
            (tab == MainTab.CHATS && activeChatId != null) ||
            (tab == MainTab.CONTACTS && activeContactId != null)
        }
    }

    // Sync navigator with ViewModel state
    LaunchedEffect(activeChatId, activeContactId) {
        if (activeChatId != null) {
            val key = "chat_${activeChatId!!.toHexString()}"
            if (navigator.currentDestination?.content != key) {
                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key)
            }
        } else if (activeContactId != null) {
            val key = "contact_${activeContactId!!.toHexString()}"
            if (navigator.currentDestination?.content != key) {
                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key)
            }
        }
    }

    // Sync ViewModel with navigator state
    LaunchedEffect(navigator.currentDestination) {
        if (navigator.currentDestination?.pane == ListDetailPaneScaffoldRole.List) {
            if (activeChatId != null) viewModel.setActiveChat(null)
            if (activeContactId != null) viewModel.setActiveContact(null)
        }
    }

    val chatGridState = rememberLazyGridState()
    val contactsGridState = rememberLazyGridState()

    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isCompact = adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT

    val showBackButton = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Hidden

    if (isCompact) {
        // Mobile Layout (Compact)
        Scaffold(
            bottomBar = {
                if (!isDetailOpen) {
                    NavigationBar {
                        visibleTabs.forEach { tab ->
                            NavigationBarItem(
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (tab == MainTab.CHATS && totalUnreadCount > 0) {
                                                Badge {
                                                    Text(totalUnreadCount.toString())
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(tab.icon, contentDescription = null)
                                    }
                                },
                                label = { Text(tab.label) },
                                selected = currentTab == tab,
                                onClick = { 
                                    selectedTabIndex = visibleTabs.indexOf(tab)
                                    scope.launch { pagerState.animateScrollToPage(selectedTabIndex) }
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.padding(innerPadding).fillMaxSize()) {
                    MainTabContent(
                        pagerState = pagerState,
                        visibleTabs = visibleTabs,
                        viewModel = viewModel,
                        onChatClick = { chatId ->
                            viewModel.setActiveChat(chatId)
                            val key = "chat_${chatId.toHexString()}"
                            scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key) }
                        },
                        onContactClick = { contactId ->
                            viewModel.setActiveContact(contactId)
                            val key = "contact_${contactId.toHexString()}"
                            scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key) }
                        },
                        isCompact = true,
                        chatGridState = chatGridState,
                        contactsGridState = contactsGridState,
                        showFab = !isCompanionMode
                    )
                }

                if (isDetailOpen) {
                    DetailPaneContent(
                        navigator = navigator,
                        activeChatId = activeChatId,
                        activeContactId = activeContactId,
                        viewModel = viewModel,
                        isCompanionMode = isCompanionMode,
                        showBackButton = true,
                        isCompact = true
                    )
                }
            }
        }
    } else {
        // Tablet/Desktop Layout
        Row(Modifier.fillMaxSize()) {
            UnifiedNavigationRail(
                selectedAccount = selectedAccount,
                contacts = contacts,
                contactAvatars = contactAvatars,
                currentTab = currentTab,
                visibleTabs = visibleTabs,
                totalUnreadCount = totalUnreadCount,
                onAccountSelect = { account ->
                    viewModel.selectAccount(account)
                    viewModel.setActiveChat(null)
                    viewModel.setActiveContact(null)
                    scope.launch { 
                        navigator.navigateBack()
                        val profileIndex = visibleTabs.indexOf(MainTab.PROFILE)
                        if (profileIndex != -1) {
                            selectedTabIndex = profileIndex
                            pagerState.scrollToPage(profileIndex)
                        }
                    }
                },
                onAddContactClick = { showAddContactDialog = true },
                onChatSelect = { chatId ->
                    viewModel.setActiveChat(chatId)
                    val key = "chat_${chatId.toHexString()}"
                    scope.launch { 
                        val chatsIndex = visibleTabs.indexOf(MainTab.CHATS)
                        if (chatsIndex != -1) {
                            selectedTabIndex = chatsIndex
                            if (pagerState.currentPage != chatsIndex) {
                                pagerState.scrollToPage(chatsIndex)
                            }
                        }
                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key)
                    }
                },
                onTabSelect = { tab ->
                    val tabIndex = visibleTabs.indexOf(tab)
                    if (tabIndex != -1) {
                        selectedTabIndex = tabIndex
                        scope.launch { 
                            if (tab != MainTab.CHATS && tab != MainTab.CONTACTS) {
                                viewModel.setActiveChat(null)
                                viewModel.setActiveContact(null)
                                navigator.navigateBack()
                            }
                            pagerState.animateScrollToPage(tabIndex)
                        }
                    }
                }
            )

            if (!isDetailOpen || (currentTab == MainTab.SETTINGS || currentTab == MainTab.PROFILE)) {
                Box(Modifier.weight(1f)) {
                    MainTabContent(
                        pagerState = pagerState,
                        visibleTabs = visibleTabs,
                        viewModel = viewModel,
                        onChatClick = { chatId ->
                            viewModel.setActiveChat(chatId)
                            val key = "chat_${chatId.toHexString()}"
                            scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key) }
                        },
                        onContactClick = { contactId ->
                            viewModel.setActiveContact(contactId)
                            val key = "contact_${contactId.toHexString()}"
                            scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key) }
                        },
                        isCompact = false,
                        isTwoColumn = true,
                        chatGridState = chatGridState,
                        contactsGridState = contactsGridState,
                        showFab = true
                    )
                }
            } else {
                ListDetailPaneScaffold(
                    directive = navigator.scaffoldDirective,
                    value = navigator.scaffoldValue,
                    listPane = {
                        Box {
                            MainTabContent(
                                pagerState = pagerState,
                                visibleTabs = visibleTabs,
                                viewModel = viewModel,
                                onChatClick = { chatId ->
                                    viewModel.setActiveChat(chatId)
                                    val key = "chat_${chatId.toHexString()}"
                                    scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key) }
                                },
                                onContactClick = { contactId ->
                                    viewModel.setActiveContact(contactId)
                                    val key = "contact_${contactId.toHexString()}"
                                    scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key) }
                                },
                                isCompact = false,
                                chatGridState = chatGridState,
                                contactsGridState = contactsGridState,
                                showFab = false
                            )
                        }
                    },
                    detailPane = {
                        Box {
                            DetailPaneContent(
                                navigator = navigator,
                                activeChatId = activeChatId,
                                activeContactId = activeContactId,
                                viewModel = viewModel,
                                isCompanionMode = isCompanionMode,
                                showBackButton = showBackButton,
                                isCompact = false
                            )
                        }
                    }
                )
            }
        }
    }

    if (showAddContactDialog) {
        AddContactDialog(
            onDismiss = { showAddContactDialog = false },
            onAdd = { uri, inPerson ->
                viewModel.addContact(uri, inPerson)
                showAddContactDialog = false
            }
        )
    }
}

@Composable
fun MainTabContent(
    pagerState: androidx.compose.foundation.pager.PagerState,
    visibleTabs: List<MainTab>,
    viewModel: RatatoskViewModel,
    onChatClick: (ByteArray) -> Unit,
    onContactClick: (ByteArray) -> Unit,
    isCompact: Boolean,
    isTwoColumn: Boolean = false,
    chatGridState: androidx.compose.foundation.lazy.grid.LazyGridState = rememberLazyGridState(),
    contactsGridState: androidx.compose.foundation.lazy.grid.LazyGridState = rememberLazyGridState(),
    showFab: Boolean = true
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = isCompact
    ) { page ->
        when (visibleTabs[page]) {
            MainTab.CHATS -> ChatListScreen(
                viewModel = viewModel,
                onChatClick = onChatClick,
                isTwoColumn = isTwoColumn,
                gridState = chatGridState,
                showFab = showFab
            )
            MainTab.CONTACTS -> ContactsScreen(
                viewModel = viewModel,
                onContactClick = onContactClick,
                isTwoColumn = isTwoColumn,
                gridState = contactsGridState,
                showFab = showFab
            )
            MainTab.SETTINGS -> SettingsScreen(
                viewModel = viewModel,
                isTwoColumn = isTwoColumn
            )
            MainTab.PROFILE -> ProfileScreen(
                viewModel = viewModel,
                isTwoColumn = isTwoColumn
            )
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun DetailPaneContent(
    navigator: ThreePaneScaffoldNavigator<String>,
    activeChatId: ByteArray?,
    activeContactId: ByteArray?,
    viewModel: RatatoskViewModel,
    isCompanionMode: Boolean,
    showBackButton: Boolean,
    isCompact: Boolean
) {
    val scope = rememberCoroutineScope()
    val contentKey = navigator.currentDestination?.content
    if (contentKey != null) {
        if (contentKey.startsWith("chat_")) {
            val effectiveChatId = activeChatId ?: remember(contentKey) {
                try { contentKey.removePrefix("chat_").hexToByteArray() } catch (e: Exception) { null }
            }
            if (effectiveChatId != null) {
                ChatScreen(
                    viewModel = viewModel,
                    chatId = effectiveChatId,
                    onBack = {
                        viewModel.setActiveChat(null)
                        scope.launch { navigator.navigateBack() }
                    },
                    onHeaderClick = { 
                        if (!isCompanionMode) viewModel.setActiveContact(effectiveChatId) 
                    },
                    showBackButton = showBackButton,
                    isCompact = isCompact
                )
            }
        } else if (contentKey.startsWith("contact_")) {
            val effectiveContactId = activeContactId ?: remember(contentKey) {
                try { contentKey.removePrefix("contact_").hexToByteArray() } catch (e: Exception) { null }
            }
            if (effectiveContactId != null) {
                ContactDetailsScreen(
                    viewModel = viewModel,
                    chatId = effectiveContactId,
                    onBack = {
                        viewModel.setActiveContact(null)
                        scope.launch { navigator.navigateBack() }
                    },
                    onChatClick = {
                        viewModel.setActiveContact(null)
                        viewModel.setActiveChat(it)
                    },
                    showBackButton = showBackButton,
                    isCompact = isCompact
                )
            }
        }
    } else {
        // Empty state
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(Strings.NO_CHATS)
        }
    }
}

@Composable
fun UnifiedNavigationRail(
    selectedAccount: FfiAccount?,
    contacts: List<org.ratatosk.core.FfiContact>,
    contactAvatars: Map<String, ByteArray>,
    currentTab: MainTab,
    visibleTabs: List<MainTab>,
    totalUnreadCount: Int,
    onAccountSelect: (FfiAccount) -> Unit,
    onAddContactClick: () -> Unit,
    onChatSelect: (ByteArray) -> Unit,
    onTabSelect: (MainTab) -> Unit
) {
    NavigationRail(
        modifier = Modifier.width(72.dp),
        header = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                // User Avatar (Profile)
                selectedAccount?.let { account ->
                    AccountAvatar(
                        account = account,
                        isSelected = currentTab == MainTab.PROFILE,
                        onClick = { onAccountSelect(account) }
                    )
                }
                
                // Recent Chats
                contacts.take(5).forEach { contact ->
                    val ikHex = contact.peerIk.toHexString()
                    val avatarBytes = contactAvatars[ikHex]
                    
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onChatSelect(contact.chatId) },
                        contentAlignment = Alignment.Center
                    ) {
                        Avatar(
                            avatarBytes = avatarBytes,
                            name = contact.localName ?: contact.displayName,
                            size = 40.dp
                        )
                    }
                }

                if (visibleTabs.contains(MainTab.CONTACTS)) {
                    IconButton(onClick = onAddContactClick) {
                        Icon(Icons.Default.Add, contentDescription = Strings.ADD_CONTACT)
                    }
                }
            }
        }
    ) {
        Spacer(Modifier.weight(1f))
        visibleTabs.filter { tab ->
            when (tab) {
                MainTab.CHATS -> false // Recent chats in header
                MainTab.PROFILE -> false // Accessible via avatar
                else -> true
            }
        }.forEach { tab ->
            NavigationRailItem(
                selected = currentTab == tab,
                onClick = { onTabSelect(tab) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (tab == MainTab.CHATS && totalUnreadCount > 0) {
                                Badge {
                                    Text(totalUnreadCount.toString())
                                }
                            }
                        }
                    ) {
                        Icon(tab.icon, contentDescription = null)
                    }
                },
                label = { Text(tab.label) }
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun AccountAvatar(
    account: FfiAccount,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Avatar(
            avatarBytes = null,
            name = account.label,
            size = 40.dp
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

private fun String.hexToByteArray() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
