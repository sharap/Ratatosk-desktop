package chat.ratatosk.desktop.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.*
import chat.ratatosk.desktop.util.AppDirs
import chat.ratatosk.desktop.ui.theme.ChatThemeData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class SettingsRepository {
    private val dataStore = PreferenceDataStoreFactory.create {
        File(AppDirs.getBaseDir(), "settings.preferences_pb")
    }

    private object Keys {
        val THEME_COLOR = longPreferencesKey("theme_color")
        val BACKGROUND_IMAGE_URI = stringPreferencesKey("background_image_uri")
        val BACKGROUND_OPACITY = longPreferencesKey("background_opacity")
        val ACCOUNTS_MAP = stringPreferencesKey("accounts_map")
        val COMPANION_PAIRINGS = stringPreferencesKey("companion_pairings")
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

    data class CompanionPairing(
        val deviceId: String,
        val inviteUri: String,
        val phoneName: String,
        val useCache: Boolean
    )

    val companionPairings: Flow<List<CompanionPairing>> = dataStore.data.map { preferences ->
        val raw = preferences[Keys.COMPANION_PAIRINGS] ?: ""
        if (raw.isEmpty()) emptyList()
        else {
            // Испорченная запись пропускается, а не роняет приложение на старте.
            raw.split(";;").filter { it.isNotBlank() }.mapNotNull {
                val parts = it.split("||")
                if (parts.size != 4 || parts[0].isBlank() || parts[1].isBlank()) null
                else CompanionPairing(parts[0], parts[1], parts[2], parts[3].toBoolean())
            }
        }
    }

    suspend fun saveCompanionPairing(pairing: CompanionPairing) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.COMPANION_PAIRINGS] ?: ""
            val pairings = current.split(";;").filter { it.isNotBlank() }.toMutableList()
            // Имя телефона задаёт его владелец: разделители формата из него убираем.
            val phoneName = pairing.phoneName.replace("||", "|").replace(";;", ";")
            val entry = "${pairing.deviceId}||${pairing.inviteUri}||$phoneName||${pairing.useCache}"
            // Remove old if exists
            pairings.removeAll { it.startsWith("${pairing.deviceId}||") }
            pairings.add(entry)
            preferences[Keys.COMPANION_PAIRINGS] = pairings.joinToString(";;")
        }
    }

    suspend fun removeCompanionPairing(deviceId: String) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.COMPANION_PAIRINGS] ?: ""
            val pairings = current.split(";;").filter { it.isNotBlank() }.toMutableList()
            pairings.removeAll { it.startsWith("$deviceId||") }
            preferences[Keys.COMPANION_PAIRINGS] = pairings.joinToString(";;")
        }
    }
}
