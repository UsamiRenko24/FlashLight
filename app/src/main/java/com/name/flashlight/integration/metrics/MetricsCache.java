package com.name.flashlight.integration.metrics;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

public final class MetricsCache {

    /**
     * 获取常规事件的各项数据
     * day：第几天（从 1 开始，跨天+1）
     * val：当天的计数
     * sum：累计总数
     * millis：最后一次触发的时间戳
     */
    @NonNull
    public static long[] getGeneraEventDataArr(@NonNull String eventName) {
        final String key = MetricsConstant.CACHE_REPORT_GENERA_PREFIX + "_" + eventName;
        String lastDataInfo = MetricsSpUtils.getInstance().getString(key, "");
        if (TextUtils.isEmpty(lastDataInfo)) {
            // 初始值，day 从 1 开始
            lastDataInfo = "1|0|0|0"; // day|val|sum|millis
        }
        final String[] lastDataArr = lastDataInfo.split("\\|");

        int lastDayNum = 1;
        int lastValNum = 0;
        int lastSumNum = 0;
        long lastSystemMillis = 0L;

        try {
            if (lastDataArr.length > 0) {
                lastDayNum = Integer.parseInt(lastDataArr[0]);
            }
        } catch (NumberFormatException ignored) {
        }

        try {
            if (lastDataArr.length > 1) {
                lastValNum = Integer.parseInt(lastDataArr[1]);
            }
        } catch (NumberFormatException ignored) {
        }

        try {
            if (lastDataArr.length > 2) {
                lastSumNum = Integer.parseInt(lastDataArr[2]);
            }
        } catch (NumberFormatException ignored) {
        }

        try {
            if (lastDataArr.length > 3) {
                lastSystemMillis = Long.parseLong(lastDataArr[3]);
            }
        } catch (NumberFormatException ignored) {
        }

        if (lastSystemMillis == 0) {
            lastSystemMillis = System.currentTimeMillis();
        }

        final boolean isToday = MetricsUtils.isToday(lastSystemMillis);

        if (!isToday) {
            lastDayNum += 1; // 跨了一天就 +1
            lastValNum = 0;  // 重置当天计数
        }

        lastValNum++;
        lastSumNum++;
        lastSystemMillis = System.currentTimeMillis();

        final long[] result = {lastDayNum, lastValNum, lastSumNum, lastSystemMillis};

        final String newDataInfo = lastDayNum + "|" + lastValNum + "|" + lastSumNum + "|" + lastSystemMillis;
        MetricsSpUtils.getInstance().putString(key, newDataInfo);

        return result;
    }

    /**
     * @return 获取地区
     */
    public static String getLocale() {
        return MetricsSpUtils.getInstance().getString(MetricsConstant.CACHE_LOCALE, null);
    }

    public static void updateLocale(@NonNull String value) {
        MetricsSpUtils.getInstance().putString(MetricsConstant.CACHE_LOCALE, value);
    }

    /**
     * 获取设备唯一标识码
     */
    @Nullable
    public static String getDeviceId() {
        return MetricsSpUtils.getInstance().getString(MetricsConstant.CACHE_GUID, null);
    }

    /**
     * 更新设备唯一标识码
     */
    public static void updateDeviceId(@NonNull String value) {
        MetricsSpUtils.getInstance().putString(MetricsConstant.CACHE_GUID, value);
    }

    /**
     * 是否上报 Referrer
     */
    public static boolean isReportReferrer() {
        final boolean canReport = MetricsSpUtils.getInstance().getBoolean(MetricsConstant.CACHE_REFERRER, true);
        if (canReport) {
            MetricsSpUtils.getInstance().putBoolean(MetricsConstant.CACHE_REFERRER, false);
        }
        return canReport;
    }

    /**
     * 是否上报 UA
     */
    public static boolean isReportUA() {
        final boolean canReport = MetricsSpUtils.getInstance().getBoolean(MetricsConstant.CACHE_UA, true);
        if (canReport) {
            MetricsSpUtils.getInstance().putBoolean(MetricsConstant.CACHE_UA, false);
        }
        return canReport;
    }

