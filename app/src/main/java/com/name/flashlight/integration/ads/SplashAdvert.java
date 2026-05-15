package com.name.flashlight.integration.ads;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LifecycleOwner;

import com.secmtp.sdk.core.api.ATAdInfo;
import com.secmtp.sdk.core.api.AdError;
import com.secmtp.sdk.splashad.api.ATSplashAd;
import com.secmtp.sdk.splashad.api.ATSplashAdExtraInfo;
import com.secmtp.sdk.splashad.api.ATSplashAdListener;
import com.secmtp.sdk.splashad.api.IATSplashEyeAd;


public class SplashAdvert extends BaseAdvert implements ATSplashAdListener, SharedLoadOwner {
    private static final PreloadOwnerRegistry<SplashAdvert> PRELOAD_OWNERS = new PreloadOwnerRegistry<>();
    private static final int SHOW_TIMEOUT_MILLIS = 5 * 1000;

    @NonNull
    private final LifecycleOwner lifecycleOwner;
    @NonNull
    private final SplashAdvertConfig advertConfig;
    @Nullable
    private FragmentActivity fragmentActivity;
    @Nullable
    private SplashAdvertCallback advertCallback;
    @Nullable
    private ViewGroup advertContainer;
    @Nullable
    private ATSplashAd atSplashAd;

    private boolean isPreloadRequest = false;
    private boolean advertLoadFailed = false;
    private boolean advertLoaded = false;
    private boolean released = false;

    public SplashAdvert(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull SplashAdvertConfig advertConfig
    ) {
        this(lifecycleOwner, advertConfig, null, null);
    }

    public SplashAdvert(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull SplashAdvertConfig advertConfig,
            @Nullable ViewGroup advertContainer,
            @Nullable SplashAdvertCallback advertCallback
    ) {
        this.lifecycleOwner = lifecycleOwner;
        this.advertContainer = advertContainer;
        this.advertConfig = advertConfig;
        this.advertCallback = advertCallback;
        this.fragmentActivity = AdvertLifeUtils.getContext(lifecycleOwner);
        initializeLifecycle();
    }

    private void initializeLifecycle() {
        if (fragmentActivity == null) {
            return;
        }
        PlacementSplashAdManager.attachOwner(lifecycleOwner, getPlacementId(), this);
        AdvertLifeUtils.launchWhenDestroy(lifecycleOwner, this::onAdvertRelease);
        if (advertCallback != null) {
            advertCallback.onAdvertStart(this);
        }
    }

    @Override
    public boolean isAdvertReady() {
        return PlacementSplashAdManager.isReady(lifecycleOwner, getPlacementId());
    }

    @Override
    public boolean isAdvertLoading() {
        return PlacementSplashAdManager.isLoading(lifecycleOwner, getPlacementId());
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
        PlacementSplashAdManager.requestLoad(lifecycleOwner, getPlacementId(), this, forceReload);
    }

    @Override
    public void onAdvertRelease() {
        if (released) return;
        released = true;
        super.onAdvertRelease();

        fragmentActivity = null;
        advertCallback = null;
        if (advertContainer != null) {
            try {
                advertContainer.removeAllViews();
            } catch (Throwable ignored) {
            }
            advertContainer = null;
        }
        if (atSplashAd != null) {
            try {
                atSplashAd.setAdListener(null);
                atSplashAd.setAdDownloadListener(null);
                atSplashAd.setAdSourceStatusListener(null);
            } catch (Throwable ignored) {
            }
            atSplashAd = null;
        }

        unregisterPreloadOwnerIfNeeded();
        PlacementSplashAdManager.detachOwner(getPlacementId(), this);
    }

    private void showAdvert() {
        if (advertContainer == null || fragmentActivity == null) {
            onAdvertShowFail();
            onAdvertRelease();
            return;
        }
        AdvertLifeUtils.launchWhenResume(lifecycleOwner, () -> {
            if (!isAdvertReady() || fragmentActivity == null || advertContainer == null) {
                onAdvertShowFail();
                onAdvertRelease();
                return;
            }
            try {
                scheduleShowTimeout();
                if (advertContainer.getVisibility() != View.VISIBLE) {
                    advertContainer.setVisibility(View.VISIBLE);
                }
                atSplashAd = new ATSplashAd(
                        fragmentActivity.getApplicationContext(),
                        getPlacementId(),
                        this,
                        PlacementSplashAdManager.SDK_FETCH_AD_TIMEOUT
                );
                atSplashAd.setAdRevenueListener(atAdInfo -> AdvertThreadUtils.runOnUiThread(() -> {
                    if (advertCallback != null) {
                        advertCallback.onAdvertRevenue(this, atAdInfo);
                    }
                }));
                atSplashAd.show(fragmentActivity, advertContainer);
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
                adInfoExt.adunit_format = AdUnitFormat.SPLASH.getValue();
                advertCallback.onAdvertRequestPre(this, adInfoExt);
            }
        });
    }

    private void onAdvertRequestAlt() {
        AdvertThreadUtils.runOnUiThread(() -> {
            if (advertCallback != null) {
                final ATAdInfoExt adInfoExt = new ATAdInfoExt();
                adInfoExt.adunit_id = getPlacementId();
                adInfoExt.adunit_format = AdUnitFormat.SPLASH.getValue();
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
    public void onAdLoaded(boolean isTimeout) {
    }

    @Override
    public void onAdLoadTimeout() {
    }

    @Override
    public void onNoAdError(@Nullable AdError adError) {
    }

    @Override
    public void onAdClick(@Nullable ATAdInfo atAdInfo) {
        AdvertThreadUtils.runOnUiThread(() -> {
            if (advertCallback != null) {
                advertCallback.onAdvertClicked(this, atAdInfo);
            }
        });
    }

    @Override
    public void onAdShow(@Nullable ATAdInfo atAdInfo) {
        AdvertThreadUtils.runOnUiThread(() -> {
            clearTimeout();
            if (advertCallback != null) {
                advertCallback.onAdvertShow(this, atAdInfo);
            }
            if (advertConfig.isPreloadAdvert()) {
                final SplashAdvertConfig splashAdvertConfig =
                        AdvertGsonUtils.getInstance().deepClone(advertConfig, SplashAdvertConfig.class);
                if (splashAdvertConfig != null) {
                    splashAdvertConfig.setShowAfterLoaded(false);
                    startOrReplacePreload(splashAdvertConfig);
                }
            }
        });
    }

    @Override
    public void onAdDismiss(@Nullable ATAdInfo atAdInfo, ATSplashAdExtraInfo atSplashAdExtraInfo) {
        AdvertThreadUtils.runOnUiThread(() -> {
            clearTimeout();
            if (atSplashAdExtraInfo != null) {
                try {
                    final IATSplashEyeAd eyeAd = atSplashAdExtraInfo.getAtSplashEyeAd();
                    if (eyeAd != null) {
                        eyeAd.destroy();
                    }
                } catch (Throwable ignored) {
                }
            }

            if (advertCallback != null) {
                advertCallback.onAdvertClose(this, atAdInfo);
            }
            onAdvertRelease();
        });
    }

    private void startOrReplacePreload(@NonNull SplashAdvertConfig preloadConfig) {
        final String key = PreloadOwnerRegistry.buildKey(lifecycleOwner, getPlacementId());
        final SplashAdvert previous = PRELOAD_OWNERS.get(key);
        if (previous != null && previous != this) {
            previous.onAdvertRelease();
        }

        final SplashAdvert preloadOwner = new SplashAdvert(lifecycleOwner, preloadConfig);
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
