package com.name.flashlight.integration.ads;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.secmtp.sdk.core.api.ATAdStatusInfo;
import com.secmtp.sdk.core.api.AdError;
import com.secmtp.sdk.nativead.api.ATNative;
import com.secmtp.sdk.nativead.api.ATNativeNetworkListener;
import com.secmtp.sdk.nativead.api.NativeAd;

/**
 * Native 广告按 placement 维度共享 load 阶段，避免同广告位多实例并发打 SDK。
 * <p>
 * - load：由 {@link PlacementSharedLoadCoordinator} 统一协调 + 分发回调
 * - show/render：由 {@link NativeAdvert} 自己接管
 */
final class PlacementNativeAdManager {
    private static final PlacementSharedLoadCoordinator<NativeAdvert, ATNative> sCoordinator =
            new PlacementSharedLoadCoordinator<>(new NativeSdkAdapter());

    private PlacementNativeAdManager() {
    }

    static void setGlobalAppContext(@Nullable Context context) {
        sCoordinator.setGlobalAppContext(context);
    }

    static void attachOwner(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull String placementId,
            @NonNull NativeAdvert owner
    ) {
        sCoordinator.attachOwner(lifecycleOwner, placementId, owner);
    }

    static void detachOwner(@NonNull String placementId, @NonNull NativeAdvert owner) {
        sCoordinator.detachOwner(placementId, owner);
    }

    static boolean isReady(@NonNull LifecycleOwner lifecycleOwner, @NonNull String placementId) {
        return sCoordinator.isReady(lifecycleOwner, placementId);
    }

    static boolean isLoading(@NonNull LifecycleOwner lifecycleOwner, @NonNull String placementId) {
        return sCoordinator.isLoading(lifecycleOwner, placementId);
    }

    static void requestLoad(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull String placementId,
            @NonNull NativeAdvert owner,
            boolean forceReload
    ) {
        sCoordinator.requestLoad(lifecycleOwner, placementId, owner, forceReload);
    }

    @Nullable
    static NativeAd getNativeAd(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull String placementId
    ) {
        final ATNative ad = getSharedAd(lifecycleOwner, placementId);
        if (ad == null) return null;
        try {
            return ad.getNativeAd();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static ATNative getSharedAd(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull String placementId
    ) {
        return sCoordinator.getAd(lifecycleOwner, placementId);
    }

    private static final class NativeSdkAdapter implements PlacementSharedLoadCoordinator.SdkAdapter<ATNative> {
        @NonNull
        @Override
        public ATNative createAd(
                @NonNull Context appContext,
                @NonNull String placementId,
                @NonNull PlacementSharedLoadCoordinator.LoadEvents events
        ) {
            return new ATNative(appContext, placementId, new ATNativeNetworkListener() {
                @Override
                public void onNativeAdLoaded() {
                    events.onLoadSuccess();
                }

                @Override
                public void onNativeAdLoadFail(AdError adError) {
                    events.onLoadFailure(adError != null ? adError.toString() : null);
                }
            });
        }

        @Override
        public boolean isReady(@NonNull ATNative ad) {
            final ATAdStatusInfo statusInfo = ad.checkAdStatus();
            return statusInfo != null && statusInfo.isReady();
        }

        @Override
        public boolean isLoading(@NonNull ATNative ad) {
            final ATAdStatusInfo statusInfo = ad.checkAdStatus();
            return statusInfo != null && statusInfo.isLoading();
        }

        @Override
        public void requestLoad(@NonNull ATNative ad) {
            ad.makeAdRequest();
        }
    }
}

