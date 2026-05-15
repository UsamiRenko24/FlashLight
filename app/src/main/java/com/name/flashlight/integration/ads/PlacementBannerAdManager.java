package com.name.flashlight.integration.ads;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.secmtp.sdk.banner.api.ATBannerListener;
import com.secmtp.sdk.banner.api.ATBannerView;
import com.secmtp.sdk.core.api.ATAdConst;
import com.secmtp.sdk.core.api.ATAdInfo;
import com.secmtp.sdk.core.api.ATAdStatusInfo;
import com.secmtp.sdk.core.api.AdError;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 以 PlacementId 为单位缓存/复用 ATBannerView。
 * <p>
 * - Banner 场景推荐复用同一个 ATBannerView；频繁 new/destroy 容易出现“回调成功但不展示”等异常。
 * - 使用 ApplicationContext 创建，避免 Activity/Fragment 泄漏。
 */
public final class PlacementBannerAdManager {
    interface BannerLoadObserver {
        void onBannerLoadSuccess();

        void onBannerLoadFail(@Nullable String adError);
    }

    interface BannerEventObserver {
        void onBannerClicked(@Nullable ATAdInfo atAdInfo);

        void onBannerShow(@Nullable ATAdInfo atAdInfo);

        void onBannerClose(@Nullable ATAdInfo atAdInfo);

        void onBannerAutoRefreshed(@Nullable ATAdInfo atAdInfo);

        void onBannerRevenue(@Nullable ATAdInfo atAdInfo);
    }

    enum LoadAction {
        REUSE_READY,
        WAITING,
        START
    }

    private static final Object sLock = new Object();
    @NonNull
    private static final Map<String, ATBannerView> sCache = new HashMap<>();
    /**
     * placementId 维度的“加载中”状态（跨实例共享）。
     * <p>
     * 由于 Banner 场景复用同一个 {@link ATBannerView}，单纯依赖 SDK {@code checkAdStatus()} 可能存在时序不准问题，
     * 因此需要一个应用侧状态来提供更稳定的 isLoading 语义（参考其它广告位对 shared load 的做法）。
     */
    @NonNull
    private static final Map<String, Boolean> sLoading = new HashMap<>();
    /**
     * refCount：同一个 placementId 的多个 BannerAdvert 实例（跨页面/Fragment）共享同一个 ATBannerView 时，
     * 只有当所有实例都释放后才真正 destroy，避免一个页面销毁导致其它页面正在展示的 banner 被销毁。
     */
    private static final Map<String, Integer> sRefCount = new HashMap<>();
    @NonNull
    private static final Map<String, Set<BannerLoadObserver>> sPendingLoadObservers = new HashMap<>();
    @NonNull
    private static final Map<String, BannerEventObserver> sActiveEventObservers = new HashMap<>();

    private PlacementBannerAdManager() {
    }

    @NonNull
    public static ATBannerView obtain(
            @NonNull Context context,
            @NonNull String placementId
    ) {
        final Context appContext = context.getApplicationContext();
        synchronized (sLock) {
            ATBannerView view = sCache.get(placementId);
            if (view == null) {
                view = new ATBannerView(appContext);
                view.setPlacementId(placementId);
                view.setVisibility(View.VISIBLE);
                bindSdkCallbacks(placementId, view);
                sCache.put(placementId, view);
            } else {
                if (view.getVisibility() != View.VISIBLE) {
                    view.setVisibility(View.VISIBLE);
                }
            }
            // 注意：Map<String, Integer> 允许 value 为 null。
            // getOrDefault 在 value==null 且 key 存在时可能返回 null，导致后续自动拆箱 NPE。
            final Integer existingRef = sRefCount.get(placementId);
            final int refCount = (existingRef != null ? existingRef : 0) + 1;
            sRefCount.put(placementId, refCount);
            return view;
        }
    }

