package com.name.FlashLight

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.name.FlashLight.databinding.BlinkBinding
import com.name.FlashLight.utils.PageConstants
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import utils.DataStoreManager
import utils.TimeRepository
import utils.feedback

class BlinkActivity : BaseActivity<BlinkBinding>() {

    private var isScreenLightSelected = false
    private var isFlashlightSelected = true   
    private var selectedFrequency = 1  
    private var isBlinking = false

    private var blinkJob: Job? = null 
    private var timerJob: Job? = null 

    private var startTime = 0L
    private var currentAutoOffMinutes = 5

    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private val selectedBlueColor = Color.parseColor("#4786EF")

    override fun createBinding(): BlinkBinding = BlinkBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PageUsageRecorder.recordPageVisit(this, PageConstants.PAGE_BLINK)
        StartupModeManager.recordLastPage(this, PageConstants.PAGE_BLINK)

        initFlashlight()
        setupClickListeners()
        observeSettings() 

        selectFrequency(1)
        binding.layoutBlink.isSelected = true
        updateSourceLayoutUI(binding.layoutBlink, true)
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            DataStoreManager.getBlinkAutoOffTime(this@BlinkActivity).collectLatest { minutes ->
                currentAutoOffMinutes = minutes
            }
        }
    }

    private fun startBlinking() {
        if (!isScreenLightSelected && !isFlashlightSelected) return
        isBlinking = true
        
        binding.btnStartBlink.text = "⬜ " + getString(R.string.btn_blink)
        binding.btnStartBlink.alpha = 0.3f 
        binding.SOS.isEnabled = false
        binding.SOS.alpha = 0.3f 

        val interval = when (selectedFrequency) { 0 -> 1000L; 1 -> 500L; 2 -> 200L; else -> 500L }
        
        // 核心：协程驱动的精准“闪烁节奏”
        blinkJob?.cancel()
        blinkJob = lifecycleScope.launch {
            while (isBlinking) {
                // ON 状态
                setLightState(true)
                delay(interval)
                // OFF 状态
                setLightState(false)
                delay(interval)
            }
        }

        startTimer()
    }

    private fun stopBlinking() {
        isBlinking = false
        blinkJob?.cancel()
        blinkJob = null
        stopTimer()

        binding.btnStartBlink.text = getString(R.string.btn_blink)
        binding.btnStartBlink.alpha = 1.0f
        binding.SOS.isEnabled = true
        binding.SOS.alpha = 1.0f
        
        // 强制确保硬件状态为 OFF
        setLightState(false)
    }

    private fun setLightState(on: Boolean) {
        if (isScreenLightSelected) controlScreenBrightness(on)
        if (isFlashlightSelected) controlFlashlight(on)
    }

    private fun startTimer() {
        timerJob?.cancel()
        startTime = System.currentTimeMillis()
        timeRepository.startRecording(TimeRepository.TYPE_BLINK)
        timerJob = lifecycleScope.launch {
            while (true) {
                updateStats()
                delay(1000)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        timeRepository.stopRecording(TimeRepository.TYPE_BLINK)
        updateStats()
    }

    private fun updateStats() {
        // 兼容 ID：如果 stats 页面用了 tvBlinkTime，这里也用 tvBlinkTime
        // 注意：这里需要确认 binding.tvBlinkTime 是否在 blink.xml 中存在
        try {
            val todayMinutes = timeRepository.getTodayUsageMinutes(TimeRepository.TYPE_BLINK)
        } catch (e: Exception) {}

        if (timerJob != null) {
            val elapsedMinutes = (System.currentTimeMillis() - startTime) / 1000f / 60f
            val autoOffMinutes = currentAutoOffMinutes

            if (autoOffMinutes > 0 && autoOffMinutes < 114514) {
                if (elapsedMinutes >= autoOffMinutes) {
                    stopBlinking()
                    Toast.makeText(this, getString(R.string.blink_auto_off), Toast.LENGTH_SHORT).show()
                    navigateToMain()
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        binding.traceback.setOnClickListener { stopBlinking(); handleBackPress() }
        binding.ivSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        binding.SOS.setOnTouchListener { v, event ->
            handleTouch(v, event) {
                if (isBlinking) Toast.makeText(this, getString(R.string.please_stop_blink), Toast.LENGTH_SHORT).show()
                else startActivity(Intent(this, SOSActivity::class.java))
            }
        }

        binding.layoutScreenLight.setOnClickListener { if (!isBlinking) toggleScreenLight() }
        binding.layoutBlink.setOnClickListener { if (!isBlinking) toggleFlashlight() }

        binding.cardLeft.setOnClickListener { if (!isBlinking) selectFrequency(0) }
        binding.cardMiddle.setOnClickListener { if (!isBlinking) selectFrequency(1) }
        binding.cardRight.setOnClickListener { if (!isBlinking) selectFrequency(2) }

        binding.btnStartBlink.setOnTouchListener { v, event ->
            handleTouch(v, event) {
                if (isBlinking) stopBlinking() else startBlinking()
            }
        }
    }

    private fun handleTouch(view: View, event: MotionEvent, action: () -> Unit): Boolean {
        val isInside = event.x >= 0 && event.x <= view.width && event.y >= 0 && event.y <= view.height
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start(); return true }
            MotionEvent.ACTION_UP -> {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).setInterpolator(OvershootInterpolator()).start()
                if (isInside) { view.feedback(); action() }
                return true
            }
            MotionEvent.ACTION_CANCEL -> { view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start(); return true }
        }
        return false
    }

    private fun toggleScreenLight() {
        if (isScreenLightSelected && !isFlashlightSelected) {
            Toast.makeText(this, getString(R.string.at_least_choose_one_light_source), Toast.LENGTH_SHORT).show()
            return
        }
        isScreenLightSelected = !isScreenLightSelected
        binding.layoutScreenLight.isSelected = isScreenLightSelected
        updateSourceLayoutUI(binding.layoutScreenLight, isScreenLightSelected)
    }

    private fun toggleFlashlight() {
        if (isFlashlightSelected && !isScreenLightSelected) {
            Toast.makeText(this, getString(R.string.at_least_choose_one_light_source), Toast.LENGTH_SHORT).show()
            return
        }
        isFlashlightSelected = !isFlashlightSelected
        binding.layoutBlink.isSelected = isFlashlightSelected
        updateSourceLayoutUI(binding.layoutBlink, isFlashlightSelected)
    }

    private fun updateSourceLayoutUI(layout: LinearLayout, selected: Boolean) {
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            if (child is TextView) child.setTextColor(if (selected) selectedBlueColor else Color.WHITE)
            if (child is ImageView) child.setColorFilter(if (selected) selectedBlueColor else Color.WHITE)
        }
    }

    private fun selectFrequency(level: Int) {
        selectedFrequency = level
        val cards = listOf(binding.cardLeft, binding.cardMiddle, binding.cardRight)
        cards.forEachIndexed { index, layout ->
            layout.isSelected = (index == level)
            for (i in 0 until layout.childCount) {
                val child = layout.getChildAt(i)
                if (child is TextView) child.setTextColor(if (index == level) selectedBlueColor else Color.WHITE)
            }
        }
    }

    private fun controlFlashlight(on: Boolean) {
        try { if (cameraId != null) cameraManager.setTorchMode(cameraId!!, on) } catch (e: Exception) { }
    }

    private fun controlScreenBrightness(on: Boolean) {
        val layoutParams = window.attributes
        layoutParams.screenBrightness = if (on) 1.0f else -1.0f
        window.attributes = layoutParams
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
        finish()
    }

    private fun initFlashlight() {
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                flashAvailable && lensFacing == CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (e: Exception) { }
    }

    override fun onPause() { super.onPause(); if (isBlinking) stopBlinking() }
    override fun onDestroy() { super.onDestroy(); if (isBlinking) stopBlinking() }
}