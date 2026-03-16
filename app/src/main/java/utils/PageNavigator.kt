package com.name.FlashLight.utils

import android.content.Context
import android.content.Intent
import com.name.FlashLight.*

/**
 * 页面导航工具类
 */
object PageNavigator {

    fun getPageIntent(context: Context, pageName: String): Intent {
        return when (pageName) {
            PageConstants.PAGE_HOME -> Intent(context, MainActivity::class.java)
            PageConstants.PAGE_FLASHLIGHT -> Intent(context, FlashlightActivity::class.java)
            PageConstants.PAGE_SCREEN_LIGHT -> Intent(context, ScreenLightActivity::class.java)
            PageConstants.PAGE_BLINK -> Intent(context, BlinkActivity::class.java)
            PageConstants.PAGE_STATS -> Intent(context, StatsActivity::class.java)
            PageConstants.PAGE_SETTINGS -> Intent(context, SettingsActivity::class.java)
            PageConstants.PAGE_LOW_BATTERY -> Intent(context, LowBatteryActivity::class.java)
            else -> Intent(context, MainActivity::class.java)
        }
    }
}