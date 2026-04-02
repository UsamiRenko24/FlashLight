package utils

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 工业级 TimeRepository
 * 职责：负责所有功能的计时逻辑、统计持久化
 * 解决 Float/Int 混合管理混乱的问题，统一使用分钟数 (Float) 进行内部计算
 */
class TimeRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "usage_stats"
        
        // 功能类型常量
        const val TYPE_FLASHLIGHT = "flashlight"
        const val TYPE_SCREEN_LIGHT = "screen_light"
        const val TYPE_BLINK = "blink"
    }

    /**
     * 开始计时
     */
    fun startRecording(featureType: String) {
        val currentTime = System.currentTimeMillis()
        prefs.edit().apply {
            putLong("${featureType}_start", currentTime)
            putBoolean("${featureType}_active", true)
            apply()
        }
    }

    /**
     * 停止计时并保存
     */
    fun stopRecording(featureType: String) {
        val startTime = prefs.getLong("${featureType}_start", 0)
        val isActive = prefs.getBoolean("${featureType}_active", false)

        if (startTime > 0 && isActive) {
            val durationMs = System.currentTimeMillis() - startTime
            // 过滤掉小于 1 秒的误操作
            if (durationMs > 1000) {
                val durationMinutes = durationMs / (1000f * 60f)
                val todayKey = getTodayKey(featureType)
                val currentTotal = prefs.getFloat(todayKey, 0f)
                prefs.edit().putFloat(todayKey, currentTotal + durationMinutes).apply()
            }
        }

        // 清理当前会话状态
        prefs.edit().apply {
            remove("${featureType}_start")
            putBoolean("${featureType}_active", false)
            apply()
        }
    }

    /**
     * 获取今天某项功能的累计使用时长（实时计算，包含当前正在进行的会话）
     * 统一返回 Float (分钟)
     */
    fun getTodayUsageMinutes(featureType: String): Float {
        val savedTime = prefs.getFloat(getTodayKey(featureType), 0f)
        val isActive = prefs.getBoolean("${featureType}_active", false)
        val startTime = prefs.getLong("${featureType}_start", 0)

        return if (isActive && startTime > 0) {
            val currentSessionMinutes = (System.currentTimeMillis() - startTime) / (1000f * 60f)
            savedTime + currentSessionMinutes
        } else {
            savedTime
        }
    }

    /**
     * 获取今天的总使用时长（所有功能汇总）
     */
    fun getTodayTotalUsageMinutes(): Float {
        return getTodayUsageMinutes(TYPE_FLASHLIGHT) +
               getTodayUsageMinutes(TYPE_SCREEN_LIGHT) +
               getTodayUsageMinutes(TYPE_BLINK)
    }

    private fun getTodayKey(featureType: String): String = "${featureType}_${getTodayDate()}"

    private fun getTodayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}