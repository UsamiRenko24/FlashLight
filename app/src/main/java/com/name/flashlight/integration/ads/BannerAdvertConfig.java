package com.name.flashlight.integration.ads;

import androidx.annotation.NonNull;

public class BannerAdvertConfig {
    private final String placementId;   // 广告位 id
    private final float advertWHRatio;  // 广告视图宽高比例
    private boolean reloadAdvert;       // 是否重新加载广告

    public BannerAdvertConfig(@NonNull String placementId, float advertWHRatio) {
        this.placementId = placementId;
        this.advertWHRatio = advertWHRatio;
    }

    public String getPlacementId() {
        return placementId;
    }

    public float getAdvertWHRatio() {
        return advertWHRatio;
    }

    public boolean isReloadAdvert() {
        return reloadAdvert;
    }

    public void setReloadAdvert(boolean reloadAdvert) {
        this.reloadAdvert = reloadAdvert;
    }
}
