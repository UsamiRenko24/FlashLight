package com.name.flashlight.integration.metrics;

import android.annotation.SuppressLint;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import com.cbin.phone.cleaner.integration.metrics.https.MetricsHttpsUtils;
import com.cbin.phone.cleaner.integration.metrics.https.MetricsSSLParams;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import kotlin.text.Charsets;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;

public final class MetricsHttp {
    private final OkHttpClient okHttpClient;

    private MetricsHttp() {
        final MetricsSSLParams sslParams = MetricsHttpsUtils.getSslSocketFactory();
        final OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(25, TimeUnit.SECONDS)
                .sslSocketFactory(sslParams.sSLSocketFactory, sslParams.trustManager)
                .hostnameVerifier(new HostnameVerifier() {
                    @SuppressLint("BadHostnameVerifier")
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                });

        if (MetricsManager.getInstance().isLogEnable()) {
            final HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

            builder.addInterceptor(interceptor);
        }

        okHttpClient = builder.build();
    }

    private static final class Holder {
        private static final MetricsHttp INSTANCE = new MetricsHttp();
    }

    public static MetricsHttp getInstance() {
        return Holder.INSTANCE;
    }

    @Nullable
    public static String decodeEncryptedJson(String result) {
        if (TextUtils.isEmpty(result)) {
            return null;
        }
        try {
            final String keyStr = result.substring(0, 16);
            final String dataStr = result.substring(16);
            final byte[] keyBytes = keyStr.getBytes(StandardCharsets.UTF_8);
            final byte[] dataBytes = dataStr.getBytes(StandardCharsets.UTF_8);
            final String transformation = MetricsConstant.AES_CBC_PKCS7;
            final byte[] bytes = MetricsCodeUtils.decryptBase64AES(dataBytes, keyBytes, transformation, keyBytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
        return null;
    }

    public void get(
            @NonNull String url,
            @Nullable Map<String, Object> queries,
            @NonNull Consumer<String> callback
    ) {
        final String newUrl = (queries == null || queries.isEmpty())
                ? url : (addQueries(url, queries));
        final Request request = new Request.Builder()
                .url(newUrl)
                .get()
                .build();

        enqueue(request, callback);
    }

    public void post(
            @NonNull String url,
            @NonNull String content,
            @NonNull Consumer<String> callback
    ) {
        final RequestBody requestBody = convertBody(content);
        if (requestBody == null) {
            callback.accept(null);
            return;
        }

        final Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        enqueue(request, callback);
    }

    public void post(
            @NonNull String url,
            @NonNull Map<String, Object> params,
            boolean confuseParams,
            @NonNull Consumer<String> callback
    ) {
        final RequestBody body = confuseConvert(params, confuseParams);
        if (body == null) {
            callback.accept(null);
            return;
        }

        final Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        enqueue(request, callback);
    }

    private void enqueue(
            @NonNull Request request,
            @NonNull Consumer<String> callback
    ) {
        try {
            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
//                    Log.e("susu", "onFailure --- err:" + e.getMessage());

                    callback.accept(null);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    if (response.code() != 200) {
                        callback.accept(null);
                        return;
                    }

                    String result = "";
                    try {
                        final ResponseBody body = response.body();
                        result = (body != null) ? body.string() : result;
                    } catch (Throwable ignored) {
                    }

//                    Log.e("susu", "onResponse --- result:" + result);

                    callback.accept(result);
                }
            });
        } catch (Throwable throwable) {
            callback.accept(null);
        }
    }

    @Nullable
    private RequestBody confuseConvert(
            @NonNull Map<String, Object> params,
            boolean confuseParams
    ) {
        try {
            final List<String> keyList = new ArrayList<>(params.keySet());
            Collections.shuffle(keyList);

            final Map<String, Object> shuffledMap = new LinkedHashMap<>();
            for (String key : keyList) {
                shuffledMap.put(key, params.get(key));
            }

            String content = MetricsJsonCodec.toJson(shuffledMap);
            if (content == null) return null;

            if (confuseParams) {
                content = encodeParams(content);
            }
            if (content == null) return null;

            return convertBody(content);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private String encodeParams(@Nullable String value) {
        if (value == null) return null;
        final byte[] input = value.getBytes(Charsets.UTF_8);
        String str = MetricsCodeUtils.base64Encode2String(input);
        if (str.length() > 1) {
            str = str.substring(1) + str.charAt(0);
        }
        return str;
    }

    @Nullable
    private RequestBody convertBody(@NonNull String content) {
        try {
            final MediaType contentType = MediaType.get("application/json; charset=UTF-8");
            return RequestBody.create(content, contentType);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String addQueries(@NonNull String url, @NonNull Map<String, Object> queries) {
        return createUrlFromParams(url, queries, false);
    }

    /**
     * @noinspection SameParameterValue
     */
    private String createUrlFromParams(
            @NonNull String url,
            @NonNull Map<String, Object> params,
            boolean isEncode
    ) {
        if (params.isEmpty()) {
            return url;
        }
        final StringBuilder builder = new StringBuilder(url);
        if (url.indexOf('&') > 0 || url.indexOf('?') > 0) {
            builder.append("&");
        } else {
            builder.append("?");
        }
        params.forEach((key, value) -> {
            // 对参数进行 utf-8 编码,防止头信息传中文
            if (isEncode && value instanceof String) {
                value = urlEncodeSafe((String) value);
            }
            builder.append(key).append("=").append(value).append("&");
        });
        //noinspection SizeReplaceableByIsEmpty
        if (builder.length() > 0) {
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder.toString();
    }

    @NonNull
    private String urlEncodeSafe(@NonNull String value) {
        return MetricsUtils.getOrDefault(() -> {
            //noinspection CharsetObjectCanBeUsed
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        }, value);
    }
}