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
import androidx.lifecycle.lifecycleScope
import com.name.FlashLight.databinding.FlashlightBinding
import com.name.FlashLight.utils.PageConstants
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import utils.BatteryRepository
import utils.DataStoreManager
import utils.SoundManager
import utils.TemperatureManager
import utils.TimeRepository
import utils.feedback
import utils.toDetailedTime
import utils.toDigitalTime

class FlashlightActivity : BaseActivity<FlashlightBinding>(), TemperatureManager.TemperatureListener {

    private var isFlashlightOn = false
    private var currentBrightnessLevel = 1
    private var currentAutoOffMinutes = 5

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

    override fun createBinding(): FlashlightBinding = FlashlightBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handler = Handler(Looper.getMainLooper())
        initTimer()

        PageUsageRecorder.recordPageVisit(this, PageConstants.PAGE_FLASHLIGHT)
        StartupModeManager.recordLastPage(this, PageConstants.PAGE_FLASHLIGHT)

        initFlashlight()
        
        // 【核心修复】：顺序初始化逻辑
        lifecycleScope.launch {
            // 1. 先从 DataStore 抓取最新的亮度配置
            currentBrightnessLevel = DataStoreManager.getDefaultBrightness(this@FlashlightActivity).first()
            
            // 2. 根据抓取到的值同步 UI
            selectBrightnessCard(currentBrightnessLevel)
            
            // 3. 配置点击监听
            setupClickListeners()
            
            // 4. 开启实时监听配置流
            observeSettings()
        }

        updateStats()
        SoundManager.initSoundPool(this)
        updateButtonState()

        if (TemperatureManager.isEnabled()) {
            showTemperatureContainer()
            updateTemperatureDisplay(TemperatureManager.getCurrentTemperature(), TemperatureManager.isOverheating())
        } else {
            hideTemperatureContainer()
        }
    }

    private fun observeSettings() {
        // 监听自动关闭时间
        lifecycleScope.launch {
            DataStoreManager.getFlashlightAutoOffTime(this@FlashlightActivity).collectLatest { minutes ->
                currentAutoOffMinutes = minutes
                binding.tvTotalTime.text = if (minutes >= 114514) getString(R.string.auto_off_never) else minutes.toFloat().toDetailedTime(this@FlashlightActivity)
            }
        }
        
        // 监听默认亮度（实现实时联动）
        lifecycleScope.launch {
            DataStoreManager.getDefaultBrightness(this@FlashlightActivity).collectLatest { level ->
                if (currentBrightnessLevel != level) {
                    currentBrightnessLevel = level
                    selectBrightnessCard(level)
                    // 如果手电筒亮着，实时调光
                    if (isFlashlightOn) adjustBrightness()
                }
            }
        }
    }

    private fun saveLevel(level: Int) {
        currentBrightnessLevel = level
        selectBrightnessCard(level)
        // 【核心修复】：将用户选中的档位存入 DataStore
    }

    // --- 业务逻辑保持稳定 ---

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

        binding.btnFlashlight.setOnClickListener { v ->
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
        timeRepository.startRecording(TimeRepository.TYPE_FLASHLIGHT)
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
        timeRepository.stopRecording(TimeRepository.TYPE_FLASHLIGHT)
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
        val todayMinutes = timeRepository.getTodayUsageMinutes(TimeRepository.TYPE_FLASHLIGHT)
        binding.tvFlashlightTime.text = todayMinutes.toDigitalTime()

        if (isTimerRunning) {
            val elapsedMillis = System.currentTimeMillis() - startTime
            val elapsedMinutes = elapsedMillis / 1000f / 60f
            val autoOffMinutes = currentAutoOffMinutes

            binding.lastTime.text = elapsedMinutes.toDigitalTime()

            if (autoOffMinutes > 0 && autoOffMinutes < 114514) {
                val progress = ((elapsedMinutes / autoOffMinutes) * 100).toInt().coerceIn(0, 100)
                binding.progressFlashlight.progress = progress
                updateTimeIndicatorPosition(progress)

                if (elapsedMinutes >= autoOffMinutes) {
                    toggleFlashlight()
                    Toast.makeText(this, getString(R.string.flashlight_auto_off), Toast.LENGTH_SHORT).show()
                }
            } else {
                binding.progressFlashlight.progress = 0
            }
        }
    }

    private fun updateBatteryDisplay(info: BatteryRepository.BatteryInfo) {
        binding.tvBatteryPercent.text = info.levelText
        binding.tvBatteryStatus.text = info.status
        binding.ivBatteryIcon.setImageResource(info.iconRes)
    }

    override fun onBatteryStatusChanged(info: BatteryRepository.BatteryInfo) {
        updateBatteryDisplay(info)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
        stopHaloAnimation()
        if (isFlashlightOn) turnOffFlashlight()
    }
}