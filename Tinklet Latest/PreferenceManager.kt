package com.tinklet.bharatdatingapp.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "tinklet_prefs")

class PreferenceManager(private val context: Context) {
    companion object {
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val THEME_MODE = stringPreferencesKey("theme_mode") // "Light", "Dark", "System"
        val APP_LANGUAGE = stringPreferencesKey("app_language") // "en", "hi", etc.
        val CACHED_PROFILE = stringPreferencesKey("cached_profile")
        val INTERACTIVE_SOUNDS = booleanPreferencesKey("interactive_sounds")
        val JWT_TOKEN = stringPreferencesKey("jwt_token")
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_LOGGED_IN] ?: false
    }

    val interactiveSounds: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[INTERACTIVE_SOUNDS] ?: true
    }

    suspend fun setInteractiveSounds(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[INTERACTIVE_SOUNDS] = enabled
        }
    }

    suspend fun setLoggedIn(loggedIn: Boolean, email: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[IS_LOGGED_IN] = loggedIn
            if (email != null) prefs[USER_EMAIL] = email
            else if (!loggedIn) prefs.remove(USER_EMAIL)
        }
    }

    val userEmail: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[USER_EMAIL]
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE] ?: "System"
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE] = mode
        }
    }

    val appLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[APP_LANGUAGE] ?: "en"
    }

    suspend fun setAppLanguage(lang: String) {
        context.dataStore.edit { prefs ->
            prefs[APP_LANGUAGE] = lang
        }
    }

    val cachedProfile: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[CACHED_PROFILE]
    }

    suspend fun saveProfileCache(json: String) {
        context.dataStore.edit { prefs ->
            prefs[CACHED_PROFILE] = json
        }
    }

    suspend fun clearProfileCache() {
        context.dataStore.edit { prefs ->
            prefs.remove(CACHED_PROFILE)
            prefs.remove(USER_EMAIL)
            prefs.remove(JWT_TOKEN)
            prefs[IS_LOGGED_IN] = false
        }
    }

    val jwtToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[JWT_TOKEN]
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[JWT_TOKEN] = token
        }
    }
}
