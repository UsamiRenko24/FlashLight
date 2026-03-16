package com.name.FlashLight.utils

import android.content.Context
import com.name.FlashLight.AutomaticActivity

object AutoOffSettings {

    private const val PREFS_NAME = "auto_off_settings"

    fun getFlashlightTime(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(
            AutomaticActivity.KEY_FLASHLIGHT_TIME,
            AutomaticActivity.TIME_5_MIN
        )
    }

    fun getScreenLightTime(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(
            AutomaticActivity.KEY_SCREEN_LIGHT_TIME,
            AutomaticActivity.TIME_5_MIN
        )
    }

    fun getBlinkTime(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(
            AutomaticActivity.KEY_BLINK_TIME,
            AutomaticActivity.TIME_5_MIN
        )
    }

    fun isNeverOff(timeValue: Int): Boolean {
        return timeValue == AutomaticActivity.TIME_NEVER
    }

    fun getTimeInMillis(timeValue: Int): Long {
        return if (timeValue == AutomaticActivity.TIME_NEVER) {
            -1L
        } else {
            timeValue * 60 * 1000L
        }
    }
}