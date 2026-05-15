package com.name.flashlight.integration.ads;

import androidx.annotation.NonNull;

public class RewardAdvertConfig {
    private final String placementId;               // 广告位 id
    private boolean isShowAfterLoaded;              // 广告加载完毕后直接展示
    private boolean isPreloadAdvert;                // 是否自动预加载广告（触发时机：广告 show）
    private boolean isPreloadWithoutReady = true;   // 自动预加载广告时忽略当前广告缓存（配合 isPreloadAdvert 使用）
    private long timeoutMillis = 20 * 1000;         // 广告加载超时时间戳，默认为20S

    public RewardAdvertConfig(@NonNull String placementId) {
        this.placementId = placementId;
    }

    public String getPlacementId() {
        return placementId;
    }

    public boolean isShowAfterLoaded() {
        return isShowAfterLoaded;
    }

    public void setShowAfterLoaded(boolean showAfterLoaded) {
        isShowAfterLoaded = showAfterLoaded;
    }

    public boolean isPreloadAdvert() {
        return isPreloadAdvert;
    }

    public void setPreloadAdvert(boolean preloadAdvert) {
        isPreloadAdvert = preloadAdvert;
    }

    public boolean isPreloadWithoutReady() {
        return isPreloadWithoutReady;
    }

    public void setPreloadWithoutReady(boolean preloadWithoutReady) {
        isPreloadWithoutReady = preloadWithoutReady;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }
}