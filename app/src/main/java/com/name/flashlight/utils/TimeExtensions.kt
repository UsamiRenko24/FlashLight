package com.name.flashlight.utils

import android.content.Context
import com.name.flashlight.R
import java.util.Locale

/**
 * 时间工具扩展函数 - 顶层函数写法 (不需要写 class)
 */
/**
 * 将分钟数 (Float) 转换为数字计时格式 (例如: 1.5 -> "01:30")
 */
fun Float.toDigitalTime(): String {
    val totalSeconds = (this * 60).toInt()
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", m, s)
}
/**
 * 智能倒计时格式化
 * 如果是永不关闭，返回“永不关闭”
 * 否则返回 “00:00” 格式的倒计时
 */
fun Float.toCountdownDisplay(autoOffMinutes: Int, context: Context): String {
    return if (autoOffMinutes >= 114514) {
        context.getString(R.string.auto_off_never)
    } else {
        this.toDigitalTime() // 之前定义的 mm:ss 格式
    }
}
/**
 * 将整秒数格式化为统计用「X时Y分Z秒」文案（供统计页「总计」与分项一致使用）
 */
fun Int.toDetailedTimeFromSeconds(context: Context): String {

    val totalSeconds =
        coerceAtLeast(0)

    val hours =
        totalSeconds / 3600

    val minutes =
        (totalSeconds % 3600) / 60

    val seconds =
        totalSeconds % 60

    return when {

        hours > 0 -> {

            if (minutes > 0) {

                "${hours}${context.getString(R.string.hour)}" +
                        "${minutes}${context.getString(R.string.minute)}"

            } else {

                "${hours}${context.getString(R.string.hour)}"
            }
        }

        minutes > 0 -> {

            if (seconds > 0) {

                "${minutes}${context.getString(R.string.minute)}" +
                        "${seconds}${context.getString(R.string.second)}"

            } else {

                "${minutes}${context.getString(R.string.minute)}"
            }
        }

        else -> {

            "${seconds}${context.getString(R.string.second)}"
        }
    }
}

/**
 * 将分钟数 (Float) 转换为带单位的统计格式 (例如: 0.5 -> "30秒")
 * 秒数仍用 (分钟 * 60).toInt() 与历史行为一致，避免 double/floor 与 Float 截断不一致导致「多一秒」
 */
fun Float.toDetailedTime(
    context: Context
): String {

    return (this * 60)
        .toInt()
        .coerceAtLeast(0)
        .toDetailedTimeFromSeconds(context)
}