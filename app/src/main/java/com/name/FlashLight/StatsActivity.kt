package com.name.FlashLight

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.name.FlashLight.utils.PageConstants
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import utils.AutoBrightnessManager
import utils.BatteryHelper
import utils.BatteryHelper.updateBatteryHealth
import utils.LowBatteryManager
import utils.TemperatureManager
import utils.TimeRecorder
import kotlin.math.abs

class StatsActivity : BaseActivity() {

    // UI 组件
    private lateinit var ivTraceback: ImageView
    private lateinit var ivSettings: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvBatteryPercent: TextView
    private lateinit var tvBatteryStatus: TextView
    private lateinit var tvState: TextView
    private lateinit var tvTimeFull: TextView
    private lateinit var tvHealth: TextView
    private lateinit var ivBatteryIcon: ImageView
    private lateinit var progressFlashlight: ProgressBar
    private lateinit var progressScreenLight: ProgressBar
    private lateinit var progressBlink: ProgressBar
    private lateinit var tvFlashlightTime: TextView
    private lateinit var tvScreenLightTime: TextView
    private lateinit var tvBlinkTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var cardContainer1: LinearLayout
    private lateinit var slidingLowBattery: SlidingButton
    private lateinit var slidingAutoBrightness: SlidingButton
    private lateinit var slidingTemperature: SlidingButton

