package chat.ratatosk.desktop.ui

import chat.ratatosk.desktop.data.SettingsRepository
import chat.ratatosk.desktop.model.AccountsApi
import chat.ratatosk.desktop.model.AppModels
import chat.ratatosk.desktop.model.ChatsApi
import chat.ratatosk.desktop.model.ContactsApi
import chat.ratatosk.desktop.model.FilesApi
import chat.ratatosk.desktop.model.PreferencesApi
import chat.ratatosk.desktop.model.SessionApi
import chat.ratatosk.desktop.model.TransportsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * **Временный фасад** над моделями из `chat.ratatosk.desktop.model`.
 *
 * Экраны пока обращаются к одному объекту; на этапе 4, когда они всё равно
 * переписываются, каждый получит только нужные ему модели, а фасад уйдёт.
 * Новое сюда не добавлять — только в модели.
 */
class RatatoskViewModel private constructor(
    val models: AppModels,
) : SessionApi by models.session,
    AccountsApi by models.accounts,
    ContactsApi by models.contacts,
    ChatsApi by models.chats,
    FilesApi by models.files,
    TransportsApi by models.transports,
    PreferencesApi by models.preferences {

    constructor(
        settingsRepository: SettingsRepository,
        viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    ) : this(AppModels(settingsRepository, viewModelScope))
}
