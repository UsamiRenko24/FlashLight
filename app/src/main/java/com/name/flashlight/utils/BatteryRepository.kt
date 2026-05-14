package com.name.flashlight.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.name.flashlight.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 工业级电池仓库 - 彻底修复多语言残留问题
 */
class BatteryRepository(private val context: Context) {

    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val prefs = context.getSharedPreferences("battery_health", Context.MODE_PRIVATE)
    private var smoothedCurrentUa = 0f

    companion object {
        private const val KEY_INITIAL_CAPACITY = "initial_capacity"
        private const val TYPICAL_CAPACITY = 4000
    }

    /**
     * 获取电池数据流
     * 关键：使用 parseBatteryIntent 时务必传入最新的 Context
     */
    fun getBatteryFlow(): Flow<BatteryInfo> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                // ctx 是系统广播传回的活跃上下文，最适合拿来做翻译
                trySend(parseBatteryIntent(ctx, intent))
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        awaitClose { context.unregisterReceiver(receiver) }
    }.onStart {
        // 初始推送：确保使用最新的 context (由于是在 Activity 里实例化的，它也是正确的)
        emit(getCurrentBatteryInfo(context))
    }
    private fun smoothCurrent(currentUa: Long): Float {

        val absCurrent = abs(currentUa).toFloat()

        if (smoothedCurrentUa == 0f) {
            smoothedCurrentUa = absCurrent
        } else {

            // 0.15 越小越稳定
            smoothedCurrentUa =
                smoothedCurrentUa * 0.85f +
                        absCurrent * 0.15f
        }

        return smoothedCurrentUa
    }

    fun getCurrentBatteryInfo(ctx: Context): BatteryInfo {
        val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return if (intent != null) parseBatteryIntent(ctx, intent) else BatteryInfo()
    }

    private fun parseBatteryIntent(ctx: Context, intent: Intent): BatteryInfo {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (level > 0 && scale > 0) level * 100f / scale else 0f
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        // 获取电流数据
        var currentMicroAmps = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (currentMicroAmps == 0L) currentMicroAmps = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        val currentMa = abs(currentMicroAmps / 1000).toInt()

        return BatteryInfo(
            level = batteryPct,
            levelText = String.format(Locale.getDefault(), "%.0f%%", batteryPct),
            // 关键：必须使用 ctx.getString 确保拿到当前语言
            status = if (isCharging) ctx.getString(R.string.battery_charging) else ctx.getString(R.string.battery_not_charging),
            chargingType = getChargingType(ctx, intent, status, isCharging),
            isCharging = isCharging,
            iconRes = getIcon(batteryPct, isCharging),
            currentMa = currentMa,
            estimateMinutes = calculateEstimate(batteryPct, isCharging, currentMicroAmps)
        )
    }

    fun getBatteryHealthDescription(ctx: Context): String {
        val currentCapacity = (batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) / 1000).toInt()
        if (prefs.getInt(KEY_INITIAL_CAPACITY, 0) == 0 && currentCapacity > 0) {
            prefs.edit().putInt(KEY_INITIAL_CAPACITY, currentCapacity).apply()
        }
        val initialCapacity = prefs.getInt(KEY_INITIAL_CAPACITY, TYPICAL_CAPACITY)
        val capacityPercent = if (initialCapacity > 0) (currentCapacity.toFloat() / initialCapacity * 100).toInt() else 100
        
        return when (capacityPercent.coerceIn(0, 100)) {
            in 90..100 -> ctx.getString(R.string.battery_health_excellent)
            in 75..89 -> ctx.getString(R.string.battery_health_good)
            in 60..74 -> ctx.getString(R.string.battery_health_fair)
            else -> ctx.getString(R.string.battery_health_poor)
        }
    }

    private fun getChargingType(ctx: Context, intent: Intent, status: Int, isCharging: Boolean): String {
        if (status == BatteryManager.BATTERY_STATUS_FULL) return ctx.getString(R.string.battery_status_full)
        if (!isCharging) return ctx.getString(R.string.battery_not_charging)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        return when (plugged) {
            BatteryManager.BATTERY_PLUGGED_USB -> ctx.getString(R.string.battery_plugged_usb)
            BatteryManager.BATTERY_PLUGGED_AC -> ctx.getString(R.string.battery_plugged_ac)
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> ctx.getString(R.string.battery_plugged_wireless)
            else -> ctx.getString(R.string.battery_charging)
        }
    }
    private fun getBatteryCapacityUa(
        pct: Float
    ): Float {

        val currentCapacity =
            batteryManager.getLongProperty(
                BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER
            )

        return if (
            currentCapacity > 0 &&
            pct > 1f
        ) {

            currentCapacity / (pct / 100f)

        } else {

            4000000f
        }
    }

    private fun calculateEstimate(
        pct: Float,
        isCharging: Boolean,
        uA: Long
    ): Int {

        val absCurrent =
            abs(uA)

        // 电流异常
        val invalidCurrent =
            absCurrent < 10000L ||
                    absCurrent > 15000000L

        if (invalidCurrent) {

            return estimateByPercent(
                pct,
                isCharging
            )
        }

        val currentUa =
            smoothCurrent(absCurrent)

        val totalCapacityUa =
            getBatteryCapacityUa(pct)

        return try {

            if (isCharging) {

                val remainPercent =
                    (100f - pct)
                        .coerceIn(0f, 100f)

                val remainCapacityUa =
                    totalCapacityUa *
                            remainPercent / 100f

                val hours =
                    remainCapacityUa / currentUa

                var minutes =
                    ceil(hours * 60f)
                        .toInt()

                // 高电量涓流修正
                if (pct >= 80f) {
                    minutes =
                        (minutes * 1.15f).toInt()
                }

                minutes.coerceIn(1, 1440)

            } else {

                val currentCapacityUa =
                    batteryManager.getLongProperty(
                        BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER
                    ).toFloat()

                val hours =
                    currentCapacityUa / currentUa

                val minutes =
                    floor(hours * 60f)
                        .toInt()

                minutes.coerceIn(1, 1440)
            }

        } catch (_: Exception) {

            estimateByPercent(
                pct,
                isCharging
            )
        }
    }

    private fun estimateByPercent(
        pct: Float,
        isCharging: Boolean
    ): Int {

        return if (isCharging) {

            when {

                pct >= 95f -> 10

                pct >= 90f -> 20

                pct >= 80f -> 35

                pct >= 70f -> 50

                pct >= 60f -> 70

                pct >= 50f -> 90

                pct >= 40f -> 110

                pct >= 30f -> 130

                pct >= 20f -> 150

                else -> 180
            }

        } else {

            when {

                pct >= 95f -> 480

                pct >= 80f -> 360

                pct >= 60f -> 240

                pct >= 40f -> 150

                pct >= 20f -> 90

                else -> 45
            }
        }
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
        val status: String = "Unknown",
        val chargingType: String = "Not Charging",
        val isCharging: Boolean = false,
        val iconRes: Int = R.drawable.ic_battery_0,
        val currentMa: Int = 0,
        val estimateMinutes: Int = -1
    )
}