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
import android.widget.TextView
import android.widget.Toast
import com.name.FlashLight.databinding.FlashlightBinding
import com.name.FlashLight.databinding.SettingsBinding
import com.name.FlashLight.utils.PageConstants
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import utils.BatteryHelper
import utils.SoundManager
import utils.TemperatureManager
import utils.TimeRecorder
import utils.feedback

class FlashlightActivity : BaseActivity<FlashlightBinding>(), TemperatureManager.TemperatureListener {

    private var isFlashlightOn = false
    private var currentBrightnessLevel = 0  
    
    // 修正：绑定类名应为 FlashlightBinding (对应 flashlight.xml)
//    private lateinit var binding: FlashlightBinding
    
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

    override fun createBinding():FlashlightBinding{
        return FlashlightBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handler = Handler(Looper.getMainLooper())
        initTimer()
        
        PageUsageRecorder.recordPageVisit(this, PageConstants.PAGE_FLASHLIGHT)
        StartupModeManager.recordLastPage(this, PageConstants.PAGE_FLASHLIGHT)

        initFlashlight()  
        
        val savedLevel = getSharedPreferences("brightness_settings", Context.MODE_PRIVATE)
            .getInt("default_brightness", 1)
        currentBrightnessLevel = savedLevel
        selectBrightnessCard(savedLevel)  
        
        setupClickListeners()
        updateBatteryDisplay()
        updateStats()
        SoundManager.initSoundPool(this)

        if (getAutoOffTime() >= 114514) {
            binding.tvTotalTime.text = getString(R.string.auto_off_never)
        } else {
            binding.tvTotalTime.text = formatMinutes(getAutoOffTime())
        }
        
        updateButtonState()
        
        if (TemperatureManager.isEnabled()) {
            showTemperatureContainer()
            updateTemperatureDisplay(TemperatureManager.getCurrentTemperature(), TemperatureManager.isOverheating())
        } else {
            hideTemperatureContainer()
        }
    }

    private fun initTimer() {
        timerRunnable = object : Runnable {
            override fun run() { 
                if (isTimerRunning) { 
                    updateStats()
                    handler?.postDelayed(this, 1000) 
                } 
            }
        }
    }

    override fun onMonitorStateChanged(isEnabled: Boolean) {
        runOnUiThread { 
            if (isEnabled) { 
                showTemperatureContainer()
                updateTemperatureDisplay(TemperatureManager.getCurrentTemperature(), TemperatureManager.isOverheating()) 
            } else { 
                hideTemperatureContainer() 
            } 
        }
    }

    override fun onTemperatureUpdate(temperature: Float, isOverheating: Boolean) {
        runOnUiThread { 
            if (binding.temperatureContainer.visibility == View.VISIBLE) {
                updateTemperatureDisplay(temperature, isOverheating) 
            }
        }
    }

    private fun showTemperatureContainer() {
        if (binding.temperatureContainer.visibility != View.VISIBLE) {
            binding.temperatureContainer.visibility = View.VISIBLE
            binding.temperatureContainer.alpha = 0f
            binding.temperatureContainer.animate().alpha(1f).setDuration(300).start()
        }
    }

    private fun hideTemperatureContainer() {
        if (binding.temperatureContainer.visibility == View.VISIBLE) {
            binding.temperatureContainer.animate().alpha(0f).setDuration(300).withEndAction {
                binding.temperatureContainer.visibility = View.GONE
            }.start()
        }
    }

    private fun updateTemperatureDisplay(temperature: Float, isOverheating: Boolean) {
        binding.tvTemperature.text = String.format("%.1f°C", temperature)
        if (isOverheating) {
            binding.tvTemperature.text = getString(R.string.overheat_reminder)
            binding.temperatureContainer.setBackgroundResource(R.drawable.bg_temperature_warning)
            binding.ivTemperature.setColorFilter(Color.RED)
        } else {
            binding.tvTemperature.text = getString(R.string.normal_temperature)
            binding.temperatureContainer.setBackgroundResource(R.drawable.bg_rounded_corner)
            binding.ivTemperature.setColorFilter(Color.WHITE)
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
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                flashAvailable && lensFacing == CameraCharacteristics.LENS_FACING_BACK
            }

            if (cameraId == null) {
                binding.btnFlashlight.isEnabled = false
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
            binding.btnFlashlight.isEnabled = false
        }
    }

