package com.name.flashlight.integration.toolkit;

import android.app.Activity;

import androidx.annotation.NonNull;

public interface OnAppStatusChangedListener {

    void onForeground(@NonNull Activity activity);

    void onBackground(@NonNull Activity activity);
}