package com.name.FlashLight

import android.content.Intent
import android.widget.ProgressBar
import androidx.lifecycle.lifecycleScope
import com.name.FlashLight.databinding.StatsBinding
import com.name.FlashLight.utils.PageConstants
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import utils.*

/**
 * 工业级模块化重构 - 统计页面
 */
class StatsActivity : BaseActivity<StatsBinding>() {

    override val pageTrackName = PageConstants.PAGE_STATS
    override val isBatteryMonitorEnabled = true
    override val isLowBatteryCheckEnabled = true

    override fun createBinding(): StatsBinding = StatsBinding.inflate(layoutInflater)

    override fun initViews() {
        binding.bottomNav.selectedItemId = R.id.nav_stats
        binding.btnBrightness.setCheckedSilently(AutoBrightnessManager.getAutoBrightnessState(this))
        binding.btnTemperatureSwitch.setCheckedSilently(TemperatureManager.isEnabled())
    }

    override fun initListeners() {
        binding.traceback.setOnClickListener { handleBackPress() }
        binding.ivSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        binding.bottomNav.setOnItemSelectedListener { item ->
            handleNavigation(item.itemId)
        }
        binding.btnLowBattery.setOnStateChangedListener { isEnabled ->
            lifecycleScope.launch {
                DataStoreManager.setLowBatteryEnabled(this@StatsActivity, isEnabled)
            }
        }

        binding.btnTemperatureSwitch.setOnStateChangedListener { isEnabled ->
            TemperatureManager.setEnabled(isEnabled)
        }

        binding.btnBrightness.setOnStateChangedListener { isEnabled ->

            AutoBrightnessManager.toggleAutoBrightness(
                this,
                isEnabled,
                {
                    binding.btnBrightness
                        .setCheckedSilently(it)
                },
                {
                    binding.btnBrightness
                        .setCheckedSilently(
                            AutoBrightnessManager
                                .getAutoBrightnessState(this)
                        )
                }
            )
        }
    }

    override fun initObservers() {
        lifecycleScope.launch {
            DataStoreManager.isLowBatteryEnabled(this@StatsActivity).collectLatest { isEnabled ->
                binding.btnLowBattery.setCheckedSilently(isEnabled)
                LowBatteryManager.setProtectionEnabled(this@StatsActivity, isEnabled)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
    }

    override fun onBatteryStatusChanged(info: BatteryRepository.BatteryInfo) {
        if (!isFinishing && !isDestroyed) {
            updateBatteryUI(info)
        }
    }

    private fun handleNavigation(itemId: Int): Boolean {

        when (itemId) {

            R.id.nav_stats -> return true

            R.id.nav_home -> {
                startActivity(
                    Intent(this, MainActivity::class.java)
                )
                finish()
                overridePendingTransition(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
                )
                return false
            }

            R.id.nav_flashlight -> {
                startActivity(
                    Intent(this, FlashlightActivity::class.java)
                )
                finish()
                overridePendingTransition(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
                )
                return false
            }

            R.id.nav_blink -> {
                startActivity(
                    Intent(this, BlinkActivity::class.java)
                )
                finish()
                overridePendingTransition(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
                )
                return false
            }

            R.id.nav_settings -> {
                startActivity(
                    Intent(this, SettingsActivity::class.java)
                )
                finish()
                overridePendingTransition(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
                )
                return false
            }
        }

        return false
    }
    private fun refreshStats() {
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
            tvBatteryStatus.text = info.status
            ivBatteryIcon.setImageResource(info.iconRes)
        }
        updateTimeEstimate(info)
    }

    /**
     * 【核心修复】：精确区分充电状态
     */
    private fun updateTimeEstimate(info: BatteryRepository.BatteryInfo) {
        if (info.isCharging) {
            binding.tvState.text = getString(R.string.time_to_full)

            binding.tvTimeToFull.text = when {
                // 1. 电量已达 100% 或状态为充满
                info.level >= 100f -> getString(R.string.battery_status_full)

                // 2. 能够算出剩余时间（分钟数 > 0）
                info.estimateMinutes > 0 -> info.estimateMinutes.toFloat().toDetailedTime(this)

                // 3. 分钟数为 -1 或刚开始充电
                else -> getString(R.string.calculating)
            }
        } else {
            // 未充电逻辑
            binding.tvState.text = getString(R.string.time_remaining)
            val minutes = if (info.estimateMinutes > 0) info.estimateMinutes.toFloat() else (info.level * 10)
            binding.tvTimeToFull.text = minutes.toDetailedTime(this)
        }
    }

    private fun renderProgressBar(bar: ProgressBar, time: Float, total: Float) {
        bar.progress = if (total > 0) (time / total * 100).toInt().coerceIn(0, 100) else 0
    }
}