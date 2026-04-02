package com.name.FlashLight

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.name.FlashLight.databinding.BlinkBinding
import com.name.FlashLight.utils.PageConstants
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import utils.TimeRepository
import utils.feedback

class BlinkActivity : BaseActivity<BlinkBinding>() {

    private var isScreenLightSelected = false
    private var isFlashlightSelected = true   
    private var selectedFrequency = 1  
    private var isBlinking = false

    private var blinkHandler = Handler(Looper.getMainLooper())
    private var blinkRunnable: Runnable? = null
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null

    private var handler: Handler? = null
    private var timerRunnable: Runnable? = null
    private var isTimerRunning = false
    private var startTime = 0L

    private val selectedBlueColor = Color.parseColor("#4786EF")

    override fun createBinding(): BlinkBinding {
        return BlinkBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handler = Handler(Looper.getMainLooper())
        PageUsageRecorder.recordPageVisit(this, PageConstants.PAGE_BLINK)
        StartupModeManager.recordLastPage(this, PageConstants.PAGE_BLINK)

        initFlashlight()
        setupClickListeners()

        selectFrequency(1)
        binding.layoutBlink.isSelected = true
        updateSourceLayoutUI(binding.layoutBlink, true)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        binding.traceback.setOnClickListener { stopBlinking(); handleBackPress() }
        binding.ivSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        binding.SOS.setOnTouchListener { v: View, event: MotionEvent ->
            handleTouch(v, event) {
                if (isBlinking) {
                    Toast.makeText(this, getString(R.string.please_stop_blink), Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(Intent(this, SOSActivity::class.java))
                }
            }
        }

        binding.layoutScreenLight.setOnClickListener { if (!isBlinking) toggleScreenLight() }
        binding.layoutBlink.setOnClickListener { if (!isBlinking) toggleFlashlight() }

        binding.cardLeft.setOnClickListener { if (!isBlinking) selectFrequency(0) }
        binding.cardMiddle.setOnClickListener { if (!isBlinking) selectFrequency(1) }
        binding.cardRight.setOnClickListener { if (!isBlinking) selectFrequency(2) }

        binding.btnStartBlink.setOnTouchListener { v: View, event: MotionEvent ->
            handleTouch(v, event) {
                if (isBlinking) {
                    stopBlinking()
                    stopTimer()
                    // 修正：使用实例并调用 stop
                    timeRepository.stopRecording(TimeRepository.TYPE_BLINK)
                } else {
                    startBlinking()
                    startTimer()
                    // 修正：使用实例并调用 start
                    timeRepository.startRecording(TimeRepository.TYPE_BLINK)
                }
            }
        }
    }

    private fun handleTouch(view: View, event: MotionEvent, action: () -> Unit): Boolean {
        val isInside = event.x >= 0 && event.x <= view.width && event.y >= 0 && event.y <= view.height
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start(); return true }
            MotionEvent.ACTION_MOVE -> { 
                val targetScale = if (isInside) 0.92f else 1.0f
                view.animate().scaleX(targetScale).scaleY(targetScale).setDuration(100).start()
                return true
            }
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
                if (child is TextView) {
                    child.setTextColor(if (index == level) selectedBlueColor else Color.WHITE)
                }
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
        blinkRunnable = object : Runnable {
            var isOn = false
            override fun run() {
                if (!isBlinking) return
                isOn = !isOn
                if (isScreenLightSelected) controlScreenBrightness(isOn)
                if (isFlashlightSelected) controlFlashlight(isOn)
                blinkHandler.postDelayed(this, interval)
            }
        }
        blinkHandler.post(blinkRunnable!!)
    }

    private fun stopBlinking() {
        isBlinking = false
        binding.btnStartBlink.text = getString(R.string.btn_blink)
        binding.btnStartBlink.alpha = 1.0f
        binding.SOS.isEnabled = true
        binding.SOS.alpha = 1.0f
        blinkHandler.removeCallbacksAndMessages(null)
        controlScreenBrightness(false)
        controlFlashlight(false)
    }

    private fun controlFlashlight(on: Boolean) {
        try { if (cameraId != null) cameraManager.setTorchMode(cameraId!!, on) } catch (e: Exception) { }
    }

    private fun controlScreenBrightness(on: Boolean) {
        val layoutParams = window.attributes
        layoutParams.screenBrightness = if (on) 1.0f else -1.0f
        window.attributes = layoutParams
    }

    private fun startTimer() {
        stopTimer()
        startTime = System.currentTimeMillis()
        isTimerRunning = true
        timerRunnable = object : Runnable {
            override fun run() { if (isTimerRunning) { updateStats(); handler?.postDelayed(this, 1000) } }
        }
        handler?.post(timerRunnable!!)
    }

    private fun stopTimer() { 
        isTimerRunning = false
        timerRunnable?.let { handler?.removeCallbacks(it) }
        startTime = 0L 
    }

    private fun updateStats() {
        if (!isBlinking || !isTimerRunning || startTime == 0L) return
        val usedTime = (System.currentTimeMillis() - startTime) / 60000F
        if (usedTime >= timeRepository.getTodayTotalUsageMinutes()) navigateToMain()
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
                characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
            }
        } catch (e: Exception) { }
    }

    override fun onPause() {
        super.onPause()
        if (isBlinking) { 
            stopBlinking()
            stopTimer()
            timeRepository.stopRecording(TimeRepository.TYPE_BLINK) 
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBlinking) { 
            stopBlinking()
            stopTimer()
            timeRepository.stopRecording(TimeRepository.TYPE_BLINK) 
        }
    }
}