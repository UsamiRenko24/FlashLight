package com.name.flashlight.utils

import android.content.Context

object AppLaunchManager {

    private const val PREF_NAME = "app_prefs"
    private const val KEY_FIRST_LAUNCH = "first_launch"

    fun isFirstLaunch(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    fun setNotFirstLaunch(context: Context) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }
}
