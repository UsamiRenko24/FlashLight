package com.name.flashlight.integration.metrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.jetbrains.annotations.Contract;

import java.lang.reflect.Type;

public final class MetricsJsonCodec {
    private static Gson gson;

    @NonNull
    public static Gson getGson() {
        if (gson == null) {
            gson = newGson();
        }
        return gson;
    }

    @Nullable
    public static <T> T fromJson(@NonNull String json, @NonNull Class<T> clz) {
        try {
            return getGson().fromJson(json, clz);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    public static <T> T fromJson(String json, @NonNull Type type) {
        if (json == null) return null;
        try {
            return getGson().fromJson(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static String toJson(Object o) {
        try {
            return getGson().toJson(o);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @NonNull
    @Contract(" -> new")
    public static <T> TypeToken<T> getTypeToken() {
        return new TypeToken<>() {
        };
    }

    @NonNull
    @Contract(" -> new")
    private static Gson newGson() {
        return new GsonBuilder()
                .disableHtmlEscaping()
                .create();
    }
}