package com.name.FlashLight

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import com.name.FlashLight.utils.PageConstants
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
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

    // 光源选择状态
    private var isScreenLightSelected = false
    private var isFlashlightSelected = true   // 手电筒（默认）

    // 频率选择状态
    private var selectedFrequency = 1  // 0=低频, 1=中频, 2=高频

    // 模式状态
    private var isBlinking = false

    // 闪烁控制
    private var blinkHandler = Handler(Looper.getMainLooper())
    private var blinkRunnable: Runnable? = null

    // 手电筒控制
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null

    private var handler: Handler? = null
    private var timerRunnable: Runnable? = null
    private var isTimerRunning = false
    private var startTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.blink)
        
        handler = Handler(Looper.getMainLooper())
        
        initViews()
        PageUsageRecorder.recordPageVisit(this, PageConstants.PAGE_BLINK)
        StartupModeManager.recordLastPage(this, PageConstants.PAGE_BLINK)

        initFlashlight()
        setupClickListeners()
        updateStats()

        // 默认选中中频
        selectFrequency(1)
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

        // 设置默认选中状态
        layoutScreenLight.setBackgroundResource(R.drawable.bg_rounded)
        layoutBlink.setBackgroundResource(R.drawable.bg_rounded_corner_selected)
    }

    private fun startTimer() {
        stopTimer()
        startTime = System.currentTimeMillis()
        isTimerRunning = true

        timerRunnable = object : Runnable {
            override fun run() {
                if (isTimerRunning) {
                    updateStats()
                    handler?.postDelayed(this, 1000)
                }
            }
        }
        handler?.post(timerRunnable!!)
    }

    private fun stopTimer() {
        isTimerRunning = false
        timerRunnable?.let {
            handler?.removeCallbacks(it)
        }
        timerRunnable = null
    }
    
    private fun updateStats() {
        if (!isTimerRunning || startTime == 0L) return
        
        val elapsedMs = System.currentTimeMillis() - startTime
        val usedTime = elapsedMs / 60000F
        val totalTime = getAutoOffTime().toFloat()
        
        if (elapsedMs < 0) {
            stopTimer()
            return
        }
        
        if (usedTime >= totalTime) {
            navigateToMain()
        }
    }
    
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
    
    private fun getAutoOffTime(): Int {
        val prefs = getSharedPreferences("auto_off_settings", Context.MODE_PRIVATE)
        return prefs.getInt(AutomaticActivity.KEY_BLINK_TIME, 5)
    }
    
    private fun initFlashlight() {
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val flashAvailable = characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                flashAvailable
            }
        } catch (e: Exception) { }
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        ivTraceback.setOnClickListener {
            stopBlinking()
            handleBackPress()
        }

        ivSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // SOS 按钮物理动效改造
        btnSos.setOnTouchListener { view, event ->
            val isInside = event.x >= 0 && event.x <= view.width && event.y >= 0 && event.y <= view.height
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).setInterpolator(AccelerateDecelerateInterpolator()).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val targetScale = if (isInside) 0.9f else 1.0f
                    view.animate().scaleX(targetScale).scaleY(targetScale).setDuration(100).start()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).setInterpolator(OvershootInterpolator()).start()
                    if (isInside) {
                        view.feedback()
                        if (isBlinking) {
                            Toast.makeText(this, "请先停止闪烁", Toast.LENGTH_SHORT).show()
                        } else {
                            val intent = Intent(this, SOSActivity::class.java)
                            startActivity(intent)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                    true
                }
                else -> false
            }
        }

        layoutScreenLight.setOnClickListener {
            if (!isBlinking) toggleScreenLight()
            else Toast.makeText(this, "请先停止闪烁", Toast.LENGTH_SHORT).show()
        }

        layoutBlink.setOnClickListener {
            if (!isBlinking) toggleFlashlight()
            else Toast.makeText(this, "请先停止闪烁", Toast.LENGTH_SHORT).show()
        }

        layoutLeft.setOnClickListener {
            if (!isBlinking) selectFrequency(0)
            else Toast.makeText(this, "请先停止闪烁", Toast.LENGTH_SHORT).show()
        }

        layoutMiddle.setOnClickListener {
            if (!isBlinking) selectFrequency(1)
            else Toast.makeText(this, "请先停止闪烁", Toast.LENGTH_SHORT).show()
        }

        layoutRight.setOnClickListener {
            if (!isBlinking) selectFrequency(2)
            else Toast.makeText(this, "请先停止闪烁", Toast.LENGTH_SHORT).show()
        }

        // 开始闪烁按钮物理动效改造
        btnStartBlink.setOnTouchListener { view, event ->
            val isInside = event.x >= 0 && event.x <= view.width && event.y >= 0 && event.y <= view.height
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).setInterpolator(AccelerateDecelerateInterpolator()).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val targetScale = if (isInside) 0.9f else 1.0f
                    view.animate().scaleX(targetScale).scaleY(targetScale).setDuration(100).start()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).setInterpolator(OvershootInterpolator()).start()
                    if (isInside) {
                        view.feedback()
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
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleScreenLight() {
        if (isScreenLightSelected) {
            if (isFlashlightSelected) {
                isScreenLightSelected = false
                layoutScreenLight.setBackgroundResource(R.drawable.bg_rounded)
                Toast.makeText(this, "屏幕补光已关闭", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "至少需要选择一种光源", Toast.LENGTH_SHORT).show()
            }
        } else {
            isScreenLightSelected = true
            layoutScreenLight.setBackgroundResource(R.drawable.bg_rounded_corner_selected)
            Toast.makeText(this, "屏幕补光已开启", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleFlashlight() {
        if (isFlashlightSelected) {
            if (isScreenLightSelected) {
                isFlashlightSelected = false
                layoutBlink.setBackgroundResource(R.drawable.bg_rounded)
                Toast.makeText(this, "手电筒已关闭", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "至少需要选择一种光源", Toast.LENGTH_SHORT).show()
            }
        } else {
            isFlashlightSelected = true
            layoutBlink.setBackgroundResource(R.drawable.bg_rounded_corner_selected)
            Toast.makeText(this, "手电筒已开启", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectFrequency(level: Int) {
        selectedFrequency = level
        layoutLeft.setBackgroundResource(R.drawable.bg_rounded)
        layoutMiddle.setBackgroundResource(R.drawable.bg_rounded)
        layoutRight.setBackgroundResource(R.drawable.bg_rounded)

        when (level) {
            0 -> layoutLeft.setBackgroundResource(R.drawable.bg_rounded_corner_selected)
            1 -> layoutMiddle.setBackgroundResource(R.drawable.bg_rounded_corner_selected)
            2 -> layoutRight.setBackgroundResource(R.drawable.bg_rounded_corner_selected)
        }
    }

    private fun startBlinking() {
        if (!isScreenLightSelected && !isFlashlightSelected) {
            Toast.makeText(this, "请至少选择一种光源", Toast.LENGTH_SHORT).show()
            return
        }

        isBlinking = true
        btnStartBlink.text = "⬜停止闪烁"
        btnStartBlink.alpha = 0.3f 
        btnSos.isEnabled = false
        btnSos.alpha = 0.3f 

        val interval = when (selectedFrequency) {
            0 -> 1000L
            1 -> 500L
            2 -> 200L
            else -> 500L
        }

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
        blinkRunnable?.let { blinkHandler.post(it) }
    }

    private fun stopBlinking() {
        isBlinking = false
        btnStartBlink.text = "⚡开始闪烁"
        btnStartBlink.alpha = 1.0f
        btnSos.isEnabled = true
        btnSos.alpha = 1.0f

        blinkHandler.removeCallbacksAndMessages(null)
        controlScreenBrightness(false)
        controlFlashlight(false)
    }

    private fun controlFlashlight(on: Boolean) {
        try {
            if (cameraId != null) cameraManager.setTorchMode(cameraId!!, on)
        } catch (e: Exception) { }
    }

    private fun controlScreenBrightness(on: Boolean) {
        val layoutParams = window.attributes
        layoutParams.screenBrightness = if (on) 1.0f else -1.0f
        window.attributes = layoutParams
    }

    override fun onPause() {
        super.onPause()
        if (isBlinking) {
            stopBlinking()
            stopTimer()
            TimeRecorder.stopRecording(this, "blink")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBlinking) {
            stopBlinking()
            stopTimer()
            TimeRecorder.stopRecording(this, "blink")
        }
    }
}