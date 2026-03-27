package com.name.FlashLight

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import com.name.FlashLight.databinding.ActivityMainBinding
import com.name.FlashLight.databinding.LowbatteryBinding
import utils.BatteryHelper
import utils.TimeRecorder
import utils.VibrationManager
import utils.feedback

class MainActivity : BaseActivity<ActivityMainBinding>() {
    
    private val REQUEST_CODE1 = 1001
    private val REQUEST_CODE2 = 1002

    override fun createBinding():ActivityMainBinding{
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupBottomNavigation()
        setupClickListeners()
        loadVibrationSetting()
        setupVibrationButton()

        binding.bottomNav.selectedItemId = R.id.nav_home
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
        syncVibrationUI() 
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

    private fun syncVibrationUI() {
        binding.btnSwitch.setCheckedSilently(VibrationManager.isVibrationEnabled(this))
    }

    private fun getAutoOffTime(): Int {
        return getSharedPreferences("auto_off_settings", Context.MODE_PRIVATE)
            .getInt(AutomaticActivity.KEY_FLASHLIGHT_TIME, 5)
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
        val flashlightTime = TimeRecorder.getTodayTime(this, "flashlight")
        binding.tvFlashlightTime.text = formatTime(flashlightTime)
        
        val offTime = getAutoOffTime()
        if (offTime >= 114514) {
            binding.tvTime.text = getString(R.string.auto_off_never)
        } else {
            binding.tvTime.text = formatMinutes(offTime)
        }
    }

    private fun formatMinutes(minutes: Int): String {
        return if (minutes >= 60) {
            "${minutes / 60}${getString(R.string.hour)}${minutes % 60}${getString(R.string.minute)}"
        } else {
            "${minutes}${getString(R.string.minute)}"
        }
    }

    private fun formatTime(minutes: Float): String {
        val totalSeconds = (minutes * 60).toInt()
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    private fun updateBatteryDisplay() {
        BatteryHelper.updateBatteryUI(this, binding.tvBatteryPercent, binding.tvBatteryStatus, binding.ivBatteryIcon)
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

    private fun loadVibrationSetting() { 
        binding.btnSwitch.isChecked = VibrationManager.isVibrationEnabled(this) 
    }
    
    private fun setupVibrationButton() { 
        binding.btnSwitch.setOnStateChangedListener { isEnabled: Boolean ->
            VibrationManager.setVibrationEnabled(this, isEnabled)
            if (isEnabled) {
                VibrationManager.vibrate(binding.btnSwitch)
            }
        } 
    }
}