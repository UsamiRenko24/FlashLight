package com.name.flashlight.integration.ads;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LifecycleOwner;

import com.secmtp.sdk.core.api.ATAdInfo;
import com.secmtp.sdk.core.api.AdError;
import com.secmtp.sdk.rewardvideo.api.ATRewardVideoAd;
import com.secmtp.sdk.rewardvideo.api.ATRewardVideoListener;


public class RewardAdvert extends BaseAdvert implements ATRewardVideoListener, SharedLoadOwner {
    private static final PreloadOwnerRegistry<RewardAdvert> PRELOAD_OWNERS = new PreloadOwnerRegistry<>();
    private static final int SHOW_TIMEOUT_MILLIS = 5 * 1000;

    @NonNull
    private final LifecycleOwner lifecycleOwner;
    @NonNull
    private final RewardAdvertConfig advertConfig;
    @Nullable
    private FragmentActivity fragmentActivity;
    @Nullable
    private ATRewardVideoAd atRewardVideoAd;
    @Nullable
    private RewardAdvertCallback advertCallback;

    private boolean isAdvertReward;
    private boolean isPreloadRequest = false;
    private boolean advertLoadFailed = false;
    private boolean advertLoaded = false;
    private boolean released = false;

    public RewardAdvert(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull RewardAdvertConfig advertConfig
    ) {
        this(lifecycleOwner, advertConfig, null);
    }

    public RewardAdvert(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull RewardAdvertConfig advertConfig,
            @Nullable RewardAdvertCallback advertCallback
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
        PlacementRewardVideoAdManager.attachOwner(lifecycleOwner, getPlacementId(), this);
        AdvertLifeUtils.launchWhenDestroy(lifecycleOwner, this::onAdvertRelease);
        if (advertCallback != null) {
            advertCallback.onAdvertStart(this);
        }
    }

    public boolean isAdvertReward() {
        return isAdvertReward;
    }

    @Override
    public boolean isAdvertReady() {
        return PlacementRewardVideoAdManager.isReady(lifecycleOwner, getPlacementId());
    }

    @Override
    public boolean isAdvertLoading() {
        return PlacementRewardVideoAdManager.isLoading(lifecycleOwner, getPlacementId());
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
        isAdvertReward = false;
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
        PlacementRewardVideoAdManager.requestLoad(lifecycleOwner, getPlacementId(), this, forceReload);
    }

    @Override
    public void onAdvertRelease() {
        if (released) return;
        released = true;
        super.onAdvertRelease();

        fragmentActivity = null;
        advertCallback = null;
        if (atRewardVideoAd != null) {
            try {
                atRewardVideoAd.setAdListener(null);
                atRewardVideoAd.setAdDownloadListener(null);
                atRewardVideoAd.setAdSourceStatusListener(null);
            } catch (Throwable ignored) {
            }
            atRewardVideoAd = null;
        }

        unregisterPreloadOwnerIfNeeded();
        PlacementRewardVideoAdManager.detachOwner(getPlacementId(), this);
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
                atRewardVideoAd = new ATRewardVideoAd(fragmentActivity.getApplicationContext(), getPlacementId());
                atRewardVideoAd.setAdListener(this);
                atRewardVideoAd.setAdRevenueListener(atAdInfo -> AdvertThreadUtils.runOnUiThread(() -> {
                    if (advertCallback != null) {
                        advertCallback.onAdvertRevenue(this, atAdInfo);
                    }
                }));
                atRewardVideoAd.show(fragmentActivity);
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
                adInfoExt.adunit_format = AdUnitFormat.REWARD.getValue();
                advertCallback.onAdvertRequestPre(this, adInfoExt);
            }
        });
    }

    private void onAdvertRequestAlt() {
        AdvertThreadUtils.runOnUiThread(() -> {
            if (advertCallback != null) {
                final ATAdInfoExt adInfoExt = new ATAdInfoExt();
                adInfoExt.adunit_id = getPlacementId();
                adInfoExt.adunit_format = AdUnitFormat.REWARD.getValue();
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
    public void onRewardedVideoAdLoaded() {
    }

    @Override
    public void onRewardedVideoAdFailed(AdError adError) {
    }

    @Override
    public void onRewardedVideoAdPlayClicked(ATAdInfo atAdInfo) {
        AdvertThreadUtils.runOnUiThread(() -> {
            if (advertCallback != null) {
                advertCallback.onAdvertClicked(this, atAdInfo);
            }
        });
    }

    @Override
    public void onRewardedVideoAdClosed(ATAdInfo atAdInfo) {
        AdvertThreadUtils.runOnUiThread(() -> {
            clearTimeout();
            if (advertCallback != null) {
                advertCallback.onAdvertClose(this, atAdInfo);
            }
            onAdvertRelease();
        });
    }

    @Override
    public void onRewardedVideoAdPlayStart(ATAdInfo atAdInfo) {
        AdvertThreadUtils.runOnUiThread(() -> {
            clearTimeout();
            if (advertCallback != null) {
                advertCallback.onAdvertShow(this, atAdInfo);
            }
            if (advertConfig.isPreloadAdvert()) {
                final RewardAdvertConfig rewardAdvertConfig =
                        AdvertGsonUtils.getInstance().deepClone(advertConfig, RewardAdvertConfig.class);
                if (rewardAdvertConfig != null) {
                    rewardAdvertConfig.setShowAfterLoaded(false);
                    startOrReplacePreload(rewardAdvertConfig);
                }
            }
        });
    }

    @Override
    public void onRewardedVideoAdPlayEnd(ATAdInfo atAdInfo) {
    }

    @Override
    public void onRewardedVideoAdPlayFailed(AdError adError, ATAdInfo atAdInfo) {
        AdvertThreadUtils.runOnUiThread(() -> {
            try {
                onAdvertShowFail();
                onAdvertRelease();
            } catch (Throwable ignored) {
            }
        });
    }

    @Override
    public void onReward(ATAdInfo atAdInfo) {
        isAdvertReward = true;
        AdvertThreadUtils.runOnUiThread(() -> {
            if (advertCallback != null) {
                advertCallback.onAdvertReward(this, atAdInfo);
            }
        });
    }

    private void startOrReplacePreload(@NonNull RewardAdvertConfig preloadConfig) {
        final String key = PreloadOwnerRegistry.buildKey(lifecycleOwner, getPlacementId());
        final RewardAdvert previous = PRELOAD_OWNERS.get(key);
        if (previous != null && previous != this) {
            previous.onAdvertRelease();
        }

        final RewardAdvert preloadOwner = new RewardAdvert(lifecycleOwner, preloadConfig);
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
