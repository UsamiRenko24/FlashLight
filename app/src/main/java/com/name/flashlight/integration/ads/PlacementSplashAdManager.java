package com.name.flashlight.integration.ads;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.secmtp.sdk.core.api.ATAdInfo;
import com.secmtp.sdk.core.api.ATAdStatusInfo;
import com.secmtp.sdk.core.api.AdError;
import com.secmtp.sdk.splashad.api.ATSplashAd;
import com.secmtp.sdk.splashad.api.ATSplashAdExtraInfo;
import com.secmtp.sdk.splashad.api.ATSplashAdListener;

final class PlacementSplashAdManager {
    static final int SDK_FETCH_AD_TIMEOUT = 20 * 1000;

    private static final PlacementSharedLoadCoordinator<SplashAdvert, ATSplashAd> sCoordinator =
            new PlacementSharedLoadCoordinator<>(new SplashSdkAdapter());

    private PlacementSplashAdManager() {
    }

    static void setGlobalAppContext(@Nullable Context context) {
        sCoordinator.setGlobalAppContext(context);
    }

    static void attachOwner(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull String placementId,
            @NonNull SplashAdvert owner
    ) {
        sCoordinator.attachOwner(lifecycleOwner, placementId, owner);
    }

    static void detachOwner(@NonNull String placementId, @NonNull SplashAdvert owner) {
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
            @NonNull SplashAdvert owner,
            boolean forceReload
    ) {
        sCoordinator.requestLoad(lifecycleOwner, placementId, owner, forceReload);
    }

    private static final class SplashSdkAdapter implements PlacementSharedLoadCoordinator.SdkAdapter<ATSplashAd> {
        @NonNull
        @Override
        public ATSplashAd createAd(
                @NonNull Context appContext,
                @NonNull String placementId,
                @NonNull PlacementSharedLoadCoordinator.LoadEvents events
        ) {
            return new ATSplashAd(
                    appContext,
                    placementId,
                    new ATSplashAdListener() {
                        @Override
                        public void onAdLoaded(boolean isTimeout) {
                            if (isTimeout) {
                                events.onLoadFailure(null);
                            } else {
                                events.onLoadSuccess();
                            }
                        }

                        @Override
                        public void onAdLoadTimeout() {
                            events.onLoadFailure(null);
                        }

                        @Override
                        public void onNoAdError(@Nullable AdError adError) {
                            events.onLoadFailure(adError != null ? adError.toString() : null);
                        }

                        @Override
                        public void onAdClick(@Nullable ATAdInfo atAdInfo) {
                        }

                        @Override
                        public void onAdShow(@Nullable ATAdInfo atAdInfo) {
                        }

                        @Override
                        public void onAdDismiss(
                                @Nullable ATAdInfo atAdInfo,
                                @Nullable ATSplashAdExtraInfo atSplashAdExtraInfo
                        ) {
                        }
                    },
                    SDK_FETCH_AD_TIMEOUT
            );
        }

        @Override
        public boolean isReady(@NonNull ATSplashAd ad) {
            return ad.isAdReady();
        }

        @Override
        public boolean isLoading(@NonNull ATSplashAd ad) {
            final ATAdStatusInfo statusInfo = ad.checkAdStatus();
            return statusInfo != null && statusInfo.isLoading();
        }

        @Override
        public void requestLoad(@NonNull ATSplashAd ad) {
            ad.loadAd();
        }
    }
}
