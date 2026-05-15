package com.name.flashlight.integration.ads;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.secmtp.sdk.nativead.api.ATNativeMaterial;
import com.secmtp.sdk.nativead.api.ATNativePrepareExInfo;

public interface INativeSelfRender {

    void onBindView(
            @NonNull Context context,
            @NonNull ATNativeMaterial atNativeMaterial,
            @NonNull ATNativePrepareExInfo atNativePrepareInfo
    );

    @NonNull
    View getSelfRenderView();
}
