package com.name.flashlight

import android.app.Application
import android.content.Context
import com.name.flashlight.utils.DataStoreManager
import com.name.flashlight.utils.FeedbackManager
import com.name.flashlight.utils.LanguageManager
import com.name.flashlight.utils.ResetScheduler
import com.name.flashlight.utils.SoundManager
import com.name.flashlight.utils.TemperatureManager
import kotlinx.coroutines.flow.first

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 启动每日重置任务
        ResetScheduler.scheduleDailyReset(this)
        FeedbackManager.init(this)
        TemperatureManager.init(this)
        // 可选：启动时检查是否需要重置（防止应用长时间未启动）
        checkAndResetIfNeeded()
        SoundManager.initSoundPool(this)
    }
    override fun attachBaseContext(base: Context) {

        val lang = runCatching {
            kotlinx.coroutines.runBlocking {
                DataStoreManager.getLanguage(base).first()
            }
        }.getOrDefault("en")

        val locale = when (lang) {
            "zh" -> java.util.Locale.SIMPLIFIED_CHINESE
            else -> java.util.Locale.ENGLISH
        }

        val config = base.resources.configuration
        config.setLocale(locale)

        val context = if (android.os.Build.VERSION.SDK_INT >= 24) {
            base.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            base.resources.updateConfiguration(
                config,
                base.resources.displayMetrics
            )
            base
        }

        super.attachBaseContext(context)
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