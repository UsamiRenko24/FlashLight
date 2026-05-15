package com.name.flashlight.integration.ads;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class AdvertGsonUtils {
    private final Gson gson;

    private AdvertGsonUtils() {
        gson = new GsonBuilder().create();
    }

    private static class Holder {
        private static final AdvertGsonUtils INSTANCE = new AdvertGsonUtils();
    }

    public static AdvertGsonUtils getInstance() {
        return Holder.INSTANCE;
    }

    @Nullable
    public String toJson(Object object) {
        try {
            return gson.toJson(object);
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    public <T> T fromJson(String json, @NonNull Class<T> aClass) {
        try {
            return gson.fromJson(json, aClass);
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    public <T> T deepClone(Object object, @NonNull Class<T> aClass) {
        final String json = toJson(object);
        return (json != null) ? fromJson(json, aClass) : null;
    }
}
