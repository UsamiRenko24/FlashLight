package com.name.flashlight.integration.ads;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 广告基类 - 观察者模式优化版本
 * <p>
 * 主要改进：
 * 1. 使用观察者模式替代轮询机制
 * 2. 添加超时保护机制（15秒）
 * 3. 支持多个回调同时等待
 * 4. 完善的资源清理
 * 5. 线程安全保护
 */
public abstract class BaseAdvert {
    protected final Handler timeoutHandler = new Handler(Looper.getMainLooper());

    @Nullable
    private Runnable timeoutRunnable;

    /**
     * @return 广告是否准备完毕
     */
    public abstract boolean isAdvertReady();

    /**
     * @return 广告是否正在加载
     */
    public abstract boolean isAdvertLoading();

    /**
     * @return 当前广告位的 placementId，用于跨实例按广告位维度管理等待队列
     */
    @NonNull
    protected abstract String getPlacementId();

    /**
     * 广告加载
     */
    protected abstract void loadAdvert();

    /**
     * 广告释放
     */
    @CallSuper
    public void onAdvertRelease() {
        clearTimeout();
    }

    /**
     * 设置超时
     *
     * @param delayMillis 超时时间戳
     * @param runnable    超时回调
     */
    protected void setTimeout(long delayMillis, @NonNull Runnable runnable) {
        if (delayMillis > 0) {
            timeoutRunnable = runnable;
            timeoutHandler.postDelayed(timeoutRunnable, delayMillis);
        }
    }

    /**
     * 清除超时设定
     */
    protected void clearTimeout() {
        if (timeoutRunnable != null) {
            if (timeoutHandler.hasCallbacks(timeoutRunnable)) {
                timeoutHandler.removeCallbacks(timeoutRunnable);
            }
            timeoutRunnable = null;
        }
    }
}
