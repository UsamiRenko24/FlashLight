package com.name.flashlight.integration.ads;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.secmtp.sdk.core.api.ATAdInfo;

public class NativeAdvertCallback implements AdvertCallback<NativeAdvert> {

    @Override
    public void onAdvertStart(@NonNull NativeAdvert nativeAdvert) {
    }

    @Override
    public void onAdvertRequestPre(@NonNull NativeAdvert nativeAdvert, @NonNull ATAdInfoExt adInfoExt) {
    }

    @Override
    public void onAdvertRequestAlt(@NonNull NativeAdvert nativeAdvert, @NonNull ATAdInfoExt adInfoExt) {
    }

    @Override
    public void onAdvertLoaded(@NonNull NativeAdvert nativeAdvert) {
    }

    @Override
    public void onAdvertLoadFail(@NonNull NativeAdvert nativeAdvert, @Nullable String adError) {
    }

    @Override
    public void onAdvertShow(@NonNull NativeAdvert nativeAdvert, @Nullable ATAdInfo atAdInfo) {
    }

    @Override
    public void onAdvertShowFail(@NonNull NativeAdvert nativeAdvert) {
    }

    @Override
    public void onAdvertClicked(@NonNull NativeAdvert nativeAdvert, @Nullable ATAdInfo atAdInfo) {
    }

    @Override
    public void onAdvertClose(@NonNull NativeAdvert nativeAdvert, @Nullable ATAdInfo atAdInfo) {
    }

    @Override
    public void onAdvertRevenue(@NonNull NativeAdvert nativeAdvert, @Nullable ATAdInfo atAdInfo) {
    }

    @Nullable
    public INativeSelfRender createSelfRender(@Nullable ATAdInfo atAdInfo) {
        return null;
    }
}