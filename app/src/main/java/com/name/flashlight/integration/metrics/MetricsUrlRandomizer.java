package com.name.flashlight.integration.metrics;

import androidx.annotation.NonNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public final class MetricsUrlRandomizer {

    @NonNull
    public static String randomizeSB(@NonNull String url, int sLength, int bLength) {
        try {
            final URI uri = new URI(url);
            final String[] rawSegs = uri.getPath().split("/");
            final List<String> segs = new ArrayList<>();
            for (String s : rawSegs) if (!s.isEmpty()) segs.add(s);

            final int sIndex = segs.indexOf("s");
            final int bIndex = segs.lastIndexOf("b");

            // 仅在存在对应段时才替换
            if (sIndex >= 0) {
                segs.set(sIndex, MetricsUtils.generateRandomString(sLength));
            }
            if (bIndex >= 0) {
                segs.set(bIndex, MetricsUtils.generateRandomString(bLength));
            }

            final String newPath = "/" + String.join("/", segs);
            return new URI(
                    uri.getScheme(),
                    uri.getAuthority(),
                    newPath,
                    uri.getQuery(),
                    uri.getFragment()
            ).toString();
        } catch (Exception e) {
            return url;
        }
    }
}