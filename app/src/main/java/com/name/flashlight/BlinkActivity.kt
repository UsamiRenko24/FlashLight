package com.name.flashlight

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.name.flashlight.databinding.BlinkBinding
import com.name.flashlight.utils.BatteryRepository
import com.name.flashlight.utils.DataStoreManager
import com.name.flashlight.utils.PageConstants
import com.name.flashlight.utils.SoundManager
import com.name.flashlight.utils.TimeRepository
import com.name.flashlight.utils.feedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 工业级模块化闪烁页面
 */
class BlinkActivity : BaseActivity<BlinkBinding>() {

    override val pageTrackName = PageConstants.PAGE_BLINK
    override val isBatteryMonitorEnabled = true
    override val isLowBatteryCheckEnabled = true

    private var isBlinking = false
    private var selectedFrequency = 1  
    private var currentAutoOffMinutes = 5
    private var startTime = 0L
    
    private var blinkJob: Job? = null
    private var timerJob: Job? = null

    private var isScreenLightSelected = false
    private var isFlashlightSelected = true   
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    
    private val selectedBlueColor = Color.parseColor("#2AE1F8")

    override fun createBinding(): BlinkBinding = BlinkBinding.inflate(layoutInflater)

    override fun initViews() {
        SoundManager.initSoundPool(this)
        initHardware()
        binding.bottomNav.selectedItemId = R.id.nav_blink
        
        selectFrequencyUI(1)
        binding.layoutBlink.isSelected = true
        updateSourceLayoutUI(binding.layoutBlink, true)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun initListeners() {
        binding.traceback.setOnClickListener { stopBlinkingSession(); handleBackPress() }
        binding.ivSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        binding.SOS.setOnTouchListener { v, event ->
            handleTouchAnimation(v, event)
            if (event.action == MotionEvent.ACTION_UP) {
                if (isBlinking) Toast.makeText(this, getString(R.string.please_stop_blink), Toast.LENGTH_SHORT).show()
                else startActivity(Intent(this, SOSActivity::class.java))
            }
            true
        }

        binding.layoutScreenLight.setOnClickListener { if (!isBlinking) toggleSourceSelection(true) }
        binding.layoutBlink.setOnClickListener { if (!isBlinking) toggleSourceSelection(false) }

        binding.cardLeft.setOnClickListener { if (!isBlinking) selectFrequencyUI(0) }
        binding.cardMiddle.setOnClickListener { if (!isBlinking) selectFrequencyUI(1) }
        binding.cardRight.setOnClickListener { if (!isBlinking) selectFrequencyUI(2) }

        binding.bottomNav.setOnItemSelectedListener { item ->
            handleNavigation(item.itemId)
        }
        binding.btnStartBlink.setOnTouchListener { v, event ->
            handleTouchAnimation(v, event)
            if (event.action == MotionEvent.ACTION_UP) {
                ensureCameraPermission { 
                    if (isBlinking) stopBlinkingSession() else startBlinkingSession() 
                }
            }
            true
        }
    }

    override fun initObservers() {

        lifecycleScope.launch {

            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {

                    DataStoreManager
                        .getBlinkAutoOffTime(this@BlinkActivity)
                        .collectLatest { minutes ->

                            currentAutoOffMinutes = minutes

                            android.util.Log.d(
                                "AUTO_OFF",
                                "minutes=$minutes"
                            )
                        }
                }
            }
        }
    }

