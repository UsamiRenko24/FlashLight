package com.name.flashlight

import android.annotation.SuppressLint
import android.content.Intent
import android.view.MotionEvent
import android.view.animation.OvershootInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.name.flashlight.databinding.ActivityMainBinding
import com.name.flashlight.utils.PageConstants
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.name.flashlight.utils.BatteryRepository
import com.name.flashlight.utils.DataStoreManager
import com.name.flashlight.utils.TimeRepository
import com.name.flashlight.utils.VibrationManager
import com.name.flashlight.utils.feedback
import com.name.flashlight.utils.toDetailedTime
import com.name.flashlight.utils.toDigitalTime

/**
 * 模块化重构后的主页 - 已修复导入污染
 */
class MainActivity : BaseActivity<ActivityMainBinding>() {

    private var flashlightAutoOffMinutes = 5

    override val pageTrackName = PageConstants.PAGE_HOME
    override val isBatteryMonitorEnabled = true
    override val isLowBatteryCheckEnabled = true

    override fun createBinding(): ActivityMainBinding = ActivityMainBinding.inflate(layoutInflater)

    override fun initViews() {
        binding.bottomNav.selectedItemId = R.id.nav_home
    }

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

        // 拓宽触发区域
        binding.layoutAutoOff.setOnClickListener {
            startActivity(Intent(this, AutomaticActivity::class.java))
        }

        binding.bottomNav.setOnItemSelectedListener { item -> handleNavigation(item.itemId) }

        binding.btnSwitch.setOnStateChangedListener { isEnabled: Boolean ->
            VibrationManager.vibrate(binding.btnSwitch, forceEnabled = isEnabled)
            lifecycleScope.launch {
                DataStoreManager.setVibrationEnabled(this@MainActivity, isEnabled)
            }
        }
    }

    override fun initObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    DataStoreManager.isVibrationEnabled(this@MainActivity).collectLatest { isEnabled: Boolean ->
                        binding.btnSwitch.setCheckedSilently(isEnabled)
                    }
                }
                launch {
                    DataStoreManager.getFlashlightAutoOffTime(this@MainActivity).collectLatest { minutes: Int ->
                        flashlightAutoOffMinutes = minutes
                        updateAutoOffDisplay(minutes)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStats()
    }

    override fun onBatteryStatusChanged(info: BatteryRepository.BatteryInfo) {
        if (isFinishing || isDestroyed) return
        binding.apply {
            tvBatteryPercent.text = info.levelText
            tvBatteryStatus.text = info.status
            ivBatteryIcon.setImageResource(info.iconRes)
        }
    }

    private fun updateAutoOffDisplay(minutes: Int) {
        if (isFinishing || isDestroyed) return
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
        if (itemId == R.id.nav_home) return true

        val targetClass = when (itemId) {
            R.id.nav_settings -> SettingsActivity::class.java
            R.id.nav_flashlight -> FlashlightActivity::class.java
            R.id.nav_blink -> BlinkActivity::class.java
            R.id.nav_stats -> StatsActivity::class.java
            else -> null
        }

        targetClass?.let {
            startActivity(Intent(this, it))
            // 注意：新版 Android 建议使用 overrideActivityTransition
            @Suppress("DEPRECATION")
            overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            return false
        }

        return false
    }
}