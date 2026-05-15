package com.name.flashlight.integration.metrics;

public final class MetricsConstant {
    // 配置常量
    public static final String STR_ANDROID = "android";
    public static final String STR_DEFAULT_VERSION = "1.0.0";
    public static final String STR_ANDROID_ID = "9774d56d682e549c";
    public static final String STR_UDID_PREFIX1 = "2";
    public static final String STR_UDID_PREFIX2 = "9";
    public static final String ENC_UTF_8 = "utf-8";

    // 缓存相关常量
    public static final String CACHE_NAME = "CACHE_NAME";
    public static final String CACHE_INFO = "CACHE_INFO";
    public static final String CACHE_REPORTING_INFO = "CACHE_REPORTING_INFO";
    public static final String CACHE_UA = "CACHE_UA";
    public static final String CACHE_REFERRER = "CACHE_REFERRER";
    public static final String CACHE_GUID = "CACHE_GUID";
    public static final String CACHE_LOCALE = "CACHE_LOCALE";
    public static final String CACHE_REPORT_GENERA_PREFIX = "CACHE_REPORT_GENERA_PREFIX";
    public static final String CACHE_REPORT_FAIL_COUNT = "CACHE_REPORT_FAIL_COUNT";

    // 类型常量
    public static final String TYPE_VER = "ver";
    public static final String TYPE_BS = "bs";
    public static final String TYPE_UA = "ua";
    public static final String TYPE_REFERRER = "referrer";
    public static final String TYPE_EVENT = "event";

    // JSON键常量
    public static final String KEY_TYPE = "type";
    public static final String KEY_TS = "ts";
    public static final String KEY_DATA = "data";
    public static final String KEY_KEY = "Key";
    public static final String KEY_VAL = "Val";
    public static final String KEY_EXTEND = "Extend";
    public static final String KEY_VER = "ver";
    public static final String KEY_BID = "bid";
    public static final String KEY_DEVICE = "device";
    public static final String KEY_SYSTEM_VER = "systemver";
    public static final String KEY_GUID = "guid";
    public static final String KEY_LOCALE = "locale";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_ZONE = "zone";
    public static final String KEY_CHL = "chl";
    public static final String KEY_UA = "ua";
    public static final String KEY_TIMESTAMP = "timestamp";
    public static final String KEY_REFERRER = "referrer";
    public static final String KEY_TOKEN = "token";
    public static final String KEY_TIME_ZONE = "time_zone";
    public static final String KEY_CODE = "code";
    public static final String KEY_EVENT_DAY = "day";
    public static final String KEY_EVENT_VAL = "val";
    public static final String KEY_EVENT_SUM = "sum";

    // 加解密相关常量
    public static final String AES_CBC_PKCS7 = "AES/CBC/PKCS7Padding";
    public static final String ALGO_AES = "AES";
    public static final String ALGO_DES = "DES";

    // 队列和批处理配置常量
    public static final int MAX_QUEUE_SIZE = 1000; // 队列最大大小
    /** 持久化待上报缓存（CACHE_INFO）中 JSONArray 最多保留的 JSONObject 条数；与队列上限一致，超限时保留前缀（旧数据） */
    public static final int CACHE_MAX_ITEM_COUNT = MAX_QUEUE_SIZE;
    public static final int MAX_BATCH_SIZE = 8;   // 上报成功/默认批量最大条数
    public static final int FAILED_MAX_BATCH_SIZE = 5; // 上报失败后下次批量最大条数

    // 定时器配置常量
    public static final long MIN_REPORT_INTERVAL_MILLIS = 5_000L; // 最小上报间隔（毫秒）
    public static final long[] REPORT_INTERVAL_BACKOFF_MILLIS = new long[]{
            5_000L,   // 5秒
            15_000L,  // 15秒
            30_000L,  // 30秒
            60_000L,  // 60秒
            120_000L, // 2分钟
            300_000L, // 5分钟
            600_000L  // 10分钟
    };

    // 随机字符串长度配置常量
    public static final int RANDOM_STRING_START_LENGTH = 6;   // 随机字符串开始长度
    public static final int RANDOM_STRING_END_LENGTH = 5;     // 随机字符串结束长度
    public static final int RANDOM_STRING_PREFIX_LENGTH = 4;  // 随机字符串前缀长度
}