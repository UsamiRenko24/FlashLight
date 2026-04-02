package utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object DataStoreManager {

    private val KEY_VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
    private val KEY_SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
    private val KEY_LOW_BATTERY_PROTECTION = booleanPreferencesKey("low_battery_protection")
    
    private val KEY_LANGUAGE = stringPreferencesKey("selected_language")
    private val KEY_STARTUP_MODE = intPreferencesKey("startup_mode")
    private val KEY_DEFAULT_BRIGHTNESS = intPreferencesKey("default_brightness")

    private val KEY_AUTO_OFF_FLASHLIGHT = intPreferencesKey("flashlight_auto_off")
    private val KEY_AUTO_OFF_SCREEN = intPreferencesKey("screen_light_auto_off")
    private val KEY_AUTO_OFF_BLINK = intPreferencesKey("blink_auto_off")

    // --- 读取 ---
    fun isVibrationEnabled(context: Context): Flow<Boolean> = context.dataStore.data.map { it[KEY_VIBRATION_ENABLED] ?: true }
    fun isSoundEnabled(context: Context): Flow<Boolean> = context.dataStore.data.map { it[KEY_SOUND_ENABLED] ?: true }
    fun isLowBatteryEnabled(context: Context): Flow<Boolean> = context.dataStore.data.map { it[KEY_LOW_BATTERY_PROTECTION] ?: true }

    fun getFlashlightAutoOffTime(context: Context): Flow<Int> = context.dataStore.data.map { it[KEY_AUTO_OFF_FLASHLIGHT] ?: 5 }
    fun getScreenAutoOffTime(context: Context): Flow<Int> = context.dataStore.data.map { it[KEY_AUTO_OFF_SCREEN] ?: 5 }
    fun getBlinkAutoOffTime(context: Context): Flow<Int> = context.dataStore.data.map { it[KEY_AUTO_OFF_BLINK] ?: 5 }
    fun getLanguage(context: Context): Flow<String> = context.dataStore.data.map { it[KEY_LANGUAGE] ?: "zh" }
    fun getStartupMode(context: Context): Flow<Int> = context.dataStore.data.map { it[KEY_STARTUP_MODE] ?: 1 }
    fun getDefaultBrightness(context: Context): Flow<Int> = context.dataStore.data.map { it[KEY_DEFAULT_BRIGHTNESS] ?: 1 }

    // --- 写入 ---
    suspend fun setVibrationEnabled(context: Context, enabled: Boolean) = context.dataStore.edit { it[KEY_VIBRATION_ENABLED] = enabled }
    suspend fun setSoundEnabled(context: Context, enabled: Boolean) = context.dataStore.edit { it[KEY_SOUND_ENABLED] = enabled }
    suspend fun setLowBatteryEnabled(context: Context, enabled: Boolean) = context.dataStore.edit { it[KEY_LOW_BATTERY_PROTECTION] = enabled }
    
    suspend fun setDefaultBrightness(context: Context, level: Int) = context.dataStore.edit { it[KEY_DEFAULT_BRIGHTNESS] = level }
    
    suspend fun setFlashlightAutoOffTime(context: Context, minutes: Int) = context.dataStore.edit { it[KEY_AUTO_OFF_FLASHLIGHT] = minutes }
    suspend fun setScreenAutoOffTime(context: Context, minutes: Int) = context.dataStore.edit { it[KEY_AUTO_OFF_SCREEN] = minutes }
    suspend fun setBlinkAutoOffTime(context: Context, minutes: Int) = context.dataStore.edit { it[KEY_AUTO_OFF_BLINK] = minutes }
    
    suspend fun setLanguage(context: Context, code: String) = context.dataStore.edit { it[KEY_LANGUAGE] = code }
    suspend fun setStartupMode(context: Context, mode: Int) = context.dataStore.edit { it[KEY_STARTUP_MODE] = mode }
}