    private fun updateBrightnessMap() {
        brightnessLevelMap[0] = (maxBrightnessLevel * 0.1).toInt().coerceAtLeast(1)
        brightnessLevelMap[1] = (maxBrightnessLevel * 0.5).toInt().coerceAtLeast(2)
        brightnessLevelMap[2] = maxBrightnessLevel

        (binding.cardLeft.getChildAt(0) as TextView).text = getString(R.string.brightness_card_low).replace("\n", " ") + ": ${brightnessLevelMap[0]}"
        (binding.cardMiddle.getChildAt(0) as TextView).text = getString(R.string.brightness_card_medium).replace("\n", " ") + ": ${brightnessLevelMap[1]}"
        (binding.cardRight.getChildAt(0) as TextView).text = getString(R.string.brightness_card_high).replace("\n", " ") + ": ${brightnessLevelMap[2]}"
    }

    private fun getAutoOffTime(): Int {
        val prefs = getSharedPreferences("auto_off_settings", Context.MODE_PRIVATE)
        return prefs.getInt(AutomaticActivity.KEY_FLASHLIGHT_TIME, 5)
    }

    private fun updateTimeIndicatorPosition(progress: Int) {
        binding.progressFlashlight.post {
            val progressBarWidth = binding.progressFlashlight.width
            val indicatorWidth = binding.lastTime.width
            val translationX = (progressBarWidth * progress / 100f) - (indicatorWidth / 2f)
            val maxTranslation = progressBarWidth - indicatorWidth
            val finalTranslation = translationX.coerceIn(0f, maxTranslation.toFloat())
            binding.lastTime.translationX = finalTranslation
        }
    }

