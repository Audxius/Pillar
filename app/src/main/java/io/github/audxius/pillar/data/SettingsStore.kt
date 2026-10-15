package io.github.audxius.pillar.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore("pillar")
private val SETTINGS = stringPreferencesKey("settings")
private val USERDATA = stringPreferencesKey("userdata")

class SettingsStore(private val context: Context, scope: CoroutineScope) {

    private fun decodeSettings(raw: String?): AppSettings =
        raw?.let { runCatching { PillarJson.decodeFromString<AppSettings>(it) }.getOrNull() }
            ?.normalized() ?: AppSettings()

    private fun decodeUserData(raw: String?): UserData =
        raw?.let { runCatching { PillarJson.decodeFromString<UserData>(it) }.getOrNull() } ?: UserData()

    // Read once up front so the first frame already has the user's theme instead of flashing defaults.
    private val initial = runBlocking { context.dataStore.data.first() }

    val settings: StateFlow<AppSettings> = context.dataStore.data
        .map { decodeSettings(it[SETTINGS]) }
        .stateIn(scope, SharingStarted.Eagerly, decodeSettings(initial[SETTINGS]))

    val userData: StateFlow<UserData> = context.dataStore.data
        .map { decodeUserData(it[USERDATA]) }
        .stateIn(scope, SharingStarted.Eagerly, decodeUserData(initial[USERDATA]))

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[SETTINGS] = PillarJson.encodeToString(AppSettings.serializer(), transform(decodeSettings(prefs[SETTINGS])))
        }
    }

    suspend fun updateUser(transform: (UserData) -> UserData) {
        context.dataStore.edit { prefs ->
            prefs[USERDATA] = PillarJson.encodeToString(UserData.serializer(), transform(decodeUserData(prefs[USERDATA])))
        }
    }

    /** Settings export excludes passwords and API keys so a shared backup never leaks credentials. */
    fun exportJson(includeSecrets: Boolean): String {
        val s = settings.value
        val out = if (includeSecrets) s else s.copy(rommPassword = "", rommToken = "", raApiKey = "")
        return PillarJson.encodeToString(AppSettings.serializer(), out)
    }

    suspend fun importJson(json: String): Boolean {
        val parsed = runCatching { PillarJson.decodeFromString<AppSettings>(json) }.getOrNull() ?: return false
        update { current ->
            // Keep existing credentials when the backup was exported without them.
            parsed.normalized().copy(
                rommPassword = parsed.rommPassword.ifBlank { current.rommPassword },
                rommToken = parsed.rommToken.ifBlank { current.rommToken },
                raApiKey = parsed.raApiKey.ifBlank { current.raApiKey },
            )
        }
        return true
    }
}
