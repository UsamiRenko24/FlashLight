package com.name.flashlight.integration.language;

import android.annotation.SuppressLint;
import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import androidx.annotation.NonNull;

import java.util.Locale;

@SuppressWarnings("deprecation")
final class LanguagesUtils {

    /**
     * 获取语种对象
     */
    static Locale getLocale(@NonNull Context context) {
        return getLocale(context.getResources().getConfiguration());
    }

    @SuppressLint("ObsoleteSdkInt")
    static Locale getLocale(Configuration config) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return config.getLocales().get(0);
        } else {
            return config.locale;
        }
    }

    /**
     * 设置语种对象
     */
    @SuppressLint("ObsoleteSdkInt")
    static void setLocale(Configuration config, Locale locale) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocaleList localeList = new LocaleList(locale);
            config.setLocales(localeList);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale);
        } else {
            config.locale = locale;
        }
    }

    /**
     * 获取系统的语种对象
     */
    static Locale getSystemLocale(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 在 Android 13 上，不能用 Resources.getSystem() 来获取系统语种了
            // Android 13 上面新增了一个 LocaleManager 的语种管理类
            // 因为如果调用 LocaleManager.setApplicationLocales 会影响获取到的结果不准确
            // 所以应该得用 LocaleManager.getSystemLocales 来获取会比较精准
            LocaleManager localeManager = context.getSystemService(LocaleManager.class);
            if (localeManager != null) {
                return localeManager.getSystemLocales().get(0);
            }
        }

        return LanguagesUtils.getLocale(Resources.getSystem().getConfiguration());
    }

    /**
     * 获取默认的语种环境
     */
    @SuppressLint("ObsoleteSdkInt")
    static Locale getDefaultLocale() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return LocaleList.getDefault().get(0);
        } else {
            return Locale.getDefault();
        }
    }

    /**
     * 设置默认的语种环境（日期格式化会用到）
     */
    @SuppressLint("ObsoleteSdkInt")
    static void setDefaultLocale(@NonNull Context context) {
        Configuration configuration = context.getResources().getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocaleList.setDefault(configuration.getLocales());
        } else {
            Locale.setDefault(configuration.locale);
        }
    }

    /**
     * 绑定当前 App 的语种
     */
    @SuppressLint("ObsoleteSdkInt")
    static Context attachLanguages(@NonNull Context context, Locale locale) {
        Resources resources = context.getResources();
        Configuration config = new Configuration(resources.getConfiguration());
        setLocale(config, locale);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            try {
                context = context.createConfigurationContext(config);
            } catch (Throwable ignored) {
            }
        }
        resources.updateConfiguration(config, resources.getDisplayMetrics());
        return context;
    }

    /**
     * 更新 Resources 语种
     */
    static void updateLanguages(@NonNull Resources resources, Locale locale) {
        Configuration config = resources.getConfiguration();
        setLocale(config, locale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }

    /**
     * 更新手机配置信息变化
     */
    static void updateConfigurationChanged(@NonNull Context context, Configuration newConfig, Locale appLanguage) {
        Configuration config = new Configuration(newConfig);
        // 绑定当前语种到这个新的配置对象中
        setLocale(config, appLanguage);
        Resources resources = context.getResources();
        // 更新上下文的配置信息
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }

    /**
     * 生成某个语种下的 Resources 对象
     */
    @SuppressLint("ObsoleteSdkInt")
    static Resources generateLanguageResources(Context context, Locale locale) {
        Configuration config = new Configuration();
        setLocale(config, locale);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return context.createConfigurationContext(config).getResources();
        }
        return new Resources(context.getAssets(), context.getResources().getDisplayMetrics(), config);
    }
}