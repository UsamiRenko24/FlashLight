package com.name.flashlight.integration.vpages;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public interface MultiFragmentCreator<T> {
    @NonNull
    Fragment createFragment(@NonNull T item, int position);
}