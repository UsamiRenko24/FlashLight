package com.name.flashlight.utils

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor

/**
 * 使用时长统计：按「整秒」累加持久化，避免 Float 分钟与毫秒转分钟带来的 1～2 秒漂移。
 * 仍对外以 Float「分钟」返回，便于现有 UI（toDetailedTime 等）不变。
 */
class TimeRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "usage_stats"

        const val TYPE_FLASHLIGHT = "flashlight"
        const val TYPE_SCREEN_LIGHT = "screen_light"
        const val TYPE_BLINK = "blink"

        /** 新版：某日累计整秒数 */
        private const val SEC_TOTAL_SUFFIX = "_sec_total"
    }

    /**
     * @param startedAtEpochMs 会话起点（毫秒）。与界面计时共用同一时刻时传入，避免 SOS 等页面「统计起点早于 UI」多计。
     */
    fun startRecording(
        featureType: String,
        startedAtEpochMs: Long = System.currentTimeMillis()
    ) {
        prefs.edit().apply {
            putLong(sessionStartKey(featureType), startedAtEpochMs)
            putBoolean(sessionActiveKey(featureType), true)
            apply()
        }
    }

    fun stopRecording(featureType: String) {
        val startTime = prefs.getLong(sessionStartKey(featureType), 0L)
        val isActive = prefs.getBoolean(sessionActiveKey(featureType), false)

        val editor = prefs.edit()

        if (startTime > 0L && isActive) {
            val durationMs = System.currentTimeMillis() - startTime
            if (durationMs > 1000L) {
                val sessionWholeSeconds = durationMs / 1000L
                val base = readTotalSecondsToday(featureType)
                editor.putLong(dayTotalSecondsKey(featureType), base + sessionWholeSeconds)
            }
        }

        editor
            .remove(sessionStartKey(featureType))
            .putBoolean(sessionActiveKey(featureType), false)
            .apply()
    }

    /**
     * 今日累计（分钟，Float）；含进行中会话时，会话时长也按「整秒」折算为分钟再加总。
     */
    fun getTodayUsageMinutes(featureType: String): Float {
        val baseSeconds = readTotalSecondsToday(featureType)
        val isActive = prefs.getBoolean(sessionActiveKey(featureType), false)
        val startTime = prefs.getLong(sessionStartKey(featureType), 0L)
        val activeWholeSeconds =
            if (isActive && startTime > 0L) {
                (System.currentTimeMillis() - startTime) / 1000L
            } else {
                0L
            }
        return (baseSeconds + activeWholeSeconds) / 60f
    }

    fun getTodayTotalUsageMinutes(): Float {
        return getTodayUsageMinutes(TYPE_FLASHLIGHT) +
            getTodayUsageMinutes(TYPE_SCREEN_LIGHT) +
            getTodayUsageMinutes(TYPE_BLINK)
    }

    private fun dayDateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun legacyDayMinutesKey(featureType: String): String =
        "${featureType}_${dayDateString()}"

    private fun dayTotalSecondsKey(featureType: String): String =
        "${featureType}_${dayDateString()}$SEC_TOTAL_SUFFIX"

    private fun sessionStartKey(featureType: String): String =
        "${featureType}_start"

    private fun sessionActiveKey(featureType: String): String =
        "${featureType}_active"

    /**
     * 读取今日已累计整秒；若仅有旧版 Float「分钟」则迁移为整秒后删除旧 key。
     */
    private fun readTotalSecondsToday(featureType: String): Long {
        val secKey = dayTotalSecondsKey(featureType)
        if (prefs.contains(secKey)) {
            return prefs.getLong(secKey, 0L)
        }
        val legacyKey = legacyDayMinutesKey(featureType)
        val legacyMinutes = prefs.getFloat(legacyKey, 0f)
        if (legacyMinutes <= 0f) {
            return 0L
        }
        val seconds = floor(legacyMinutes.toDouble() * 60.0).toLong()
        prefs.edit()
            .putLong(secKey, seconds)
            .remove(legacyKey)
            .apply()
        return seconds
    }
}
