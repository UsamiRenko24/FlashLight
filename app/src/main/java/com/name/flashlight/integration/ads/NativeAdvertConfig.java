package com.name.flashlight.integration.ads;

public class NativeAdvertConfig {
    private final String placementId;           // 广告位 id
    private final int advertWidth;              // 广告视图宽度
    private final int advertHeight;             // 广告视图高度
    private boolean isShowAfterLoaded;          // 广告加载完毕后直接展示
    private boolean isPreloadAdvert;            // 是否自动预加载广告（触发时机：广告 show）
    private boolean isPreloadWithoutReady;      // 自动预加载广告时忽略当前广告缓存（配合 isPreloadAdvert 使用）

    public NativeAdvertConfig(String placementId, int advertWidth, int advertHeight) {
        this.placementId = placementId;
        this.advertWidth = advertWidth;
        this.advertHeight = advertHeight;
    }

    public String getPlacementId() {
        return placementId;
    }

    public int getAdvertWidth() {
        return advertWidth;
    }

    public int getAdvertHeight() {
        return advertHeight;
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
}