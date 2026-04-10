package com.name.FlashLight

import android.annotation.SuppressLint
import android.content.Intent
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.lifecycle.lifecycleScope
import com.name.FlashLight.databinding.ActivityMainBinding
import com.name.FlashLight.utils.PageConstants
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import utils.BatteryRepository
import utils.DataStoreManager
import utils.TimeRepository
import utils.VibrationManager
import utils.feedback
import utils.toDetailedTime
import utils.toDigitalTime

/**
 * 模块化重构后的主页
 */
class MainActivity : BaseActivity<ActivityMainBinding>() {

    // 本地缓存变量
    private var flashlightAutoOffMinutes = 5

    // --- 1. 声明式配置 ---
    override val pageTrackName = PageConstants.PAGE_HOME
    override val isBatteryMonitorEnabled = true
    override val isLowBatteryCheckEnabled = true

    override fun createBinding(): ActivityMainBinding = ActivityMainBinding.inflate(layoutInflater)

    /**
     * 模块 A: 初始化静态视图
     */
    override fun initViews() {
        binding.bottomNav.selectedItemId = R.id.nav_home
    }

    /**
     * 模块 B: 事件监听
     */
    override fun initListeners() {
        setupFlashlightTouchEffect()

        binding.flashlight.setOnClickListener { v ->
            v.feedback()
            startActivity(Intent(this, FlashlightActivity::class.java))
        }

        binding.layoutScreenLight.setOnClickListener { v ->
            v.feedback()
            startActivity(Intent(this, ScreenLightActivity::class.java))
        }

        binding.layoutBlink.setOnClickListener { v ->
            v.feedback()
            startActivity(Intent(this, BlinkActivity::class.java))
        }

        binding.ivSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.tvTime.setOnClickListener {
            startActivity(Intent(this, AutomaticActivity::class.java))
        }

        binding.bottomNav.setOnItemSelectedListener { item -> handleNavigation(item.itemId) }

        binding.btnSwitch.setOnStateChangedListener { isEnabled ->
            binding.btnSwitch.feedback()
            lifecycleScope.launch {
                DataStoreManager.setVibrationEnabled(this@MainActivity, isEnabled)
            }
        }
    }

    /**
     * 模块 C: 响应式观察
     */
    override fun initObservers() {
        // 监听震动配置
        lifecycleScope.launch {
            DataStoreManager.isVibrationEnabled(this@MainActivity).collectLatest { isEnabled ->
                binding.btnSwitch.setCheckedSilently(isEnabled)
            }
        }

        // 监听自动关闭时间联动
        lifecycleScope.launch {
            DataStoreManager.getFlashlightAutoOffTime(this@MainActivity).collectLatest { minutes ->
                flashlightAutoOffMinutes = minutes
                updateAutoOffDisplay(minutes)
            }
        }
    }

    // --- 业务逻辑与钩子重写 ---

    override fun onResume() {
        super.onResume()
        updateStats()
    }

    override fun onBatteryStatusChanged(info: BatteryRepository.BatteryInfo) {
        binding.apply {
            tvBatteryPercent.text = info.levelText
            tvBatteryStatus.text = info.status
            ivBatteryIcon.setImageResource(info.iconRes)
        }
    }

    private fun updateAutoOffDisplay(minutes: Int) {
        binding.tvTime.text = if (minutes >= 114514) getString(R.string.auto_off_never) 
                             else minutes.toFloat().toDetailedTime(this)
    }

    private fun updateStats() {
        val flashlightTime = timeRepository.getTodayUsageMinutes(TimeRepository.TYPE_FLASHLIGHT)
        binding.tvFlashlightTime.text = flashlightTime.toDigitalTime()
        updateAutoOffDisplay(flashlightAutoOffMinutes)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFlashlightTouchEffect() {
        binding.flashlight.setOnTouchListener { v, event ->
            val isInside = event.x >= 0 && event.x <= v.width && event.y >= 0 && event.y <= v.height
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start()
                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(OvershootInterpolator()).start()
                    if (isInside) v.performClick()
                }
                MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
            }
            true
        }
    }
    private fun handleNavigation(itemId: Int): Boolean {
        when (itemId) {
            R.id.nav_home -> return true
            R.id.nav_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); return false }
            R.id.nav_flashlight -> { startActivity(Intent(this, FlashlightActivity::class.java)); return false }
            R.id.nav_blink -> { startActivity(Intent(this, BlinkActivity::class.java)); return false }
            R.id.nav_stats -> { startActivity(Intent(this, StatsActivity::class.java)); return false }
        }
        return false
    }
}