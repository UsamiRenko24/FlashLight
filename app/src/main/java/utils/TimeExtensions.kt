package utils

import android.content.Context
import com.name.FlashLight.R
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
 * 将分钟数 (Float) 转换为带单位的统计格式 (例如: 0.5 -> "30秒")
 */
fun Float.toDetailedTime(context: Context): String {
    return when {
        this < 1 -> "${(this * 60).toInt()}${context.getString(R.string.second)}"
        this < 60 -> "${this.toInt()}${context.getString(R.string.minute)}"
        else -> {
            val h = this.toInt() / 60
            val m = this.toInt() % 60
            "${h}${context.getString(R.string.hour)}${m}${context.getString(R.string.minute)}"
        }
    }
}
