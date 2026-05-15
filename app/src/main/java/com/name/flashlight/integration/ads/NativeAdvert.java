package com.name.flashlight.integration.ads;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LifecycleOwner;

import com.secmtp.sdk.core.api.ATAdInfo;
import com.secmtp.sdk.nativead.api.ATNativeAdView;
import com.secmtp.sdk.nativead.api.ATNativeDislikeListener;
import com.secmtp.sdk.nativead.api.ATNativeEventListener;
import com.secmtp.sdk.nativead.api.ATNativePrepareExInfo;
import com.secmtp.sdk.nativead.api.NativeAd;


public class NativeAdvert extends BaseAdvert implements ATNativeEventListener, SharedLoadOwner {
    private static final PreloadOwnerRegistry<NativeAdvert> PRELOAD_OWNERS = new PreloadOwnerRegistry<>();

    @NonNull
    private final LifecycleOwner lifecycleOwner;
    @NonNull
    private final NativeAdvertConfig advertConfig;
    @Nullable
    private final FragmentActivity fragmentActivity;
    @Nullable
    private final ATNativeAdView atNativeAdView;

    @Nullable
    private INativeSelfRender nativeSelfRender;
    @Nullable
    private NativeAdvertCallback advertCallback;
    @Nullable
    private NativeAd nativeAd;
    @Nullable
    private View.OnLayoutChangeListener templateHeightChangeListener;
    @Nullable
    private ATNativeAdView templateHeightWatchView;

    private boolean isAdvertLoadFailed = false;
    private boolean isAdvertLoaded = false;
    private boolean isReleased = false;
    private boolean isPreloadRequest = false;

    public NativeAdvert(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull NativeAdvertConfig advertConfig
    ) {
        this(lifecycleOwner, advertConfig, null, null, null);
    }

    public NativeAdvert(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull NativeAdvertConfig advertConfig,
            @Nullable ATNativeAdView atNativeAdView,
            @Nullable INativeSelfRender nativeSelfRender,
            @Nullable NativeAdvertCallback advertCallback
    ) {
        this.lifecycleOwner = lifecycleOwner;
        this.advertConfig = advertConfig;
        this.atNativeAdView = atNativeAdView;
        this.nativeSelfRender = nativeSelfRender;
        this.advertCallback = advertCallback;
        this.fragmentActivity = AdvertLifeUtils.getContext(lifecycleOwner);

        if (fragmentActivity == null) {
            return;
        }

        PlacementNativeAdManager.attachOwner(lifecycleOwner, getPlacementId(), this);

        if (atNativeAdView != null) {
            final ViewGroup.LayoutParams layoutParams = atNativeAdView.getLayoutParams();
            if (layoutParams != null) {
                if (layoutParams.width == ViewGroup.LayoutParams.WRAP_CONTENT) {
                    layoutParams.width = advertConfig.getAdvertWidth();
                }
                atNativeAdView.setLayoutParams(layoutParams);
            }
        }

        AdvertLifeUtils.launchWhenDestroy(lifecycleOwner, this::onAdvertRelease);

        if (advertCallback != null) {
            advertCallback.onAdvertStart(this);
        }
    }

    /**
     * @return 广告是否准备完毕
     */
    @Override
    public boolean isAdvertReady() {
        return PlacementNativeAdManager.isReady(lifecycleOwner, getPlacementId());
    }

    /**
     * 广告是否正在加载
     */
    @Override
    public boolean isAdvertLoading() {
        return PlacementNativeAdManager.isLoading(lifecycleOwner, getPlacementId());
    }

    @Override
    public void onAdvertRelease() {
        if (isReleased) return;
        isReleased = true;
        super.onAdvertRelease();

        if (advertCallback != null) {
            advertCallback = null;
        }
        if (nativeSelfRender != null) {
            nativeSelfRender = null;
        }
        if (atNativeAdView != null) {
            try {
                atNativeAdView.removeAllViews();
            } catch (Throwable ignored) {
                // 避免 Activity 销毁时 removeAllViews 触发第三方广告 View 的 onViewDetachedFromWindow，
                // 导致 SDK 内部抛出 IllegalStateException 进而崩溃（见 Facebook Audience Network 等）
            }
        }
        if (nativeAd != null) {
            try {
                nativeAd.destory();
            } catch (Throwable ignored) {
            }
            nativeAd = null;
        }
        clearTemplateHeightListener();

        unregisterPreloadOwnerIfNeeded();
        PlacementNativeAdManager.detachOwner(getPlacementId(), this);
    }

