package com.name.flashlight.integration.ads;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LifecycleOwner;

import com.secmtp.sdk.core.api.ATAdInfo;
import com.secmtp.sdk.core.api.AdError;
import com.secmtp.sdk.interstitial.api.ATInterstitial;
import com.secmtp.sdk.interstitial.api.ATInterstitialListener;

public class InsertAdvert extends BaseAdvert implements ATInterstitialListener, SharedLoadOwner {
    private static final PreloadOwnerRegistry<InsertAdvert> PRELOAD_OWNERS = new PreloadOwnerRegistry<>();
    private static final int SHOW_TIMEOUT_MILLIS = 5 * 1000;

    @NonNull
    private final LifecycleOwner lifecycleOwner;
    @NonNull
    private final InsertAdvertConfig advertConfig;
    @Nullable
    private FragmentActivity fragmentActivity;
    @Nullable
    private InsertAdvertCallback advertCallback;
    @Nullable
    private ATInterstitial atInterstitial;

    private boolean isPreloadRequest = false;
    private boolean advertLoadFailed = false;
    private boolean advertLoaded = false;
    private boolean released = false;

    public InsertAdvert(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull InsertAdvertConfig advertConfig
    ) {
        this(lifecycleOwner, advertConfig, null);
    }

    public InsertAdvert(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull InsertAdvertConfig advertConfig,
            @Nullable InsertAdvertCallback advertCallback
    ) {
        this.lifecycleOwner = lifecycleOwner;
        this.advertConfig = advertConfig;
        this.advertCallback = advertCallback;
        this.fragmentActivity = AdvertLifeUtils.getContext(lifecycleOwner);
        initializeLifecycle();
    }

    private void initializeLifecycle() {
        if (fragmentActivity == null) {
            return;
        }
        PlacementInterstitialAdManager.attachOwner(lifecycleOwner, getPlacementId(), this);
        AdvertLifeUtils.launchWhenDestroy(lifecycleOwner, this::onAdvertRelease);
        if (advertCallback != null) {
            advertCallback.onAdvertStart(this);
        }
    }

    @Override
    public boolean isAdvertReady() {
        return PlacementInterstitialAdManager.isReady(lifecycleOwner, getPlacementId());
    }

    @Override
    public boolean isAdvertLoading() {
        return PlacementInterstitialAdManager.isLoading(lifecycleOwner, getPlacementId());
    }

    @NonNull
    @Override
    protected String getPlacementId() {
        return advertConfig.getPlacementId();
    }

    @Override
    protected void loadAdvert() {
        loadAdvert(false);
    }

    private void loadAdvert(boolean isPreloadAdvert) {
        isPreloadRequest = isPreloadAdvert;
        advertLoadFailed = false;
        advertLoaded = false;

        if (fragmentActivity == null) {
            onAdvertLoadFail(null);
            return;
        }

        setTimeout(advertConfig.getTimeoutMillis(), () -> {
            onAdvertLoadFail(null);
            onAdvertRelease();
        });

        final boolean forceReload = isPreloadAdvert && advertConfig.isPreloadWithoutReady();
        PlacementInterstitialAdManager.requestLoad(lifecycleOwner, getPlacementId(), this, forceReload);
    }

    @Override
    public void onAdvertRelease() {
        if (released) return;
        released = true;
        super.onAdvertRelease();

        fragmentActivity = null;
        advertCallback = null;
        if (atInterstitial != null) {
            try {
                atInterstitial.setAdListener(null);
                atInterstitial.setAdDownloadListener(null);
                atInterstitial.setAdSourceStatusListener(null);
            } catch (Throwable ignored) {
            }
            atInterstitial = null;
        }

        unregisterPreloadOwnerIfNeeded();
        PlacementInterstitialAdManager.detachOwner(getPlacementId(), this);
    }

    private void showAdvert() {
        if (fragmentActivity == null) {
            onAdvertShowFail();
            onAdvertRelease();
            return;
        }
        AdvertLifeUtils.launchWhenResume(lifecycleOwner, () -> {
            if (!isAdvertReady() || fragmentActivity == null) {
                onAdvertShowFail();
                onAdvertRelease();
                return;
            }
            try {
                scheduleShowTimeout();
                atInterstitial = new ATInterstitial(fragmentActivity.getApplicationContext(), getPlacementId());
                atInterstitial.setAdListener(this);
                atInterstitial.setAdRevenueListener(atAdInfo -> AdvertThreadUtils.runOnUiThread(() -> {
                    if (advertCallback != null) {
                        advertCallback.onAdvertRevenue(this, atAdInfo);
                    }
                }));
                atInterstitial.show(fragmentActivity);
            } catch (Throwable ignored) {
                onAdvertShowFail();
                onAdvertRelease();
            }
        });
    }

    private void scheduleShowTimeout() {
        clearTimeout();
        setTimeout(SHOW_TIMEOUT_MILLIS, () -> {
            onAdvertShowFail();
            onAdvertRelease();
        });
    }

