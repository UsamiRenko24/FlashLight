package com.name.flashlight.integration.language;

import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.res.Configuration;

final class ConfigurationObserver implements ComponentCallbacks {

    /**
     * 注册系统语种变化监听
     */
    static void register(Application application) {
        ConfigurationObserver configurationObserver = new ConfigurationObserver(application);
        application.registerComponentCallbacks(configurationObserver);
    }

    private final Application mApplication;

    private ConfigurationObserver(Application application) {
        mApplication = application;
    }

    /**
     * 手机的配置发生了变化
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (newConfig == null) {
            return;
        }
        // 如果当前是跟随系统语种，就则不往下执行
        if (MultiLanguages.isSystemLanguage(mApplication)) {
            return;
        }
        // 更新 Application 的配置，否则会出现横竖屏切换之后 Application 的 orientation 没有随之变化的问题
        LanguagesUtils.updateConfigurationChanged(mApplication, newConfig, MultiLanguages.getAppLanguage(mApplication));
    }

    @Override
    public void onLowMemory() {
    }
}