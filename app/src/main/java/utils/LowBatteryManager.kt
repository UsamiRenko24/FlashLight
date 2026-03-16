package utils

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.name.FlashLight.LowBatteryActivity

object LowBatteryManager {
    // 1. 修改阈值为 15%
    private const val LOW_BATTERY_THRESHOLD = 15
    private var isLowBatteryModeActive = false
    private val handler = Handler(Looper.getMainLooper())

    fun checkBatteryLevel(context: Context, level: Int) {
        if (level <= LOW_BATTERY_THRESHOLD && !isLowBatteryModeActive) {
            enterLowBatteryMode(context, level)
        } else if (level > LOW_BATTERY_THRESHOLD && isLowBatteryModeActive) {
            // 如果电量回升（比如充电），可以考虑在这里重置模式
            // isLowBatteryModeActive = false
        }
    }

    private fun enterLowBatteryMode(context: Context, level: Int) {
        isLowBatteryModeActive = true

        // 2. 立即尝试对当前 Activity 降级亮度
        applyLowBatteryBrightness(context)

        // 停止所有功能（手电筒、闪烁等）
        stopAllFeatures(context)

        // 跳转到低电量页面
        handler.postDelayed({
            val intent = Intent(context, LowBatteryActivity::class.java).apply {
                putExtra("battery_level", level)
                // 使用这种 Flag 确保低电量页是栈顶唯一的
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }, 100)
    }

    /**
     * 核心方法：应用 30% 亮度
     */
    fun applyLowBatteryBrightness(context: Context) {
        if (isLowBatteryModeActive && context is android.app.Activity) {
            val layoutParams = context.window.attributes
            layoutParams.screenBrightness = 0.3f // 30% 亮度
            context.window.attributes = layoutParams
        }
    }

    private fun stopAllFeatures(context: Context) {
        context.sendBroadcast(Intent("ACTION_STOP_ALL_FEATURES"))
    }

    fun isLowBatteryModeActive(): Boolean = isLowBatteryModeActive
}