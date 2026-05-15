package com.name.flashlight.integration.ads;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.secmtp.sdk.banner.api.ATBannerView;
import com.secmtp.sdk.core.api.ATAdInfo;
import com.secmtp.sdk.core.api.ATAdStatusInfo;

import java.util.Collections;

public class BannerAdvert extends BaseAdvert
        implements PlacementBannerAdManager.BannerLoadObserver,
        PlacementBannerAdManager.BannerEventObserver {
    @NonNull
    private final BannerAdvertConfig advertConfig;
    @Nullable
    private ViewGroup advertContainer;
    @Nullable
    private ATBannerView atBannerView;
    @Nullable
    private BannerAdvertCallback advertCallback;

    private int advertWidth;
    private int advertHeight;
    private int lastBannerViewHeight;
    private int updateBannerViewHeightCount;
    @Nullable
    private ViewTreeObserver.OnGlobalLayoutListener bannerHeightLayoutListener;

    private boolean isAdvertLoaded;
    private boolean isAdvertLoadFailed;
    private boolean isReleased;

    public BannerAdvert(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull ViewGroup advertContainer,
            @NonNull BannerAdvertConfig advertConfig,
            @Nullable BannerAdvertCallback advertCallback
    ) {
        this.advertConfig = advertConfig;
        this.advertContainer = advertContainer;
        this.advertCallback = advertCallback;

        final Context context = AdvertLifeUtils.getContext(lifecycleOwner);
        if (context == null) {
            return;
        }

        atBannerView = PlacementBannerAdManager.obtain(context, advertConfig.getPlacementId());
        atBannerView.setPlacementId(advertConfig.getPlacementId());
        atBannerView.setVisibility(View.VISIBLE);

        advertContainer.setVisibility(View.VISIBLE);
        advertContainer.post(() -> {
            final float whRatio = advertConfig.getAdvertWHRatio();
            if (whRatio <= 0f) {
                return;
            }

            advertWidth = advertContainer.getWidth();
            advertHeight = (int) (advertWidth / whRatio);

            if (atBannerView != null) {
                PlacementBannerAdManager.setLocalExtra(atBannerView, advertWidth, advertHeight);
                if (atBannerView.getParent() == advertContainer) {
                    updateBannerViewHeight(advertWidth, advertHeight);
                }
            }
        });

        ViewVisibilityUtils.onVisibilityChange(
                advertContainer,
                Collections.emptyList(),
                true,
                true,
                (view, isVisible) -> {
                    if (atBannerView == null) {
                        return;
                    }

                    if (isVisible) {
                        // Only the currently visible BannerAdvert instance should receive show/click/revenue events.
                        PlacementBannerAdManager.setActiveEventObserver(getPlacementId(), this);
                        PlacementBannerAdManager.attachToContainer(advertContainer, atBannerView);
                        if (advertWidth > 0 && advertHeight > 0) {
                            updateBannerViewHeight(advertWidth, advertHeight);
                        }
                    } else {
                        PlacementBannerAdManager.clearActiveEventObserver(getPlacementId(), this);
                        detachBannerFromContainer();
                    }
                }
        );

        AdvertLifeUtils.launchWhenDestroy(lifecycleOwner, this::onAdvertRelease);

        if (advertCallback != null) {
            advertCallback.onAdvertStart(this);
        }
    }

    @Override
    public boolean isAdvertLoading() {
        if (atBannerView == null) {
            return false;
        }

        if (PlacementBannerAdManager.isLoading(getPlacementId())) {
            return true;
        }

        try {
            final ATAdStatusInfo atAdStatusInfo = atBannerView.checkAdStatus();
            return atAdStatusInfo != null && atAdStatusInfo.isLoading();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean isAdvertReady() {
        return PlacementBannerAdManager.isReady(atBannerView);
    }

    @Override
    public void onAdvertRelease() {
        if (isReleased) {
            return;
        }
        isReleased = true;
        super.onAdvertRelease();

        if (advertCallback != null) {
            advertCallback = null;
        }
        PlacementBannerAdManager.unregisterLoadObserver(getPlacementId(), this);
        PlacementBannerAdManager.clearActiveEventObserver(getPlacementId(), this);

        if (advertContainer != null) {
            detachBannerFromContainer();
            advertContainer = null;
        }

        if (atBannerView != null) {
            clearBannerHeightLayoutListener();
            PlacementBannerAdManager.destroy(advertConfig.getPlacementId());
            atBannerView = null;
        }
    }

    @Override
    protected void loadAdvert() {
        isAdvertLoaded = false;
        isAdvertLoadFailed = false;

        if (advertContainer == null) {
            return;
        }

        advertContainer.post(() -> {
            if (atBannerView == null || advertContainer == null) {
                return;
            }

            final PlacementBannerAdManager.LoadAction loadAction =
                    PlacementBannerAdManager.requestLoad(
                            getPlacementId(),
                            this,
                            advertConfig.isReloadAdvert()
                    );
            if (loadAction == PlacementBannerAdManager.LoadAction.REUSE_READY) {
                final int readyHeight = atBannerView.getHeight();
                final int readyContainerWidth = advertContainer.getWidth();
                if (readyHeight > 0 && readyContainerWidth > 0) {
                    onAdvertLoaded(false);
                    return;
                }
                PlacementBannerAdManager.unregisterLoadObserver(getPlacementId(), this);
            }
            if (loadAction == PlacementBannerAdManager.LoadAction.WAITING) {
                return;
            }

            onAdvertRequestPre();

            try {
                atBannerView.loadAd();
            } catch (Throwable throwable) {
                PlacementBannerAdManager.notifyLoadStartFailed(
                        getPlacementId(),
                        throwable.getMessage()
                );
            }
        });
    }

    @NonNull
    @Override
    protected String getPlacementId() {
        return advertConfig.getPlacementId();
    }

    private void onAdvertRequestPre() {
        AdvertThreadUtils.runOnUiThread(() -> {
            if (advertCallback != null) {
                final ATAdInfoExt adInfoExt = new ATAdInfoExt();
                adInfoExt.adunit_id = advertConfig.getPlacementId();
                adInfoExt.adunit_format = AdUnitFormat.BANNER.getValue();
                advertCallback.onAdvertRequestPre(this, adInfoExt);
            }
        });
    }

    private void onAdvertRequestAlt() {
        AdvertThreadUtils.runOnUiThread(() -> {
            if (advertCallback != null) {
                final ATAdInfoExt adInfoExt = new ATAdInfoExt();
                adInfoExt.adunit_id = advertConfig.getPlacementId();
                adInfoExt.adunit_format = AdUnitFormat.BANNER.getValue();
                advertCallback.onAdvertRequestAlt(this, adInfoExt);
            }
        });
    }

    private void onAdvertLoaded(boolean adLoadedReal) {
        if (isAdvertLoaded) {
            return;
        }
        isAdvertLoaded = true;

        if (adLoadedReal) {
            onAdvertRequestAlt();
        }
        AdvertThreadUtils.runOnUiThread(() -> {
            if (advertCallback != null) {
                advertCallback.onAdvertLoaded(this);
            }
        });
    }

    private void onAdvertLoadFail(@Nullable String adError) {
        if (isAdvertLoadFailed) {
            return;
        }
        isAdvertLoadFailed = true;

        AdvertThreadUtils.runOnUiThread(() -> {
            if (advertCallback != null) {
                advertCallback.onAdvertLoadFail(this, adError);
            }
        });
    }

    private void updateBannerViewHeight(int viewWidth, int viewHeight) {
        if (atBannerView == null) {
            return;
        }
        clearBannerHeightLayoutListener();
        updateBannerViewHeightCount = 0;
        lastBannerViewHeight = 0;
        final ViewTreeObserver observer = atBannerView.getViewTreeObserver();
        if (!observer.isAlive()) {
            return;
        }
        bannerHeightLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (atBannerView == null) {
                    return;
                }

                int bannerHeight = atBannerView.getHeight();
                if (bannerHeight <= 0) {
                    updateBannerViewHeightCount++;
                    if (updateBannerViewHeightCount >= 30) {
                        removeBannerHeightLayoutListener(this);
                    }
                    return;
                }

                if (bannerHeight > viewHeight) {
                    bannerHeight = viewHeight;
                }
                if (lastBannerViewHeight == bannerHeight) {
                    updateBannerViewHeightCount++;

                    if (updateBannerViewHeightCount >= 10) {
                        removeBannerHeightLayoutListener(this);
                    }
                    return;
                }

                updateBannerViewHeightCount = 0;
                lastBannerViewHeight = bannerHeight;

                final ViewGroup.LayoutParams currentLayoutParams = atBannerView.getLayoutParams();
                final ViewGroup.LayoutParams layoutParams = currentLayoutParams != null
                        ? currentLayoutParams
                        : new FrameLayout.LayoutParams(viewWidth, bannerHeight);
                layoutParams.width = viewWidth;
                layoutParams.height = bannerHeight;
                atBannerView.setLayoutParams(layoutParams);
            }
        };
        observer.addOnGlobalLayoutListener(bannerHeightLayoutListener);
    }

    private void detachBannerFromContainer() {
        if (advertContainer == null || atBannerView == null || atBannerView.getParent() != advertContainer) {
            return;
        }
        try {
            advertContainer.removeView(atBannerView);
        } catch (Throwable ignored) {
        }
    }

    private void removeBannerHeightLayoutListener(
            @NonNull ViewTreeObserver.OnGlobalLayoutListener listener
    ) {
        if (atBannerView == null) {
            return;
        }
        final ViewTreeObserver currentObserver = atBannerView.getViewTreeObserver();
        if (currentObserver.isAlive()) {
            currentObserver.removeOnGlobalLayoutListener(listener);
        }
        if (bannerHeightLayoutListener == listener) {
            bannerHeightLayoutListener = null;
        }
    }

    private void clearBannerHeightLayoutListener() {
        if (atBannerView == null || bannerHeightLayoutListener == null) {
            bannerHeightLayoutListener = null;
            return;
        }
        try {
            removeBannerHeightLayoutListener(bannerHeightLayoutListener);
        } catch (Throwable ignored) {
        }
        bannerHeightLayoutListener = null;
    }

    @Override
    public void onBannerLoadSuccess() {
        AdvertThreadUtils.runOnUiThread(() -> onAdvertLoaded(true));
    }

    @Override
    public void onBannerLoadFail(@Nullable String adError) {
        AdvertThreadUtils.runOnUiThread(() -> onAdvertLoadFail(adError));
    }

    @Override
    public void onBannerClicked(@Nullable ATAdInfo atAdInfo) {
        AdvertThreadUtils.runOnUiThread(() -> {
            if (advertCallback != null) {
                advertCallback.onAdvertClicked(this, atAdInfo);
            }
        });
    }

    @Override
    public void onBannerShow(@Nullable ATAdInfo atAdInfo) {
        AdvertThreadUtils.runOnUiThread(() -> {
            if (advertCallback != null) {
                advertCallback.onAdvertShow(this, atAdInfo);
            }
        });
    }

    @Override
    public void onBannerClose(@Nullable ATAdInfo atAdInfo) {
        AdvertThreadUtils.runOnUiThread(() -> {
            if (advertCallback != null) {
                advertCallback.onAdvertClose(this, atAdInfo);
            }
        });
    }

    @Override
    public void onBannerAutoRefreshed(@Nullable ATAdInfo atAdInfo) {
        AdvertThreadUtils.runOnUiThread(() -> {
            onAdvertLoaded(true);
            if (advertCallback != null) {
                advertCallback.onAdvertShow(this, atAdInfo);
            }
        });
    }

    @Override
    public void onBannerRevenue(@Nullable ATAdInfo atAdInfo) {
        AdvertThreadUtils.runOnUiThread(() -> {
            if (advertCallback != null) {
                advertCallback.onAdvertRevenue(this, atAdInfo);
            }
        });
    }
}
