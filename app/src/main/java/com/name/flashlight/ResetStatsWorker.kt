package com.name.flashlight

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.*

class ResetStatsWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            val prefs = applicationContext.getSharedPreferences("usage_stats", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val allKeys = prefs.all.keys
            var deleteCount = 0

            // 删除非今天的「按日统计」：旧版 type_yyyy-MM-dd（分钟 Float）与新版 type_yyyy-MM-dd_sec_total（整秒）
            allKeys.forEach { key ->
                val isDayAggregate = key.matches(
                    Regex("(flashlight|screen_light|blink)_\\d{4}-\\d{2}-\\d{2}(_sec_total)?")
                )
                if (isDayAggregate && !key.contains(today)) {
                    editor.remove(key)
                    deleteCount++
                }
            }

            // 清除过期的活动标记
            val featureTypes = listOf("flashlight", "screen_light", "blink")
            featureTypes.forEach { type ->
                editor.remove("${type}_start")
                editor.remove("${type}_active")
            }

            editor.apply()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}