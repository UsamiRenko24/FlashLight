package com.name.FlashLight

import android.content.BroadcastReceiver
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.name.FlashLight.utils.PageConstants
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import utils.AutoBrightnessManager

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

    // 电池状态监听器
    private lateinit var batteryReceiver: BroadcastReceiver
    private lateinit var slidingAutoBrightness: SlidingButton

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
        updateBatteryDisplay()
        startBatteryMonitor()
    }
    override fun onResume() {
        super.onResume()
        // 每次回到这个界面都刷新数据
        updateStats()
        updateBatteryDisplay()
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
        slidingAutoBrightness = findViewById(R.id.btn_brightness)
    }

    private fun setupClickListeners() {
        // 返回按钮
        ivTraceback.setOnClickListener {
            handleBackPress()
        }

        // 设置按钮
        ivSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
    private fun loadAutoBrightnessState() {
        val isAuto = AutoBrightnessManager.getAutoBrightnessState(this)
        slidingAutoBrightness.setCheckedSilently(isAuto)
    }

    private fun setupAutoBrightnessListener() {
        slidingAutoBrightness.setOnStateChangedListener { isChecked ->
            AutoBrightnessManager.toggleAutoBrightness(
                activity = this,
                targetState = isChecked,
                onSuccess = { /* 状态已由 UI 改变，无需操作 */ },
                onFailure = {
                    // 权限失败或设置失败，静默回滚 UI
                    slidingAutoBrightness.setCheckedSilently(!isChecked)
                }
            )
        }
    }

    // ✅ 修复：处理权限申请后的返回结果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // 调用管理器来处理权限结果
        AutoBrightnessManager.handlePermissionResult(this, requestCode) { successState ->
            // 如果授权成功并自动应用了设置，同步 UI 状态
            slidingAutoBrightness.setCheckedSilently(successState)
        }
    }
    private fun updateStats() {
        // 获取各功能今日使用时间（分钟）
        val flashlightTime = TimeRecorder.getTodayTime(this, "flashlight")
        val screenLightTime = TimeRecorder.getTodayTime(this, "screen_light")
        val blinkTime = TimeRecorder.getTodayTime(this, "blink")
        // 总使用时间
        val totalTime = flashlightTime + screenLightTime + blinkTime
        tvTotalTime.text = formatTime(totalTime)

        setProgress(progressFlashlight, flashlightTime,totalTime)
        setProgress(progressScreenLight, screenLightTime,totalTime)
        setProgress(progressBlink, blinkTime,totalTime)

        // 显示时间
        tvFlashlightTime.text = formatTime(flashlightTime)
        tvScreenLightTime.text = formatTime(screenLightTime)
        tvBlinkTime.text = formatTime(blinkTime)

    }
    private fun setProgress(progressBar: ProgressBar, timeMinutes: Float, totalTime: Float) {
        val percentage = (timeMinutes / totalTime * 100).toInt().coerceIn(0, 100)
        progressBar.progress = percentage
    }
    private fun formatTime(minutes: Float): String {
        return when {
            minutes < 1 -> "${(minutes * 60).toInt()}秒"
            minutes < 60 -> "${minutes.toInt()}分钟"
            else -> {
                val hours = minutes.toInt() / 60
                val mins = minutes.toInt() % 60
                "${hours}小时${mins}分钟"
            }
        }
    }
    private fun updateBatteryDisplay() {
        // ✅ 获取完整的电池信息
        val batteryInfo = BatteryHelper.getBatteryInfo(this)

        // 更新基本UI
        tvBatteryPercent.text = batteryInfo.levelText
        tvBatteryStatus.text = batteryInfo.chargingType
        ivBatteryIcon.setImageResource(batteryInfo.iconRes)

        // 更新时间估计
        updateTimeEstimate(batteryInfo.level, batteryInfo.isCharging)
    }
    private fun updateTimeEstimate(batteryPct: Float, isCharging: Boolean) {
        if (isCharging) {
            val remainingToFull = 100 - batteryPct
            val minutesToFull = (remainingToFull / 2).toInt().coerceAtLeast(1)
            tvTimeFull.text = formatMinutes(minutesToFull)
            tvState.text = "预计充满时间"
        } else {
            val minutesRemaining = (batteryPct / 1).toInt().coerceAtLeast(1)
            tvTimeFull.text = formatMinutes(minutesRemaining)
            tvState.text = "预计剩余时间"
        }
    }
    private fun formatMinutes(minutes: Int): String {
        return if (minutes >= 60) {
            "${minutes / 60}小时${minutes % 60}分钟"
        } else {
            "${minutes}分钟"
        }
    }
    private fun startBatteryMonitor() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                updateBatteryDisplay()
                updateStats()
                handler.postDelayed(this, 5000) // 每5秒更新一次
            }
        }
        handler.post(runnable)
    }
    override fun onDestroy() {
        super.onDestroy()
        // 取消注册广播接收器
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // 忽略未注册的情况
        }
    }
}