package com.name.flashlight.integration.toolkit;

import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.cbin.phone.cleaner.R;
import com.cbin.phone.cleaner.integration.toaster.Toaster;


public final class ToastUtils {

    public static void showShort(@StringRes int stringId) {
        show(stringId, true);
    }

    public static void showShort(@NonNull CharSequence sequence) {
        show(sequence, true);
    }

    public static void showLong(@StringRes int stringId) {
        show(stringId, false);
    }

    public static void showLong(@NonNull CharSequence sequence) {
        show(sequence, false);
    }

    private static void show(
            @StringRes int stringId,
            boolean isShowShort
    ) {
        show(AppUtils.getApp().getString(stringId), isShowShort);
    }

    private static void show(
            @NonNull CharSequence sequence,
            boolean isShowShort
    ) {
        try {
            final int yOffset = AppUtils.getApp().getResources().getDimensionPixelOffset(R.dimen.sw_dp_48);
            Toaster.setView(R.layout.layout_custom_toast);
            Toaster.setGravity(Gravity.BOTTOM, 0, yOffset);

            if (isShowShort) {
                Toaster.showShort(sequence);
            } else {
                Toaster.showLong(sequence);
            }
        } catch (Throwable ignored) {
        }
    }
}