    // 【核心修复】将 Handler 设为类成员，用于生命周期管理
    private val batteryHandler = Handler(Looper.getMainLooper())
    private var batteryRunnable: Runnable? = null
    private var isMonitoringActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.stats)
        initViews()
        
        PageUsageRecorder.recordPageVisit(this, PageConstants.PAGE_STATS)
        StartupModeManager.recordLastPage(this, PageConstants.PAGE_STATS)

        setupClickListeners()
        loadAutoBrightnessState()
        setupAutoBrightnessListener()
        setupSlidingButton()
        loadLowBatterySetting()
        setupLowBatterySwitch()
    }

    override fun onResume() {
        super.onResume()
        // 标记监控开启
        isMonitoringActive = true
        updateBatteryDisplay()
        updateStats()
        startBatteryMonitor()
    }

    override fun onPause() {
        // 【核心修复】在 Pause 阶段就停止监控，比 Destroy 更及时，防止非法资源访问
        isMonitoringActive = false
        batteryRunnable?.let { batteryHandler.removeCallbacks(it) }
        super.onPause()
    }

    private fun initViews() {
        ivTraceback = findViewById(R.id.traceback)
        ivSettings = findViewById(R.id.iv_settings)
        tvTitle = findViewById(R.id.tv_title)
        tvBatteryPercent = findViewById(R.id.tv_battery_percent)
        tvBatteryStatus = findViewById(R.id.tv_battery_status)
        tvState = findViewById(R.id.tv_state)
        tvTimeFull = findViewById(R.id.tv_time_to_full)
        tvHealth = findViewById(R.id.tv_health)
        ivBatteryIcon = findViewById(R.id.iv_battery_icon)
        progressFlashlight = findViewById(R.id.progress_flashlight)
        progressScreenLight = findViewById(R.id.progress_screen_light)
        progressBlink = findViewById(R.id.progress_blink)
        tvFlashlightTime = findViewById(R.id.tv_flashlight_time)
        tvScreenLightTime = findViewById(R.id.tv_screen_light_time)
        tvBlinkTime = findViewById(R.id.tv_blink_time)
        tvTotalTime = findViewById(R.id.tv_total_time)
        cardContainer1 = findViewById(R.id.card_container1)
        slidingLowBattery = findViewById(R.id.btn_low_battery)
        slidingAutoBrightness = findViewById(R.id.btn_brightness)
        slidingTemperature = findViewById(R.id.btn_temperature_switch)
    }

    private fun startBatteryMonitor() {
        batteryRunnable = object : Runnable {
            override fun run() {
                // 安全判定锁
                if (isMonitoringActive && !isFinishing && !isDestroyed) {
                    updateBatteryDisplay()
                    updateStats()
                    batteryHandler.postDelayed(this, 2000)
                }
            }
        }
        batteryHandler.postDelayed(batteryRunnable!!, 2000)
    }

    private fun updateBatteryDisplay() {
        if (!isMonitoringActive || isFinishing || isDestroyed) return
        
        val batteryInfo = BatteryHelper.getBatteryInfo(this)
        
        // 背景根据电量切换逻辑
        val level = batteryInfo.level
        when {
            level <= 25 -> cardContainer1.setBackgroundResource(R.drawable.bg_sos_card_red)
            level <= 50 -> cardContainer1.setBackgroundResource(R.drawable.bg_yellow_card)
            else -> cardContainer1.setBackgroundResource(R.drawable.bg_green_card)
        }

        tvBatteryPercent.text = batteryInfo.levelText
        val direction = if (batteryInfo.isCharging) "↑" else "↓"
        tvBatteryStatus.text = "${batteryInfo.chargingType} $direction ${abs(batteryInfo.currentMa)} mA"
        ivBatteryIcon.setImageResource(batteryInfo.iconRes)
        
        updateTimeEstimate(batteryInfo)
    }

    private fun updateStats() {
        if (!isMonitoringActive || isFinishing || isDestroyed) return
        
        val flashlightTime = TimeRecorder.getTodayTime(this, "flashlight")
        val screenLightTime = TimeRecorder.getTodayTime(this, "screen_light")
        val blinkTime = TimeRecorder.getTodayTime(this, "blink")
        val totalTime = flashlightTime + screenLightTime + blinkTime
        
        tvTotalTime.text = formatTime(totalTime)
        setProgress(progressFlashlight, flashlightTime, totalTime)
        setProgress(progressScreenLight, screenLightTime, totalTime)
        setProgress(progressBlink, blinkTime, totalTime)

        tvHealth.text = updateBatteryHealth(this)
        tvFlashlightTime.text = formatTime(flashlightTime)
        tvScreenLightTime.text = formatTime(screenLightTime)
        tvBlinkTime.text = formatTime(blinkTime)
    }

    private fun formatTime(minutes: Float): String {
        if (isFinishing || isDestroyed) return ""
        return when {
            minutes < 1 -> "${(minutes * 60).toInt()}" + getString(R.string.second)
            minutes < 60 -> "${minutes.toInt()}" + getString(R.string.minute)
            else -> "${minutes.toInt() / 60}" + getString(R.string.hour) + "${minutes.toInt() % 60}" + getString(R.string.minute)
        }
    }

    private fun updateTimeEstimate(info: BatteryHelper.BatteryInfo) {
        if (info.isCharging) {
            if (info.level >= 100f || info.chargingType.contains(getString(R.string.battery_status_full))) {
                tvState.text = getString(R.string.battery_status)
                tvTimeFull.text = getString(R.string.battery_status_full)
            } else {
                tvState.text = getString(R.string.time_to_full)
                if (info.estimateMinutes > 0) {
                    tvTimeFull.text = formatMinutes(info.estimateMinutes)
                } else {
                    tvTimeFull.text = getString(R.string.calculating)
                }
            }
        } else {
            tvState.text = getString(R.string.time_remaining)
            if (info.estimateMinutes > 0) {
                tvTimeFull.text = formatMinutes(info.estimateMinutes)
            } else {
                val fallback = (info.level * 10).toInt()
                tvTimeFull.text = formatMinutes(fallback)
            }
        }
    }

    private fun formatMinutes(minutes: Int): String {
        if (isFinishing || isDestroyed) return ""
        return if (minutes >= 60) "${minutes / 60}" + getString(R.string.hour) + "${minutes % 60}" + getString(R.string.minute) else "${minutes}" + getString(R.string.minute)
    }

    private fun loadLowBatterySetting() {
        val isEnabled = LowBatteryManager.isProtectionEnabled(this)
        slidingLowBattery.setCheckedSilently(isEnabled)
    }

    private fun setupLowBatterySwitch() {
        slidingLowBattery.setOnStateChangedListener { isChecked ->
            LowBatteryManager.setProtectionEnabled(this, isChecked)
            Toast.makeText(this, if (isChecked) "低电量保护已开启" else "低电量保护已关闭", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSlidingButton() {
        val isEnabled = TemperatureManager.isEnabled()
        slidingTemperature.setCheckedSilently(isEnabled)
        slidingTemperature.setOnStateChangedListener { isChecked ->
            TemperatureManager.setEnabled(isChecked)
            Toast.makeText(this, if (isChecked) "温度监控已开启" else "温度监控已关闭", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupClickListeners() {
        ivTraceback.setOnClickListener { handleBackPress() }
        ivSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
    }

    private fun loadAutoBrightnessState() {
        slidingAutoBrightness.setCheckedSilently(AutoBrightnessManager.getAutoBrightnessState(this))
    }

    private fun setupAutoBrightnessListener() {
        slidingAutoBrightness.setOnStateChangedListener { isChecked ->
            AutoBrightnessManager.toggleAutoBrightness(this, isChecked, {}, { slidingAutoBrightness.setCheckedSilently(!isChecked) })
        }
    }

    private fun setProgress(progressBar: ProgressBar, timeMinutes: Float, totalTime: Float) {
        val percentage = if (totalTime > 0) (timeMinutes / totalTime * 100).toInt().coerceIn(0, 100) else 0
        progressBar.progress = percentage
    }

    override fun onDestroy() {
        isMonitoringActive = false
        batteryRunnable?.let { batteryHandler.removeCallbacksAndMessages(null) }
        super.onDestroy()
    }
}