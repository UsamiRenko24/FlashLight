package com.name.flashlight.integration.ads;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.secmtp.sdk.core.api.ATAdInfo;
import com.secmtp.sdk.core.api.ATAdStatusInfo;
import com.secmtp.sdk.core.api.AdError;
import com.secmtp.sdk.rewardvideo.api.ATRewardVideoAd;
import com.secmtp.sdk.rewardvideo.api.ATRewardVideoListener;

final class PlacementRewardVideoAdManager {
    private static final PlacementSharedLoadCoordinator<RewardAdvert, ATRewardVideoAd> sCoordinator =
            new PlacementSharedLoadCoordinator<>(new RewardVideoSdkAdapter());

    private PlacementRewardVideoAdManager() {
    }

    static void setGlobalAppContext(@Nullable Context context) {
        sCoordinator.setGlobalAppContext(context);
    }

    static void attachOwner(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull String placementId,
            @NonNull RewardAdvert owner
    ) {
        sCoordinator.attachOwner(lifecycleOwner, placementId, owner);
    }

    static void detachOwner(@NonNull String placementId, @NonNull RewardAdvert owner) {
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
            @NonNull RewardAdvert owner,
            boolean forceReload
    ) {
        sCoordinator.requestLoad(lifecycleOwner, placementId, owner, forceReload);
    }

    private static final class RewardVideoSdkAdapter implements PlacementSharedLoadCoordinator.SdkAdapter<ATRewardVideoAd> {
        @NonNull
        @Override
        public ATRewardVideoAd createAd(
                @NonNull Context appContext,
                @NonNull String placementId,
                @NonNull PlacementSharedLoadCoordinator.LoadEvents events
        ) {
            final ATRewardVideoAd ad = new ATRewardVideoAd(appContext, placementId);
            ad.setAdListener(new ATRewardVideoListener() {
                @Override
                public void onRewardedVideoAdLoaded() {
                    events.onLoadSuccess();
                }

                @Override
                public void onRewardedVideoAdFailed(AdError adError) {
                    events.onLoadFailure(adError != null ? adError.toString() : null);
                }

                @Override
                public void onRewardedVideoAdPlayClicked(ATAdInfo atAdInfo) {
                }

                @Override
                public void onRewardedVideoAdClosed(ATAdInfo atAdInfo) {
                }

                @Override
                public void onRewardedVideoAdPlayStart(ATAdInfo atAdInfo) {
                }

                @Override
                public void onRewardedVideoAdPlayEnd(ATAdInfo atAdInfo) {
                }

                @Override
                public void onRewardedVideoAdPlayFailed(AdError adError, ATAdInfo atAdInfo) {
                }

                @Override
                public void onReward(ATAdInfo atAdInfo) {
                }
            });
            return ad;
        }

        @Override
        public boolean isReady(@NonNull ATRewardVideoAd ad) {
            return ad.isAdReady();
        }

        @Override
        public boolean isLoading(@NonNull ATRewardVideoAd ad) {
            final ATAdStatusInfo statusInfo = ad.checkAdStatus();
            return statusInfo != null && statusInfo.isLoading();
        }

        @Override
        public void requestLoad(@NonNull ATRewardVideoAd ad) {
            ad.load();
        }
    }
}

