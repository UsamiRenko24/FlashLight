package com.name.flashlight.integration.ads;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.secmtp.sdk.core.api.ATAdInfo;

public class InsertAdvertCallback implements AdvertCallback<InsertAdvert> {

    @Override
    public void onAdvertStart(@NonNull InsertAdvert insertAdvert) {
    }

    @Override
    public void onAdvertRequestPre(@NonNull InsertAdvert insertAdvert, @NonNull ATAdInfoExt adInfoExt) {
    }

    @Override
    public void onAdvertRequestAlt(@NonNull InsertAdvert insertAdvert, @NonNull ATAdInfoExt adInfoExt) {
    }

    @Override
    public void onAdvertLoaded(@NonNull InsertAdvert insertAdvert) {
    }

    @Override
    public void onAdvertLoadFail(@NonNull InsertAdvert insertAdvert, @Nullable String adError) {
    }

    @Override
    public void onAdvertShow(@NonNull InsertAdvert insertAdvert, @Nullable ATAdInfo atAdInfo) {
    }

    @Override
    public void onAdvertShowFail(@NonNull InsertAdvert insertAdvert) {
    }

    @Override
    public void onAdvertClicked(@NonNull InsertAdvert insertAdvert, @Nullable ATAdInfo atAdInfo) {
    }

    @Override
    public void onAdvertClose(@NonNull InsertAdvert insertAdvert, @Nullable ATAdInfo atAdInfo) {
    }

    @Override
    public void onAdvertRevenue(@NonNull InsertAdvert insertAdvert, @Nullable ATAdInfo atAdInfo) {
    }
}