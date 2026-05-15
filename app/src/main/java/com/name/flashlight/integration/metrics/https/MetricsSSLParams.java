package com.name.flashlight.integration.metrics.https;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

public class MetricsSSLParams {
    public SSLSocketFactory sSLSocketFactory;
    public X509TrustManager trustManager;
}