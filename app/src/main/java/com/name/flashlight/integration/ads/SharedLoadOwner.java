package com.name.flashlight.integration.ads;

import androidx.annotation.Nullable;

interface SharedLoadOwner {
    void onSharedRequestPre();

    void onSharedAdLoaded(boolean adLoadedReal);

    void onSharedAdLoadFail(@Nullable String error);
}