    public static void attachToContainer(
            @NonNull ViewGroup container,
            @NonNull ATBannerView bannerView
    ) {
        if (container.getVisibility() != View.VISIBLE) {
            container.setVisibility(View.VISIBLE);
        }
        if (bannerView.getVisibility() != View.VISIBLE) {
            bannerView.setVisibility(View.VISIBLE);
        }

        final ViewParent parent = bannerView.getParent();
        if (parent instanceof ViewGroup && parent != container) {
            try {
                ((ViewGroup) parent).removeView(bannerView);
            } catch (Throwable ignored) {
            }
        }
        if (bannerView.getParent() == null) {
            try {
                container.removeAllViews();
            } catch (Throwable ignored) {
            }
            container.addView(bannerView);
        } else if (container.getChildCount() != 1 || container.getChildAt(0) != bannerView) {
            try {
                container.removeAllViews();
            } catch (Throwable ignored) {
            }
            container.addView(bannerView);
        }
    }

    public static void setLocalExtra(
            @NonNull ATBannerView bannerView,
            int advertWidth,
            int advertHeight
    ) {
        final Map<String, Object> localMap = new HashMap<>();
        localMap.put(ATAdConst.KEY.AD_WIDTH, advertWidth);
        localMap.put(ATAdConst.KEY.AD_HEIGHT, advertHeight);
        bannerView.setLocalExtra(localMap);
    }

    public static void destroy(@NonNull String placementId) {
        final ATBannerView view;
        boolean shouldDestroy;
        synchronized (sLock) {
            // 见 obtain()：避免 value==null 时自动拆箱触发 NPE。
            final Integer existingRef = sRefCount.get(placementId);
            final int current = existingRef != null ? existingRef : 0;
            if (current <= 1) {
                sRefCount.remove(placementId);
                sLoading.remove(placementId);
                sPendingLoadObservers.remove(placementId);
                sActiveEventObservers.remove(placementId);
                view = sCache.remove(placementId);
                shouldDestroy = true;
            } else {
                sRefCount.put(placementId, current - 1);
                view = null;
                shouldDestroy = false;
            }
        }
        if (!shouldDestroy || view == null) return;
        destroyViewSafely(view);
    }

    public static void clearAll() {
        final Map<String, ATBannerView> copy;
        synchronized (sLock) {
            copy = new HashMap<>(sCache);
            sCache.clear();
            sRefCount.clear();
            sLoading.clear();
            sPendingLoadObservers.clear();
            sActiveEventObservers.clear();
        }
        for (ATBannerView view : copy.values()) {
            destroyViewSafely(view);
        }
    }

    static boolean isLoading(@NonNull String placementId) {
        synchronized (sLock) {
            final Boolean loading = sLoading.get(placementId);
            return loading != null && loading;
        }
    }

