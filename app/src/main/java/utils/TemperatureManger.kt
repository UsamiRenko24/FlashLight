package utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper

object TemperatureManager {

    private const val PREFS_NAME = "temperature_settings"
    private const val KEY_MONITOR_ENABLED = "monitor_enabled"
    const val TEMP_WARNING = 45f

    // ✅ 不保存 Context，改用 Application 初始化
    private lateinit var appContext: Context

    private val listeners = mutableListOf<TemperatureListener>()
    private var handler: Handler? = null
    private var monitorRunnable: Runnable? = null
    private var isMonitoring = false
    private var currentTemperature = 0f
    private var isOverheating = false

    // ✅ 初始化时保存 Application Context
    fun init(context: Context) {
        this.appContext = context.applicationContext
    }

    // ✅ 检查是否已初始化
    private fun checkInitialized() {
        if (!::appContext.isInitialized) {
            throw IllegalStateException("TemperatureManager must be initialized first")
        }
    }
    // 设置开关状态（由 StatsActivity 调用）
    fun setEnabled(enabled: Boolean) {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_MONITOR_ENABLED, enabled).apply()

        if (enabled) {
            startMonitoring()
        } else {
            stopMonitoring()
        }

        // 通知所有监听器状态变化
        notifyStateChange()
    }

    // 移除监听器
    fun removeListener(listener: TemperatureListener) {
        listeners.remove(listener)
    }

    // 通知所有监听器
    private fun notifyStateChange() {
        listeners.forEach { it.onMonitorStateChanged(isEnabled()) }
    }
    // ✅ 从 SharedPreferences 读取，需要传入 Context
    fun isEnabled(): Boolean {
        if (!::appContext.isInitialized) return false
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_MONITOR_ENABLED, false)
    }

    private fun startMonitoring() {
        checkInitialized()
        if (isMonitoring) return

        isMonitoring = true
        handler = Handler(Looper.getMainLooper())

        monitorRunnable = object : Runnable {
            override fun run() {
                if (isMonitoring) {
                    updateTemperature()
                    handler?.postDelayed(this, 2000)
                }
            }
        }

        handler?.post(monitorRunnable!!)
    }

    private fun stopMonitoring() {
        isMonitoring = false
        monitorRunnable?.let { handler?.removeCallbacks(it) }
        handler = null
    }
    fun getCurrentTemperature(): Float = currentTemperature

    // 公开方法
    fun isOverheating(): Boolean = isOverheating

    // ✅ 使用保存的 appContext
    private fun updateTemperature() {
        val temperature = getBatteryTemperature()
        val overheating = temperature >= TEMP_WARNING

        if (temperature != currentTemperature || overheating != isOverheating) {
            currentTemperature = temperature
            isOverheating = overheating

            listeners.forEach { listener ->
                listener.onTemperatureUpdate(temperature, overheating)
            }
        }
    }

    // ✅ 使用 appContext
    private fun getBatteryTemperature(): Float {
        checkInitialized()
        return try {
            val batteryIntent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryIntent?.let { intent ->
                val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                temp / 10.0f
            } ?: 0f
        } catch (e: Exception) {
            e.printStackTrace()
            0f
        }
    }


    interface TemperatureListener {
        fun onMonitorStateChanged(isEnabled: Boolean)  // 开关状态变化
        fun onTemperatureUpdate(temperature: Float, isOverheating: Boolean)  // 温度更新
    }
}