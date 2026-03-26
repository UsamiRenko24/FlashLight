package utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.name.FlashLight.LowBatteryActivity

object LowBatteryManager {
    private const val PREFS_NAME = "battery_settings"
    private const val KEY_LOW_BATTERY_PROTECTION = "low_battery_protection_enabled"
    private const val KEY_IS_ACTIVE = "low_battery_mode_is_active"
    
    private const val LOW_BATTERY_THRESHOLD = 15
    private val handler = Handler(Looper.getMainLooper())

    fun isProtectionEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LOW_BATTERY_PROTECTION, true)
    }

    fun setProtectionEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LOW_BATTERY_PROTECTION, enabled).apply()

        if (!enabled && isLowBatteryModeActive(context)) {
            exitLowBatteryMode(context)
        }
    }

    fun checkBatteryLevel(context: Context, level: Int) {
        if (!isProtectionEnabled(context)) return

        val isActive = isLowBatteryModeActive(context)
        if (level <= LOW_BATTERY_THRESHOLD && !isActive) {
            enterLowBatteryMode(context, level)
        } else if (level > LOW_BATTERY_THRESHOLD && isActive) {
            exitLowBatteryMode(context)
        }
    }

    private fun enterLowBatteryMode(context: Context, level: Int) {
        setModeActive(context, true)
        applyLowBatteryBrightness(context)
        stopAllFeatures(context)

        handler.postDelayed({
            val intent = Intent(context, LowBatteryActivity::class.java).apply {
                putExtra("battery_level", level)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }, 100)
    }

    private fun exitLowBatteryMode(context: Context) {
        setModeActive(context, false)
        restoreSystemBrightness(context)
        
        // 发送通知，让 LowBatteryActivity 知道该自动关闭了
        context.sendBroadcast(Intent("ACTION_EXIT_LOW_BATTERY_DISPLAY"))
    }

    /**
     * 核心方法：应用 30% 亮度
     */
    fun applyLowBatteryBrightness(context: Context) {
        if (isLowBatteryModeActive(context) && context is Activity) {
            val layoutParams = context.window.attributes
            layoutParams.screenBrightness = 0.3f
            context.window.attributes = layoutParams
        }
    }

    /**
     * 恢复系统默认亮度
     */
    fun restoreSystemBrightness(context: Context) {
        if (context is Activity) {
            val layoutParams = context.window.attributes
            layoutParams.screenBrightness = -1.0f // -1 表示恢复系统设置
            context.window.attributes = layoutParams
        }
    }

    private fun stopAllFeatures(context: Context) {
        context.sendBroadcast(Intent("ACTION_STOP_ALL_FEATURES"))
    }

    private fun setModeActive(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_IS_ACTIVE, active).apply()
    }

    fun isLowBatteryModeActive(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_ACTIVE, false)
    }
}