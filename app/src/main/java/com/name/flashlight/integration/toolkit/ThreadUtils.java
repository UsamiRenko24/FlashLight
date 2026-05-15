package com.name.flashlight.integration.toolkit;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;

public final class ThreadUtils {
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    public static void runOnUiThread(@NonNull final Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            HANDLER.post(runnable);
        }
    }

    public static void runOnUiThread(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull Runnable runnable
    ) {
        LifecycleUtils.launch(lifecycleOwner, runnable);
    }

    public static void runOnUiThreadDelayed(@NonNull final Runnable runnable, long delayMillis) {
        HANDLER.postDelayed(runnable, delayMillis);
    }

    public static void runOnUiThreadDelayed(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull final Runnable runnable,
            long delayMillis
    ) {
        HANDLER.postDelayed(() -> LifecycleUtils.launch(lifecycleOwner, runnable), delayMillis);
    }

    public static void runOnAsyncThread(@NonNull Runnable runnable) {
        final Thread thread = new Thread(runnable);
        thread.start();
    }

    @NonNull
    public static Thread runOnAsyncThread(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull Runnable runnable
    ) {
        final Thread thread = new Thread(runnable);
        thread.start();

        LifecycleUtils.launchWhenDestroy(lifecycleOwner, () -> interruptThread(thread));
        return thread;
    }

    public static boolean isInterrupted() {
        return Thread.currentThread().isInterrupted();
    }

    public static void interruptThread(Thread thread) {
        if (thread == null) {
            return;
        }
        try {
            if (thread.isAlive() && !thread.isInterrupted()) {
                thread.interrupt();
            }
        } catch (Throwable ignored) {
        }
    }
}