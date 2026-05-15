package com.name.flashlight.integration.metrics;

public interface MetricsSupplier<T> {
    T get() throws Exception;
}
