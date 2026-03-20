package com.name.FlashLight

import android.app.Application
import android.content.Intent
import com.name.FlashLight.utils.StartupModeManager
import utils.SoundManager
import utils.TemperatureManager

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 启动每日重置任务
        ResetScheduler.scheduleDailyReset(this)
        TemperatureManager.init(this)
        // 可选：启动时检查是否需要重置（防止应用长时间未启动）
        checkAndResetIfNeeded()
        TemperatureManager.init(this)
        SoundManager.initSoundPool(this)
    }
    private fun checkAndResetIfNeeded() {
        val prefs = getSharedPreferences("usage_stats", MODE_PRIVATE)
        val lastDate = prefs.getString("last_date", "")
        val today = getTodayDate()

        // 如果最后记录的日期不是今天，说明跨天了，需要重置
        if (lastDate != today) {
            resetDataForNewDay()
            // 保存今天的日期
            prefs.edit().putString("last_date", today).apply()
        }
    }

    private fun getTodayDate(): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date())
    }

    private fun resetDataForNewDay() {
        val prefs = getSharedPreferences("usage_stats", MODE_PRIVATE)
        val editor = prefs.edit()

        val yesterday = getYesterdayDate()

        // 清除昨天的数据
        prefs.all.keys.forEach { key ->
            if (key.contains(yesterday)) {
                editor.remove(key)
            }
        }

        editor.apply()
        println("📅 检测到新的一天，已重置数据")
    }

    private fun getYesterdayDate(): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return dateFormat.format(calendar.time)
    }
    override fun onTerminate() {
        super.onTerminate()

        // 释放资源
        SoundManager.release()
    }
}