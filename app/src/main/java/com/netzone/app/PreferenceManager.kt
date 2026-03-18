package com.netzone.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class AppSortMode { NAME, UID, SMART }

class PreferenceManager(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    companion object {
        private val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        private val IS_LOCKDOWN = booleanPreferencesKey("is_lockdown")
        private val MANAGE_SYSTEM_APPS = booleanPreferencesKey("manage_system_apps")
        private val BLOCK_WHEN_SCREEN_OFF = booleanPreferencesKey("block_when_screen_off")
        private val CUSTOM_DNS = stringPreferencesKey("custom_dns")
        private val APP_SORT_MODE = stringPreferencesKey("app_sort_mode")
    }

    val appSortMode: Flow<AppSortMode> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val modeName = preferences[APP_SORT_MODE] ?: AppSortMode.SMART.name
            try {
                AppSortMode.valueOf(modeName)
            } catch (e: IllegalArgumentException) {
                AppSortMode.SMART
            }
        }

    val isDarkMode: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[IS_DARK_MODE] ?: false
        }

    val isLockdown: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[IS_LOCKDOWN] ?: false
        }

    val manageSystemApps: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[MANAGE_SYSTEM_APPS] ?: false
        }

    val blockWhenScreenOff: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[BLOCK_WHEN_SCREEN_OFF] ?: false
        }

    val customDns: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[CUSTOM_DNS] ?: "8.8.8.8"
        }

    suspend fun setDarkMode(isDark: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_DARK_MODE] = isDark
        }
    }

    suspend fun setLockdown(isLockdown: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_LOCKDOWN] = isLockdown
        }
    }

    suspend fun setManageSystemApps(manage: Boolean) {
        dataStore.edit { preferences ->
            preferences[MANAGE_SYSTEM_APPS] = manage
        }
    }

    suspend fun setBlockWhenScreenOff(block: Boolean) {
        dataStore.edit { preferences ->
            preferences[BLOCK_WHEN_SCREEN_OFF] = block
        }
    }

    suspend fun setCustomDns(dns: String) {
        dataStore.edit { preferences ->
            preferences[CUSTOM_DNS] = dns
        }
    }

    suspend fun setAppSortMode(mode: AppSortMode) {
        dataStore.edit { it[APP_SORT_MODE] = mode.name }
    }
}
