package com.name.flashlight.integration.vpages;

import androidx.annotation.NonNull;

/**
 * 提供稳定 long 型 ID 的接口，避免直接依赖 hashCode。
 */
public interface StableIdProvider<T> {
    long getItemId(@NonNull T item);
}