package com.name.FlashLight

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.lifecycle.lifecycleScope
import com.name.FlashLight.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import utils.BatteryRepository
import utils.DataStoreManager
import utils.TimeRepository
import utils.feedback
import utils.toDetailedTime
import utils.toDigitalTime

class MainActivity : BaseActivity<ActivityMainBinding>() {
    
    private val REQUEST_CODE1 = 1001
    private val REQUEST_CODE2 = 1002

    // 【新增】本地缓存变量
    private var flashlightAutoOffMinutes = 5

    override fun createBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupBottomNavigation()
        setupClickListeners()
        
        // 【核心修改】：开启 DataStore 全局实时监听
        observeSettings()
        
        setupVibrationButton()

        binding.bottomNav.selectedItemId = R.id.nav_home
    }

    /**
     * 响应式监听：这里是全应用逻辑的“大脑”
     */
    private fun observeSettings() {
        // 监听震动开关
        lifecycleScope.launch {
            DataStoreManager.isVibrationEnabled(this@MainActivity).collectLatest { isEnabled ->
                binding.btnSwitch.setCheckedSilently(isEnabled)
            }
        }

        // 【核心修复】：监听自动关闭时间，实现实时文字联动
        lifecycleScope.launch {
            DataStoreManager.getFlashlightAutoOffTime(this@MainActivity).collectLatest { minutes ->
                flashlightAutoOffMinutes = minutes
                // 只要 DataStore 变了，主页文字立刻跟着变
                updateAutoOffDisplay(minutes)
            }
        }
    }

    private fun updateAutoOffDisplay(minutes: Int) {
        if (minutes >= 114514) {
            binding.tvTime.text = getString(R.string.auto_off_never)
        } else {
            binding.tvTime.text = minutes.toFloat().toDetailedTime(this)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    override fun onBatteryStatusChanged(info: BatteryRepository.BatteryInfo) {
        if (!isFinishing && !isDestroyed) {
            updateBatteryDisplay(info)
        }
    }

    private fun refreshUI() {
        updateBatteryDisplay(batteryRepository.getCurrentBatteryInfo(this))
        updateStats()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        binding.flashlight.setOnTouchListener { v: View, event: MotionEvent ->
            val isInside = event.x >= 0 && event.x <= v.width && event.y >= 0 && event.y <= v.height
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val targetScale = if (isInside) 0.92f else 1.0f
                    v.animate().scaleX(targetScale).scaleY(targetScale).setDuration(100).start()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(OvershootInterpolator()).start()
                    if (isInside) v.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                    true
                }
                else -> false
            }
        }

        binding.flashlight.setOnClickListener { v: View ->
            v.feedback()
            startActivity(Intent(this, FlashlightActivity::class.java))
        }

        binding.layoutScreenLight.setOnClickListener { v: View ->
            v.feedback()
            startActivity(Intent(this, ScreenLightActivity::class.java))
        }

        binding.layoutBlink.setOnClickListener { v: View ->
            v.feedback()
            startActivityForResult(Intent(this, BlinkActivity::class.java), REQUEST_CODE2)
        }

        binding.ivSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun updateStats() {
        // 更新今日时长统计
        val flashlightTime = timeRepository.getTodayUsageMinutes(TimeRepository.TYPE_FLASHLIGHT)
        binding.tvFlashlightTime.text = flashlightTime.toDigitalTime()
        
        // 这里不再需要读取 DataStore，因为 observeSettings 已经帮我们同步好了
        updateAutoOffDisplay(flashlightAutoOffMinutes)
    }

    private fun updateBatteryDisplay(info: BatteryRepository.BatteryInfo) {
        binding.tvBatteryPercent.text = info.levelText
        binding.tvBatteryStatus.text = info.status
        binding.ivBatteryIcon.setImageResource(info.iconRes)
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_flashlight -> { 
                    startActivityForResult(Intent(this, FlashlightActivity::class.java), REQUEST_CODE1)
                    false 
                }
                R.id.nav_blink -> { 
                    startActivityForResult(Intent(this, BlinkActivity::class.java), REQUEST_CODE2)
                    false 
                }
                R.id.nav_stats -> { 
                    startActivity(Intent(this, StatsActivity::class.java))
                    false 
                }
                R.id.nav_settings -> { 
                    startActivity(Intent(this, SettingsActivity::class.java))
                    false 
                }
                else -> false
            }
        }
    }

    private fun setupVibrationButton() { 
        binding.btnSwitch.setOnStateChangedListener { isEnabled: Boolean ->
            // 开关操作直接给予预览式反馈
            binding.btnSwitch.feedback()

            lifecycleScope.launch {
                DataStoreManager.setVibrationEnabled(this@MainActivity, isEnabled)
            }
        }
    }
}