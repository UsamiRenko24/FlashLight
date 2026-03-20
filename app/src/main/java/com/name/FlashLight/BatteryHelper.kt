package com.name.FlashLight

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.widget.ImageView
import android.widget.TextView

/**
 * 电池监控工具类 - 所有电池相关方法集中管理
 */
object BatteryHelper {

    // 缓存最新的电池数据，避免频繁读取
    private var lastBatteryLevel = 0f
    private var lastBatteryStatus = ""
    private var lastChargingType = ""
    private var lastIconRes = R.drawable.ic_battery_75

    /**
     * 获取当前电池信息（返回数据类）
     */
    fun getBatteryInfo(context: Context): BatteryInfo {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return BatteryInfo()

        return parseBatteryInfo(intent)
    }

    /**
     * 从Intent解析电池信息
     */
    private fun parseBatteryInfo(intent: Intent): BatteryInfo {
        // 获取电池电量
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (level > 0 && scale > 0) level * 100f / scale else 0f

        // 获取充电状态
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        // 获取充电方式
        val chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB
        val acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC
        val wirelessCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS

        // 充电状态文字
        val statusText = when {
            status == BatteryManager.BATTERY_STATUS_FULL -> "已充满"
            isCharging -> {
                when {
                    usbCharge -> "USB充电"
                    acCharge -> "电源充电"
                    wirelessCharge -> "无线充电"
                    else -> "充电中"
                }
            }
            else -> "未充电"
        }

        // 充电类型详情
        val chargingType = when {
            status == BatteryManager.BATTERY_STATUS_FULL -> "已充满"
            isCharging -> {
                when {
                    usbCharge -> "USB充电中"
                    acCharge -> "电源充电中"
                    wirelessCharge -> "无线充电中"
                    else -> "充电中"
                }
            }
            else -> "未充电"
        }

        // 电池图标
        val iconRes = when {
            isCharging -> R.drawable.ic_battery_charging
            batteryPct >= 90 -> R.drawable.ic_battery_100
            batteryPct >= 70 -> R.drawable.ic_battery_75
            batteryPct >= 50 -> R.drawable.ic_battery_50
            batteryPct >= 25 -> R.drawable.ic_battery_25
            else -> R.drawable.ic_battery_0
        }

        // 缓存数据
        lastBatteryLevel = batteryPct
        lastBatteryStatus = statusText
        lastChargingType = chargingType
        lastIconRes = iconRes

        return BatteryInfo(
            level = batteryPct,
            levelText = String.format("%.0f%%", batteryPct),
            status = statusText,
            chargingType = chargingType,
            isCharging = isCharging,
            iconRes = iconRes
        )
    }


    fun updateBatteryUI(
        context: Context,
        tvPercent: TextView? = null,
        tvStatus: TextView? = null,
        ivIcon: ImageView? = null
    ) {
        val info = getBatteryInfo(context)

        tvPercent?.text = info.levelText
        tvStatus?.text = info.chargingType
        ivIcon?.setImageResource(info.iconRes)
    }

    data class BatteryInfo(
        val level: Float = 0f,
        val levelText: String = "0%",
        val status: String = "未知",
        val chargingType: String = "未充电",
        val isCharging: Boolean = false,
        val iconRes: Int = R.drawable.ic_battery_0
    )
}