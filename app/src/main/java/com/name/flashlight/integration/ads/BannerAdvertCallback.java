package com.name.flashlight.integration.ads;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.secmtp.sdk.core.api.ATAdInfo;

public class BannerAdvertCallback implements AdvertCallback<BannerAdvert> {

    @Override
    public void onAdvertStart(@NonNull BannerAdvert bannerAdvert) {
    }

    @Override
    public void onAdvertRequestPre(@NonNull BannerAdvert bannerAdvert, @NonNull ATAdInfoExt adInfoExt) {
    }

    @Override
    public void onAdvertRequestAlt(@NonNull BannerAdvert bannerAdvert, @NonNull ATAdInfoExt adInfoExt) {
    }

    @Override
    public void onAdvertLoaded(@NonNull BannerAdvert bannerAdvert) {
    }

    @Override
    public void onAdvertLoadFail(@NonNull BannerAdvert bannerAdvert, @Nullable String adError) {
    }

    @Override
    public void onAdvertShow(@NonNull BannerAdvert bannerAdvert, @Nullable ATAdInfo atAdInfo) {
    }

    @Override
    public void onAdvertShowFail(@NonNull BannerAdvert bannerAdvert) {
    }

    @Override
    public void onAdvertClicked(@NonNull BannerAdvert bannerAdvert, @Nullable ATAdInfo atAdInfo) {
    }

    @Override
    public void onAdvertClose(@NonNull BannerAdvert bannerAdvert, @Nullable ATAdInfo atAdInfo) {
    }

    @Override
    public void onAdvertRevenue(@NonNull BannerAdvert bannerAdvert, @Nullable ATAdInfo atAdInfo) {
    }
}