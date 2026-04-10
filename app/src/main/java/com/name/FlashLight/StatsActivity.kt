package com.name.FlashLight

import android.content.Intent
import android.widget.ProgressBar
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.name.FlashLight.databinding.StatsBinding
import com.name.FlashLight.utils.PageConstants
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import utils.*

/**
 * 工业级模块化重构 - 统计页面
 * 职责：负责展示时长统计、电池健康度及系统级开关管理
 */
class StatsActivity : BaseActivity<StatsBinding>() {

    override val pageTrackName = PageConstants.PAGE_STATS
    override val isBatteryMonitorEnabled = true
    override val isLowBatteryCheckEnabled = true

    override fun createBinding(): StatsBinding = StatsBinding.inflate(layoutInflater)

    /**
     * 职责模块 A: UI 静态初始化
     */
    override fun initViews() {
        // 同步系统亮度状态
        binding.btnBrightness.setCheckedSilently(AutoBrightnessManager.getAutoBrightnessState(this))
        // 同步温度监控开关状态
        binding.btnTemperatureSwitch.setCheckedSilently(TemperatureManager.isEnabled())
    }

    /**
     * 职责模块 B: 事件监听集中营
     */
    override fun initListeners() {
        binding.traceback.setOnClickListener { handleBackPress() }
        binding.ivSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        // 处理低电量开关手动动作
        binding.btnLowBattery.setOnStateChangedListener { isEnabled ->
            lifecycleScope.launch {
                DataStoreManager.setLowBatteryEnabled(this@StatsActivity, isEnabled)
                Toast.makeText(this@StatsActivity, 
                    if (isEnabled) getString(R.string.toast_on) else getString(R.string.toast_off),
                    Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnTemperatureSwitch.setOnStateChangedListener { isEnabled ->
            TemperatureManager.setEnabled(isEnabled)
        }

        binding.btnBrightness.setOnStateChangedListener { isEnabled ->
            AutoBrightnessManager.toggleAutoBrightness(this, isEnabled, {}, {
                binding.btnBrightness.setCheckedSilently(!isEnabled)
            })
        }
    }

    /**
     * 职责模块 C: 响应式配置观察中心
     */
    override fun initObservers() {
        lifecycleScope.launch {
            // 修正：调用正确的方法名 isLowBatteryEnabled
            DataStoreManager.isLowBatteryEnabled(this@StatsActivity).collectLatest { isEnabled ->
                binding.btnLowBattery.setCheckedSilently(isEnabled)
                LowBatteryManager.setProtectionEnabled(this@StatsActivity, isEnabled)
            }
        }
    }

    // --- 行为钩子重写 (由父类自动驱动) ---

    override fun onResume() {
        super.onResume()
        refreshStats()
    }

    /**
     * 关键钩子：父类监听到电池变化，这里只管“怎么画”
     */
    override fun onBatteryStatusChanged(info: BatteryRepository.BatteryInfo) {
        if (!isFinishing && !isDestroyed) {
            updateBatteryUI(info)
        }
    }

    private fun refreshStats() {
        // 初始化时手动同步一次电池信息
        updateBatteryUI(batteryRepository.getCurrentBatteryInfo(this))
        
        val fTime = timeRepository.getTodayUsageMinutes(TimeRepository.TYPE_FLASHLIGHT)
        val sTime = timeRepository.getTodayUsageMinutes(TimeRepository.TYPE_SCREEN_LIGHT)
        val bTime = timeRepository.getTodayUsageMinutes(TimeRepository.TYPE_BLINK)
        val total = timeRepository.getTodayTotalUsageMinutes()

        binding.apply {
            tvTotalTime.text = total.toDetailedTime(this@StatsActivity)
            tvHealth.text = batteryRepository.getBatteryHealthDescription(this@StatsActivity)

            renderProgressBar(progressFlashlight, fTime, total)
            renderProgressBar(progressScreenLight, sTime, total)
            renderProgressBar(progressBlink, bTime, total)

            tvFlashlightTime.text = fTime.toDetailedTime(this@StatsActivity)
            tvScreenLightTime.text = sTime.toDetailedTime(this@StatsActivity)
            tvBlinkTime.text = bTime.toDetailedTime(this@StatsActivity)
        }
    }

    private fun updateBatteryUI(info: BatteryRepository.BatteryInfo) {
        val level = info.level
        val cardBg = when {
            level <= 25 -> R.drawable.bg_sos_card_red
            level <= 50 -> R.drawable.bg_yellow_card
            else -> R.drawable.bg_green_card
        }

        binding.apply {
            cardContainer1.setBackgroundResource(cardBg)
            tvBatteryPercent.text = info.levelText
            tvBatteryStatus.text = info.status // 统一字段，确保多语言正确
            ivBatteryIcon.setImageResource(info.iconRes)
        }
        updateTimeEstimate(info)
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

    private fun renderProgressBar(bar: ProgressBar, time: Float, total: Float) {
        bar.progress = if (total > 0) (time / total * 100).toInt().coerceIn(0, 100) else 0
    }
}