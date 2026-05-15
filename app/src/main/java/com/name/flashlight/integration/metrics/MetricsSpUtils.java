package com.name.flashlight.integration.metrics;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class MetricsSpUtils {
    @Nullable
    private volatile SharedPreferences sharedPreferences;

    private MetricsSpUtils() {
    }

    private static final class Holder {
        private static final MetricsSpUtils INSTANCE = new MetricsSpUtils();
    }

    public static MetricsSpUtils getInstance() {
        return Holder.INSTANCE;
    }

    @Nullable
    private SharedPreferences getPreferences() {
        SharedPreferences preferences = sharedPreferences;
        if (preferences != null) {
            return preferences;
        }

        synchronized (this) {
            preferences = sharedPreferences;
            if (preferences != null) {
                return preferences;
            }

            final Context context = MetricsManager.getInstance().getContext();
            if (context == null) {
                return null;
            }

            preferences = context.getSharedPreferences(MetricsConstant.CACHE_NAME, Context.MODE_PRIVATE);
            sharedPreferences = preferences;
            return preferences;
        }
    }

    @Nullable
    public String getString(@NonNull String key, @Nullable String defValue) {
        final SharedPreferences preferences = getPreferences();
        if (preferences != null) {
            return preferences.getString(key, defValue);
        }
        return defValue;
    }

    @SuppressLint("ApplySharedPref")
    public void putString(@NonNull String key, @Nullable String value) {
        try {
            final SharedPreferences preferences = getPreferences();
            if (preferences != null) {
                preferences.edit().putString(key, value).commit();
            }
        } catch (Throwable ignored) {
        }
    }

    public boolean getBoolean(@NonNull String key, boolean defValue) {
        final SharedPreferences preferences = getPreferences();
        if (preferences != null) {
            return preferences.getBoolean(key, defValue);
        }
        return defValue;
    }

    @SuppressLint("ApplySharedPref")
    public void putBoolean(@NonNull String key, boolean value) {
        try {
            final SharedPreferences preferences = getPreferences();
            if (preferences != null) {
                preferences.edit().putBoolean(key, value).commit();
            }
        } catch (Throwable ignored) {
        }
    }

    public int getInt(@NonNull String key, int defValue) {
        final SharedPreferences preferences = getPreferences();
        if (preferences != null) {
            return preferences.getInt(key, defValue);
        }
        return defValue;
    }

    @SuppressLint("ApplySharedPref")
    public void putInt(@NonNull String key, int value) {
        try {
            final SharedPreferences preferences = getPreferences();
            if (preferences != null) {
                preferences.edit().putInt(key, value).commit();
            }
        } catch (Throwable ignored) {
        }
    }
}