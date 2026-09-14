package chat.ratatosk.desktop.ui

import chat.ratatosk.desktop.data.SettingsRepository
import org.ratatosk.core.FfiAccount

sealed class AccountItem {
    data class Local(val account: FfiAccount) : AccountItem()
    data class Companion(val pairing: SettingsRepository.CompanionPairing) : AccountItem()
}
