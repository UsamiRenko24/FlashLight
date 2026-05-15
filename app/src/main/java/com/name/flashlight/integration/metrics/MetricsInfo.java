package com.name.flashlight.integration.metrics;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

public final class MetricsInfo {
    @Nullable
    public static String APP_VERSION_NAME = "";
    @Nullable
    public static String APP_PACKAGE_NAME = "";
    @NonNull
    public static String DEVICE_MODEL = "";
    @NonNull
    public static String SYSTEM_VERSION = "";
    @NonNull
    public static String DEVICE_GUID = "";
    @NonNull
    public static String DEVICE_LOCALE = "";
    @NonNull
    public static String DEVICE_LANGUAGE = "";
    @NonNull
    public static String TIME_ZONE = "";

    private static volatile boolean isInitialized = false;
    private static final Object INIT_LOCK = new Object();

    /**
     * 初始化设备信息
     * 应在应用启动时调用，避免在静态块中使用Context导致内存泄漏
     * 线程安全的初始化方法
     */
    public static void init() {
        if (isInitialized) {
            return;
        }

        synchronized (INIT_LOCK) {
            if (isInitialized) {
                return;
            }

            final Application app = MetricsManager.getInstance().getContext();
            Locale sysLocale = Locale.getDefault();

            if (app != null) {
                APP_VERSION_NAME = MetricsUtils.getAppVersionName(app);
                APP_PACKAGE_NAME = app.getPackageName();
                sysLocale = MetricsUtils.getSystemLanguage(app);
            }

            DEVICE_MODEL = MetricsUtils.getDeviceModel();
            SYSTEM_VERSION = MetricsUtils.getSystemVersion();
            DEVICE_GUID = MetricsUtils.getDeviceId();

            final String localeStr = MetricsCache.getLocale();
            if (!TextUtils.isEmpty(localeStr)) {
                DEVICE_LOCALE = localeStr;
            } else {
                DEVICE_LOCALE = sysLocale.toString();
            }

            DEVICE_LANGUAGE = sysLocale.getLanguage();
            TIME_ZONE = MetricsUtils.getTimeZone();

            isInitialized = true;
        }
    }

    public static void updateDeviceLocale(@NonNull String value) {
        DEVICE_LOCALE = value;
    }
}