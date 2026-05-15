package com.name.flashlight.integration.language;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.Locale;

final class LanguagesConfig {
    private static final String CACHE_KEY_LANGUAGE = "CACHE_KEY_LANGUAGE";
    private static final String CACHE_KEY_COUNTRY = "CACHE_KEY_COUNTRY";
    private static String LANGUAGE_SP_NAME = "LANGUAGE_SP_NAME";

    /**
     * 当前语种
     */
    private static volatile Locale sCurrentLocale;

    /**
     * 默认语种
     */
    private static volatile Locale sDefaultLocale;

    static void setSharedPreferencesName(String name) {
        LANGUAGE_SP_NAME = name;
    }

    private static SharedPreferences getSharedPreferences(@NonNull Context context) {
        return context.getSharedPreferences(LANGUAGE_SP_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 读取 App 语种
     */
    static Locale readAppLanguageSetting(Context context) {
        if (sCurrentLocale != null) {
            return sCurrentLocale;
        }

        String language = getSharedPreferences(context).getString(CACHE_KEY_LANGUAGE, "");
        String country = getSharedPreferences(context).getString(CACHE_KEY_COUNTRY, "");

        if (!TextUtils.isEmpty(language)) {
            sCurrentLocale = new Locale(language, country);
            return sCurrentLocale;
        }

        if (sDefaultLocale != null) {
            sCurrentLocale = sDefaultLocale;
            return sCurrentLocale;
        }

        sCurrentLocale = LanguagesUtils.getLocale(context);

        return sCurrentLocale;
    }

    /**
     * 保存 App 语种设置
     */
    static void saveAppLanguageSetting(Context context, Locale locale) {
        sCurrentLocale = locale;
        getSharedPreferences(context).edit()
                .putString(CACHE_KEY_LANGUAGE, locale.getLanguage())
                .putString(CACHE_KEY_COUNTRY, locale.getCountry())
                .apply();
    }

    /**
     * 清除语种设置
     */
    static void clearLanguageSetting(Context context) {
        sCurrentLocale = MultiLanguages.getSystemLanguage(context);
        getSharedPreferences(context).edit()
                .remove(CACHE_KEY_LANGUAGE)
                .remove(CACHE_KEY_COUNTRY)
                .apply();
    }

    /**
     * 是否跟随系统
     */
    public static boolean isSystemLanguage(Context context) {
        if (sDefaultLocale != null) {
            return false;
        }

        String language = getSharedPreferences(context).getString(CACHE_KEY_LANGUAGE, "");
        return TextUtils.isEmpty(language);
    }

    /**
     * 设置默认的语种
     */
    public static void setDefaultLanguage(Locale locale) {
        if (sCurrentLocale != null) {
            // 这个 API 需要越早调用越好，建议放在 Application 静态代码块中初始化
            // 当然也可以在 Application 调用 super.attachBaseContext 方法之前
            return;
        }
        sDefaultLocale = locale;
    }
}