package com.name.flashlight

import android.app.Application
import android.content.Context
import com.name.flashlight.integration.language.MultiLanguages
import com.name.flashlight.utils.FeedbackManager
import com.name.flashlight.utils.ResetScheduler
import com.name.flashlight.utils.SoundManager
import com.name.flashlight.utils.TemperatureManager
import java.util.Locale

class MyApp : Application() {

    override fun attachBaseContext(base: Context) {

        /**
         * 这里只做 attach
         * 不要调用 setAppLanguage
         */
        super.attachBaseContext(
            MultiLanguages.attach(base)
        )
    }

    override fun onCreate() {
        super.onCreate()

        /**
         * 默认语言
         */
        MultiLanguages.setDefaultLanguage(
            Locale.ENGLISH
        )

        /**
         * 初始化语言框架
         */
        MultiLanguages.init(this)

        /**
         * 每日重置
         */
        ResetScheduler.scheduleDailyReset(this)

        /**
         * 初始化
         */
        FeedbackManager.init(this)

        TemperatureManager.init(this)

        SoundManager.initSoundPool(this)

        /**
         * 检查跨天
         */
        checkAndResetIfNeeded()
    }

    /**
     * 检查是否跨天
     */
    private fun checkAndResetIfNeeded() {

        val prefs =
            getSharedPreferences(
                "usage_stats",
                MODE_PRIVATE
            )

        val lastDate =
            prefs.getString(
                "last_date",
                ""
            )

        val today =
            getTodayDate()

        if (lastDate != today) {

            resetDataForNewDay()

            prefs.edit()
                .putString("last_date", today)
                .apply()
        }
    }

    /**
     * 今天日期
     */
    private fun getTodayDate(): String {

        val dateFormat =
            java.text.SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.getDefault()
            )

        return dateFormat.format(
            java.util.Date()
        )
    }

    /**
     * 重置昨日数据
     */
    private fun resetDataForNewDay() {

        val prefs =
            getSharedPreferences(
                "usage_stats",
                MODE_PRIVATE
            )

        val editor =
            prefs.edit()

        val yesterday =
            getYesterdayDate()

        prefs.all.keys.forEach { key ->

            if (key.contains(yesterday)) {

                editor.remove(key)
            }
        }

        editor.apply()
    }

    /**
     * 昨天日期
     */
    private fun getYesterdayDate(): String {

        val calendar =
            java.util.Calendar.getInstance()

        calendar.add(
            java.util.Calendar.DAY_OF_YEAR,
            -1
        )

        val dateFormat =
            java.text.SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.getDefault()
            )

        return dateFormat.format(
            calendar.time
        )
    }

    override fun onTerminate() {
        super.onTerminate()

        /**
         * 释放声音资源
         */
        SoundManager.release()
    }
}