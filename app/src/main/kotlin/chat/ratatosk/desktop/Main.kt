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
import chat.ratatosk.desktop.data.SettingsRepository
import chat.ratatosk.desktop.ui.RatatoskViewModel
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.ui.theme.RatatoskTheme
import chat.ratatosk.desktop.ui.onboarding.OnboardingScreen
import chat.ratatosk.desktop.ui.unlock.AccountSelectionScreen
import chat.ratatosk.desktop.ui.unlock.UnlockScreen
import chat.ratatosk.desktop.ui.main.MainScreen
import chat.ratatosk.desktop.ui.media.MediaViewerWindow
import chat.ratatosk.desktop.util.DesktopNotifier
import chat.ratatosk.desktop.util.NotificationIcon
import chat.ratatosk.desktop.util.TrayNotifier

fun main() = application {
    val settingsRepository = remember { SettingsRepository() }
    val viewModel = remember { RatatoskViewModel(settingsRepository) }
    
    val isCoreReady by viewModel.isInitialized.collectAsState()
    val availableAccounts by viewModel.availableAccounts.collectAsState()
    val selectedAccount by viewModel.selectedAccount.collectAsState()
    val isCreatingAccount by viewModel.isCreatingNewAccount.collectAsState()
    val chatTheme by viewModel.chatTheme.collectAsState()
    
    var isWindowVisible by remember { mutableStateOf(true) }
    var bringToFront by remember { mutableStateOf(false) }

    val trayState = rememberTrayState()
    val icon = painterResource("icon.webp")

    // Уведомления: что сказать — решает модель, как показать — платформа.
    val notifier = remember(trayState) {
        DesktopNotifier.create(
            iconPath = NotificationIcon.path(),
            openLabel = Strings.NOTIFY_OPEN,
            fallback = TrayNotifier(trayState),
        )
    }
    LaunchedEffect(notifier) {
        viewModel.notificationRequests.collect { request ->
            notifier.show(request.key, request.title, request.body) {
                isWindowVisible = true
                bringToFront = true
                viewModel.openChat(request.chatId)
            }
        }
    }
    LaunchedEffect(notifier) {
        viewModel.notificationDismissals.collect { notifier.dismiss(it) }
    }
    // Скрытое в трей окно не видно, даже если фокус формально за ним.
    LaunchedEffect(isWindowVisible) {
        if (!isWindowVisible) viewModel.setWindowFocused(false)
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

    // Просмотр картинки — своим окном: чат рядом остаётся живым.
    val viewerMedia by viewModel.viewerMedia.collectAsState()
    viewerMedia?.let { media ->
        RatatoskTheme(themeColor = chatTheme.themeColor) {
            MediaViewerWindow(media = media, onClose = { viewModel.closeViewer() })
        }
    }

    Window(
        onCloseRequest = { isWindowVisible = false },
        visible = isWindowVisible,
        title = Strings.APP_NAME
    ) {
        DisposableEffect(window) {
            val listener = object : java.awt.event.WindowFocusListener {
                override fun windowGainedFocus(e: java.awt.event.WindowEvent?) = viewModel.setWindowFocused(true)
                override fun windowLostFocus(e: java.awt.event.WindowEvent?) = viewModel.setWindowFocused(false)
            }
            window.addWindowFocusListener(listener)
            onDispose { window.removeWindowFocusListener(listener) }
        }
        LaunchedEffect(bringToFront) {
            if (bringToFront) {
                window.toFront()
                window.requestFocus()
                bringToFront = false
            }
        }
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
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}
