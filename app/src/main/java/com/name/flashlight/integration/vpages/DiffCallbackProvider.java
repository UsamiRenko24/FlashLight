package com.name.flashlight.integration.vpages;

import androidx.annotation.NonNull;

public interface DiffCallbackProvider<T> {
    boolean areItemsTheSame(@NonNull T oldItem, @NonNull T newItem);

    boolean areContentsTheSame(@NonNull T oldItem, @NonNull T newItem);
}