    private fun formatMinutes(minutes: Int): String {
        return if (minutes >= 60) {
            "${minutes / 60}${getString(R.string.hour)}${minutes % 60}${getString(R.string.minute)}"
        } else {
            "$minutes${getString(R.string.minute)}"
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        binding.traceback.setOnClickListener { handleBackPress() }
        binding.ivSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        binding.btnFlashlight.setOnTouchListener { v: View, event: MotionEvent ->
            val isInside = event.x >= 0 && event.x <= v.width && event.y >= 0 && event.y <= v.height
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).setInterpolator(AccelerateDecelerateInterpolator()).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val targetScale = if (isInside) 0.9f else 1.0f
                    v.animate().scaleX(targetScale).scaleY(targetScale).setDuration(100).start()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).setInterpolator(OvershootInterpolator()).start()
                    if (isInside) {
                        v.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                    true
                }
                else -> false
            }
        }

        binding.btnFlashlight.setOnClickListener { v: View ->
            v.feedback()
            toggleFlashlight()
        }

        binding.cardLeft.setOnClickListener {
            saveLevel(0)
            if (isFlashlightOn) adjustBrightness()
        }

        binding.cardMiddle.setOnClickListener {
            saveLevel(1)
            if (isFlashlightOn) adjustBrightness()
        }

        binding.cardRight.setOnClickListener {
            saveLevel(2)
            if (isFlashlightOn) adjustBrightness()
        }
    }

    private fun saveLevel(level: Int) {
        currentBrightnessLevel = level
        selectBrightnessCard(level)
        getSharedPreferences("brightness_settings", Context.MODE_PRIVATE)
            .edit().putInt("default_brightness", level).apply()
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
        timerRunnable?.let { handler?.post(it) } 
    }

    private fun stopTimer() {
        binding.tvFlashlightTime.text = "00:00"
        binding.lastTime.text = "00:00"
        binding.progressFlashlight.progress = 0
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
            val targetLevel = brightnessLevelMap[currentBrightnessLevel] ?: 1
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isStrengthSupported) {
                cameraManager.turnOnTorchWithStrengthLevel(cameraId!!, targetLevel)
            }
        } catch (e: Exception) {}
    }

    private fun updateButtonState() {
        if (isFlashlightOn) {
            binding.btnFlashlight.setBackgroundResource(R.drawable.btn_flashlight_on)
            binding.btnFlashlight.text = getString(R.string.btn_on)
            startHaloAnimation()
        } else {
            binding.btnFlashlight.setBackgroundResource(R.drawable.btn_flashlight_off)
            binding.btnFlashlight.text = getString(R.string.btn_off)
            stopHaloAnimation()
        }
    }

    private fun startHaloAnimation() {
        if (haloAnimator != null) return
        binding.viewHalo.visibility = View.VISIBLE
        
        val scaleX = ObjectAnimator.ofFloat(binding.viewHalo, "scaleX", 1.0f, 1.2f)
        val scaleY = ObjectAnimator.ofFloat(binding.viewHalo, "scaleY", 1.0f, 1.2f)
        val alpha = ObjectAnimator.ofFloat(binding.viewHalo, "alpha", 0.3f, 0.6f)

        scaleX.repeatCount = ValueAnimator.INFINITE
        scaleX.repeatMode = ValueAnimator.REVERSE
        scaleY.repeatCount = ValueAnimator.INFINITE
        scaleY.repeatMode = ValueAnimator.REVERSE
        alpha.repeatCount = ValueAnimator.INFINITE
        alpha.repeatMode = ValueAnimator.REVERSE

        haloAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 1500
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopHaloAnimation() {
        haloAnimator?.cancel()
        haloAnimator = null
        binding.viewHalo.visibility = View.GONE
    }

    private fun selectBrightnessCard(level: Int) {
        val cards = listOf(binding.cardLeft, binding.cardMiddle, binding.cardRight)
        cards.forEachIndexed { index, card ->
            if (index == level) {
                card.isSelected = true
                card.setBackgroundResource(R.drawable.bg_rounded_selector)
                (card.getChildAt(0) as TextView).setTextColor(selectedBlueColor)
            } else {
                card.isSelected = false
                card.setBackgroundResource(R.drawable.bg_rounded_selector)
                (card.getChildAt(0) as TextView).setTextColor(Color.WHITE)
            }
        }
    }

    private fun updateStats() {
        if (isTimerRunning) {
            val elapsedMillis = System.currentTimeMillis() - startTime
            val elapsedMinutes = elapsedMillis / 1000f / 60f
            val autoOffMinutes = getAutoOffTime().toFloat()

            binding.tvFlashlightTime.text = formatTime(elapsedMinutes)
            binding.lastTime.text = formatTime(elapsedMinutes)

            if (autoOffMinutes > 0 && autoOffMinutes < 114514) {
                val progress = ((elapsedMinutes / autoOffMinutes) * 100).toInt().coerceIn(0, 100)
                binding.progressFlashlight.progress = progress
                updateTimeIndicatorPosition(progress)

                if (elapsedMinutes >= autoOffMinutes) {
                    toggleFlashlight()
                    Toast.makeText(this, getString(R.string.flashlight_auto_off), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun formatTime(minutes: Float): String {
        val totalSeconds = (minutes * 60).toInt()
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return String.format("%02d:%02d", m, s)
    }

    private fun updateBatteryDisplay() {
        BatteryHelper.updateBatteryUI(this, binding.tvBatteryPercent, binding.tvBatteryStatus, binding.ivBatteryIcon)
    }

    override fun onBatteryStatusChanged() {
        updateBatteryDisplay()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
        stopHaloAnimation()
        if (isFlashlightOn) turnOffFlashlight()
    }
}