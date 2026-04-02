package com.name.FlashLight

import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.name.FlashLight.databinding.StatsBinding
import com.name.FlashLight.utils.PageConstants
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import utils.AutoBrightnessManager
import utils.BatteryRepository
import utils.DataStoreManager
import utils.LowBatteryManager
import utils.TemperatureManager
import utils.TimeRepository
import utils.toDetailedTime

class StatsActivity : BaseActivity<StatsBinding>() {

    override fun createBinding(): StatsBinding {
        return StatsBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        PageUsageRecorder.recordPageVisit(this, PageConstants.PAGE_STATS)
        StartupModeManager.recordLastPage(this, PageConstants.PAGE_STATS)

        setupClickListeners()

        observeDataStoreSettings()

        setupSlidingButtons()
        loadAutoBrightnessState()
        setupAutoBrightnessListener()
    }

    /**
     * DataStore 响应式逻辑：
     * 这是 DataStore 最强的地方：你不需要手动在 onResume 里刷开关状态，
     * 它像是一个“永不关闭的监听器”。
     */
    private fun observeDataStoreSettings() {
        lifecycleScope.launch {
            DataStoreManager.isLowBatteryEnabled(this@StatsActivity).collectLatest { isEnabled ->
                // 收到新值，立即同步 UI
                binding.btnLowBattery.setCheckedSilently(isEnabled)
                // 同时同步底层逻辑
                LowBatteryManager.setProtectionEnabled(this@StatsActivity, isEnabled)
            }
        }
    }

    private fun setupLowBatterySwitch() {
        binding.btnLowBattery.setOnStateChangedListener { isEnabled ->
            // 2. 【核心：写入】DataStore
            // 写入是异步的，必须在协程里跑
            lifecycleScope.launch {
                DataStoreManager.setLowBatteryEnabled(this@StatsActivity, isEnabled)
                Toast.makeText(this@StatsActivity, if (isEnabled) "保护已开启" else "保护已关闭", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    /**
     * 处理电池广播：直接在 Activity 逻辑里更新 UI
     */
    override fun onBatteryStatusChanged(info: BatteryRepository.BatteryInfo) {
        if (!isFinishing && !isDestroyed) {
            updateBatteryDisplay(info)
        }
    }

    private fun refreshUI() {
        // 使用 this (Activity) 作为 Context，确保拿到最新的多语言字符串
        updateBatteryDisplay(batteryRepository.getCurrentBatteryInfo(this))
        updateStats()
    }

    private fun updateBatteryDisplay(info: BatteryRepository.BatteryInfo) {
        val level = info.level
        // 根据电量动态切换卡片背景颜色
        when {
            level <= 25 -> binding.cardContainer1.setBackgroundResource(R.drawable.bg_sos_card_red)
            level <= 50 -> binding.cardContainer1.setBackgroundResource(R.drawable.bg_yellow_card)
            else -> binding.cardContainer1.setBackgroundResource(R.drawable.bg_green_card)
        }

        binding.tvBatteryPercent.text = info.levelText
        binding.tvBatteryStatus.text = info.chargingType
        binding.ivBatteryIcon.setImageResource(info.iconRes)

        updateTimeEstimate(info)
    }

    private fun updateStats() {
        val fTime = timeRepository.getTodayUsageMinutes(TimeRepository.TYPE_FLASHLIGHT)
        val sTime = timeRepository.getTodayUsageMinutes(TimeRepository.TYPE_SCREEN_LIGHT)
        val bTime = timeRepository.getTodayUsageMinutes(TimeRepository.TYPE_BLINK)
        val totalTime = timeRepository.getTodayTotalUsageMinutes()

        binding.tvTotalTime.text = totalTime.toDetailedTime(this)
        
        // 进度条逻辑
        setProgress(binding.progressFlashlight, fTime, totalTime)
        setProgress(binding.progressScreenLight, sTime, totalTime)
        setProgress(binding.progressBlink, bTime, totalTime)

        // 刷新健康度描述（传入 this 确保多语言正确）
        binding.tvHealth.text = batteryRepository.getBatteryHealthDescription(this)
        
        binding.tvFlashlightTime.text = fTime.toDetailedTime(this)
        binding.tvScreenLightTime.text = sTime.toDetailedTime(this)
        binding.tvBlinkTime.text = bTime.toDetailedTime(this)
    }

    private fun updateTimeEstimate(info: BatteryRepository.BatteryInfo) {
        if (info.isCharging) {
            binding.tvState.text = getString(R.string.time_to_full)
            binding.tvTimeToFull.text = if (info.estimateMinutes > 0) info.estimateMinutes.toFloat().toDetailedTime(this) else getString(R.string.battery_status_full)
        } else {
            binding.tvState.text = getString(R.string.time_remaining)
            val minutes = if (info.estimateMinutes > 0) info.estimateMinutes.toFloat() else (info.level * 10)
            binding.tvTimeToFull.text = minutes.toDetailedTime(this)
        }
    }

    private fun setProgress(progressBar: ProgressBar, time: Float, total: Float) {
        progressBar.progress = if (total > 0) (time / total * 100).toInt().coerceIn(0, 100) else 0
    }

    private fun setupSlidingButtons() {
        binding.btnTemperatureSwitch.setCheckedSilently(TemperatureManager.isEnabled())
        binding.btnTemperatureSwitch.setOnStateChangedListener { TemperatureManager.setEnabled(it) }
        setupLowBatterySwitch()
    }

    private fun loadAutoBrightnessState() {
        binding.btnBrightness.setCheckedSilently(AutoBrightnessManager.getAutoBrightnessState(this))
    }

    private fun setupAutoBrightnessListener() {
        binding.btnBrightness.setOnStateChangedListener { isEnabled ->
            AutoBrightnessManager.toggleAutoBrightness(this, isEnabled, {}, {
                binding.btnBrightness.setCheckedSilently(!isEnabled)
            })
        }
    }

    private fun setupClickListeners() {
        binding.traceback.setOnClickListener { handleBackPress() }
        binding.ivSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
    }
}