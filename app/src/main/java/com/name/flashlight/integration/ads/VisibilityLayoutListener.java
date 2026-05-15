package com.name.flashlight.integration.ads;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class VisibilityLayoutListener implements ViewTreeObserver.OnGlobalLayoutListener {
    @NonNull
    private final View targetView;
    @NonNull
    private final ViewVisibilityCallback callback;
    @NonNull
    private final Runnable visibilityCheck;
    private final int visibilityTagKey;

    @Nullable
    private View addedView;

    VisibilityLayoutListener(
            @NonNull View targetView,
            @NonNull ViewVisibilityCallback callback,
            @NonNull Runnable visibilityCheck,
            int visibilityTagKey
    ) {
        this.targetView = targetView;
        this.callback = callback;
        this.visibilityCheck = visibilityCheck;
        this.visibilityTagKey = visibilityTagKey;
    }

    void setAddedView(@Nullable View addedView) {
        this.addedView = addedView;
    }

    @Override
    public void onGlobalLayout() {
        final View overlay = addedView;
        if (overlay != null) {
            final Rect addedRect = new Rect();
            overlay.getGlobalVisibleRect(addedRect);

            final Rect targetRect = new Rect();
            targetView.getGlobalVisibleRect(targetRect);

            final boolean visible = !addedRect.contains(targetRect);
            callback.onViewVisibilityChanged(targetView, visible);
            targetView.setTag(visibilityTagKey, visible);
            return;
        }

        visibilityCheck.run();
    }
}
