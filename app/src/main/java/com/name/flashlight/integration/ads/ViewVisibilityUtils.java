package com.name.flashlight.integration.ads;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cbin.phone.cleaner.R;

import java.util.Collections;
import java.util.List;

public final class ViewVisibilityUtils {
    private static final int KEY_VISIBILITY = R.id.view_visibility_state_tag;
    private static final int KEY_REGISTRATION = R.id.view_visibility_has_listener_tag;
    private static final float MIN_VISIBLE_AREA_RATIO = 0.1f;
    private static final float MIN_VISIBLE_ALPHA = 0.01f;

    private ViewVisibilityUtils() {
    }

    public static void onVisibilityChange(
            @NonNull View targetView,
            @NonNull ViewVisibilityCallback callback
    ) {
        onVisibilityChange(targetView, Collections.emptyList(), true, callback);
    }

    public static void onVisibilityChange(
            @NonNull View targetView,
            @NonNull List<ViewGroup> viewGroups,
            boolean needScrollListener,
            @NonNull ViewVisibilityCallback callback
    ) {
        onVisibilityChange(targetView, viewGroups, needScrollListener, false, callback);
    }

    public static void onVisibilityChange(
            @NonNull View targetView,
            @NonNull List<ViewGroup> viewGroups,
            boolean needScrollListener,
            boolean dispatchInitialState,
            @NonNull ViewVisibilityCallback callback
    ) {
        final Object registrationTag = targetView.getTag(KEY_REGISTRATION);
        if (registrationTag instanceof VisibilityRegistration) {
            ((VisibilityRegistration) registrationTag).unregister();
        }
        targetView.setTag(KEY_VISIBILITY, null);

        final Runnable checkVisibility = () -> {
            final boolean isInScreenNow = isInScreen(targetView);
            dispatchVisibilityIfNeeded(targetView, callback, isInScreenNow, isInScreenNow);
        };

        final VisibilityLayoutListener layoutListener = new VisibilityLayoutListener(
                targetView,
                callback,
                checkVisibility,
                KEY_VISIBILITY
        );

        final ViewGroup.OnHierarchyChangeListener hierarchyChangeListener = new ViewGroup.OnHierarchyChangeListener() {
            @Override
            public void onChildViewAdded(View parent, View child) {
                layoutListener.setAddedView(child);
            }

            @Override
            public void onChildViewRemoved(View parent, View child) {
                layoutListener.setAddedView(null);
            }
        };

        final ViewTreeObserver.OnScrollChangedListener scrollListener = needScrollListener
                ? checkVisibility::run
                : null;

        final ViewTreeObserver.OnWindowFocusChangeListener focusChangeListener =
                createFocusChangeListener(targetView, callback);

        final View.OnAttachStateChangeListener attachStateChangeListener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                registerHierarchyListeners(viewGroups, hierarchyChangeListener);
                registerViewTreeObservers(v, layoutListener, scrollListener, focusChangeListener);
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                dispatchVisibilityIfNeeded(v, callback, false, false);
                unregisterViewTreeObservers(v, layoutListener, scrollListener, focusChangeListener);
                unregisterHierarchyListeners(viewGroups);
                layoutListener.setAddedView(null);
            }
        };

        if (targetView.isAttachedToWindow()) {
            registerHierarchyListeners(viewGroups, hierarchyChangeListener);
            registerViewTreeObservers(targetView, layoutListener, scrollListener, focusChangeListener);
        }
        targetView.addOnAttachStateChangeListener(attachStateChangeListener);

        if (dispatchInitialState) {
            final boolean isInScreen = isInScreen(targetView);
            callback.onViewVisibilityChanged(targetView, isInScreen);
            targetView.setTag(KEY_VISIBILITY, isInScreen);
        }

        targetView.setTag(
                KEY_REGISTRATION,
                new VisibilityRegistration(
                        targetView,
                        viewGroups,
                        layoutListener,
                        scrollListener,
                        focusChangeListener,
                        attachStateChangeListener
                )
        );
    }

    public static boolean isInScreen(@NonNull View view) {
        if (!view.isAttachedToWindow() || view.getVisibility() != View.VISIBLE) {
            return false;
        }

        final int width = view.getWidth();
        final int height = view.getHeight();
        if (width <= 0 || height <= 0) {
            return false;
        }

        if (!view.getLocalVisibleRect(new Rect())) {
            return false;
        }

        final Rect globalVisibleRect = new Rect();
        if (!view.getGlobalVisibleRect(globalVisibleRect)) {
            return false;
        }

        final int totalArea = width * height;
        final int visibleArea = globalVisibleRect.width() * globalVisibleRect.height();
        if (totalArea <= 0 || visibleArea <= 0) {
            return false;
        }

        final float visibleRatio = (float) visibleArea / (float) totalArea;
        if (visibleRatio < MIN_VISIBLE_AREA_RATIO) {
            return false;
        }

        final float combinedAlpha = calculateCombinedAlpha(view);
        return combinedAlpha >= MIN_VISIBLE_ALPHA;
    }

    @NonNull
    private static ViewTreeObserver.OnWindowFocusChangeListener createFocusChangeListener(
            @NonNull View targetView,
            @NonNull ViewVisibilityCallback callback
    ) {
        return hasFocus -> {
            final Object lastVisibilityTag = targetView.getTag(KEY_VISIBILITY);
            final Boolean lastVisibility = lastVisibilityTag instanceof Boolean ? (Boolean) lastVisibilityTag : null;
            final boolean isInScreen = isInScreen(targetView);

            if (hasFocus) {
                if (lastVisibility == null || lastVisibility != isInScreen) {
                    callback.onViewVisibilityChanged(targetView, isInScreen);
                    targetView.setTag(KEY_VISIBILITY, isInScreen);
                }
            } else {
                if (Boolean.TRUE.equals(lastVisibility)) {
                    callback.onViewVisibilityChanged(targetView, false);
                    targetView.setTag(KEY_VISIBILITY, false);
                }
            }
        };
    }

    private static void dispatchVisibilityIfNeeded(
            @NonNull View targetView,
            @NonNull ViewVisibilityCallback callback,
            boolean newVisible,
            boolean dispatchWhenLastNull
    ) {
        final Object lastVisibilityTag = targetView.getTag(KEY_VISIBILITY);
        final Boolean lastVisibility = lastVisibilityTag instanceof Boolean ? (Boolean) lastVisibilityTag : null;
        final boolean shouldDispatch = lastVisibility == null ? dispatchWhenLastNull : lastVisibility != newVisible;
        if (!shouldDispatch) {
            return;
        }
        callback.onViewVisibilityChanged(targetView, newVisible);
        targetView.setTag(KEY_VISIBILITY, newVisible);
    }

    private static void registerHierarchyListeners(
            @NonNull List<ViewGroup> viewGroups,
            @NonNull ViewGroup.OnHierarchyChangeListener hierarchyChangeListener
    ) {
        if (viewGroups.isEmpty()) {
            return;
        }
        for (ViewGroup viewGroup : viewGroups) {
            viewGroup.setOnHierarchyChangeListener(hierarchyChangeListener);
        }
    }

    private static void unregisterHierarchyListeners(@NonNull List<ViewGroup> viewGroups) {
        if (viewGroups.isEmpty()) {
            return;
        }
        for (ViewGroup viewGroup : viewGroups) {
            viewGroup.setOnHierarchyChangeListener(null);
        }
    }

    private static void registerViewTreeObservers(
            @NonNull View view,
            @NonNull VisibilityLayoutListener layoutListener,
            @Nullable ViewTreeObserver.OnScrollChangedListener scrollListener,
            @NonNull ViewTreeObserver.OnWindowFocusChangeListener focusChangeListener
    ) {
        view.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        if (scrollListener != null) {
            view.getViewTreeObserver().addOnScrollChangedListener(scrollListener);
        }
        view.getViewTreeObserver().addOnWindowFocusChangeListener(focusChangeListener);
    }

    @SuppressWarnings("deprecation")
    private static void unregisterViewTreeObservers(
            @NonNull View view,
            @NonNull VisibilityLayoutListener layoutListener,
            @Nullable ViewTreeObserver.OnScrollChangedListener scrollListener,
            @NonNull ViewTreeObserver.OnWindowFocusChangeListener focusChangeListener
    ) {
        final ViewTreeObserver observer = view.getViewTreeObserver();
        try {
            if (observer.isAlive()) {
                try {
                    observer.removeOnGlobalLayoutListener(layoutListener);
                } catch (Exception ignored) {
                    observer.removeGlobalOnLayoutListener(layoutListener);
                }
                observer.removeOnWindowFocusChangeListener(focusChangeListener);
                if (scrollListener != null) {
                    observer.removeOnScrollChangedListener(scrollListener);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static float calculateCombinedAlpha(@NonNull View view) {
        View current = view;
        float alpha = 1f;
        while (current != null) {
            alpha *= current.getAlpha();
            final ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return alpha;
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static final class VisibilityRegistration {
        @NonNull
        private final View targetView;
        @NonNull
        private final List<ViewGroup> viewGroups;
        @NonNull
        private final VisibilityLayoutListener layoutListener;
        @Nullable
        private final ViewTreeObserver.OnScrollChangedListener scrollListener;
        @NonNull
        private final ViewTreeObserver.OnWindowFocusChangeListener focusChangeListener;
        @NonNull
        private final View.OnAttachStateChangeListener attachStateChangeListener;

        private VisibilityRegistration(
                @NonNull View targetView,
                @NonNull List<ViewGroup> viewGroups,
                @NonNull VisibilityLayoutListener layoutListener,
                @Nullable ViewTreeObserver.OnScrollChangedListener scrollListener,
                @NonNull ViewTreeObserver.OnWindowFocusChangeListener focusChangeListener,
                @NonNull View.OnAttachStateChangeListener attachStateChangeListener
        ) {
            this.targetView = targetView;
            this.viewGroups = viewGroups;
            this.layoutListener = layoutListener;
            this.scrollListener = scrollListener;
            this.focusChangeListener = focusChangeListener;
            this.attachStateChangeListener = attachStateChangeListener;
        }

        private void unregister() {
            unregisterViewTreeObservers(
                    targetView,
                    layoutListener,
                    scrollListener,
                    focusChangeListener
            );
            unregisterHierarchyListeners(viewGroups);
            layoutListener.setAddedView(null);
            targetView.removeOnAttachStateChangeListener(attachStateChangeListener);
            if (targetView.getTag(KEY_REGISTRATION) == this) {
                targetView.setTag(KEY_REGISTRATION, null);
            }
        }
    }
}
