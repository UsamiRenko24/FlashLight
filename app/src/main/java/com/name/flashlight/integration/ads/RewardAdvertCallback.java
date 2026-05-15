package com.name.flashlight.integration.ads;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.secmtp.sdk.core.api.ATAdInfo;


public class RewardAdvertCallback implements AdvertCallback<RewardAdvert> {

    @Override
    public void onAdvertStart(@NonNull RewardAdvert rewardAdvert) {
    }

    @Override
    public void onAdvertRequestPre(@NonNull RewardAdvert rewardAdvert, @NonNull ATAdInfoExt adInfoExt) {
    }

    @Override
    public void onAdvertRequestAlt(@NonNull RewardAdvert rewardAdvert, @NonNull ATAdInfoExt adInfoExt) {
    }

    @Override
    public void onAdvertLoaded(@NonNull RewardAdvert rewardAdvert) {
    }

    @Override
    public void onAdvertLoadFail(@NonNull RewardAdvert rewardAdvert, @Nullable String adError) {
    }

    @Override
    public void onAdvertShow(@NonNull RewardAdvert rewardAdvert, @Nullable ATAdInfo atAdInfo) {
    }

    @Override
    public void onAdvertShowFail(@NonNull RewardAdvert rewardAdvert) {
    }

    @Override
    public void onAdvertClicked(@NonNull RewardAdvert rewardAdvert, @Nullable ATAdInfo atAdInfo) {
    }

    @Override
    public void onAdvertClose(@NonNull RewardAdvert rewardAdvert, @Nullable ATAdInfo atAdInfo) {
    }

    @Override
    public void onAdvertRevenue(@NonNull RewardAdvert rewardAdvert, @Nullable ATAdInfo atAdInfo) {
    }

    public void onAdvertReward(@NonNull RewardAdvert rewardAdvert, @Nullable ATAdInfo atAdInfo) {
    }
}