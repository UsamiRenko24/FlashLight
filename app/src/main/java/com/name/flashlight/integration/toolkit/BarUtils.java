package com.name.flashlight.integration.toolkit;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.DialogFragment;

import java.util.function.Consumer;

public final class BarUtils {

    /**
     * 隐藏底部导航栏
     */
    public static void hideNavBar(@NonNull Activity activity) {
        hideNavBar(activity.getWindow());
    }

    /**
     * 隐藏底部导航栏
     */
    public static void hideNavBar(@NonNull DialogFragment dialogFragment) {
        final Dialog dialog = dialogFragment.getDialog();
        final Window window = dialog != null ? dialog.getWindow() : null;
        hideNavBar(window);
    }

    /**
     * 隐藏底部导航栏
     */
    public static void hideNavBar(Window window) {
        if (window == null) return;

        hasNavigationBarCompat(window, isSupportNavBar -> {
            try {
                final WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
                // 隐藏底部导航栏
                controller.hide(WindowInsetsCompat.Type.navigationBars());
                // 隐藏底部导航栏行为
                controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } catch (Throwable ignored) {
            }
        });
    }

    public static void hasNavigationBarCompat(
            @NonNull Window window,
            @NonNull Consumer<Boolean> callback
    ) {
        final View decorView = window.getDecorView();
        ViewCompat.setOnApplyWindowInsetsListener(decorView, (v, insets) -> {
            final Insets navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            final boolean hasNavBar = navInsets.bottom > 0;
            callback.accept(hasNavBar);
            return insets;
        });
        ViewCompat.requestApplyInsets(decorView);
    }
}