package utils

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {

    private const val PREFS_NAME = "theme_settings"
    private const val KEY_THEME_MODE = "theme_mode"

    // 主题模式常量
    const val MODE_LIGHT = 0      // 日间模式
    const val MODE_DARK = 1       // 夜间模式
    const val MODE_SYSTEM = 2     // 跟随系统

    /**
     * 设置主题模式
     */
    fun setThemeMode(context: Context, mode: Int) {
        // 保存设置
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()

        // 应用主题
        applyTheme(mode)
    }

    /**
     * 获取当前主题模式
     */
    fun getThemeMode(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_THEME_MODE, MODE_SYSTEM)
    }

    /**
     * 应用主题
     */
    fun applyTheme(mode: Int) {
        when (mode) {
            MODE_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            MODE_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            MODE_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    /**
     * 获取当前是否是夜间模式
     */
    fun isNightMode(context: Context): Boolean {
        val mode = getThemeMode(context)
        return when (mode) {
            MODE_DARK -> true
            MODE_SYSTEM -> {
                val nightModeFlags = context.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            else -> false
        }
    }
}