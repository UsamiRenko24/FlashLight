package com.name.flashlight.integration.ads;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.secmtp.sdk.core.api.ATAdInfo;


public class SplashAdvertCallback implements AdvertCallback<SplashAdvert> {

    @Override
    public void onAdvertStart(@NonNull SplashAdvert splashAdvert) {
    }

    @Override
    public void onAdvertRequestPre(@NonNull SplashAdvert splashAdvert, @NonNull ATAdInfoExt adInfoExt) {
    }

    @Override
    public void onAdvertRequestAlt(@NonNull SplashAdvert splashAdvert, @NonNull ATAdInfoExt adInfoExt) {
    }

    @Override
    public void onAdvertLoaded(@NonNull SplashAdvert splashAdvert) {
    }

    @Override
    public void onAdvertLoadFail(@NonNull SplashAdvert splashAdvert, @Nullable String adError) {
    }

    @Override
    public void onAdvertShow(@NonNull SplashAdvert splashAdvert, @Nullable ATAdInfo atAdInfo) {
    }

    @Override
    public void onAdvertShowFail(@NonNull SplashAdvert splashAdvert) {
    }

    @Override
    public void onAdvertClicked(@NonNull SplashAdvert splashAdvert, @Nullable ATAdInfo atAdInfo) {
    }

    @Override
    public void onAdvertClose(@NonNull SplashAdvert splashAdvert, @Nullable ATAdInfo atAdInfo) {
    }

    @Override
    public void onAdvertRevenue(@NonNull SplashAdvert splashAdvert, @Nullable ATAdInfo atAdInfo) {
    }
}