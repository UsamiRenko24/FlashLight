package utils

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeRecorder {

    private const val PREFS_NAME = "usage_stats"

    // 记录开始时间
    fun startRecording(context: Context, featureType: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()

        prefs.edit().apply {
            putLong("${featureType}_start", currentTime)
            putBoolean("${featureType}_active", true)
            apply()
        }
    }

    // 停止记录并保存时长
    fun stopRecording(context: Context, featureType: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val startTime = prefs.getLong("${featureType}_start", 0)

        if (startTime > 0 && prefs.getBoolean("${featureType}_active", false)) {
            val durationMs = System.currentTimeMillis() - startTime
            if (durationMs > 1000) {
                val durationMinutes = durationMs / (1000.0 * 60.0)
                val todayKey = getTodayKey(featureType)
                val currentTotal = prefs.getFloat(todayKey, 0f)
                prefs.edit().putFloat(todayKey, currentTotal + durationMinutes.toFloat()).apply()
            }
        }

        prefs.edit().apply {
            remove("${featureType}_start")
            putBoolean("${featureType}_active", false)
            apply()
        }
    }

    /**
     * 获取今天的累计时间（实时计算）
     * 核心逻辑：已存的历史时间 + (如果当前正在运行 ? 本次已持续时间 : 0)
     */
    fun getTodayTime(context: Context, featureType: String): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTime = prefs.getFloat(getTodayKey(featureType), 0f)

        val isActive = prefs.getBoolean("${featureType}_active", false)
        val startTime = prefs.getLong("${featureType}_start", 0)

        if (isActive && startTime > 0) {
            val currentSessionMs = System.currentTimeMillis() - startTime
            val currentSessionMinutes = currentSessionMs / (1000f * 60f)
            return savedTime + currentSessionMinutes
        }

        return savedTime
    }

    private fun getTodayKey(featureType: String): String = "${featureType}_${getTodayDate()}"

    private fun getTodayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}