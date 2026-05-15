package com.name.flashlight.integration.ads;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.secmtp.sdk.core.api.ATAdInfo;
import com.secmtp.sdk.core.api.ATAdStatusInfo;
import com.secmtp.sdk.core.api.AdError;
import com.secmtp.sdk.interstitial.api.ATInterstitial;
import com.secmtp.sdk.interstitial.api.ATInterstitialListener;

final class PlacementInterstitialAdManager {
    private static final PlacementSharedLoadCoordinator<InsertAdvert, ATInterstitial> sCoordinator =
            new PlacementSharedLoadCoordinator<>(new InterstitialSdkAdapter());

    private PlacementInterstitialAdManager() {
    }

    static void setGlobalAppContext(@Nullable Context context) {
        sCoordinator.setGlobalAppContext(context);
    }

    static void attachOwner(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull String placementId,
            @NonNull InsertAdvert owner
    ) {
        sCoordinator.attachOwner(lifecycleOwner, placementId, owner);
    }

    static void detachOwner(@NonNull String placementId, @NonNull InsertAdvert owner) {
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
            @NonNull InsertAdvert owner,
            boolean forceReload
    ) {
        sCoordinator.requestLoad(lifecycleOwner, placementId, owner, forceReload);
    }

    private static final class InterstitialSdkAdapter implements PlacementSharedLoadCoordinator.SdkAdapter<ATInterstitial> {
        @NonNull
        @Override
        public ATInterstitial createAd(
                @NonNull Context appContext,
                @NonNull String placementId,
                @NonNull PlacementSharedLoadCoordinator.LoadEvents events
        ) {
            final ATInterstitial ad = new ATInterstitial(appContext, placementId);
            ad.setAdListener(new ATInterstitialListener() {
                @Override
                public void onInterstitialAdLoaded() {
                    events.onLoadSuccess();
                }

                @Override
                public void onInterstitialAdLoadFail(@Nullable AdError adError) {
                    events.onLoadFailure(adError != null ? adError.toString() : null);
                }

                @Override
                public void onInterstitialAdClicked(@Nullable ATAdInfo atAdInfo) {
                }

                @Override
                public void onInterstitialAdShow(@Nullable ATAdInfo atAdInfo) {
                }

                @Override
                public void onInterstitialAdClose(@Nullable ATAdInfo atAdInfo) {
                }

                @Override
                public void onInterstitialAdVideoStart(@Nullable ATAdInfo atAdInfo) {
                }

                @Override
                public void onInterstitialAdVideoEnd(@Nullable ATAdInfo atAdInfo) {
                }

                @Override
                public void onInterstitialAdVideoError(@Nullable AdError adError) {
                }
            });
            return ad;
        }

        @Override
        public boolean isReady(@NonNull ATInterstitial ad) {
            return ad.isAdReady();
        }

        @Override
        public boolean isLoading(@NonNull ATInterstitial ad) {
            final ATAdStatusInfo statusInfo = ad.checkAdStatus();
            return statusInfo != null && statusInfo.isLoading();
        }

        @Override
        public void requestLoad(@NonNull ATInterstitial ad) {
            ad.load();
        }
    }
}
