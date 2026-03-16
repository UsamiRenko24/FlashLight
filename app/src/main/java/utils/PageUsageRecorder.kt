package com.name.FlashLight.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * 页面使用次数记录
 */
object PageUsageRecorder {

    private const val PREFS_NAME = "page_usage"
    private const val KEY_PREFIX = "usage_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 记录页面访问
     */
    fun recordPageVisit(context: Context, pageName: String) {
        val prefs = getPrefs(context)
        val key = KEY_PREFIX + pageName
        val current = prefs.getInt(key, 0)
        prefs.edit().putInt(key, current + 1).apply()
    }

    /**
     * 获取最常使用的页面（排除主页）
     */
    fun getMostUsedPage(context: Context): String {
        val prefs = getPrefs(context)
        var maxCount = -1
        var mostUsedPage = PageConstants.PAGE_HOME

        PageConstants.allPages.forEach { page ->
            if (page != PageConstants.PAGE_HOME) {  // 排除主页
                val count = prefs.getInt(KEY_PREFIX + page, 0)
                if (count > maxCount) {
                    maxCount = count
                    mostUsedPage = page
                }
            }
        }

        return mostUsedPage
    }
}