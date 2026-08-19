package com.deepseek.personal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "deepseek_settings")

class SettingsStore(private val context: Context) {

    /** 旧版本明文 Key 迁移为 AES-GCM 密文（读一次旧值→加密写回）。 */
    suspend fun migrateLegacyKeyIfNeeded() {
        val stored = context.dataStore.data.first()[KEY_API_KEY].orEmpty()
        if (stored.isNotEmpty() && !CryptoManager.isEncrypted(stored)) {
            context.dataStore.edit { it[KEY_API_KEY] = CryptoManager.encrypt(stored) }
        }
    }

    val apiKey: Flow<String> = context.dataStore.data.map {
        val stored = it[KEY_API_KEY].orEmpty()
        if (stored.isEmpty()) "" else CryptoManager.decrypt(stored)
    }

    val model: Flow<String> = context.dataStore.data.map {
        it[KEY_MODEL] ?: ModelInfo.FLASH
    }

    val thinking: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_THINKING] ?: false
    }

    val reasoningEffort: Flow<String> = context.dataStore.data.map {
        it[KEY_EFFORT] ?: "high"
    }

    val autoMemory: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_AUTO_MEMORY] ?: true
    }

    val themeKey: Flow<String> = context.dataStore.data.map {
        it[KEY_THEME] ?: "deepseek"
    }

    val webSearch: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_WEB_SEARCH] ?: false
    }

    val vibrateOnOutput: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_VIBRATE] ?: true
    }

    val highRefresh: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_HIGH_REFRESH] ?: true
    }

    val themeMode: Flow<String> = context.dataStore.data.map {
        it[KEY_THEME_MODE] ?: "system"
    }

    suspend fun setApiKey(value: String) {
        context.dataStore.edit { it[KEY_API_KEY] = CryptoManager.encrypt(value) }
    }

    suspend fun setModel(value: String) {
        context.dataStore.edit { it[KEY_MODEL] = value }
    }

    suspend fun setThinking(value: Boolean) {
        context.dataStore.edit { it[KEY_THINKING] = value }
    }

    suspend fun setReasoningEffort(value: String) {
        context.dataStore.edit { it[KEY_EFFORT] = value }
    }

    suspend fun setAutoMemory(value: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_MEMORY] = value }
    }

    suspend fun setThemeKey(value: String) {
        context.dataStore.edit { it[KEY_THEME] = value }
    }

    suspend fun setWebSearch(value: Boolean) {
        context.dataStore.edit { it[KEY_WEB_SEARCH] = value }
    }

    suspend fun setVibrateOnOutput(value: Boolean) {
        context.dataStore.edit { it[KEY_VIBRATE] = value }
    }

    suspend fun setHighRefresh(value: Boolean) {
        context.dataStore.edit { it[KEY_HIGH_REFRESH] = value }
    }

    suspend fun setThemeMode(value: String) {
        context.dataStore.edit { it[KEY_THEME_MODE] = value }
    }

    companion object {
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_MODEL = stringPreferencesKey("model")
        private val KEY_THINKING = booleanPreferencesKey("thinking")
        private val KEY_EFFORT = stringPreferencesKey("reasoning_effort")
        private val KEY_AUTO_MEMORY = booleanPreferencesKey("auto_memory")
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_WEB_SEARCH = booleanPreferencesKey("web_search")
        private val KEY_VIBRATE = booleanPreferencesKey("vibrate_on_output")
        private val KEY_HIGH_REFRESH = booleanPreferencesKey("high_refresh")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
