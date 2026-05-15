package com.name.flashlight.integration.ads;

import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.secmtp.sdk.core.api.ATSDK;
import com.secmtp.sdk.nativead.api.ATNativeAdView;

public final class AdvertManager {

    public static void init(
            @NonNull Context context,
            @NonNull String appId,
            @NonNull String appKey,
            boolean debug
    ) {
        PlacementSplashAdManager.setGlobalAppContext(context);
        PlacementInterstitialAdManager.setGlobalAppContext(context);
        PlacementRewardVideoAdManager.setGlobalAppContext(context);
        PlacementNativeAdManager.setGlobalAppContext(context);
        new Thread(() -> {
            try {
                if (debug) {
                    ATSDK.setNetworkLogDebug(true);
                }
                ATSDK.init(context, appId, appKey);
            } catch (Throwable ignored) {
            }
        }).start();
    }

    // =================================== 信息流广告 ===================================

    /**
     * @param lifecycleOwner 传入 FragmentActivity 或 Fragment
     * @param advertConfig   广告配置
     * @return 信息流广告是否已准备完毕
     */
    public static boolean isNativeAdvertReady(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull NativeAdvertConfig advertConfig
    ) {
        return PlacementNativeAdManager.isReady(lifecycleOwner, advertConfig.getPlacementId());
    }

    /**
     * @param lifecycleOwner 传入 FragmentActivity 或 Fragment
     * @param advertConfig   广告配置
     * @return 信息流广告是否正在加载
     */
    public static boolean isNativeAdvertLoading(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull NativeAdvertConfig advertConfig
    ) {
        return PlacementNativeAdManager.isLoading(lifecycleOwner, advertConfig.getPlacementId());
    }

    /**
     * 加载信息流广告
     *
     * @param lifecycleOwner   传入 FragmentActivity 或 Fragment
     * @param advertConfig     广告配置
     * @param atNativeAdView   广告容器
     * @param nativeSelfRender 自渲染视图
     * @param advertCallback   广告回调
     */
    public static void loadNativeAdvert(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull NativeAdvertConfig advertConfig,
            @Nullable ATNativeAdView atNativeAdView,
            @Nullable INativeSelfRender nativeSelfRender,
            @Nullable NativeAdvertCallback advertCallback
    ) {
        final NativeAdvert nativeAdvert = new NativeAdvert(
                lifecycleOwner,
                advertConfig,
                atNativeAdView,
                nativeSelfRender,
                advertCallback
        );
        nativeAdvert.loadAdvert();
    }

    // =================================== 信息流广告 ===================================


    // =================================== 横幅广告 ===================================

    /**
     * 加载横幅广告
     *
     * @param lifecycleOwner  传入 FragmentActivity 或 Fragment
     * @param advertContainer 广告容器
     * @param advertConfig    广告配置
     * @param advertCallback  广告回调
     */
    public static void loadBannerAdvert(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull ViewGroup advertContainer,
            @NonNull BannerAdvertConfig advertConfig,
            @Nullable BannerAdvertCallback advertCallback
    ) {
        final BannerAdvert bannerAdvert = new BannerAdvert(
                lifecycleOwner,
                advertContainer,
                advertConfig,
                advertCallback
        );
        bannerAdvert.loadAdvert();
    }

    // =================================== 横幅广告 ===================================


    // =================================== 开屏广告 ===================================

    /**
     * @param lifecycleOwner 传入 FragmentActivity 或 Fragment
     * @param advertConfig   广告配置
     * @return 开屏广告是否已准备完毕
     */
    public static boolean isSplashAdvertReady(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull SplashAdvertConfig advertConfig
    ) {
        return PlacementSplashAdManager.isReady(lifecycleOwner, advertConfig.getPlacementId());
    }

    /**
     * @param lifecycleOwner 传入 FragmentActivity 或 Fragment
     * @param advertConfig   广告配置
     * @return 开屏广告是否正在加载
     */
    public static boolean isSplashAdvertLoading(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull SplashAdvertConfig advertConfig
    ) {
        return PlacementSplashAdManager.isLoading(lifecycleOwner, advertConfig.getPlacementId());
    }

    /**
     * 加载开屏广告
     *
     * @param lifecycleOwner  传入 FragmentActivity 或 Fragment
     * @param advertConfig    广告配置
     * @param advertContainer 广告容器
     * @param advertCallback  广告回调
     */
    public static void loadSplashAdvert(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull SplashAdvertConfig advertConfig,
            @Nullable ViewGroup advertContainer,
            @Nullable SplashAdvertCallback advertCallback
    ) {
        final SplashAdvert splashAdvert = new SplashAdvert(
                lifecycleOwner,
                advertConfig,
                advertContainer,
                advertCallback
        );
        splashAdvert.loadAdvert();
    }

    // =================================== 开屏广告 ===================================


    // =================================== 插屏广告 ===================================

    /**
     * @param lifecycleOwner 传入 FragmentActivity 或 Fragment
     * @param advertConfig   广告配置
     * @return 插屏广告是否已准备完毕
     */
    public static boolean isInsertAdvertReady(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull InsertAdvertConfig advertConfig
    ) {
        return PlacementInterstitialAdManager.isReady(lifecycleOwner, advertConfig.getPlacementId());
    }

    /**
     * @param lifecycleOwner 传入 FragmentActivity 或 Fragment
     * @param advertConfig   广告配置
     * @return 插屏广告是否正在加载
     */
    public static boolean isInsertAdvertLoading(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull InsertAdvertConfig advertConfig
    ) {
        return PlacementInterstitialAdManager.isLoading(lifecycleOwner, advertConfig.getPlacementId());
    }

    /**
     * 加载插屏广告
     *
     * @param lifecycleOwner 传入 FragmentActivity 或 Fragment
     * @param advertConfig   广告配置
     * @param advertCallback 广告回调
     */
    public static void loadInsertAdvert(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull InsertAdvertConfig advertConfig,
            @Nullable InsertAdvertCallback advertCallback
    ) {
        final InsertAdvert insertAdvert = new InsertAdvert(
                lifecycleOwner,
                advertConfig,
                advertCallback
        );
        insertAdvert.loadAdvert();
    }

    // =================================== 插屏广告 ===================================


    // =================================== 激励广告 ===================================

    /**
     * @param lifecycleOwner 传入 FragmentActivity 或 Fragment
     * @param advertConfig   广告配置
     * @return 激励广告是否已准备完毕
     */
    public static boolean isRewardAdvertReady(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull RewardAdvertConfig advertConfig
    ) {
        return PlacementRewardVideoAdManager.isReady(lifecycleOwner, advertConfig.getPlacementId());
    }

    /**
     * @param lifecycleOwner 传入 FragmentActivity 或 Fragment
     * @param advertConfig   广告配置
     * @return 激励广告是否正在加载
     */
    public static boolean isRewardAdvertLoading(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull RewardAdvertConfig advertConfig
    ) {
        return PlacementRewardVideoAdManager.isLoading(lifecycleOwner, advertConfig.getPlacementId());
    }

    /**
     * 加载激励广告
     *
     * @param lifecycleOwner 传入 FragmentActivity 或 Fragment
     * @param advertConfig   广告配置
     * @param advertCallback 广告回调
     */
    public static void loadRewardAdvert(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull RewardAdvertConfig advertConfig,
            @Nullable RewardAdvertCallback advertCallback
    ) {
        final RewardAdvert rewardAdvert = new RewardAdvert(
                lifecycleOwner,
                advertConfig,
                advertCallback
        );
        rewardAdvert.loadAdvert();
    }

    // =================================== 激励广告 ===================================
}