    private void onAdvertRequestPre() {
        AdvertThreadUtils.runOnUiThread(() -> {
            if (advertCallback != null) {
                final ATAdInfoExt adInfoExt = new ATAdInfoExt();
                adInfoExt.adunit_id = getPlacementId();
                adInfoExt.adunit_format = AdUnitFormat.INSERT.getValue();
                advertCallback.onAdvertRequestPre(this, adInfoExt);
            }
        });
    }

    private void onAdvertRequestAlt() {
        AdvertThreadUtils.runOnUiThread(() -> {
            if (advertCallback != null) {
                final ATAdInfoExt adInfoExt = new ATAdInfoExt();
                adInfoExt.adunit_id = getPlacementId();
                adInfoExt.adunit_format = AdUnitFormat.INSERT.getValue();
                advertCallback.onAdvertRequestAlt(this, adInfoExt);
            }
        });
    }

    private void onAdvertLoaded(boolean adLoadedReal) {
        clearTimeout();
        if (advertLoadFailed) {
            return;
        }
        if (advertLoaded) return;
        advertLoaded = true;

        AdvertThreadUtils.runOnUiThread(() -> {
            if (adLoadedReal) {
                onAdvertRequestAlt();
            }
            if (advertCallback != null) {
                advertCallback.onAdvertLoaded(this);
            }
            if (advertConfig.isShowAfterLoaded()) {
                showAdvert();
            }
        });
    }

    private void onAdvertLoadFail(@Nullable String adError) {
        clearTimeout();
        if (advertLoadFailed) return;
        advertLoadFailed = true;

        AdvertThreadUtils.runOnUiThread(() -> {
            if (advertCallback != null) {
                advertCallback.onAdvertLoadFail(this, adError);
            }
        });
    }

    private void onAdvertShowFail() {
        AdvertThreadUtils.runOnUiThread(() -> {
            if (advertCallback != null) {
                advertCallback.onAdvertShowFail(this);
            }
        });
    }

    @Override
    public void onSharedRequestPre() {
        onAdvertRequestPre();
    }

    @Override
    public void onSharedAdLoaded(boolean adLoadedReal) {
        onAdvertLoaded(adLoadedReal);
    }

    @Override
    public void onSharedAdLoadFail(@Nullable String error) {
        onAdvertLoadFail(error);
    }

    @Override
    public void onInterstitialAdLoaded() {
    }

    @Override
    public void onInterstitialAdLoadFail(@Nullable AdError adError) {
    }

    @Override
    public void onInterstitialAdClicked(@Nullable ATAdInfo atAdInfo) {
        AdvertThreadUtils.runOnUiThread(() -> {
            if (advertCallback != null) {
                advertCallback.onAdvertClicked(this, atAdInfo);
            }
        });
    }

    @Override
    public void onInterstitialAdShow(@Nullable ATAdInfo atAdInfo) {
        AdvertThreadUtils.runOnUiThread(() -> {
            clearTimeout();
            if (advertCallback != null) {
                advertCallback.onAdvertShow(this, atAdInfo);
            }
            if (advertConfig.isPreloadAdvert()) {
                final InsertAdvertConfig insertAdvertConfig =
                        AdvertGsonUtils.getInstance().deepClone(advertConfig, InsertAdvertConfig.class);
                if (insertAdvertConfig != null) {
                    insertAdvertConfig.setShowAfterLoaded(false);
                    startOrReplacePreload(insertAdvertConfig);
                }
            }
        });
    }

    @Override
    public void onInterstitialAdClose(@Nullable ATAdInfo atAdInfo) {
        AdvertThreadUtils.runOnUiThread(() -> {
            clearTimeout();
            if (advertCallback != null) {
                advertCallback.onAdvertClose(this, atAdInfo);
            }
            onAdvertRelease();
        });
    }

    @Override
    public void onInterstitialAdVideoStart(@Nullable ATAdInfo atAdInfo) {
    }

    @Override
    public void onInterstitialAdVideoEnd(@Nullable ATAdInfo atAdInfo) {
    }

    @Override
    public void onInterstitialAdVideoError(@Nullable AdError adError) {
        AdvertThreadUtils.runOnUiThread(() -> {
            try {
                onAdvertShowFail();
                onAdvertRelease();
            } catch (Throwable ignored) {
            }
        });
    }

    private void startOrReplacePreload(@NonNull InsertAdvertConfig preloadConfig) {
        final String key = PreloadOwnerRegistry.buildKey(lifecycleOwner, getPlacementId());
        final InsertAdvert previous = PRELOAD_OWNERS.get(key);
        if (previous != null && previous != this) {
            previous.onAdvertRelease();
        }

        final InsertAdvert preloadOwner = new InsertAdvert(lifecycleOwner, preloadConfig);
        PRELOAD_OWNERS.put(key, preloadOwner);
        preloadOwner.loadAdvert(true);
    }

    private void unregisterPreloadOwnerIfNeeded() {
        if (!isPreloadRequest) {
            return;
        }
        final String key = PreloadOwnerRegistry.buildKey(lifecycleOwner, getPlacementId());
        PRELOAD_OWNERS.removeIfSame(key, this);
    }
}
