package com.name.flashlight.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale

object LanguageManager {

    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_CHINESE = "zh"

    const val DEFAULT_LANGUAGE = LANGUAGE_ENGLISH

    /**
     * 获取支持语言
     */
    fun getSupportedLanguages(): List<Pair<String, String>> {
        return listOf(
            LANGUAGE_ENGLISH to "English",
            LANGUAGE_CHINESE to "简体中文"
        )
    }

    /**
     * 获取 Locale
     */
    private fun getLocale(language: String): Locale {
        return when (language) {

            LANGUAGE_CHINESE ->
                Locale.SIMPLIFIED_CHINESE

            else ->
                Locale.ENGLISH
        }
    }

    /**
     * 应用语言
     */
    fun applyLanguage(
        context: Context,
        language: String
    ): Context {

        val locale = getLocale(language)

        Locale.setDefault(locale)

        val config =
            Configuration(
                context.resources.configuration
            )

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.N
        ) {

            config.setLocale(locale)

            config.setLocales(
                LocaleList(locale)
            )

            context.createConfigurationContext(
                config
            )

        } else {

            @Suppress("DEPRECATION")
            config.locale = locale

            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(
                config,
                context.resources.displayMetrics
            )

            context
        }
    }

    /**
     * 应用已保存语言
     */
    fun applySavedLanguage(
        context: Context
    ): Context {

        val language =
            runBlocking {

                DataStoreManager
                    .getLanguage(context)
                    .first()
            }

        return applyLanguage(
            context,
            language
        )
    }

    /**
     * 重启 App
     */
    fun restartApp(activity: Activity) {

        val intent =
            activity.packageManager
                .getLaunchIntentForPackage(
                    activity.packageName
                )

        intent?.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
        )

        activity.startActivity(intent)

        activity.finishAffinity()
    }
}