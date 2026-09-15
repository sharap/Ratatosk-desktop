package chat.ratatosk.desktop.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.*
import chat.ratatosk.desktop.util.AppDirs
import chat.ratatosk.desktop.util.SecretStore
import chat.ratatosk.desktop.ui.theme.ChatThemeData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class SettingsRepository(
    file: File = File(AppDirs.getBaseDir(), "settings.preferences_pb"),
) {
    private val dataStore = PreferenceDataStoreFactory.create { file }

    private object Keys {
        val THEME_COLOR = longPreferencesKey("theme_color")
        val BACKGROUND_IMAGE_URI = stringPreferencesKey("background_image_uri")
        val BACKGROUND_OPACITY = longPreferencesKey("background_opacity")
        val ACCOUNTS_MAP = stringPreferencesKey("accounts_map")
        val COMPANION_PAIRINGS = stringPreferencesKey("companion_pairings")
        val CORE_LOG_ENABLED = booleanPreferencesKey("core_log_enabled")
    }

    val accountsMap: Flow<Map<String, String>> = dataStore.data.map { preferences ->
        val raw = preferences[Keys.ACCOUNTS_MAP] ?: ""
        if (raw.isEmpty()) emptyMap()
        else {
            raw.split(";").filter { it.contains(":") }.associate { 
                val parts = it.split(":", limit = 2)
                parts[0] to parts[1]
            }
        }
    }

    suspend fun registerAccount(accountId: String, displayName: String) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.ACCOUNTS_MAP] ?: ""
            val accounts = current.split(";").filter { it.isNotBlank() }.toMutableList()
            val entry = "$accountId:$displayName"
            if (!accounts.any { it.startsWith("$accountId:") }) {
                accounts.add(entry)
                preferences[Keys.ACCOUNTS_MAP] = accounts.joinToString(";")
            }
            preferences[stringPreferencesKey(accountKey(accountId, "display_name"))] = displayName
        }
    }

    private fun accountKey(accountId: String, key: String) = "${accountId}_$key"

    fun getDisplayName(accountId: String): Flow<String?> = dataStore.data.map { it[stringPreferencesKey(accountKey(accountId, "display_name"))] }
    fun getNotificationsShowName(accountId: String): Flow<Boolean> = dataStore.data.map { it[booleanPreferencesKey(accountKey(accountId, "notifications_show_name"))] ?: true }
    fun getNotificationsShowText(accountId: String): Flow<Boolean> = dataStore.data.map { it[booleanPreferencesKey(accountKey(accountId, "notifications_show_text"))] ?: true }
    fun getDownloadDirPath(accountId: String): Flow<String?> = dataStore.data.map { it[stringPreferencesKey(accountKey(accountId, "download_dir_path"))] }

    /** Вести ли журнал ядра в файл. Настройка процесса, а не аккаунта. */
    val coreLogEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.CORE_LOG_ENABLED] ?: false }

    suspend fun setCoreLogEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.CORE_LOG_ENABLED] = enabled }
    }

    val chatTheme: Flow<ChatThemeData> = dataStore.data.map { preferences ->
        ChatThemeData(
            themeColor = preferences[Keys.THEME_COLOR]?.let { Color(it.toInt()) } ?: Color.Unspecified,
            backgroundImageUri = preferences[Keys.BACKGROUND_IMAGE_URI],
            backgroundOpacity = (preferences[Keys.BACKGROUND_OPACITY]?.toInt() ?: 100) / 100f
        )
    }

    suspend fun setDisplayName(accountId: String, name: String) {
        dataStore.edit { it[stringPreferencesKey(accountKey(accountId, "display_name"))] = name }
    }

    suspend fun setNotificationsShowName(accountId: String, show: Boolean) {
        dataStore.edit { it[booleanPreferencesKey(accountKey(accountId, "notifications_show_name"))] = show }
    }

    suspend fun setNotificationsShowText(accountId: String, show: Boolean) {
        dataStore.edit { it[booleanPreferencesKey(accountKey(accountId, "notifications_show_text"))] = show }
    }

    suspend fun setDownloadDirPath(accountId: String, path: String?) {
        dataStore.edit { preferences ->
            val key = stringPreferencesKey(accountKey(accountId, "download_dir_path"))
            if (path != null) {
                preferences[key] = path
            } else {
                preferences.remove(key)
            }
        }
    }

    suspend fun updateChatTheme(data: ChatThemeData) {
        dataStore.edit { preferences ->
            if (data.backgroundImageUri != null) {
                preferences[Keys.BACKGROUND_IMAGE_URI] = data.backgroundImageUri
            } else {
                preferences.remove(Keys.BACKGROUND_IMAGE_URI)
            }
            
            if (data.themeColor != Color.Unspecified) {
                preferences[Keys.THEME_COLOR] = data.themeColor.toArgb().toLong()
            } else {
                preferences.remove(Keys.THEME_COLOR)
            }

            preferences[Keys.BACKGROUND_OPACITY] = (data.backgroundOpacity * 100).toLong()
        }
    }

    /**
     * Сохранённое сопряжение с телефоном — **без ссылки**.
     *
     * Ссылка сопряжения и есть секрет: у кого она, тот второй экран этого
     * телефона до отзыва (DESKTOP.md). Она лежит в [SecretStore], а здесь —
     * только то, что можно показать в списке. [legacyInviteUri] — запись
     * старого формата, которую не удалось перенести (хранилища ОС нет).
     */
    data class CompanionPairing(
        val deviceId: String,
        val phoneName: String,
        val useCache: Boolean,
        val legacyInviteUri: String? = null,
    )

    val companionPairings: Flow<List<CompanionPairing>> = dataStore.data.map { preferences ->
        parsePairings(preferences[Keys.COMPANION_PAIRINGS] ?: "")
    }

    private fun parsePairings(raw: String): List<CompanionPairing> =
        // Испорченная запись пропускается, а не роняет приложение на старте.
        raw.split(";;").filter { it.isNotBlank() }.mapNotNull {
            val parts = it.split("||")
            when {
                parts.isEmpty() || parts[0].isBlank() -> null
                // Новый формат: deviceId||phoneName||useCache
                parts.size == 3 -> CompanionPairing(parts[0], parts[1], parts[2].toBoolean())
                // Старый: deviceId||inviteUri||phoneName||useCache
                parts.size == 4 && parts[1].isNotBlank() ->
                    CompanionPairing(parts[0], parts[2], parts[3].toBoolean(), legacyInviteUri = parts[1])
                else -> null
            }
        }

    private fun encodePairing(pairing: CompanionPairing): String {
        // Имя телефона задаёт его владелец: разделители формата из него убираем.
        val phoneName = pairing.phoneName.replace("||", "|").replace(";;", ";")
        return if (pairing.legacyInviteUri != null) {
            "${pairing.deviceId}||${pairing.legacyInviteUri}||$phoneName||${pairing.useCache}"
        } else {
            "${pairing.deviceId}||$phoneName||${pairing.useCache}"
        }
    }

    private suspend fun updatePairings(transform: (List<CompanionPairing>) -> List<CompanionPairing>) {
        dataStore.edit { preferences ->
            val current = parsePairings(preferences[Keys.COMPANION_PAIRINGS] ?: "")
            preferences[Keys.COMPANION_PAIRINGS] = transform(current).joinToString(";;") { encodePairing(it) }
        }
    }

    /** Запомнить сопряжение; ссылку вызывающий уже положил в [SecretStore]. */
    suspend fun saveCompanionPairing(pairing: CompanionPairing) {
        updatePairings { list -> list.filterNot { it.deviceId == pairing.deviceId } + pairing.copy(legacyInviteUri = null) }
    }

    suspend fun removeCompanionPairing(deviceId: String) {
        updatePairings { list -> list.filterNot { it.deviceId == deviceId } }
    }

    /**
     * Переносит ссылки из записей старого формата в [SecretStore] и стирает
     * их из файла настроек. Без хранилища записи остаются как были —
     * иначе человек молча потерял бы сопряжение.
     *
     * @return сколько записей осталось в старом формате.
     */
    suspend fun migratePairingSecrets(store: SecretStore): Int {
        var remaining = 0
        updatePairings { list ->
            list.map { pairing ->
                val uri = pairing.legacyInviteUri ?: return@map pairing
                if (store.isAvailable && store.put(SecretStore.PAIRING_PREFIX + pairing.deviceId, uri.toByteArray())) {
                    pairing.copy(legacyInviteUri = null)
                } else {
                    remaining++
                    pairing
                }
            }
        }
        return remaining
    }

    /** Аккаунт открывается секретом устройства из [SecretStore]. */
    fun isDeviceBound(accountId: String): Flow<Boolean> =
        dataStore.data.map { it[booleanPreferencesKey(accountKey(accountId, "device_bound"))] ?: false }

    suspend fun setDeviceBound(accountId: String, bound: Boolean) {
        dataStore.edit { it[booleanPreferencesKey(accountKey(accountId, "device_bound"))] = bound }
    }
}
