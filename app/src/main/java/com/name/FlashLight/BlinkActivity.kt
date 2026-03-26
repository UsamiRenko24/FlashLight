package com.name.FlashLight

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.name.FlashLight.utils.PageConstants
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import utils.TimeRecorder
import utils.feedback

class BlinkActivity : BaseActivity() {

    private lateinit var ivTraceback: ImageView
    private lateinit var ivSettings: ImageView
    private lateinit var btnSos: Button
    private lateinit var btnStartBlink: Button
    private lateinit var layoutScreenLight: LinearLayout
    private lateinit var layoutBlink: LinearLayout
    private lateinit var layoutLeft: LinearLayout
    private lateinit var layoutMiddle: LinearLayout
    private lateinit var layoutRight: LinearLayout

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.blink)
        
        handler = Handler(Looper.getMainLooper())
        initViews()
        PageUsageRecorder.recordPageVisit(this, PageConstants.PAGE_BLINK)
        StartupModeManager.recordLastPage(this, PageConstants.PAGE_BLINK)

        initFlashlight()
        setupClickListeners()

        // 核心修复：初始状态 - 选中中频和手电筒
        selectFrequency(1)
        layoutBlink.isSelected = true
        updateSourceLayoutUI(layoutBlink, true)
        layoutScreenLight.isSelected = false
        updateSourceLayoutUI(layoutScreenLight, false)
    }

    private fun initViews() {
        ivTraceback = findViewById(R.id.traceback)
        ivSettings = findViewById(R.id.iv_settings)
        btnSos = findViewById(R.id.SOS)
        btnStartBlink = findViewById(R.id.btn_start_blink)
        layoutScreenLight = findViewById(R.id.layout_screen_light)
        layoutBlink = findViewById(R.id.layout_blink)
        layoutLeft = findViewById(R.id.card_left)
        layoutMiddle = findViewById(R.id.card_middle)
        layoutRight = findViewById(R.id.card_right)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        ivTraceback.setOnClickListener { stopBlinking(); handleBackPress() }
        ivSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        // SOS 按钮物理动效
        btnSos.setOnTouchListener { view, event ->
            handleTouch(view, event) {
                if (isBlinking) Toast.makeText(this, getString(R.string.please_stop_blink), Toast.LENGTH_SHORT).show()
                else startActivity(Intent(this, SOSActivity::class.java))
            }
        }

        layoutScreenLight.setOnClickListener { if (!isBlinking) toggleScreenLight() }
        layoutBlink.setOnClickListener { if (!isBlinking) toggleFlashlight() }

        layoutLeft.setOnClickListener { if (!isBlinking) selectFrequency(0) }
        layoutMiddle.setOnClickListener { if (!isBlinking) selectFrequency(1) }
        layoutRight.setOnClickListener { if (!isBlinking) selectFrequency(2) }

        btnStartBlink.setOnTouchListener { view, event ->
            handleTouch(view, event) {
                if (isBlinking) {
                    stopBlinking()
                    stopTimer()
                    TimeRecorder.stopRecording(this, "blink")
                } else {
                    startBlinking()
                    startTimer()
                    TimeRecorder.startRecording(this, "blink")
                }
            }
        }
    }

    private fun handleTouch(view: View, event: MotionEvent, action: () -> Unit): Boolean {
        val isInside = event.x >= 0 && event.x <= view.width && event.y >= 0 && event.y <= view.height
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start(); return true }
            MotionEvent.ACTION_MOVE -> { 
                val targetScale = if (isInside) 0.9f else 1.0f
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
        // 【关键保护】：如果要取消当前选中的光源，必须确保手电筒仍被选中
        if (isScreenLightSelected && !isFlashlightSelected) {
            Toast.makeText(this, getString(R.string.at_least_choose_one_light_source), Toast.LENGTH_SHORT).show()
            return
        }
        isScreenLightSelected = !isScreenLightSelected
        layoutScreenLight.isSelected = isScreenLightSelected
        updateSourceLayoutUI(layoutScreenLight, isScreenLightSelected)
    }

    private fun toggleFlashlight() {
        // 【关键保护】：如果要取消当前选中的光源，必须确保屏幕补光仍被选中
        if (isFlashlightSelected && !isScreenLightSelected) {
            Toast.makeText(this, getString(R.string.at_least_choose_one_light_source), Toast.LENGTH_SHORT).show()
            return
        }
        isFlashlightSelected = !isFlashlightSelected
        layoutBlink.isSelected = isFlashlightSelected
        updateSourceLayoutUI(layoutBlink, isFlashlightSelected)
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
        val cards = listOf(layoutLeft, layoutMiddle, layoutRight)
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
        btnStartBlink.text = "⬜" + getString(R.string.btn_blink).replace("⚡", "")
        btnStartBlink.alpha = 0.3f 
        btnSos.isEnabled = false
        btnSos.alpha = 0.3f 

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
        btnStartBlink.text = getString(R.string.btn_blink)
        btnStartBlink.alpha = 1.0f
        btnSos.isEnabled = true
        btnSos.alpha = 1.0f
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
        startTime = 0L // 停止时清零，防止判定失效
    }

    private fun updateStats() {
        // 【核心修复】：必须正在闪烁且有有效开始时间
        if (!isBlinking || !isTimerRunning || startTime == 0L) return

        val usedTime = (System.currentTimeMillis() - startTime) / 60000F
        if (usedTime >= getAutoOffTime().toFloat()) navigateToMain()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
        finish()
    }

    private fun getAutoOffTime() = getSharedPreferences("auto_off_settings", Context.MODE_PRIVATE).getInt(AutomaticActivity.KEY_BLINK_TIME, 5)

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
        if (isBlinking) { stopBlinking(); stopTimer(); TimeRecorder.stopRecording(this, "blink") }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBlinking) { stopBlinking(); stopTimer(); TimeRecorder.stopRecording(this, "blink") }
    }
}