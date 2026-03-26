package utils

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.name.FlashLight.ResetStatsWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

object ResetScheduler {

    // 启动每日重置任务
    fun scheduleDailyReset(context: Context) {
        val workManager = WorkManager.Companion.getInstance(context)

        // 计算到明天0点的延迟时间
        val delay = calculateDelayToMidnight()

        // 创建一次性请求（每天执行一次）
        val resetRequest = PeriodicWorkRequestBuilder<ResetStatsWorker>(
            1, TimeUnit.DAYS  // 每天执行一次
        ).setInitialDelay(delay, TimeUnit.MILLISECONDS)  // 第一次执行的时间
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        // 给这个任务一个唯一的名称
        workManager.enqueueUniquePeriodicWork(
            "daily_reset_work",
            ExistingPeriodicWorkPolicy.UPDATE,  // 如果已存在就更新
            resetRequest
        )
    }

    // 计算到明天0点的毫秒数
    private fun calculateDelayToMidnight(): Long {
        val calendar = Calendar.getInstance()
        // 设置为明天0点
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        return calendar.timeInMillis - System.currentTimeMillis()
    }
}