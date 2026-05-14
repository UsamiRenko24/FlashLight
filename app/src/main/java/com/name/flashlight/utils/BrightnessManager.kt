package com.name.flashlight.utils

import android.app.Activity
import android.view.WindowManager

object BrightnessManager {

    /**
     * 跟随系统亮度
     */
    fun followSystemBrightness(
        activity: Activity
    ) {

        val lp = activity.window.attributes

        lp.screenBrightness =
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

        activity.window.attributes = lp
    }

    /**
     * 设置当前页面亮度
     * 0f ~ 1f
     */
    fun setBrightness(
        activity: Activity,
        brightness: Float
    ) {

        val safeBrightness =
            brightness.coerceIn(0f, 1f)

        val lp = activity.window.attributes

        lp.screenBrightness = safeBrightness

        activity.window.attributes = lp
    }

    /**
     * 设置最高亮度
     */
    fun setMaxBrightness(
        activity: Activity
    ) {

        setBrightness(activity, 1f)
    }

    /**
     * 恢复系统默认亮度
     */
    fun resetBrightness(
        activity: Activity
    ) {

        followSystemBrightness(activity)
    }
}