package com.name.flashlight.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.name.flashlight.LowBatteryActivity

object LowBatteryManager {
    private const val PREFS_NAME = "battery_settings"
    private const val KEY_LOW_BATTERY_PROTECTION = "low_battery_protection_enabled"
    private const val KEY_IS_ACTIVE = "low_battery_mode_is_active"

    private const val ENTER_THRESHOLD = 15
    private const val EXIT_THRESHOLD = 17
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

    /**
     * 检查电量及充电状态
     */
    fun checkBatteryLevel(context: Context, level: Int, isCharging: Boolean = false) {
        if (!isProtectionEnabled(context)) return

        val isActive = isLowBatteryModeActive(context)

        if ((level >= EXIT_THRESHOLD || isCharging) && isActive) {
            exitLowBatteryMode(context)
        }
        else if (level <= ENTER_THRESHOLD && !isCharging && !isActive) {
            enterLowBatteryMode(context, level)
        }
    }

    private fun enterLowBatteryMode(
        context: Context,
        level: Int
    ) {

        setModeActive(context, true)

        applyLowBatteryBrightness(context)

        stopAllFeatures(context)

        handler.postDelayed({

            val intent =
                Intent(
                    context,
                    LowBatteryActivity::class.java
                ).apply {

                    putExtra(
                        "battery_level",
                        level
                    )

                    addFlags(
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )

                    if (context !is Activity) {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                        )
                    }
                }

            context.startActivity(intent)

        }, 100)
    }

    private fun exitLowBatteryMode(context: Context) {
        setModeActive(context, false)
        restoreSystemBrightness(context)
        
        val intent = Intent("ACTION_EXIT_LOW_BATTERY_DISPLAY")
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
    }

    /**
     * 核心方法：应用 30% 亮度
     * 增加 isFinishing 校验防止 DeadObjectException
     */
    fun applyLowBatteryBrightness(context: Context) {
        if (context is Activity && !context.isFinishing && !context.isDestroyed) {
            if (isLowBatteryModeActive(context)) {
                try {
                    val layoutParams = context.window.attributes
                    layoutParams.screenBrightness = 0.3f
                    context.window.attributes = layoutParams
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * 恢复系统默认亮度
     */
    fun restoreSystemBrightness(context: Context) {
        if (context is Activity && !context.isFinishing && !context.isDestroyed) {
            try {
                val layoutParams = context.window.attributes
                layoutParams.screenBrightness = -1.0f 
                context.window.attributes = layoutParams
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopAllFeatures(context: Context) {

        val intent = Intent("ACTION_STOP_ALL_FEATURES").apply {
            setPackage(context.packageName)
        }

        context.sendBroadcast(intent)
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