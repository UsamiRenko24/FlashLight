package com.name.flashlight.integration.metrics;

import android.annotation.SuppressLint;
import android.app.LocaleManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.icu.util.TimeZone;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.webkit.WebSettings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;

public final class MetricsUtils {
    // 定义字符池，包含数字和大小写英文字母
    private static final String CHAR_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static void runOnUiThread(@NonNull final Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            HANDLER.post(runnable);
        }
    }

    public static void runOnUiThreadDelayed(@NonNull final Runnable runnable, long delayMillis) {
        HANDLER.postDelayed(runnable, delayMillis);
    }

    /**
     * @return 判断时间戳是否为今天
     */
    public static boolean isToday(long timestamp) {
        // 获取当前时间的 Calendar 实例
        final Calendar today = Calendar.getInstance();

        // 获取时间戳对应的 Calendar 实例
        final Calendar timestampCalendar = Calendar.getInstance();
        timestampCalendar.setTimeInMillis(timestamp);

        // 比较年、月、日是否相同
        return today.get(Calendar.YEAR) == timestampCalendar.get(Calendar.YEAR)
                && today.get(Calendar.MONTH) == timestampCalendar.get(Calendar.MONTH)
                && today.get(Calendar.DAY_OF_MONTH) == timestampCalendar.get(Calendar.DAY_OF_MONTH);
    }

    @NonNull
    public static String replaceCountry(@NonNull String localeStr, @NonNull String newCountry) {
        if (localeStr.trim().isEmpty()) {
            return localeStr;
        }
        final String[] parts = localeStr.split("_");
        if (parts.length == 1) {
            // 没有国家部分，只返回语言 + 新国家
            return parts[0] + "_" + newCountry;
        } else if (parts.length >= 2) {
            // 替换国家部分
            return parts[0] + "_" + newCountry;
        }
        return localeStr;
    }

    /**
     * @return 获取系统的语种
     */
    public static Locale getSystemLanguage(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 在 Android 13 上，不能用 Resources.getSystem() 来获取系统语种了
            // Android 13 上面新增了一个 LocaleManager 的语种管理类
            // 因为如果调用 LocaleManager.setApplicationLocales 会影响获取到的结果不准确
            // 所以应该得用 LocaleManager.getSystemLocales 来获取会比较精准
            try {
                final LocaleManager localeManager = context.getSystemService(LocaleManager.class);
                return localeManager.getSystemLocales().get(0);
            } catch (Throwable ignored) {
                return Locale.getDefault();
            }
        }

        try {
            final Configuration config = Resources.getSystem().getConfiguration();
            return config.getLocales().get(0);
        } catch (Throwable ignored) {
            return Locale.getDefault();
        }
    }

    // 生成随机字符串的方法
    @NonNull
    public static String generateRandomString(int length) {
        final StringBuilder result = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            // 随机选择字符池中的一个字符
            int index = SECURE_RANDOM.nextInt(CHAR_POOL.length());
            result.append(CHAR_POOL.charAt(index));
        }

        return result.toString();
    }

    @NonNull
    public static String convertToTwoDigitHex(int number) {
        // 将整数转换为16进制字符串
        final String hexString = Integer.toHexString(number);

        // 如果结果不足两位，则前面补充零
        return hexString.length() < 2 ? "0" + hexString : hexString;
    }

    /**
     * 获取时区
     */
    @NonNull
    public static String getTimeZone() {
        return getOrDefault(() -> TimeZone.getDefault().getID(), "");
    }

    /**
     * 获取 UA
     */
    public static String getUserAgent(@NonNull Context context) {
        return getOrNull(() -> WebSettings.getDefaultUserAgent(context));
    }

    /**
     * @return 获取设备唯一标识码
     */
    @NonNull
    public static String getDeviceId() {
        final String deviceId = MetricsCache.getDeviceId();
        if (!TextUtils.isEmpty(deviceId)) {
            return deviceId;
        }

        final String androidId = getAndroidID();
        if (!TextUtils.isEmpty(androidId)) {
            return saveDeviceUdid(MetricsConstant.STR_UDID_PREFIX1, androidId);
        }
        return saveDeviceUdid(MetricsConstant.STR_UDID_PREFIX2, androidId);
    }

    @NonNull
    public static String getSystemVersion() {
        return getOrDefault(() -> Build.VERSION.RELEASE, "");
    }

    /**
     * @return 获取设备模型
     */
    @NonNull
    public static String getDeviceModel() {
        return getOrDefault(() -> {
            String model = Build.MODEL;
            if (model != null) {
                model = model.trim().replaceAll("\\s*", "");
            } else {
                model = "";
            }
            //noinspection CharsetObjectCanBeUsed
            return URLEncoder.encode(model, MetricsConstant.ENC_UTF_8);
        }, "");
    }

    /**
     * @return 获取应用版本号
     */
    @NonNull
    public static String getAppVersionName(@NonNull Context context) {
        return getOrDefault(() -> {
            final PackageManager packageManager = context.getPackageManager();
            final String packageName = context.getPackageName();
            final PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
            return packageInfo.versionName;
        }, MetricsConstant.STR_DEFAULT_VERSION);
    }

    @Nullable
    public static <T> T getOrNull(@NonNull MetricsSupplier<T> supplier) {
        T t = null;
        try {
            t = supplier.get();
        } catch (Throwable ignored) {
        }
        return t;
    }

    @NonNull
    public static <T> T getOrDefault(@NonNull MetricsSupplier<T> supplier, @NonNull T defaultValue) {
        final T t = getOrNull(supplier);
        return t != null ? t : defaultValue;
    }

    @NonNull
    @SuppressLint("HardwareIds")
    private static String getAndroidID() {
        final Context context = MetricsManager.getInstance().getContext();
        if (context == null) {
            return "";
        }
        return getOrDefault(() -> {
            final String androidId = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ANDROID_ID
            );
            return (MetricsConstant.STR_ANDROID_ID.equals(androidId)) ? "" : androidId;
        }, "");
    }

    @NonNull
    private static String saveDeviceUdid(String prefix, String id) {
        final String udid = getDeviceUdid(prefix, id);
        MetricsCache.updateDeviceId(udid);
        return udid;
    }

    @NonNull
    private static String getDeviceUdid(String prefix, String id) {
        if (TextUtils.isEmpty(id)) {
            return prefix + UUID.randomUUID().toString().replace("-", "");
        }
        return prefix + UUID.nameUUIDFromBytes(id.getBytes()).toString().replace("-", "");
    }
}
