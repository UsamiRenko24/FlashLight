package utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatActivity
import java.util.*

/**
 * 语言管理器 - 负责应用的多语言切换功能
 */
object LanguageManager {

    // SharedPreferences 配置
    private const val PREFS_NAME = "language_settings"
    private const val KEY_LANGUAGE = "selected_language"

    // 支持的语言代码常量
    const val LANGUAGE_CHINESE = "zh"
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_JAPANESE = "ja"
    const val LANGUAGE_KOREAN = "ko"

    // 默认语言（简体中文）
    const val DEFAULT_LANGUAGE = LANGUAGE_CHINESE

    /**
     * 获取 SharedPreferences 实例
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 获取当前语言代码
     */
    fun getCurrentLanguage(context: Context): String {
        val prefs = getPrefs(context)
        return prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    /**
     * 保存语言设置
     */
    fun saveLanguage(context: Context, languageCode: String) {
        val prefs = getPrefs(context)
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
        println("💾 语言设置已保存: $languageCode")
    }

    /**
     * 判断当前是否是中文
     */
    fun isChinese(context: Context): Boolean {
        return getCurrentLanguage(context) == LANGUAGE_CHINESE
    }

    /**
     * 判断当前是否是英文
     */
    fun isEnglish(context: Context): Boolean {
        return getCurrentLanguage(context) == LANGUAGE_ENGLISH
    }

    /**
     * 获取当前语言的显示名称
     */
    fun getCurrentLanguageDisplayName(context: Context): String {
        val languageCode = getCurrentLanguage(context)
        return getLanguageDisplayName(languageCode)
    }

    /**
     * 根据语言代码获取显示名称
     */
    fun getLanguageDisplayName(languageCode: String): String {
        return when (languageCode) {
            LANGUAGE_CHINESE -> "简体中文"
            LANGUAGE_ENGLISH -> "English"
            LANGUAGE_JAPANESE -> "日本語"
            LANGUAGE_KOREAN -> "한국어"
            else -> "简体中文"
        }
    }

    /**
     * 获取支持的语言列表
     * 返回 Pair<语言代码, 显示名称>
     */
    fun getSupportedLanguages(): List<Pair<String, String>> {
        return listOf(
            LANGUAGE_CHINESE to "简体中文",
            LANGUAGE_ENGLISH to "English",
            LANGUAGE_JAPANESE to "日本語",
            LANGUAGE_KOREAN to "한국어"
        )
    }

    /**
     * 根据语言代码获取 Locale 对象
     */
    fun getLocaleFromCode(languageCode: String): Locale {
        return when (languageCode) {
            LANGUAGE_CHINESE -> Locale.SIMPLIFIED_CHINESE
            LANGUAGE_ENGLISH -> Locale.ENGLISH
            LANGUAGE_JAPANESE -> Locale.JAPANESE
            LANGUAGE_KOREAN -> Locale.KOREAN
            else -> Locale.SIMPLIFIED_CHINESE
        }
    }

    /**
     * 应用语言设置到当前 Context
     */
    fun applyLanguage(context: Context): Context {
        val languageCode = getCurrentLanguage(context)
        return applyLanguage(context, languageCode)
    }

    /**
     * 应用指定的语言到 Context
     */
    fun applyLanguage(context: Context, languageCode: String): Context {
        val locale = getLocaleFromCode(languageCode)

        // 保存设置
        saveLanguage(context, languageCode)

        // 应用语言到资源
        return updateResources(context, locale)
    }

    /**
     * 更新资源配置
     */
    private fun updateResources(context: Context, locale: Locale): Context {
        val config = Configuration(context.resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Android 7.0+
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            return context.createConfigurationContext(config)
        } else {
            // Android 6.0 及以下
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            return context
        }
    }

    /**
     * 重启应用以应用语言变化
     */
    fun restartApp(activity: AppCompatActivity) {
        val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
        activity.finishAffinity()
    }

    /**
     * 获取系统当前语言
     */
    fun getSystemLanguage(): String {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocaleList.getDefault()[0]
        } else {
            @Suppress("DEPRECATION")
            Locale.getDefault()
        }
        return locale.language
    }

    /**
     * 检查是否支持该语言
     */
    fun isLanguageSupported(languageCode: String): Boolean {
        return getSupportedLanguages().any { it.first == languageCode }
    }
}