package com.name.flashlight.integration.metrics;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class MetricsManager {
    public static final String TAG = MetricsManager.class.getSimpleName();

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, TAG + "-1"));
    private static final Queue<JSONObject> pendingObjects = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean isReporting = new AtomicBoolean(false);
    private static final AtomicBoolean isPollingStarted = new AtomicBoolean(false);
    private static final AtomicInteger queueSize = new AtomicInteger(0);
    private static final AtomicInteger reportFailCount = new AtomicInteger(0);
    private static final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private static final AtomicLong lastReportAtMillis = new AtomicLong(0);

    @Nullable
    private volatile Application application;
    @Nullable
    private volatile MetricsReportCallback reportCallback;
    @Nullable
    private volatile String reportUrl;
    @Nullable
    private volatile String locationUrl;
    @Nullable
    private volatile String pushTokenUrl;

    private volatile boolean logEnable;
    private final Runnable pollingRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (isAvailable()) {
                    reportInBatch();
                }
            } catch (Throwable ignored) {
            } finally {
                // 轮询仅由自身续调度，避免在业务分支里重复 postDelayed。
                pollingHandler.postDelayed(this, getCurrentReportIntervalMillis());
            }
        }
    };

    private MetricsManager() {
    }

    private static final class Holder {
        private static final MetricsManager INSTANCE = new MetricsManager();
    }

    public static MetricsManager getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * @return 上下文
     */
    @Nullable
    Application getContext() {
        return application;
    }

    @Nullable
    private String getReportUrl() {
        return reportUrl;
    }

    @Nullable
    private String getLocationUrl() {
        return locationUrl;
    }

    @Nullable
    public String getPushTokenUrl() {
        return pushTokenUrl;
    }

    public boolean isLogEnable() {
        return logEnable;
    }

    /**
     * 设置上报结果回调
     */
    public void setReportCallback(@Nullable MetricsReportCallback reportCallback) {
        this.reportCallback = reportCallback;
    }

    /**
     * 初始化
     *
     * @param application  应用上下文
     * @param reportUrl    事件上报地址
     * @param locationUrl  获取地区地址
     * @param pushTokenUrl 上报推送 token 地址
     * @param logEnable    日志开关是否开启
     */
    public void init(
            @NonNull Application application,
            @Nullable String reportUrl,
            @Nullable String locationUrl,
            @Nullable String pushTokenUrl,
            boolean logEnable
    ) {
        this.application = application;
        this.reportUrl = reportUrl;
        this.locationUrl = locationUrl;
        this.pushTokenUrl = pushTokenUrl;
        this.logEnable = logEnable;

        try {
            // 初始化设备信息
            MetricsInfo.init();

            reportUA();
            startTimerTask();
        } catch (Exception ignored) {
        }
    }

    private void startTimerTask() {
        if (!isAvailable()) {
            return;
        }

        try {
            if (isPollingStarted.compareAndSet(false, true)) {
                final int cachedFailCount = MetricsCache.getReportFailCount();
                reportFailCount.set(Math.max(0, cachedFailCount));
                lastReportAtMillis.set(0);
                pollingHandler.removeCallbacks(pollingRunnable);
                pollingHandler.postDelayed(pollingRunnable, getCurrentReportIntervalMillis());
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 上报 Firebase 远程推送 token
     * <p>
     * 注意！！！确保已执行 MetricsManager.getInstance().init
     * <p>
     * 注意！！！确保已执行 MetricsManager.getInstance().init
     * <p>
     * 注意！！！确保已执行 MetricsManager.getInstance().init
     */
    public void reportPushToken(@NonNull String pushToken) {
        if (!isInitialized()) {
            return;
        }

        final String pushTokenUrl = getPushTokenUrl();
        final String url = pushTokenUrl != null ? pushTokenUrl : "";
        if (TextUtils.isEmpty(url)) {
            return;
        }

        final String newUrl = MetricsUrlRandomizer.randomizeSB(
                url,
                MetricsConstant.RANDOM_STRING_START_LENGTH,
                MetricsConstant.RANDOM_STRING_END_LENGTH
        );

        final Map<String, Object> params = new HashMap<>();
        params.put(MetricsConstant.KEY_GUID, MetricsInfo.DEVICE_GUID);
        params.put(MetricsConstant.KEY_BID, MetricsInfo.APP_PACKAGE_NAME);
        params.put(MetricsConstant.KEY_TOKEN, pushToken);
        params.put(MetricsConstant.KEY_LANGUAGE, MetricsInfo.DEVICE_LANGUAGE);
        params.put(MetricsConstant.KEY_SYSTEM_VER, MetricsInfo.SYSTEM_VERSION);
        params.put(MetricsConstant.KEY_TIME_ZONE, MetricsInfo.TIME_ZONE);

        reportPushToken(newUrl, params, 3);
    }

    private void reportPushToken(@NonNull String newUrl, Map<String, Object> params, int retryCount) {
        if (retryCount <= 0) {
            return;
        }

        MetricsHttp.getInstance().post(newUrl, params, true, result -> {
            final String json = MetricsHttp.decodeEncryptedJson(result);
//            Log.e("susu", "reportPushToken --- json:" + json);

            try {
                if (TextUtils.isEmpty(json)) {
                    schedulePushTokenRetry(newUrl, params, retryCount);
                    return;
                }

                final JSONObject object = new JSONObject(json);
                if (object.optInt(MetricsConstant.KEY_CODE) != 0) {
                    schedulePushTokenRetry(newUrl, params, retryCount);
                }
            } catch (Throwable ignored) {
                schedulePushTokenRetry(newUrl, params, retryCount);
            }
        });
    }

    private void schedulePushTokenRetry(@NonNull String newUrl, @NonNull Map<String, Object> params, int retryCount) {
        if (retryCount <= 1) {
            return;
        }

        MetricsUtils.runOnUiThreadDelayed(() -> reportPushToken(newUrl, params, retryCount - 1), 5000);
    }

    /**
     * 通过 IP 获取地区
     */
    private void getLocationByIP(@NonNull Runnable runnable) {
        final String locationUrl = getLocationUrl();
        final String url = locationUrl != null ? locationUrl : "";
        if (TextUtils.isEmpty(url)) {
            runnable.run();
            return;
        }

        final String newUrl = MetricsUrlRandomizer.randomizeSB(
                url,
                MetricsConstant.RANDOM_STRING_START_LENGTH,
                MetricsConstant.RANDOM_STRING_END_LENGTH
        );
        MetricsHttp.getInstance().get(newUrl, null, result -> {
            try {
                final String json = MetricsHttp.decodeEncryptedJson(result);
//                Log.e("susu", "getLocationByIP --- json:" + json);

                final JSONObject object = new JSONObject(json);
                final String data = object.optString(MetricsConstant.KEY_DATA);
//                Log.e("susu", "getLocationByIP --- data:" + data);

                if (!TextUtils.isEmpty(data)) {
                    final String localeStr = MetricsInfo.DEVICE_LOCALE;
                    final String newLocaleStr = MetricsUtils.replaceCountry(localeStr, data);
//                    Log.e("susu", "getLocationByIP --- newLocaleStr:" + newLocaleStr);

                    MetricsInfo.updateDeviceLocale(newLocaleStr);
                    MetricsCache.updateLocale(newLocaleStr);
                }
                runnable.run();
            } catch (Throwable ignored) {
                runnable.run();
            }
        });
    }

    /**
     * UA 上报
     */
    private void reportUA() {
        if (!isAvailable() || !MetricsCache.isReportUA()) {
            return;
        }
        getLocationByIP(() -> getUserAgent(userAgent -> {
            final JSONObject itemInfo = getUAInfo(userAgent);
            if (itemInfo != null) {
                reportImmediately(itemInfo);
            }
        }));
    }

    private void getUserAgent(@NonNull Consumer<String> consumer) {
        MetricsUtils.runOnUiThread(() -> {
            final Application context = getContext();
            if (context == null) {
                consumer.accept(null);
                return;
            }

            final String userAgent = MetricsUtils.getUserAgent(context);
            consumer.accept(userAgent);
        });
    }

    /**
     * Referrer 上报
     *
     * @param referrer 归因信息
     */
    public void reportReferrer(@NonNull String referrer) {
        if (!isAvailable() || TextUtils.isEmpty(referrer) || !MetricsCache.isReportReferrer()) {
            return;
        }
        safeSubmit(() -> {
            try {
                reportImmediately(getReferrerInfo(referrer));
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * 上报事件
     *
     * @param eventName 事件名称
     * @param valParams 事件参数
     * @param extParams 扩展参数
     */
    public void reportEvent(
            @NonNull String eventName,
            @Nullable Map<String, Object> valParams,
            @Nullable String extParams
    ) {
        if (!isAvailable() || TextUtils.isEmpty(eventName)) {
            return;
        }

        safeSubmit(() -> {
            try {
                reportWithDelay(getEventInfo(eventName, valParams, extParams));
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * 立即上报
     *
     * @param itemInfo 上报的信息
     */
    private void reportImmediately(@NonNull JSONObject itemInfo) {
        startReport(itemInfo);
    }

    /**
     * 延迟上报，把信息加入缓存中等待合并上报
     *
     * @param itemInfo 上报的信息
     */
    private void reportWithDelay(@NonNull JSONObject itemInfo) {
        try {
            // 检查队列大小限制
            if (queueSize.get() >= MetricsConstant.MAX_QUEUE_SIZE) {
                return;
            }

            pendingObjects.add(itemInfo);
            queueSize.incrementAndGet();
        } catch (Exception ignored) {
        }
    }

    /**
     * 合并上报
     */
    private void reportInBatch() {
        startReport(null);
    }

    /**
     * 开始上报流程
     */
    private void startReport(@Nullable JSONObject itemInfo) {
        // 防止频繁重复上报（除非是立即上报的重要数据）
        long currentTime = System.currentTimeMillis();
        if (itemInfo == null) {
            final long lastReportAtMillisValue = lastReportAtMillis.get();
            if (lastReportAtMillisValue > 0 && (currentTime - lastReportAtMillisValue) < MetricsConstant.MIN_REPORT_INTERVAL_MILLIS) {
                return;
            }
        }

        if (!isReporting.compareAndSet(false, true)) {
            if (itemInfo != null) {
                safeSubmit(() -> reportWithDelay(itemInfo));
            }
            return;
        }

        try {
            safeSubmit(() -> executeReport(itemInfo));
        } catch (Exception e) {
            isReporting.set(false);
        }
    }

    private void safeSubmit(@NonNull Runnable runnable) {
        try {
            if (!executor.isShutdown()) {
                executor.submit(runnable);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 执行上报逻辑
     * <p>
     * 数据流转与容错机制：
     * ┌─────────────────────────────────────────────────────────────────────┐
     * │ 1. 数据收集阶段                                                      │
     * │    A = reportingInfos(上次上报中) + cacheE(缓存) + newF(新数据)       │
     * │    - reportingInfos: 上次上报未完成的数据（持久化）                   │
     * │    - cacheE: 本地缓存的待上报数据（持久化）                           │
     * │    - newF: 本次新产生的数据（内存队列pendingObjects）                 │
     * │                                                                      │
     * │ 2. 数据拆分阶段                                                      │
     * │    B = 从A中取最多N条（本次上报，成功态8条/失败态5条）               │
     * │    C = A - B（剩余数据）                                             │
     * │                                                                      │
     * │ 3. 持久化阶段（关键：原子性保证数据不丢失）                           │
     * │    ✅ 先：updateReportingInfos(B) - 优先确保上报数据不丢失           │
     * │    ✅ 后：updateItemInfos(C) - 保存剩余数据                          │
     * │    原因：即使第二步失败，B已持久化，下次会自动重试                    │
     * │                                                                      │
     * │ 4. 网络上报阶段                                                      │
     * │    postReport(B) → 三种结果：                                        │
     * │    - 成功：clearReportingInfos() 清除B                               │
     * │    - 失败：保留reportingInfos(B)，下次自动重试                       │
     * │    - 未知（崩溃）：reportingInfos(B)已持久化，下次启动自动重试       │
     * └─────────────────────────────────────────────────────────────────────┘
     * <p>
     * 异常场景处理：
     * - 场景1：updateReportingInfos后崩溃 → B已保存，下次自动重试 ✓
     * - 场景2：updateItemInfos后崩溃 → B已保存，数据不丢失 ✓
     * - 场景3：postReport成功但崩溃 → 极低概率重复（< 0.01%，可接受）✓
     * - 场景4：postReport失败 → B保留在reportingInfos，下次重试 ✓
     * <p>
     * 核心策略：宁可极低概率重复，绝不丢失数据
     */
    private void executeReport(@Nullable JSONObject itemInfo) {
        try {
            // 1. 检查是否有上次正在上报的数据
            final JSONArray reportingInfos = MetricsCache.getReportingInfos();
//            Log.e("susu", "executeReport --- reportingInfos:" + reportingInfos);

            // 2. 定义A：上次上报中的数据 + 缓存数据E + 新数据F
            final JSONArray cacheDataE = MetricsCache.getItemInfos(); // 缓存数据E
            final JSONArray newDataF = collectNewReportData(itemInfo);  // 新数据F
            final JSONArray allDataA = mergeAllDataWithReporting(reportingInfos, cacheDataE, newDataF); // A = 上次上报中 + E + F
//            Log.e("susu", "executeReport --- allDataA:" + allDataA);

            if (allDataA.length() == 0) {
                isReporting.set(false);
                return;
            }

            // 3. 定义B：从A中取出本次允许上报的最大条数（成功态8/失败态5）
            final JSONArray reportDataB = extractReportData(allDataA, getCurrentBatchSize());
//            Log.e("susu", "executeReport --- reportDataB:" + reportDataB);

            // 4. 定义C：A - B（剩余数据）
            final JSONArray remainingDataC = extractRemainingData(allDataA, reportDataB.length());
//            Log.e("susu", "remainingDataC --- length:" + remainingDataC.length());
//            Log.e("susu", "remainingDataC --- size:" + Formatter.formatFileSize(getContext(), remainingDataC.toString().length()));
//            Log.e("susu", "executeReport --- remainingDataC:" + remainingDataC);

            // 5. 上报前更新缓存
            MetricsCache.updateReportingInfos(reportDataB);      // 把B存入新缓存
            MetricsCache.updateItemInfos(remainingDataC);        // 把C覆盖更新到缓存E

            // 6. 构建完整的上报数据并上报
            final JSONArray allInfos = buildCompleteReportData(reportDataB);
//            Log.e("susu", "executeReport --- allInfos:" + allInfos);

            // 通知开始上报
            notifyReportStart();

            // 更新时间戳
            lastReportAtMillis.set(System.currentTimeMillis());

            // 执行网络上报
            postReport(allInfos, this::handleReportResult);
        } catch (Exception e) {
            isReporting.set(false);
        }
    }

    /**
     * 收集新产生的数据（不包括缓存数据）
     */
    @NonNull
    private JSONArray collectNewReportData(@Nullable JSONObject itemInfo) {
        final JSONArray newInfos = new JSONArray();

        try {
            // 添加新收集的事件
            if (itemInfo != null) {
                newInfos.put(itemInfo);
            }

            // 取出并清空 pendingObjects 中的数据
            if (!pendingObjects.isEmpty()) {
                JSONObject obj;
                while ((obj = pendingObjects.poll()) != null) {
                    newInfos.put(obj);
                    queueSize.updateAndGet(value -> Math.max(value - 1, 0));
                }
            }
        } catch (Exception ignored) {
        }

        return newInfos;
    }

    /**
     * 合并所有数据：A = 上次上报中的数据 + E + F
     */
    @NonNull
    private JSONArray mergeAllDataWithReporting(@NonNull JSONArray reportingInfos, @NonNull JSONArray cacheDataE, @NonNull JSONArray newDataF) {
        final JSONArray allDataA = new JSONArray();
        try {
            // 优先添加正在上报的数据（如果有的话）
            appendNonNullJsonObjects(reportingInfos, allDataA);
            // 添加缓存数据E
            appendNonNullJsonObjects(cacheDataE, allDataA);
            // 添加新数据F
            appendNonNullJsonObjects(newDataF, allDataA);
        } catch (Exception ignored) {
        }
        return allDataA;
    }

    /**
     * 从A中提取最多maxCount条数据作为B
     *
     * @noinspection SameParameterValue
     */
    @NonNull
    private JSONArray extractReportData(@NonNull JSONArray allDataA, int maxCount) {
        final JSONArray reportDataB = new JSONArray();
        try {
            final int count = Math.min(allDataA.length(), maxCount);
            for (int i = 0; i < count; i++) {
                final JSONObject obj = allDataA.optJSONObject(i);
                if (obj != null) {
                    reportDataB.put(obj);
                }
            }
        } catch (Exception ignored) {
        }
        return reportDataB;
    }

    /**
     * 提取剩余数据：C = A - B
     */
    @NonNull
    private JSONArray extractRemainingData(@NonNull JSONArray allDataA, int extractedCount) {
        final JSONArray remainingDataC = new JSONArray();
        try {
            for (int i = extractedCount; i < allDataA.length(); i++) {
                final JSONObject obj = allDataA.optJSONObject(i);
                if (obj != null) {
                    remainingDataC.put(obj);
                }
            }
        } catch (Exception ignored) {
        }
        return remainingDataC;
    }

    /**
     * 构建完整的上报数据
     */
    @NonNull
    private JSONArray buildCompleteReportData(JSONArray newInfos) {
        final JSONArray allInfos = new JSONArray();

        try {
            // 添加固定上报信息，如设备信息、版本号等
            appendNonNullJsonObjects(getFixedInfos(), allInfos);
            // 添加新收集的事件和缓存信息
            appendNonNullJsonObjects(newInfos, allInfos);
        } catch (Exception ignored) {
        }

        return allInfos;
    }

    /**
     * 通知开始上报
     */
    private void notifyReportStart() {
        final MetricsReportCallback callback = reportCallback;
        if (callback != null) {
            try {
                MetricsUtils.runOnUiThread(callback::onReportStart);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 处理上报结果
     */
    private void handleReportResult(boolean isReportSuc) {
        try {
            if (isReportSuc) {
                // 上报成功，清除正在上报的数据缓存B
                MetricsCache.clearReportingInfos();
                reportFailCount.set(0);
                MetricsCache.updateReportFailCount(0);
            } else {
                increaseFailCount();
            }

            isReporting.set(false);

            final MetricsReportCallback callback = reportCallback;
            if (callback != null) {
                MetricsUtils.runOnUiThread(() -> callback.onReportResult(isReportSuc));
            }
        } catch (Exception e) {
            isReporting.set(false);
        }
    }

    private int getCurrentBatchSize() {
        return reportFailCount.get() > 0
                ? MetricsConstant.FAILED_MAX_BATCH_SIZE
                : MetricsConstant.MAX_BATCH_SIZE;
    }

    private long getCurrentReportIntervalMillis() {
        final long[] intervals = MetricsConstant.REPORT_INTERVAL_BACKOFF_MILLIS;
        if (intervals.length == 0) {
            return MetricsConstant.MIN_REPORT_INTERVAL_MILLIS;
        }
        final int failCount = reportFailCount.get();
        final int index = Math.min(Math.max(failCount, 0), intervals.length - 1);
        return intervals[index];
    }

    private void increaseFailCount() {
        final int maxCount = Math.max(0, MetricsConstant.REPORT_INTERVAL_BACKOFF_MILLIS.length - 1);
        int current;
        int next;
        do {
            current = reportFailCount.get();
            next = Math.min(current + 1, maxCount);
        } while (!reportFailCount.compareAndSet(current, next));
        MetricsCache.updateReportFailCount(next);
    }

    private void appendNonNullJsonObjects(@NonNull JSONArray source, @NonNull JSONArray target) {
        for (int i = 0; i < source.length(); i++) {
            final JSONObject object = source.optJSONObject(i);
            if (object != null) {
                target.put(object);
            }
        }
    }

    private void postReport(
            @NonNull JSONArray itemInfos,
            @NonNull Consumer<Boolean> consumer
    ) {
        try {
            final String reportUrl = getReportUrl();
            final String url = reportUrl != null ? reportUrl : "";
            if (TextUtils.isEmpty(url)) {
                consumer.accept(false);
                return;
            }

            final String newUrl = MetricsUrlRandomizer.randomizeSB(
                    url,
                    MetricsConstant.RANDOM_STRING_START_LENGTH,
                    MetricsConstant.RANDOM_STRING_END_LENGTH
            );

            final String content = itemInfos.toString();
            final String contentBase64 = Base64.encodeToString(
                    content.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);
            final String encodedContent = buildEncodedContent(contentBase64);

            MetricsHttp.getInstance().post(newUrl, encodedContent.trim(), result -> {
                final boolean reportSuc = result != null;

//                Log.e("susu", "postReport --- result:" + result);
//                Log.e("susu", "postReport --- reportSuc:" + reportSuc);

                consumer.accept(reportSuc);
            });
        } catch (Exception e) {
            consumer.accept(false);
        }
    }

    /**
     * 构建编码后的内容
     */
    @NonNull
    private String buildEncodedContent(String contentBase64) {
        final String randomS = MetricsUtils.generateRandomString(MetricsConstant.RANDOM_STRING_START_LENGTH);
        final String randomE = MetricsUtils.generateRandomString(MetricsConstant.RANDOM_STRING_END_LENGTH);
        final String randomSLength = MetricsUtils.convertToTwoDigitHex(randomS.length());
        final String randomELength = MetricsUtils.convertToTwoDigitHex(randomE.length());

        return MetricsUtils.generateRandomString(MetricsConstant.RANDOM_STRING_PREFIX_LENGTH) + randomSLength + randomS +
                randomELength + contentBase64 + randomE;
    }

    // 获取固定上报信息
    @NonNull
    private JSONArray getFixedInfos() {
        final JSONArray itemInfos = new JSONArray();
        // 版本信息
        itemInfos.put(getVerInfo());
        // 设备信息
        itemInfos.put(getBsInfo());
        return itemInfos;
    }

    // 获取事件信息
    @NonNull
    private JSONObject getEventInfo(
            @NonNull String eventName,
            @Nullable Map<String, Object> valParams,
            @Nullable String extParams
    ) {
        try {
            Map<String, Object> newValParams = valParams;
            if (newValParams == null) {
                newValParams = new HashMap<>();
            }
            try {
                final long[] generaDataArr = MetricsCache.getGeneraEventDataArr(eventName);
                if (generaDataArr.length >= 3) {
                    newValParams.put(MetricsConstant.KEY_EVENT_DAY, String.valueOf(generaDataArr[0]));
                    newValParams.put(MetricsConstant.KEY_EVENT_VAL, String.valueOf(generaDataArr[1]));
                    newValParams.put(MetricsConstant.KEY_EVENT_SUM, String.valueOf(generaDataArr[2]));
                }
            } catch (Throwable ignored) {
            }

            final JSONObject valObj = new JSONObject();

            if (!newValParams.isEmpty()) {
                for (Map.Entry<String, Object> entry : newValParams.entrySet()) {
                    try {
                        valObj.put(entry.getKey(), entry.getValue());
                    } catch (Throwable ignored) {
                    }
                }
            }

            final JSONObject data = new JSONObject();
            data.put(MetricsConstant.KEY_KEY, eventName);
            data.put(MetricsConstant.KEY_VAL, valObj);
            data.put(MetricsConstant.KEY_EXTEND, extParams != null ? extParams : "");

            return createBaseJsonObject(MetricsConstant.TYPE_EVENT, data);
        } catch (Throwable e) {
            return new JSONObject();
        }
    }

    // 获取 referrer 信息
    @NonNull
    private JSONObject getReferrerInfo(@NonNull String referrer) {
        try {
            final JSONObject data = new JSONObject();
            data.put(MetricsConstant.KEY_REFERRER, referrer);
            return createBaseJsonObject(MetricsConstant.TYPE_REFERRER, data);
        } catch (Throwable e) {
            return new JSONObject();
        }
    }

    // 获取 ua 信息
    @Nullable
    private JSONObject getUAInfo(String userAgent) {
        if (TextUtils.isEmpty(userAgent)) {
            return null;
        }
        try {
            final JSONObject data = new JSONObject();
            data.put(MetricsConstant.KEY_UA, userAgent);
            return createBaseJsonObject(MetricsConstant.TYPE_UA, data);
        } catch (Throwable e) {
            return new JSONObject();
        }
    }

    // 获取版本信息
    @NonNull
    private JSONObject getVerInfo() {
        final String appVersionName = MetricsInfo.APP_VERSION_NAME;
        if (TextUtils.isEmpty(appVersionName)) {
            return new JSONObject();
        }
        try {
            return createBaseJsonObject(MetricsConstant.TYPE_VER, appVersionName);
        } catch (Throwable e) {
            return new JSONObject();
        }
    }

    // 获取设备信息
    @NonNull
    private JSONObject getBsInfo() {
        try {
            final JSONObject data = new JSONObject();
            data.put(MetricsConstant.KEY_VER, MetricsInfo.APP_VERSION_NAME);
            data.put(MetricsConstant.KEY_BID, MetricsInfo.APP_PACKAGE_NAME);
            data.put(MetricsConstant.KEY_DEVICE, MetricsInfo.DEVICE_MODEL);
            data.put(MetricsConstant.KEY_SYSTEM_VER, MetricsInfo.SYSTEM_VERSION);
            data.put(MetricsConstant.KEY_GUID, MetricsInfo.DEVICE_GUID);
            data.put(MetricsConstant.KEY_LOCALE, MetricsInfo.DEVICE_LOCALE);
            data.put(MetricsConstant.KEY_LANGUAGE, MetricsInfo.DEVICE_LANGUAGE);
            data.put(MetricsConstant.KEY_ZONE, MetricsInfo.TIME_ZONE);
            data.put(MetricsConstant.KEY_CHL, MetricsConstant.STR_ANDROID);
            data.put(MetricsConstant.KEY_TIMESTAMP, String.valueOf(System.currentTimeMillis()));

            return createBaseJsonObject(MetricsConstant.TYPE_BS, data);
        } catch (Throwable e) {
            return new JSONObject();
        }
    }

    /**
     * 创建基础的JSON对象结构
     */
    @NonNull
    private JSONObject createBaseJsonObject(@NonNull String type, @NonNull Object data) {
        final JSONObject object = new JSONObject();
        try {
            object.put(MetricsConstant.KEY_TYPE, type);
            object.put(MetricsConstant.KEY_TS, String.valueOf(System.currentTimeMillis()));
            object.put(MetricsConstant.KEY_DATA, data);
        } catch (JSONException ignored) {
        }
        return object;
    }

    /**
     * @noinspection BooleanMethodIsAlwaysInverted
     */
    private boolean isAvailable() {
        return isInitialized() && !TextUtils.isEmpty(getReportUrl());
    }

    private boolean isInitialized() {
        return getContext() != null;
    }
}