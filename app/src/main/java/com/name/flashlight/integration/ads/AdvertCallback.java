package com.name.flashlight.integration.ads;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.secmtp.sdk.core.api.ATAdInfo;

public interface AdvertCallback<T> {

    void onAdvertStart(@NonNull T t);

    void onAdvertRequestPre(@NonNull T t, @NonNull ATAdInfoExt adInfoExt);

    void onAdvertRequestAlt(@NonNull T t, @NonNull ATAdInfoExt adInfoExt);

    void onAdvertLoaded(@NonNull T t);

    void onAdvertLoadFail(@NonNull T t, @Nullable String adError);

    void onAdvertShow(@NonNull T t, @Nullable ATAdInfo atAdInfo);

    void onAdvertShowFail(@NonNull T t);

    void onAdvertClicked(@NonNull T t, @Nullable ATAdInfo atAdInfo);

    void onAdvertClose(@NonNull T t, @Nullable ATAdInfo atAdInfo);

    void onAdvertRevenue(@NonNull T t, @Nullable ATAdInfo atAdInfo);
}