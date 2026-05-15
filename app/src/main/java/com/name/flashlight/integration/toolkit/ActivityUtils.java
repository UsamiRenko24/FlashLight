package com.name.flashlight.integration.toolkit;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ActivityUtils implements Application.ActivityLifecycleCallbacks {
    private final LinkedList<Activity> activityStack = new LinkedList<>();
    private final List<OnAppStatusChangedListener> mStatusListeners = new CopyOnWriteArrayList<>();
    // 使用 WeakHashMap，避免 Activity 泄漏
    private static final Map<Activity, String> AFFINITY_CACHE = new WeakHashMap<>();

    private int mForegroundCount = 0;
    private int mConfigCount = 0;

    @Nullable
    private Boolean mIsBackground = null;
    public static boolean isActivityAlive(final Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    @Nullable
    public static Activity currentActivity() {
        return Holder.INSTANCE.activityStack.isEmpty() ? null : Holder.INSTANCE.activityStack.getFirst();
    }

    public static LinkedList<Activity> getActivityList() {
        return Holder.INSTANCE.activityStack;
    }

    public static void init(@NonNull Application application) {
        try {
            application.registerActivityLifecycleCallbacks(Holder.INSTANCE);
        } catch (Throwable ignored) {
        }
    }

    public static void startActivity(@NonNull Intent intent) {
        startActivity(intent, null);
    }

    public static void startActivity(@NonNull Intent intent, @Nullable Bundle bundle) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (bundle != null) {
            intent.putExtras(bundle);
        }
        try {
            AppUtils.getApp().startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    /**
     * Start an Activity
     */
    public static void startActivity(@NonNull Class<? extends Activity> cls) {
        startActivity(cls, null);
    }

    /**
     * Start an Activity
     */
    public static void startActivity(
            @NonNull Class<? extends Activity> cls,
            @Nullable Bundle bundle
    ) {
        if (AppUtils.getApp() != null) {
            final Intent intent = new Intent(AppUtils.getApp(), cls);
            startActivity(intent, bundle);
        }
    }

    /**
     * Finish a specific Activity
     */
    public static void finishActivity(@NonNull Activity activity) {
        finishActivity(activity, true);
    }

    /**
     * Finish a specific Activity
     */
    public static void finishActivity(@NonNull Activity activity, boolean isLoadAnim) {
        activity.finish();

        if (!isLoadAnim) {
            activity.overridePendingTransition(0, 0);
        }
    }

    public static void exitApp() {
        List<Activity> activityList = getActivityList();
        for (Activity act : activityList) {
            // sActivityList remove the index activity at onActivityDestroyed
            act.finishAffinity();
            act.overridePendingTransition(0, 0);
        }
    }

    @Nullable
    public static Activity getTopActivity() {
        List<Activity> activityList = getActivityList();
        for (Activity activity : activityList) {
            if (!isActivityAlive(activity)) {
                continue;
            }
            return activity;
        }
        return null;
    }

    /**
     * 获取 Activity 的 taskAffinity
     *
     * @param activity Activity 实例
     * @return taskAffinity，如果未配置则返回 applicationId；异常时返回 null
     */
    @Nullable
    public static String getTaskAffinity(Activity activity) {
        if (activity == null) {
            return null;
        }

        // 优先走缓存
        String cached = AFFINITY_CACHE.get(activity);
        if (cached != null) {
            return cached;
        }

        try {
            ActivityInfo info = activity.getPackageManager()
                    .getActivityInfo(activity.getComponentName(), 0);
            String affinity = info.taskAffinity;

            // 缓存结果
            AFFINITY_CACHE.put(activity, affinity);
            return affinity;

        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 按 taskAffinity finish Activity，可排除指定的 Activity
     *
     * @param targetAffinity  目标 taskAffinity
     * @param excludeActivity 需要排除的 Activity，不会被 finish
     */
    public static void finishByTaskAffinity(String targetAffinity, @Nullable Activity excludeActivity) {
        if (TextUtils.isEmpty(targetAffinity)) {
            return;
        }

        for (Activity activity : new ArrayList<>(getActivityList())) {
            // 跳过需要排除的 Activity
            if (activity == excludeActivity) {
                continue;
            }

            final String affinity = getTaskAffinity(activity);
            if (targetAffinity.equals(affinity) && isActivityAlive(activity)) {
                finishActivity(activity, false);
            }
        }
    }

    public static void addOnAppStatusChangedListener(final OnAppStatusChangedListener listener) {
        Holder.INSTANCE.mStatusListeners.add(listener);
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
        if (activityStack.isEmpty()) {
            postStatus(activity, true);
        }
        setTopActivity(activity);
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        activityStack.remove(activity);
        AFFINITY_CACHE.remove(activity);
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {

    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        setTopActivity(activity);

        if (Boolean.TRUE.equals(mIsBackground)) {
            postStatus(activity, true);
        }
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {

    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        if (Boolean.FALSE.equals(mIsBackground)) {
            setTopActivity(activity);
        }
        if (mConfigCount < 0) {
            ++mConfigCount;
        } else {
            ++mForegroundCount;
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        if (activity.isChangingConfigurations()) {
            --mConfigCount;
        } else {
            --mForegroundCount;

            if (mForegroundCount <= 0) {
                postStatus(activity, false);
            }
        }
    }

    private void setTopActivity(Activity activity) {
        if (activityStack.contains(activity)) {
            if (!activityStack.getFirst().equals(activity)) {
                activityStack.remove(activity);
                activityStack.addFirst(activity);
            }
        } else {
            activityStack.addFirst(activity);
        }
    }

    private static final class Holder {
        private static final ActivityUtils INSTANCE = new ActivityUtils();
    }

    public static void removeOnAppStatusChangedListener(final OnAppStatusChangedListener listener) {
        Holder.INSTANCE.mStatusListeners.remove(listener);
    }

    private void postStatus(final Activity activity, final boolean isForeground) {
        mIsBackground = !isForeground;

        if (mStatusListeners.isEmpty()) return;
        for (OnAppStatusChangedListener statusListener : mStatusListeners) {
            if (isForeground) {
                statusListener.onForeground(activity);
            } else {
                statusListener.onBackground(activity);
            }
        }
    }
}