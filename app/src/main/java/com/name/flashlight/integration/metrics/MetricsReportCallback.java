package com.name.flashlight.integration.metrics;

public interface MetricsReportCallback {
    void onReportStart();

    void onReportResult(boolean isSuccess);
}