    public static void updateItemInfos(@NonNull JSONArray jsonArray) {
        final int max = MetricsConstant.CACHE_MAX_ITEM_COUNT;
        try {
            int objectCount = 0;
            for (int i = 0; i < jsonArray.length(); i++) {
                if (jsonArray.optJSONObject(i) != null && ++objectCount > max) {
                    persistCacheInfoLimited(jsonArray, max);
                    return;
                }
            }
            try {
                final String full = jsonArray.toString();
                MetricsSpUtils.getInstance().putString(MetricsConstant.CACHE_INFO, full);
            } catch (Throwable t) {
                persistCacheInfoLimited(jsonArray, max);
            }
        } catch (Throwable t) {
            persistCacheInfoLimited(jsonArray, max);
        }
    }

    /**
     * 截断后写入 CACHE_INFO；序列化仍失败则写入空数组
     */
    private static void persistCacheInfoLimited(@NonNull JSONArray jsonArray, int max) {
        try {
            MetricsSpUtils.getInstance().putString(MetricsConstant.CACHE_INFO,
                    limitArrayItemCount(jsonArray, max).toString());
        } catch (Throwable ignored) {
        }
    }

    /**
     * 仅保留按顺序的前 maxItems 个非 null 的 JSONObject（与 MetricsManager 合并顺序下的「旧数据在前」一致）
     */
    @SuppressWarnings("SameParameterValue")
    @NonNull
    private static JSONArray limitArrayItemCount(@NonNull JSONArray original, int maxItems) {
        final JSONArray limited = new JSONArray();
        if (maxItems <= 0) {
            return limited;
        }
        try {
            for (int i = 0; i < original.length() && limited.length() < maxItems; i++) {
                final JSONObject obj = original.optJSONObject(i);
                if (obj != null) {
                    limited.put(obj);
                }
            }
        } catch (Throwable ignored) {
        }
        return limited;
    }

    @NonNull
    public static JSONArray getItemInfos() {
        final String json = MetricsSpUtils.getInstance().getString(MetricsConstant.CACHE_INFO, null);
        if (TextUtils.isEmpty(json)) {
            return new JSONArray();
        }
        try {
            return new JSONArray(json);
        } catch (Throwable e) {
            return new JSONArray();
        }
    }

    public static void clearItemInfos() {
        MetricsSpUtils.getInstance().putString(MetricsConstant.CACHE_INFO, null);
    }

    /**
     * 更新正在上报的数据缓存
     */
    public static void updateReportingInfos(@NonNull JSONArray jsonArray) {
        try {
            final String json = jsonArray.toString();
            MetricsSpUtils.getInstance().putString(MetricsConstant.CACHE_REPORTING_INFO, json);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 获取正在上报的数据缓存
     */
    @NonNull
    public static JSONArray getReportingInfos() {
        final String json = MetricsSpUtils.getInstance().getString(MetricsConstant.CACHE_REPORTING_INFO, null);
        if (TextUtils.isEmpty(json)) {
            return new JSONArray();
        }
        try {
            return new JSONArray(json);
        } catch (Throwable e) {
            return new JSONArray();
        }
    }

    /**
     * 清空正在上报的数据缓存
     */
    public static void clearReportingInfos() {
        MetricsSpUtils.getInstance().putString(MetricsConstant.CACHE_REPORTING_INFO, null);
    }

    /**
     * 获取上报失败次数（用于退避重试）
     */
    public static int getReportFailCount() {
        return Math.max(0, MetricsSpUtils.getInstance().getInt(MetricsConstant.CACHE_REPORT_FAIL_COUNT, 0));
    }

    /**
     * 更新上报失败次数（用于退避重试）
     */
    public static void updateReportFailCount(int value) {
        MetricsSpUtils.getInstance().putInt(MetricsConstant.CACHE_REPORT_FAIL_COUNT, Math.max(0, value));
    }
}
