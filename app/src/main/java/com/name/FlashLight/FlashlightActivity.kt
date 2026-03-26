package com.name.FlashLight

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.name.FlashLight.utils.PageConstants
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import utils.BatteryHelper
import utils.SoundManager
import utils.TemperatureManager
import utils.TimeRecorder
import utils.feedback

class FlashlightActivity : BaseActivity(), TemperatureManager.TemperatureListener {

    private var isFlashlightOn = false
    private var currentBrightnessLevel = 0  
    private lateinit var btnFlashlight: Button
    private lateinit var ivTraceback: ImageView
    private lateinit var viewHalo: View

    private lateinit var ivSettings: ImageView
    private lateinit var ivTemperature: ImageView
    private lateinit var cardLow: LinearLayout
    private lateinit var cardMedium: LinearLayout
    private lateinit var cardHigh: LinearLayout
    private lateinit var temperatureContainer: LinearLayout

    private lateinit var tvLow: TextView
    private lateinit var tvMedium: TextView
    private lateinit var tvHigh: TextView

    private lateinit var tvBatteryPercent: TextView
    private lateinit var tvBatteryStatus: TextView
    private lateinit var ivBatteryIcon: ImageView
    private lateinit var tvFlashlightTime: TextView
    private lateinit var tvLastTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var tvTemperature: TextView

    private lateinit var progressFlashlight: ProgressBar
    private lateinit var frameLayout: FrameLayout
    
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var maxBrightnessLevel = 1 
    private var isStrengthSupported = false 

    private var handler: Handler? = null
    private var timerRunnable: Runnable? = null
    private var isTimerRunning = false
    private var startTime = 0L

    private var haloAnimator: AnimatorSet? = null 
    private val selectedBlueColor = Color.parseColor("#4786EF")

    private val brightnessLevelMap = mutableMapOf(0 to 1, 1 to 1, 2 to 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.flashlight)
        
        handler = Handler(Looper.getMainLooper())
        initViews()
        initTimer()
        PageUsageRecorder.recordPageVisit(this, PageConstants.PAGE_FLASHLIGHT)
        StartupModeManager.recordLastPage(this, PageConstants.PAGE_FLASHLIGHT)

        initFlashlight()  
        
        val savedLevel = getSharedPreferences("brightness_settings", Context.MODE_PRIVATE).getInt("default_brightness", 1)
        currentBrightnessLevel = savedLevel
        selectBrightnessCard(savedLevel)  
        
        setupClickListeners()
        updateBatteryDisplay()
        updateStats()
        SoundManager.initSoundPool(this)
        
        if (getAutoOffTime() >= 114514) {
            tvTotalTime.text = getString(R.string.auto_off_never)
        } else {
            tvTotalTime.text = formatMinutes(getAutoOffTime())
        }
        
        startBatteryMonitor()
        updateButtonState()
        