    private fun handleNavigation(itemId: Int): Boolean {

        when (itemId) {

            R.id.nav_blink -> return true

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

            R.id.nav_stats -> {
                startActivity(
                    Intent(this, StatsActivity::class.java)
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
    private fun startBlinkingSession() {
        if (!isScreenLightSelected && !isFlashlightSelected) {
            Toast.makeText(this, getString(R.string.at_least_choose_one_light_source), Toast.LENGTH_SHORT).show()
            return
        }
        isBlinking = true
        updateActionUI(true)

        val interval = when (selectedFrequency) { 0 -> 1000L; 1 -> 500L; 2 -> 200L; else -> 500L }
        
        blinkJob?.cancel()
        blinkJob = lifecycleScope.launch {
            var isOn = false
            while (isBlinking) {
                isOn = !isOn
                applyHardwareLightState(isOn)
                delay(interval)
            }
        }

        startTimerJob()
    }

    private fun stopBlinkingSession() {
        isBlinking = false
        blinkJob?.cancel(); blinkJob = null
        stopTimerJob()

        updateActionUI(false)
        applyHardwareLightState(false)
    }

    private fun startTimerJob() {
        timerJob?.cancel()
        startTime = System.currentTimeMillis()
        timeRepository.startRecording(TimeRepository.TYPE_BLINK)
        timerJob = lifecycleScope.launch {
            while (true) {
                if (checkAutoOffReached()) break
                delay(1000)
            }
        }
    }

    private fun stopTimerJob() {
        timerJob?.cancel(); timerJob = null
        timeRepository.stopRecording(TimeRepository.TYPE_BLINK)
    }

    private fun checkAutoOffReached(): Boolean {
        val elapsed = (System.currentTimeMillis() - startTime) / 60000f
        return if (currentAutoOffMinutes < 114514 && elapsed >= currentAutoOffMinutes) {
            stopBlinkingSession()
            Toast.makeText(this, getString(R.string.blink_auto_off), Toast.LENGTH_SHORT).show()
            navigateToMain()
            true
        } else false
    }

    private fun initHardware() {
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (e: Exception) { }
    }

    private fun applyHardwareLightState(on: Boolean) {
        if (isFinishing || isDestroyed) return
        
        if (isScreenLightSelected) {
            try {
                val lp = window.attributes
                lp.screenBrightness = if (on) 1.0f else -1.0f
                window.attributes = lp
            } catch (e: Exception) { e.printStackTrace() }
        }
        if (isFlashlightSelected) {
            try { cameraId?.let { cameraManager.setTorchMode(it, on) } } catch (e: Exception) { }
        }
    }

    private fun toggleSourceSelection(isScreen: Boolean) {
        if (isScreen) {
            if (isScreenLightSelected && !isFlashlightSelected) return
            isScreenLightSelected = !isScreenLightSelected
            binding.layoutScreenLight.isSelected = isScreenLightSelected
            updateSourceLayoutUI(binding.layoutScreenLight, isScreenLightSelected)
        } else {
            if (isFlashlightSelected && !isScreenLightSelected) return
            isFlashlightSelected = !isFlashlightSelected
            binding.layoutBlink.isSelected = isFlashlightSelected
            updateSourceLayoutUI(binding.layoutBlink, isFlashlightSelected)
        }
    }

    private fun updateSourceLayoutUI(layout: LinearLayout, selected: Boolean) {
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            if (child is TextView) child.setTextColor(if (selected) selectedBlueColor else Color.WHITE)
            if (child is ImageView) child.setColorFilter(if (selected) selectedBlueColor else Color.WHITE)
        }
    }

    private fun selectFrequencyUI(level: Int) {
        selectedFrequency = level
        val cards = listOf(binding.cardLeft, binding.cardMiddle, binding.cardRight)
        cards.forEachIndexed { i, card ->
            card.isSelected = (i == level)
            for (j in 0 until card.childCount) {
                val child = card.getChildAt(j)
                if (child is TextView) {
                    child.setTextColor(if (i == level) selectedBlueColor else Color.WHITE)
                }
            }
        }
    }

    private fun updateActionUI(active: Boolean) {
        binding.btnStartBlink.alpha = if (active) 0.3f else 1.0f
        binding.SOS.isEnabled = !active
        binding.SOS.alpha = if (active) 0.3f else 1.0f
    }

    private fun handleTouchAnimation(view: View, event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).setInterpolator(OvershootInterpolator()).start()
                if (event.action == MotionEvent.ACTION_UP) view.feedback()
            }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
        overridePendingTransition(0, 0)
        finish()
    }

    override fun stopAllFeatures() { if (isBlinking) stopBlinkingSession() }
    override fun onPause() { super.onPause(); if (isBlinking) stopBlinkingSession() }
    override fun onBatteryStatusChanged(info: BatteryRepository.BatteryInfo) {}
}