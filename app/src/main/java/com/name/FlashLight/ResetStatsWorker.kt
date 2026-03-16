package com.name.FlashLight

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

            // 健壮逻辑：删除所有包含日期格式但不是今天的 Key
            allKeys.forEach { key ->
                if (key.matches(Regex(".*\\d{4}-\\d{2}-\\d{2}$")) && !key.contains(today)) {
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
            println("✅ 每日自动清理完成，已移除 $deleteCount 条过期记录")
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}