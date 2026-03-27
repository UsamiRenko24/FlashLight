package com.name.FlashLight

import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.Toast
import com.name.FlashLight.databinding.StatsBinding
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

class StatsActivity : BaseActivity<StatsBinding>() {

    override fun createBinding(): StatsBinding {
        return StatsBinding.inflate(layoutInflater)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
        refreshUI()
    }

    override fun onBatteryStatusChanged() {
        if (!isFinishing && !isDestroyed) {
            updateBatteryDisplay()
        }
    }

    private fun refreshUI() {
        updateBatteryDisplay()
        updateStats()
    }

    private fun updateBatteryDisplay() {
        val batteryInfo = BatteryHelper.getBatteryInfo(this)

        val level = batteryInfo.level
        when {
            level <= 25 -> binding.cardContainer1.setBackgroundResource(R.drawable.bg_sos_card_red)
            level <= 50 -> binding.cardContainer1.setBackgroundResource(R.drawable.bg_yellow_card)
            else -> binding.cardContainer1.setBackgroundResource(R.drawable.bg_green_card)
        }

        binding.tvBatteryPercent.text = batteryInfo.levelText
        val direction = if (batteryInfo.isCharging) "↑" else "↓"
        binding.tvBatteryStatus.text = "${batteryInfo.chargingType} $direction ${abs(batteryInfo.currentMa)} mA"
        binding.ivBatteryIcon.setImageResource(batteryInfo.iconRes)

        updateTimeEstimate(batteryInfo)
    }

    private fun updateStats() {
        val flashlightTime = TimeRecorder.getTodayTime(this, "flashlight")
        val screenLightTime = TimeRecorder.getTodayTime(this, "screen_light")
        val blinkTime = TimeRecorder.getTodayTime(this, "blink")
        val totalTime = flashlightTime + screenLightTime + blinkTime
        
        binding.tvTotalTime.text = formatTime(totalTime)
        setProgress(binding.progressFlashlight, flashlightTime, totalTime)
        setProgress(binding.progressScreenLight, screenLightTime, totalTime)
        setProgress(binding.progressBlink, blinkTime, totalTime)

        binding.tvHealth.text = updateBatteryHealth(this)
        binding.tvFlashlightTime.text = formatTime(flashlightTime)
        binding.tvScreenLightTime.text = formatTime(screenLightTime)
        binding.tvBlinkTime.text = formatTime(blinkTime)
    }

    private fun formatTime(minutes: Float): String {
        return when {
            minutes < 1 -> "${(minutes * 60).toInt()}${getString(R.string.second)}"
            minutes < 60 -> "${minutes.toInt()}${getString(R.string.minute)}"
            else -> "${minutes.toInt() / 60}${getString(R.string.hour)}${minutes.toInt() % 60}${getString(R.string.minute)}"
        }
    }

    private fun updateTimeEstimate(info: BatteryHelper.BatteryInfo) {
        if (info.isCharging) {
            if (info.level >= 100f || info.chargingType.contains(getString(R.string.battery_status_full))) {
                binding.tvState.text = getString(R.string.battery_status)
                binding.tvTimeToFull.text = getString(R.string.battery_status_full)
            } else {
                binding.tvState.text = getString(R.string.time_to_full)
                binding.tvTimeToFull.text = if (info.estimateMinutes > 0) formatMinutes(info.estimateMinutes) else getString(R.string.calculating)
            }
        } else {
            binding.tvState.text = getString(R.string.time_remaining)
            val minutes = if (info.estimateMinutes > 0) info.estimateMinutes else (info.level * 10).toInt()
            binding.tvTimeToFull.text = formatMinutes(minutes)
        }
    }

    private fun formatMinutes(minutes: Int): String = if (minutes >= 60) "${minutes / 60}${getString(R.string.hour)}${minutes % 60}${getString(R.string.minute)}" else "$minutes${getString(R.string.minute)}"

    private fun setProgress(progressBar: ProgressBar, time: Float, total: Float) {
        progressBar.progress = if (total > 0) (time / total * 100).toInt().coerceIn(0, 100) else 0
    }

    private fun loadLowBatterySetting() { binding.btnLowBattery.setCheckedSilently(LowBatteryManager.isProtectionEnabled(this)) }
    private fun setupLowBatterySwitch() { binding.btnLowBattery.setOnStateChangedListener { LowBatteryManager.setProtectionEnabled(this, it); Toast.makeText(this, if (it) "开启" else "关闭", Toast.LENGTH_SHORT).show() } }
    private fun setupSlidingButton() { binding.btnTemperatureSwitch.setCheckedSilently(TemperatureManager.isEnabled()); binding.btnTemperatureSwitch.setOnStateChangedListener { TemperatureManager.setEnabled(it) } }
    private fun setupClickListeners() {
        binding.traceback.setOnClickListener { handleBackPress() }
        binding.ivSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
    }
    private fun loadAutoBrightnessState() { binding.btnBrightness.setCheckedSilently(AutoBrightnessManager.getAutoBrightnessState(this)) }
    private fun setupAutoBrightnessListener() { binding.btnBrightness.setOnStateChangedListener { AutoBrightnessManager.toggleAutoBrightness(this, it, {}, { binding.btnBrightness.setCheckedSilently(!it) }) } }
}