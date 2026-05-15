package com.name.flashlight.integration.toolkit;

import android.annotation.SuppressLint;
import android.app.Application;

import androidx.annotation.Nullable;

import java.lang.reflect.Field;

public final class AppUtils {
    private static Application sApp;

    public static Application getApp() {
        if (sApp != null) {
            return sApp;
        }
        init(getApplicationByReflect());
        return sApp;
    }

    public static void init(Application app) {
        if (app == null) {
            return;
        }
        if (sApp == null) {
            sApp = app;
            return;
        }
        if (sApp.equals(app)) {
            return;
        }
        sApp = app;
    }

    @SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
    @Nullable
    static Application getApplicationByReflect() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object thread = getActivityThread();
            if (thread == null) return null;
            Object app = activityThreadClass.getMethod("getApplication").invoke(thread);
            if (app == null) return null;
            return (Application) app;
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Object getActivityThread() {
        Object activityThread = getActivityThreadInActivityThreadStaticField();
        if (activityThread != null) return activityThread;
        return getActivityThreadInActivityThreadStaticMethod();
    }

    @SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
    @Nullable
    private static Object getActivityThreadInActivityThreadStaticField() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Field sCurrentActivityThreadField = activityThreadClass.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThreadField.setAccessible(true);
            return sCurrentActivityThreadField.get(null);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
    @Nullable
    private static Object getActivityThreadInActivityThreadStaticMethod() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            return activityThreadClass.getMethod("currentActivityThread").invoke(null);
        } catch (Exception e) {
            return null;
        }
    }
}