    static boolean isReady(@Nullable ATBannerView bannerView) {
        if (bannerView == null) {
            return false;
        }
        try {
            final ATAdStatusInfo atAdStatusInfo = bannerView.checkAdStatus();
            return atAdStatusInfo != null && atAdStatusInfo.isReady();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @NonNull
    static LoadAction requestLoad(
            @NonNull String placementId,
            @NonNull BannerLoadObserver observer,
            boolean forceReload
    ) {
        synchronized (sLock) {
            final ATBannerView bannerView = sCache.get(placementId);
            if (bannerView == null) {
                return LoadAction.START;
            }

            if (!forceReload && isReady(bannerView)) {
                return LoadAction.REUSE_READY;
            }

            Set<BannerLoadObserver> observers = sPendingLoadObservers.get(placementId);
            //noinspection Java8MapApi
            if (observers == null) {
                observers = new LinkedHashSet<>();
                sPendingLoadObservers.put(placementId, observers);
            }
            observers.add(observer);

            if (Boolean.TRUE.equals(sLoading.get(placementId))) {
                return LoadAction.WAITING;
            }

            sLoading.put(placementId, true);
            return LoadAction.START;
        }
    }

    static void unregisterLoadObserver(
            @NonNull String placementId,
            @NonNull BannerLoadObserver observer
    ) {
        synchronized (sLock) {
            final Set<BannerLoadObserver> observers = sPendingLoadObservers.get(placementId);
            if (observers != null) {
                observers.remove(observer);
                if (observers.isEmpty()) {
                    sPendingLoadObservers.remove(placementId);
                }
            }
        }
    }

    static void setActiveEventObserver(
            @NonNull String placementId,
            @Nullable BannerEventObserver observer
    ) {
        synchronized (sLock) {
            if (observer == null) {
                sActiveEventObservers.remove(placementId);
            } else {
                sActiveEventObservers.put(placementId, observer);
            }
        }
    }

    static void clearActiveEventObserver(
            @NonNull String placementId,
            @NonNull BannerEventObserver observer
    ) {
        synchronized (sLock) {
            final BannerEventObserver current = sActiveEventObservers.get(placementId);
            if (current == observer) {
                sActiveEventObservers.remove(placementId);
            }
        }
    }

    static void notifyLoadStartFailed(
            @NonNull String placementId,
            @Nullable String adError
    ) {
        final Set<BannerLoadObserver> observers;
        synchronized (sLock) {
            sLoading.remove(placementId);
            observers = removePendingObserversLocked(placementId);
        }
        notifyLoadFailed(observers, adError);
    }

    private static void bindSdkCallbacks(
            @NonNull String placementId,
            @NonNull ATBannerView view
    ) {
        view.setBannerAdListener(new ATBannerListener() {
            @Override
            public void onBannerLoaded() {
                final Set<BannerLoadObserver> observers;
                synchronized (sLock) {
                    sLoading.remove(placementId);
                    observers = removePendingObserversLocked(placementId);
                }
                for (BannerLoadObserver observer : observers) {
                    observer.onBannerLoadSuccess();
                }
            }

            @Override
            public void onBannerFailed(AdError adError) {
                final Set<BannerLoadObserver> observers;
                synchronized (sLock) {
                    sLoading.remove(placementId);
                    observers = removePendingObserversLocked(placementId);
                }
                notifyLoadFailed(observers, adError != null ? adError.toString() : null);
            }

            @Override
            public void onBannerClicked(ATAdInfo atAdInfo) {
                final BannerEventObserver observer = getActiveEventObserver(placementId);
                if (observer != null) {
                    observer.onBannerClicked(atAdInfo);
                }
            }

            @Override
            public void onBannerShow(ATAdInfo atAdInfo) {
                final BannerEventObserver observer = getActiveEventObserver(placementId);
                if (observer != null) {
                    observer.onBannerShow(atAdInfo);
                }
            }

            @Override
            public void onBannerClose(ATAdInfo atAdInfo) {
                final BannerEventObserver observer = getActiveEventObserver(placementId);
                if (observer != null) {
                    observer.onBannerClose(atAdInfo);
                }
            }

            @Override
            public void onBannerAutoRefreshed(ATAdInfo atAdInfo) {
                final BannerEventObserver observer = getActiveEventObserver(placementId);
                if (observer != null) {
                    observer.onBannerAutoRefreshed(atAdInfo);
                }
            }

            @Override
            public void onBannerAutoRefreshFail(AdError adError) {
            }
        });
        view.setAdRevenueListener(atAdInfo -> {
            final BannerEventObserver observer = getActiveEventObserver(placementId);
            if (observer != null) {
                observer.onBannerRevenue(atAdInfo);
            }
        });
    }

    @NonNull
    private static Set<BannerLoadObserver> removePendingObserversLocked(@NonNull String placementId) {
        final Set<BannerLoadObserver> observers = sPendingLoadObservers.remove(placementId);
        return observers != null ? new LinkedHashSet<>(observers) : new LinkedHashSet<>();
    }

    @Nullable
    private static BannerEventObserver getActiveEventObserver(@NonNull String placementId) {
        synchronized (sLock) {
            return sActiveEventObservers.get(placementId);
        }
    }

    private static void notifyLoadFailed(
            @NonNull Set<BannerLoadObserver> observers,
            @Nullable String adError
    ) {
        for (BannerLoadObserver observer : observers) {
            observer.onBannerLoadFail(adError);
        }
    }

    private static void destroyViewSafely(@NonNull ATBannerView view) {
        try {
            view.setBannerAdListener(null);
            view.setAdDownloadListener(null);
            view.setAdSourceStatusListener(null);
        } catch (Throwable ignored) {
        }
        final ViewParent parent = view.getParent();
        if (parent instanceof ViewGroup) {
            try {
                ((ViewGroup) parent).removeView(view);
            } catch (Throwable ignored) {
            }
        }
        try {
            view.destroy();
        } catch (Throwable ignored) {
        }
    }
}
