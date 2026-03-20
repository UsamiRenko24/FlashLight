package com.name.FlashLight

import android.content.BroadcastReceiver
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
import utils.TemperatureManager

class StatsActivity : BaseActivity() {

    // UI 组件
    private lateinit var ivTraceback: ImageView
    private lateinit var ivSettings: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvBatteryPercent: TextView
    private lateinit var tvBatteryStatus: TextView
    private lateinit var tvState: TextView
    private lateinit var tvTimeFull: TextView
    private lateinit var ivBatteryIcon: ImageView
    private lateinit var progressFlashlight: ProgressBar
    private lateinit var progressScreenLight: ProgressBar
    private lateinit var progressBlink: ProgressBar
    private lateinit var tvFlashlightTime: TextView
    private lateinit var tvScreenLightTime: TextView
    private lateinit var tvBlinkTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var cardContainer1: LinearLayout

    private lateinit var slidingAutoBrightness: SlidingButton
    private lateinit var slidingTemperature: SlidingButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.stats)
        initViews()
        
        PageUsageRecorder.recordPageVisit(this, PageConstants.PAGE_STATS)
        StartupModeManager.recordLastPage(this, PageConstants.PAGE_STATS)

        setupClickListeners()
        updateBatteryDisplay()
        updateStats()
        loadAutoBrightnessState()
        setupAutoBrightnessListener()
        setupSlidingButton()
        startBatteryMonitor()
    }

    override fun onResume() {
        super.onResume()
        updateStats()
        updateBatteryDisplay()
        loadAutoBrightnessState()
    }

    private fun initViews() {
        ivTraceback = findViewById(R.id.traceback)
        ivSettings = findViewById(R.id.iv_settings)
        tvTitle = findViewById(R.id.tv_title)
        tvBatteryPercent = findViewById(R.id.tv_battery_percent)
        tvBatteryStatus = findViewById(R.id.tv_battery_status)
        tvState = findViewById(R.id.tv_state)
        tvTimeFull = findViewById(R.id.tv_time_to_full)
        ivBatteryIcon = findViewById(R.id.iv_battery_icon)
        progressFlashlight = findViewById(R.id.progress_flashlight)
        progressScreenLight = findViewById(R.id.progress_screen_light)
        progressBlink = findViewById(R.id.progress_blink)
        tvFlashlightTime = findViewById(R.id.tv_flashlight_time)
        tvScreenLightTime = findViewById(R.id.tv_screen_light_time)
        tvBlinkTime = findViewById(R.id.tv_blink_time)
        tvTotalTime = findViewById(R.id.tv_total_time)
        cardContainer1 = findViewById(R.id.card_container1)
        
        slidingAutoBrightness = findViewById(R.id.btn_brightness)
        slidingTemperature = findViewById(R.id.btn_temperature_switch)
    }

    private fun setupSlidingButton() {
        // 加载保存的状态
        val isEnabled = TemperatureManager.isEnabled()
        slidingTemperature.isChecked = isEnabled

        // 设置监听器
        slidingTemperature.setOnStateChangedListener { isChecked ->
            // 保存状态并通知 TemperatureManager
            TemperatureManager.setEnabled(isChecked)

            Toast.makeText(
                this,
                if (isChecked) "温度监控已开启" else "温度监控已关闭",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupClickListeners() {
        ivTraceback.setOnClickListener { handleBackPress() }
        ivSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun loadAutoBrightnessState() {
        slidingAutoBrightness.setCheckedSilently(AutoBrightnessManager.getAutoBrightnessState(this))
    }

    private fun setupAutoBrightnessListener() {
        slidingAutoBrightness.setOnStateChangedListener { isChecked ->
            AutoBrightnessManager.toggleAutoBrightness(
                activity = this,
                targetState = isChecked,
                onSuccess = { },
                onFailure = { slidingAutoBrightness.setCheckedSilently(!isChecked) }
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        AutoBrightnessManager.handlePermissionResult(this, requestCode) { successState ->
            slidingAutoBrightness.setCheckedSilently(successState)
        }
    }

    private fun updateStats() {
        val flashlightTime = TimeRecorder.getTodayTime(this, "flashlight")
        val screenLightTime = TimeRecorder.getTodayTime(this, "screen_light")
        val blinkTime = TimeRecorder.getTodayTime(this, "blink")
        val totalTime = flashlightTime + screenLightTime + blinkTime
        
        tvTotalTime.text = formatTime(totalTime)
        setProgress(progressFlashlight, flashlightTime, totalTime)
        setProgress(progressScreenLight, screenLightTime, totalTime)
        setProgress(progressBlink, blinkTime, totalTime)

        tvFlashlightTime.text = formatTime(flashlightTime)
        tvScreenLightTime.text = formatTime(screenLightTime)
        tvBlinkTime.text = formatTime(blinkTime)
    }

    private fun setProgress(progressBar: ProgressBar, timeMinutes: Float, totalTime: Float) {
        val percentage = if (totalTime > 0) (timeMinutes / totalTime * 100).toInt().coerceIn(0, 100) else 0
        progressBar.progress = percentage
    }

    private fun formatTime(minutes: Float): String {
        return when {
            minutes < 1 -> "${(minutes * 60).toInt()}秒"
            minutes < 60 -> "${minutes.toInt()}分钟"
            else -> "${minutes.toInt() / 60}小时${minutes.toInt() % 60}分钟"
        }
    }

    private fun updateBatteryDisplay() {
        val batteryInfo = BatteryHelper.getBatteryInfo(this)
        
        // 根据电量切换背景
        val level = batteryInfo.level
        when {
            level <= 25 -> cardContainer1.setBackgroundResource(R.drawable.bg_sos_card_red)
            level <= 50 -> cardContainer1.setBackgroundResource(R.drawable.bg_yellow_card)
            else -> cardContainer1.setBackgroundResource(R.drawable.bg_green_card)
        }

        tvBatteryPercent.text = batteryInfo.levelText
        tvBatteryStatus.text = batteryInfo.chargingType
        ivBatteryIcon.setImageResource(batteryInfo.iconRes)
        updateTimeEstimate(batteryInfo.level, batteryInfo.isCharging)
    }

    private fun updateTimeEstimate(batteryPct: Float, isCharging: Boolean) {
        if (isCharging) {
            val minutesToFull = ((100 - batteryPct) / 2).toInt().coerceAtLeast(1)
            tvTimeFull.text = formatMinutes(minutesToFull)
            tvState.text = "预计充满时间"
        } else {
            val minutesRemaining = (batteryPct / 1).toInt().coerceAtLeast(1)
            tvTimeFull.text = formatMinutes(minutesRemaining)
            tvState.text = "预计剩余时间"
        }
    }

    private fun formatMinutes(minutes: Int): String = if (minutes >= 60) "${minutes / 60}小时${minutes % 60}分钟" else "${minutes}分钟"

    private fun startBatteryMonitor() {
        val handler = Handler(Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                updateBatteryDisplay()
                updateStats()
                handler.postDelayed(this, 5000)
            }
        })
    }
}