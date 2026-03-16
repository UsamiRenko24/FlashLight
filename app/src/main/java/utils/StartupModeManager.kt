package com.name.FlashLight.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.name.FlashLight.*

/**
 * 启动模式管理器
 */
object StartupModeManager {

    private const val PREFS_NAME = "startup_settings"
    private const val KEY_STARTUP_MODE = "startup_mode"
    private const val KEY_LAST_PAGE = "last_page"

    // 启动模式常量
    const val MODE_LAST_USED = 0      // 记住上次使用的功能
    const val MODE_HOME = 1           // 总是启动到主页面
    const val MODE_MOST_USED = 2      // 启动到最常用功能

    // 默认模式：主页面
    const val DEFAULT_MODE = MODE_HOME

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 设置启动模式
     */
    fun setStartupMode(context: Context, mode: Int) {
        getPrefs(context).edit().putInt(KEY_STARTUP_MODE, mode).apply()
    }

    /**
     * 获取启动模式
     */
    fun getStartupMode(context: Context): Int {
        return getPrefs(context).getInt(KEY_STARTUP_MODE, DEFAULT_MODE)
    }

    /**
     * 记录上次使用的页面
     */
    fun recordLastPage(context: Context, pageName: String) {
        getPrefs(context).edit().putString(KEY_LAST_PAGE, pageName).apply()
    }

    /**
     * 获取上次使用的页面
     */
    fun getLastPage(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_PAGE, PageConstants.PAGE_HOME) ?: PageConstants.PAGE_HOME
    }

    /**
     * 根据当前模式获取启动 Intent
     */
    fun getStartupIntent(context: Context): Intent {
        val mode = getStartupMode(context)

        return when (mode) {
            MODE_LAST_USED -> {
                // 记住上次使用的功能
                val lastPage = getLastPage(context)
                PageNavigator.getPageIntent(context, lastPage)
            }
            MODE_HOME -> {
                // 总是启动到主页面
                Intent(context, MainActivity::class.java)
            }
            MODE_MOST_USED -> {
                // 启动到最常用功能
                val mostUsedPage = PageUsageRecorder.getMostUsedPage(context)
                PageNavigator.getPageIntent(context, mostUsedPage)
            }
            else -> Intent(context, MainActivity::class.java)
        }
    }

    /**
     * 获取模式名称
     */
}