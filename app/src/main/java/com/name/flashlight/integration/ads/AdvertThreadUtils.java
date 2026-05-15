package com.name.flashlight.integration.ads;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

public final class AdvertThreadUtils {
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    public static void runOnUiThreadDelayed(@NonNull final Runnable runnable, long delayMillis) {
        HANDLER.postDelayed(runnable, delayMillis);
    }

    public static void runOnUiThread(@NonNull final Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            HANDLER.post(runnable);
        }
    }
}
