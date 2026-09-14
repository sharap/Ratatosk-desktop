package chat.ratatosk.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import chat.ratatosk.desktop.core.RatatoskCore
import chat.ratatosk.desktop.data.SettingsRepository
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.ui.theme.RatatoskTheme
import chat.ratatosk.desktop.ui.onboarding.OnboardingScreen
import chat.ratatosk.desktop.ui.unlock.AccountSelectionScreen
import chat.ratatosk.desktop.ui.unlock.UnlockScreen
import chat.ratatosk.desktop.ui.main.MainScreen
import org.ratatosk.core.FfiEvent

fun main() = application {
    val settingsRepository = remember { SettingsRepository() }
    val viewModel = remember { RatatoskViewModel(settingsRepository) }
    
    val isCoreReady by viewModel.isInitialized.collectAsState()
    val availableAccounts by viewModel.availableAccounts.collectAsState()
    val selectedAccount by viewModel.selectedAccount.collectAsState()
    val isCreatingAccount by viewModel.isCreatingNewAccount.collectAsState()
    val chatTheme by viewModel.chatTheme.collectAsState()
    
    var isWindowVisible by remember { mutableStateOf(true) }
    val activeChatId by viewModel.activeChatIdFlow.collectAsState()

    val trayState = rememberTrayState()
    val icon = painterResource("icon.webp")

    LaunchedEffect(Unit) {
        RatatoskCore.events.collect { event ->
            if (event is FfiEvent.MessageReceived) {
                if (!isWindowVisible || activeChatId?.contentEquals(event.chatId) != true) {
                    try {
                        val name = if (viewModel.isCompanionMode.value) {
                             viewModel.contacts.value.find { it.chatId.contentEquals(event.chatId) }?.let { it.localName ?: it.displayName } ?: "???"
                        } else {
                            val client = RatatoskCore.getClient()
                            val contact = client.contacts().find { it.chatId.contentEquals(event.chatId) }
                            contact?.localName ?: contact?.displayName ?: "???"
                        }
                        
                        trayState.sendNotification(
                            androidx.compose.ui.window.Notification(
                                title = Strings.MESSAGE,
                                message = name
                            )
                        )
                    } catch (e: Exception) { }
                }
            }
        }
    }

    Tray(
        state = trayState,
        icon = icon,
        menu = {
            Item(
                text = if (isWindowVisible) Strings.TRAY_HIDE else Strings.TRAY_OPEN,
                onClick = { isWindowVisible = !isWindowVisible }
            )
            Separator()
            Item(
                text = Strings.EXIT,
                onClick = { exitApplication() }
            )
        }
    )

    Window(
        onCloseRequest = { isWindowVisible = false },
        visible = isWindowVisible,
        title = Strings.APP_NAME
    ) {
        RatatoskTheme(themeColor = chatTheme.themeColor) {
            Surface(modifier = Modifier.fillMaxSize()) {
                if (!isCoreReady) {
                    if (isCreatingAccount || availableAccounts.isEmpty()) {
                        OnboardingScreen(viewModel)
                    } else if (selectedAccount != null) {
                        UnlockScreen(viewModel, selectedAccount!!)
                    } else {
                        AccountSelectionScreen(viewModel)
                    }
                } else {
                    MainScreen(
                        viewModel = viewModel,
                        onChatClick = { chatId ->
                            viewModel.setActiveChat(chatId)
                        },
                        onContactClick = { chatId ->
                            viewModel.setActiveContact(chatId)
                        }
                    )
                }
            }
        }
    }
}
