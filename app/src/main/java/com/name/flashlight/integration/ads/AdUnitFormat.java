package com.name.flashlight.integration.ads;

public enum AdUnitFormat {
    NATIVE("Native"),
    INSERT("Interstitial"),
    SPLASH("Splash"),
    BANNER("Banner"),
    REWARD("RewardedVideo");

    private final String value;

    public String getValue() {
        return value;
    }

    AdUnitFormat(String format) {
        this.value = format;
    }
}