package com.name.flashlight.integration.ads;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

final class PreloadOwnerRegistry<T> {
    @NonNull
    private final ConcurrentHashMap<String, WeakReference<T>> owners = new ConcurrentHashMap<>();

    @Nullable
    T get(@NonNull String key) {
        final WeakReference<T> ref = owners.get(key);
        if (ref == null) {
            return null;
        }
        final T owner = ref.get();
        if (owner == null) {
            owners.remove(key, ref);
        }
        return owner;
    }

    void put(@NonNull String key, @NonNull T owner) {
        owners.put(key, new WeakReference<>(owner));
    }

    void removeIfSame(@NonNull String key, @NonNull T owner) {
        final T current = get(key);
        if (current == owner) {
            owners.remove(key);
        }
    }

    @NonNull
    static String buildKey(@NonNull LifecycleOwner owner, @NonNull String placementId) {
        return System.identityHashCode(owner) + ":" + placementId;
    }
}