        if (TemperatureManager.isEnabled()) {
            showTemperatureContainer()
            updateTemperatureDisplay(TemperatureManager.getCurrentTemperature(), TemperatureManager.isOverheating())
        } else {
            hideTemperatureContainer()
        }
    }

    private fun initViews() {
        btnFlashlight = findViewById(R.id.btn_flashlight)
        ivTraceback = findViewById(R.id.traceback)
        ivSettings = findViewById(R.id.iv_settings)
        viewHalo = findViewById(R.id.view_halo) 
        cardLow = findViewById(R.id.card_left)
        cardMedium = findViewById(R.id.card_middle)
        cardHigh = findViewById(R.id.card_right)
        tvLow = cardLow.getChildAt(0) as TextView
        tvMedium = cardMedium.getChildAt(0) as TextView
        tvHigh = cardHigh.getChildAt(0) as TextView
        tvBatteryPercent = findViewById(R.id.tv_battery_percent)
        tvBatteryStatus = findViewById(R.id.tv_battery_status)
        ivBatteryIcon = findViewById(R.id.iv_battery_icon)
        tvFlashlightTime = findViewById(R.id.tv_flashlight_time)
        tvTotalTime = findViewById(R.id.tv_total_time)
        progressFlashlight = findViewById(R.id.progress_flashlight)
        tvLastTime = findViewById(R.id.last_time)
        frameLayout = findViewById(R.id.frameLayout)
        temperatureContainer = findViewById(R.id.temperature_container)
        tvTemperature = findViewById(R.id.tv_temperature)
        ivTemperature = findViewById(R.id.iv_temperature)
    }

    private fun initTimer() {
        timerRunnable = object : Runnable {
            override fun run() { if (isTimerRunning) { updateStats(); handler?.postDelayed(this, 1000) } }
        }
    }

    override fun onMonitorStateChanged(isEnabled: Boolean) {
        runOnUiThread { if (isEnabled) { showTemperatureContainer(); updateTemperatureDisplay(TemperatureManager.getCurrentTemperature(), TemperatureManager.isOverheating()) } else { hideTemperatureContainer() } }
    }

    override fun onTemperatureUpdate(temperature: Float, isOverheating: Boolean) {
        runOnUiThread { if (temperatureContainer.visibility == View.VISIBLE) updateTemperatureDisplay(temperature, isOverheating) }
    }

    private fun showTemperatureContainer() {
        if (temperatureContainer.visibility != View.VISIBLE) {
            temperatureContainer.visibility = View.VISIBLE
            temperatureContainer.alpha = 0f
            temperatureContainer.animate().alpha(1f).setDuration(300).start()
        }
    }

    private fun hideTemperatureContainer() {
        if (temperatureContainer.visibility == View.VISIBLE) {
            temperatureContainer.animate().alpha(0f).setDuration(300).withEndAction {
                temperatureContainer.visibility = View.GONE
            }.start()
        }
    }

    private fun updateTemperatureDisplay(temperature: Float, isOverheating: Boolean) {
        tvTemperature.text = String.format("%.1f°C", temperature)
        if (isOverheating) {
            tvTemperature.text = getString(R.string.overheat_reminder)
            temperatureContainer.setBackgroundResource(R.drawable.bg_temperature_warning)
            ivTemperature.setColorFilter(Color.RED)
        } else {
            tvTemperature.text = getString(R.string.normal_temperature)
            temperatureContainer.setBackgroundResource(R.drawable.bg_rounded_corner)
            ivTemperature.setColorFilter(Color.WHITE)
        }
    }

    override fun onResume() {
        super.onResume()
        if (TemperatureManager.isEnabled()) {
            showTemperatureContainer()
            updateTemperatureDisplay(TemperatureManager.getCurrentTemperature(), TemperatureManager.isOverheating())
        } else {
            hideTemperatureContainer()
        }
    }

    private fun initFlashlight() {
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                flashAvailable && lensFacing == CameraCharacteristics.LENS_FACING_BACK
            }

            if (cameraId == null) {
                btnFlashlight.isEnabled = false
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId!!)
                val maxLevel = characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                if (maxLevel > 1) {
                    maxBrightnessLevel = maxLevel
                    isStrengthSupported = true
                    updateBrightnessMap()
                }
            }
        } catch (e: Exception) {
            btnFlashlight.isEnabled = false
        }
    }

    private fun updateBrightnessMap() {
        brightnessLevelMap[0] = (maxBrightnessLevel * 0.1).toInt().coerceAtLeast(1)
        brightnessLevelMap[1] = (maxBrightnessLevel * 0.5).toInt().coerceAtLeast(2)
        brightnessLevelMap[2] = maxBrightnessLevel

        tvLow.text = getString(R.string.brightness_low) + ": ${brightnessLevelMap[0]}"
        tvMedium.text = getString(R.string.brightness_medium) + ": ${brightnessLevelMap[1]}"
        tvHigh.text = getString(R.string.brightness_high) + ": ${brightnessLevelMap[2]}"
    }

    private fun getAutoOffTime(): Int {
        val prefs = getSharedPreferences("auto_off_settings", Context.MODE_PRIVATE)
        return prefs.getInt(AutomaticActivity.KEY_FLASHLIGHT_TIME, 5)
    }

    private fun updateTimeIndicatorPosition(progress: Int) {
        progressFlashlight.post {
            val progressBarWidth = progressFlashlight.width
            val indicatorWidth = tvLastTime.width
            val translationX = (progressBarWidth * progress / 100f) - (indicatorWidth / 2f)
            val maxTranslation = progressBarWidth - indicatorWidth
            val finalTranslation = translationX.coerceIn(0f, maxTranslation.toFloat())
            tvLastTime.translationX = finalTranslation
        }
    }

    private fun formatMinutes(minutes: Int): String {
        return if (minutes >= 60) "${minutes / 60}${getString(R.string.hour)}${minutes % 60}${getString(R.string.minute)}" else "$minutes${getString(R.string.minute)}"
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        ivTraceback.setOnClickListener { handleBackPress() }
        ivSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        btnFlashlight.setOnTouchListener { view, event ->
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
                        view.performClick()
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

        btnFlashlight.setOnClickListener {
            it.feedback()
            toggleFlashlight()
        }

        cardLow.setOnClickListener {
            saveLevel(0)
            if (isFlashlightOn) adjustBrightness()
        }

        cardMedium.setOnClickListener {
            saveLevel(1)
            if (isFlashlightOn) adjustBrightness()
        }

        cardHigh.setOnClickListener {
            saveLevel(2)
            if (isFlashlightOn) adjustBrightness()
        }
    }

    private fun saveLevel(level: Int) {
        currentBrightnessLevel = level
        selectBrightnessCard(level)
        getSharedPreferences("brightness_settings", Context.MODE_PRIVATE).edit().putInt("default_brightness", level).apply()
    }

    private fun toggleFlashlight() {
        if (cameraId == null) return
        try {
            isFlashlightOn = !isFlashlightOn
            updateButtonState()
            if (isFlashlightOn) turnOnFlashlight() else turnOffFlashlight()
        } catch (e: Exception) {
            isFlashlightOn = !isFlashlightOn
            updateButtonState()
        }
    }

    private fun startTimer() {
        stopTimer()
        startTime = System.currentTimeMillis()
        isTimerRunning = true
        handler?.post(timerRunnable!!) 
    }

    private fun stopTimer() {
        tvFlashlightTime.text = "00:00"
        tvLastTime.text = "00:00"
        progressFlashlight.progress = 0
        isTimerRunning = false
        timerRunnable?.let { handler?.removeCallbacks(it) }
    }

    private fun turnOnFlashlight() {
        TimeRecorder.startRecording(this, "flashlight")
        startTimer()
        try {
            val targetLevel = brightnessLevelMap[currentBrightnessLevel] ?: 1
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isStrengthSupported) {
                cameraManager.turnOnTorchWithStrengthLevel(cameraId!!, targetLevel)
            } else {
                cameraManager.setTorchMode(cameraId!!, true)
            }
        } catch (e: Exception) { }
    }

    private fun turnOffFlashlight() {
        try { cameraManager.setTorchMode(cameraId!!, false) } catch (e: Exception) { }
        TimeRecorder.stopRecording(this, "flashlight")
        stopTimer()
        updateStats()
    }

    private fun adjustBrightness() {
        if (!isFlashlightOn || cameraId == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isStrengthSupported) {
                val level = brightnessLevelMap[currentBrightnessLevel] ?: 1
                cameraManager.turnOnTorchWithStrengthLevel(cameraId!!, level)
            } else {
                Toast.makeText(this, getString(R.string.toast_failed), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) { }
    }

    private fun selectBrightnessCard(level: Int) {
        cardLow.isSelected = false
        cardMedium.isSelected = false
        cardHigh.isSelected = false
        tvLow.setTextColor(Color.WHITE)
        tvMedium.setTextColor(Color.WHITE)
        tvHigh.setTextColor(Color.WHITE)

        when (level) {
            0 -> {
                cardLow.isSelected = true
                tvLow.setTextColor(selectedBlueColor)
            }
            1 -> {
                cardMedium.isSelected = true
                tvMedium.setTextColor(selectedBlueColor)
            }
            2 -> {
                cardHigh.isSelected = true
                tvHigh.setTextColor(selectedBlueColor)
            }
        }
    }

    private fun updateButtonState() {
        if (isFlashlightOn) {
            btnFlashlight.text = getString(R.string.btn_on)
            btnFlashlight.setBackgroundResource(R.drawable.btn_flashlight_on)
            btnFlashlight.translationZ = 12f
            startHaloAnimation()
        } else {
            btnFlashlight.text = getString(R.string.btn_off)
            btnFlashlight.setBackgroundResource(R.drawable.btn_flashlight_off)
            btnFlashlight.translationZ = 0f
            stopHaloAnimation()
        }
    }

    private fun startHaloAnimation() {
        if (haloAnimator != null) return
        viewHalo.visibility = View.VISIBLE
        viewHalo.translationZ = 6f
        val scaleX = ObjectAnimator.ofFloat(viewHalo, "scaleX", 1.0f, 1.35f)
        val scaleY = ObjectAnimator.ofFloat(viewHalo, "scaleY", 1.0f, 1.35f)
        val alpha = ObjectAnimator.ofFloat(viewHalo, "alpha", 0.3f, 0.9f)
        scaleX.repeatCount = ValueAnimator.INFINITE
        scaleX.repeatMode = ValueAnimator.REVERSE
        scaleY.repeatCount = ValueAnimator.INFINITE
        scaleY.repeatMode = ValueAnimator.REVERSE
        alpha.repeatCount = ValueAnimator.INFINITE
        alpha.repeatMode = ValueAnimator.REVERSE
        haloAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 1000
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopHaloAnimation() {
        haloAnimator?.cancel()
        haloAnimator = null
        viewHalo.animate().alpha(0f).setDuration(300).withEndAction {
            viewHalo.visibility = View.GONE
        }.start()
    }

    private fun updateStats() {
        if (!isTimerRunning || startTime == 0L) return
        val elapsedMs = System.currentTimeMillis() - startTime
        val usedTime = elapsedMs / 60000F
        
        if (getAutoOffTime() >= 114514) {
            tvTotalTime.text = getString(R.string.auto_off_never)
        } else {
            val totalTime = getAutoOffTime().toFloat()
            val progress = ((usedTime / totalTime) * 100).toInt().coerceIn(0, 100)
            tvTotalTime.text = formatMinutes(getAutoOffTime())
            progressFlashlight.progress = progress
            updateTimeIndicatorPosition(progress)
            if (usedTime >= totalTime && isFlashlightOn) navigateToMain()
        }
        
        tvFlashlightTime.text = formatTime(usedTime)
        tvLastTime.text = formatTime(usedTime)
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun formatTime(minutes: Float): String {
        val totalSeconds = (minutes * 60).toInt()
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    private fun updateBatteryDisplay() {
        BatteryHelper.updateBatteryUI(this, tvBatteryPercent, tvBatteryStatus, ivBatteryIcon)
    }

    private fun startBatteryMonitor() {
        val monitorHandler = Handler(Looper.getMainLooper())
        monitorHandler.post(object : Runnable {
            override fun run() {
                updateBatteryDisplay()
                updateStats()
                monitorHandler.postDelayed(this, 1000)
            }
        })
    }

    override fun onPause() {
        super.onPause()
        if (isFlashlightOn && cameraId != null) {
            try {
                cameraManager.setTorchMode(cameraId!!, false)
                isFlashlightOn = false
                updateButtonState()
            } catch (e: Exception) { }
        }
        TimeRecorder.stopRecording(this, "flashlight")
        stopTimer()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (cameraId != null) try { cameraManager.setTorchMode(cameraId!!, false) } catch (e: Exception) { }
        TimeRecorder.stopRecording(this, "flashlight")
        stopTimer()
        TemperatureManager.removeListener(this)
        handler?.removeCallbacksAndMessages(null)
        haloAnimator?.cancel()
    }
}