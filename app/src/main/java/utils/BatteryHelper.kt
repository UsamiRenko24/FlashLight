package utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.widget.ImageView
import android.widget.TextView
import com.name.FlashLight.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * 电池监控工具类 - 支持实时充放电速率计算
 */
object BatteryHelper {
    private const val PREFS_NAME = "battery_health"
    private const val KEY_INITIAL_CAPACITY = "initial_capacity"
    private const val TYPICAL_CAPACITY = 4000

    private val _batteryHealthText = MutableStateFlow("计算中...")
    /**
     * 获取当前电池信息
     */
    fun getBatteryInfo(context: Context): BatteryInfo {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return BatteryInfo()

        val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        // 1. 获取基础信息
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (level > 0 && scale > 0) level * 100f / scale else 0f
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        // 2. 获取实时电流 (单位：微安 uA)
        var currentMicroAmps = manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

        // 3. 获取当前电量电荷 (单位：微安时 uAh)
        val chargeCounter = manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)

        if (currentMicroAmps == 0L) {
            currentMicroAmps = manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        }

        // 4. 计算预计时间 (单位：分钟)
        var estimateMinutes = -1
        if (abs(currentMicroAmps) > 1000) {
            if (isCharging) {
                val remainingCapacity = (100 - batteryPct) / 100f * getEstimatedTotalCapacity(manager)
                estimateMinutes = (remainingCapacity / abs(currentMicroAmps) * 60).toInt()
            } else {
                estimateMinutes = (chargeCounter.toFloat() / abs(currentMicroAmps) * 60).toInt()
            }
        }

        // 5. 格式化充电类型
        val chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val chargingType = when {
            status == BatteryManager.BATTERY_STATUS_FULL -> context.getString(R.string.battery_status_full)
            isCharging -> when (chargePlug) {
                BatteryManager.BATTERY_PLUGGED_USB -> context.getString(R.string.battery_plugged_usb)
                BatteryManager.BATTERY_PLUGGED_AC -> context.getString(R.string.battery_plugged_ac)
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> context.getString(R.string.battery_plugged_wireless)
                else -> context.getString(R.string.battery_charging)
            }
            else -> context.getString(R.string.battery_not_charging)
        }

        return BatteryInfo(
            level = batteryPct,
            levelText = String.format("%.0f%%", batteryPct),
            status = if (isCharging) context.getString(R.string.battery_charging) else context.getString(
                R.string.battery_not_charging),
            chargingType = chargingType,
            isCharging = isCharging,
            iconRes = getIcon(batteryPct, isCharging),
            currentMa = (currentMicroAmps / 1000).toInt(),
            estimateMinutes = estimateMinutes.coerceAtMost(1440)
        )
    }
    /**
     * 更新电池健康状态
     */
    fun updateBatteryHealth(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 获取当前容量
        val currentCapacity = getCurrentCapacity(context)

        // 记录初始容量（首次使用时）
        if (prefs.getInt(KEY_INITIAL_CAPACITY, 0) == 0 && currentCapacity > 0) {
            prefs.edit().putInt(KEY_INITIAL_CAPACITY, currentCapacity).apply()
        }

        val initialCapacity = prefs.getInt(KEY_INITIAL_CAPACITY, TYPICAL_CAPACITY)

        // 计算容量百分比
        val capacityPercent = if (initialCapacity > 0) {
            (currentCapacity.toFloat() / initialCapacity * 100).toInt().coerceIn(0, 100)
        } else {
            100
        }

        // 获取温度影响
        val temperature = getCurrentTemperature(context)
        val tempScore = when {
            temperature < 35 -> 1.0f
            temperature < 40 -> 0.8f
            temperature < 45 -> 0.5f
            else -> 0.3f
        }

        // 综合评分
        val healthScore = (capacityPercent * 0.7 + tempScore * 30).toInt().coerceIn(0, 100)

        return getHealthText(context,healthScore)
    }

    /**
     * 获取当前电池容量（毫安时）
     */
    private fun getCurrentCapacity(context: Context): Int {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            val chargeCounter = manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            if (chargeCounter > 0) {
                (chargeCounter / 1000).toInt()
            } else {
                // 估算：基于电量百分比和典型容量
                val info = getBatteryInfo(context)
                (TYPICAL_CAPACITY * info.level / 100).toInt()
            }
        } else {
            TYPICAL_CAPACITY
        }
    }

    /**
     * 获取电池温度
     */
    private fun getCurrentTemperature(context: Context): Float {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return temp / 10.0f
    }

    /**
     * 生成健康文本
     */
    private fun getHealthText(context: Context,score: Int): String {
        return when (score) {
            in 90..100 -> context.getString(R.string.battery_health_excellent)
            in 75..89 -> context.getString(R.string.battery_health_good)
            in 60..74 -> context.getString(R.string.battery_health_fair)
            in 40..59 -> context.getString(R.string.battery_health_poor)
            else -> context.getString(R.string.battery_health_critical)
        }
    }
    /**
     * 【恢复的方法】供其它 Activity 调用的公共 UI 更新方法
     */
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

    private fun getEstimatedTotalCapacity(manager: BatteryManager): Long {
        val chargeCounter = manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        return if (chargeCounter > 0) chargeCounter * 100 / 50 else 3000000L // 默认 3000mAh
    }

    private fun getIcon(pct: Float, isCharging: Boolean): Int {
        if (isCharging) return R.drawable.ic_battery_charging
        return when {
            pct >= 90 -> R.drawable.ic_battery_100
            pct >= 70 -> R.drawable.ic_battery_75
            pct >= 50 -> R.drawable.ic_battery_50
            pct >= 25 -> R.drawable.ic_battery_25
            else -> R.drawable.ic_battery_0
        }
    }

    data class BatteryInfo(
        val level: Float = 0f,
        val levelText: String = "0%",
        val status: String = "未知",
        val chargingType: String = "未充电",
        val isCharging: Boolean = false,
        val iconRes: Int = R.drawable.ic_battery_0,
        val currentMa: Int = 0,
        val estimateMinutes: Int = -1
    )
}