    @SuppressWarnings("unused")
    public void onPause() {
        if (nativeAd != null) {
            try {
                nativeAd.onPause();
            } catch (Throwable ignored) {
            }
        }
    }

    @SuppressWarnings("unused")
    public void onResume() {
        if (nativeAd != null) {
            try {
                nativeAd.onResume();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 加载广告
     */
    @Override
    protected void loadAdvert() {
        loadAdvert(false);
    }

    private void loadAdvert(boolean isPreloadAdvert) {
        isPreloadRequest = isPreloadAdvert;
        isAdvertLoadFailed = false;
        isAdvertLoaded = false;

        if (fragmentActivity == null) {
            onAdvertLoadFail(null);
            return;
        }

        final boolean forceReload = isPreloadAdvert && advertConfig.isPreloadWithoutReady();
        PlacementNativeAdManager.requestLoad(
                lifecycleOwner,
                getPlacementId(),
                this,
                forceReload
        );
    }

    @NonNull
    @Override
    protected String getPlacementId() {
        return advertConfig.getPlacementId();
    }

    /**
     * 展示广告
     */
    private void showAdvert() {
        onAdvertRender();
    }

    /**
     * 广告渲染
     */
    private void onAdvertRender() {
        if (atNativeAdView == null) {
            onAdvertShowFail();
            return;
        }
        if (!isAdvertReady()) {
            onAdvertShowFail();
            return;
        }

        final NativeAd nativeAd = PlacementNativeAdManager.getNativeAd(lifecycleOwner, getPlacementId());
        if (nativeAd == null) {
            onAdvertShowFail();
            return;
        }

        if (this.nativeAd != null && this.nativeAd != nativeAd) {
            try {
                this.nativeAd.destory();
            } catch (Throwable ignored) {
            }
        }
        this.nativeAd = nativeAd;
        try {
            atNativeAdView.removeAllViews();
        } catch (Throwable ignored) {
            onAdvertShowFail();
            return;
        }

        nativeAd.setNativeEventListener(this);
        nativeAd.setDislikeCallbackListener(new ATNativeDislikeListener() {
            @Override
            public void onAdCloseButtonClick(ATNativeAdView atNativeAdView, ATAdInfo atAdInfo) {
                AdvertThreadUtils.runOnUiThread(() -> {
                    if (advertCallback != null) {
                        advertCallback.onAdvertClose(NativeAdvert.this, atAdInfo);
                    }
                    onAdvertRelease();
                });
            }
        });
        //noinspection CodeBlock2Expr
        nativeAd.setAdRevenueListener(atAdInfo -> {
            AdvertThreadUtils.runOnUiThread(() -> {
                if (advertCallback != null) {
                    advertCallback.onAdvertRevenue(NativeAdvert.this, atAdInfo);
                }
            });
        });

        final ATNativePrepareExInfo atNativePrepareExInfo = new ATNativePrepareExInfo();

        // 模板
        if (nativeAd.isNativeExpress()) {
            try {
                final boolean hasExplicitHeight = hasExplicitHeightConstraint(atNativeAdView);
                nativeAd.renderAdContainer(atNativeAdView, null);
                nativeAd.prepare(atNativeAdView, atNativePrepareExInfo);
                applyTemplateMaxHeightIfNeeded(atNativeAdView, hasExplicitHeight);

                if (atNativeAdView.getVisibility() != View.VISIBLE) {
                    atNativeAdView.setVisibility(View.VISIBLE);
                }
            } catch (Throwable ignored) {
                onAdvertShowFail();
            }
            return;
        }

        // 自渲染
        if (advertCallback != null) {
            final INativeSelfRender selfRender = advertCallback.createSelfRender(nativeAd.getAdInfo());
            if (selfRender != null) {
                nativeSelfRender = selfRender;
            }
        }
        if (nativeSelfRender != null && fragmentActivity != null) {
            try {
                nativeSelfRender.onBindView(fragmentActivity, nativeAd.getAdMaterial(), atNativePrepareExInfo);

                final View selfRenderView = nativeSelfRender.getSelfRenderView();
                nativeAd.renderAdContainer(atNativeAdView, selfRenderView);
                nativeAd.prepare(atNativeAdView, atNativePrepareExInfo);

                if (atNativeAdView.getVisibility() != View.VISIBLE) {
                    atNativeAdView.setVisibility(View.VISIBLE);
                }
            } catch (Throwable ignored) {
                onAdvertShowFail();
            }
        } else {
            onAdvertShowFail();
        }
    }

    /**
     * 将已加载的 NativeAd 重新渲染到指定容器，用于按广告位复用同一条广告
     *
     * @param targetView         新的广告容器
     * @param selfRenderOverride 可选的自渲染实现，优先使用；为空则回退到内部的 nativeSelfRender
     * @return 是否渲染成功
     */
    @SuppressWarnings("unused")
    public boolean renderTo(
            @NonNull ATNativeAdView targetView,
            @Nullable INativeSelfRender selfRenderOverride
    ) {
        if (nativeAd == null) {
            return false;
        }

        try {
            targetView.removeAllViews();
        } catch (Throwable ignored) {
            onAdvertShowFail();
            return false;
        }

        nativeAd.setNativeEventListener(this);
        nativeAd.setDislikeCallbackListener(new ATNativeDislikeListener() {
            @Override
            public void onAdCloseButtonClick(ATNativeAdView atNativeAdView, ATAdInfo atAdInfo) {
                AdvertThreadUtils.runOnUiThread(() -> {
                    if (advertCallback != null) {
                        advertCallback.onAdvertClose(NativeAdvert.this, atAdInfo);
                    }
                    onAdvertRelease();
                });
            }
        });
        //noinspection CodeBlock2Expr
        nativeAd.setAdRevenueListener(atAdInfo -> {
            AdvertThreadUtils.runOnUiThread(() -> {
                if (advertCallback != null) {
                    advertCallback.onAdvertRevenue(NativeAdvert.this, atAdInfo);
                }
            });
        });

        final ATNativePrepareExInfo atNativePrepareExInfo = new ATNativePrepareExInfo();

        // 模板广告复用
        if (nativeAd.isNativeExpress()) {
            try {
                final boolean hasExplicitHeight = hasExplicitHeightConstraint(targetView);
                nativeAd.renderAdContainer(targetView, null);
                nativeAd.prepare(targetView, atNativePrepareExInfo);
                applyTemplateMaxHeightIfNeeded(targetView, hasExplicitHeight);

                if (targetView.getVisibility() != View.VISIBLE) {
                    targetView.setVisibility(View.VISIBLE);
                }
                return true;
            } catch (Throwable ignored) {
                onAdvertShowFail();
                return false;
            }
        }

        // 自渲染广告复用
        INativeSelfRender selfRender = selfRenderOverride != null ? selfRenderOverride : nativeSelfRender;
        if (selfRender != null && fragmentActivity != null) {
            try {
                selfRender.onBindView(fragmentActivity, nativeAd.getAdMaterial(), atNativePrepareExInfo);

                final View selfRenderView = selfRender.getSelfRenderView();
                nativeAd.renderAdContainer(targetView, selfRenderView);
                nativeAd.prepare(targetView, atNativePrepareExInfo);

                if (targetView.getVisibility() != View.VISIBLE) {
                    targetView.setVisibility(View.VISIBLE);
                }
                return true;
            } catch (Throwable ignored) {
                onAdvertShowFail();
                return false;
            }
        } else {
            onAdvertShowFail();
            return false;
        }
    }

    private void onAdvertRequestPre() {
        AdvertThreadUtils.runOnUiThread(() -> {
            if (advertCallback != null) {
                final ATAdInfoExt adInfoExt = new ATAdInfoExt();
                adInfoExt.adunit_id = advertConfig.getPlacementId();
                adInfoExt.adunit_format = AdUnitFormat.NATIVE.getValue();
                advertCallback.onAdvertRequestPre(this, adInfoExt);
            }
        });
    }

    private void onAdvertRequestAlt() {
        AdvertThreadUtils.runOnUiThread(() -> {
            if (advertCallback != null) {
                final ATAdInfoExt adInfoExt = new ATAdInfoExt();
                adInfoExt.adunit_id = advertConfig.getPlacementId();
                adInfoExt.adunit_format = AdUnitFormat.NATIVE.getValue();
                advertCallback.onAdvertRequestAlt(this, adInfoExt);
            }
        });
    }

    private void onAdvertLoaded(boolean adLoadedReal) {
        clearTimeout();

        if (isAdvertLoadFailed) {
            return;
        }
        if (isAdvertLoaded) return;
        isAdvertLoaded = true;

        if (adLoadedReal) {
            onAdvertRequestAlt();
        }
        AdvertThreadUtils.runOnUiThread(() -> {
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

        if (isAdvertLoadFailed) return;
        isAdvertLoadFailed = true;

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
    public void onAdImpressed(ATNativeAdView atNativeAdView, ATAdInfo atAdInfo) {
        AdvertThreadUtils.runOnUiThread(() -> {
            if (advertCallback != null) {
                advertCallback.onAdvertShow(this, atAdInfo);
            }
            if (advertConfig.isPreloadAdvert()) {
                final NativeAdvertConfig nativeAdvertConfig
                        = AdvertGsonUtils.getInstance().deepClone(advertConfig, NativeAdvertConfig.class);
                if (nativeAdvertConfig != null) {
                    nativeAdvertConfig.setShowAfterLoaded(false);
                    startOrReplacePreload(nativeAdvertConfig);
                }
            }
        });
    }

    @Override
    public void onAdClicked(ATNativeAdView atNativeAdView, ATAdInfo atAdInfo) {
        AdvertThreadUtils.runOnUiThread(() -> {
            if (advertCallback != null) {
                advertCallback.onAdvertClicked(this, atAdInfo);
            }
        });
    }

    @Override
    public void onAdVideoStart(ATNativeAdView atNativeAdView) {
    }

    @Override
    public void onAdVideoEnd(ATNativeAdView atNativeAdView) {
    }

    @Override
    public void onAdVideoProgress(ATNativeAdView atNativeAdView, int i) {
    }

    // =================================== SharedLoadOwner (load 阶段回调) ===================================

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

    private void startOrReplacePreload(@NonNull NativeAdvertConfig preloadConfig) {
        final String key = PreloadOwnerRegistry.buildKey(lifecycleOwner, getPlacementId());
        final NativeAdvert previous = PRELOAD_OWNERS.get(key);
        if (previous != null && previous != this) {
            previous.onAdvertRelease();
        }

        final NativeAdvert preloadOwner = new NativeAdvert(lifecycleOwner, preloadConfig);
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

    private boolean hasExplicitHeightConstraint(@NonNull ATNativeAdView nativeAdView) {
        final ViewGroup.LayoutParams params = nativeAdView.getLayoutParams();
        return params != null && params.height != ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    private void applyTemplateMaxHeightIfNeeded(
            @NonNull ATNativeAdView nativeAdView,
            boolean hasExplicitHeight
    ) {
        final int maxHeight = advertConfig.getAdvertHeight();
        if (maxHeight <= 0) {
            clearTemplateHeightListener();
            return;
        }
        if (hasExplicitHeight) {
            clearTemplateHeightListener();
            return;
        }
        attachTemplateHeightListener(nativeAdView, maxHeight);
        nativeAdView.post(() -> {
            if (isReleased) {
                return;
            }
            capViewHeightIfNeeded(nativeAdView, maxHeight);
        });
    }

    private void attachTemplateHeightListener(@NonNull ATNativeAdView nativeAdView, int maxHeight) {
        if (templateHeightWatchView != null
                && templateHeightChangeListener != null
                && templateHeightWatchView != nativeAdView) {
            templateHeightWatchView.removeOnLayoutChangeListener(templateHeightChangeListener);
            templateHeightWatchView = null;
            templateHeightChangeListener = null;
        }
        if (templateHeightChangeListener == null) {
            templateHeightChangeListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom)
                    -> capViewHeightIfNeeded(v, maxHeight);
            nativeAdView.addOnLayoutChangeListener(templateHeightChangeListener);
            templateHeightWatchView = nativeAdView;
        }
    }

    private void clearTemplateHeightListener() {
        if (templateHeightWatchView != null && templateHeightChangeListener != null) {
            templateHeightWatchView.removeOnLayoutChangeListener(templateHeightChangeListener);
        }
        templateHeightWatchView = null;
        templateHeightChangeListener = null;
    }

    private void capViewHeightIfNeeded(@NonNull View view, int maxHeight) {
        final int currentHeight = view.getHeight();
        if (currentHeight <= 0 || currentHeight <= maxHeight) {
            return;
        }
        final ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null || params.height == maxHeight) {
            return;
        }
        params.height = maxHeight;
        view.setLayoutParams(params);
    }
}
