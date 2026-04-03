package com.name.FlashLight

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.name.FlashLight.databinding.FlashlightBinding
import com.name.FlashLight.utils.PageConstants
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    
    // 局部临时亮度变量，不存入 DataStore
    private var currentBrightnessLevel = 1
    private var currentAutoOffMinutes = 5

    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var maxBrightnessLevel = 1
    private var isStrengthSupported = false

    private var timerJob: Job? = null
    private var startTime = 0L

    private var haloAnimator: AnimatorSet? = null
    private val selectedBlueColor = Color.parseColor("#4786EF")

    private val brightnessLevelMap = mutableMapOf(0 to 1, 1 to 1, 2 to 1)

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) initFlashlight() else { finish() }
    }

    override fun createBinding(): FlashlightBinding = FlashlightBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PageUsageRecorder.recordPageVisit(this, PageConstants.PAGE_FLASHLIGHT)
        StartupModeManager.recordLastPage(this, PageConstants.PAGE_FLASHLIGHT)

        checkPermissionAndStart()
        
        lifecycleScope.launch {
            // 【核心逻辑】：进入时只同步一次默认值
            currentBrightnessLevel = DataStoreManager.getDefaultBrightness(this@FlashlightActivity).first()
            selectBrightnessCard(currentBrightnessLevel)
            setupClickListeners()
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

    private fun checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            initFlashlight()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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

            if (cameraId == null) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId!!)
                val maxLevel = characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                if (maxLevel > 1) {
                    maxBrightnessLevel = maxLevel
                    isStrengthSupported = true
                    updateBrightnessMap()
                } else {
                    disableBrightnessUI()
                }
            } else {
                disableBrightnessUI()
            }
        } catch (e: Exception) { }
    }

    private fun disableBrightnessUI() {
        isStrengthSupported = false
        // 使用具体的卡片列表进行置灰
        val cards = listOf(binding.cardLeft, binding.cardMiddle, binding.cardRight)
        cards.forEach { it.alpha = 0.5f; it.isEnabled = false }
    }

    private fun updateBrightnessMap() {
        brightnessLevelMap[0] = (maxBrightnessLevel * 0.1).toInt().coerceAtLeast(1)
        brightnessLevelMap[1] = (maxBrightnessLevel * 0.5).toInt().coerceAtLeast(2)
        brightnessLevelMap[2] = maxBrightnessLevel

        (binding.cardLeft.getChildAt(0) as TextView).text = getString(R.string.brightness_card_low).replace("\n", " ") + ": ${brightnessLevelMap[0]}"
        (binding.cardMiddle.getChildAt(0) as TextView).text = getString(R.string.brightness_card_medium).replace("\n", " ") + ": ${brightnessLevelMap[1]}"
        (binding.cardRight.getChildAt(0) as TextView).text = getString(R.string.brightness_card_high).replace("\n", " ") + ": ${brightnessLevelMap[2]}"
    }

    private fun startTimer() {
        timerJob?.cancel()
        startTime = System.currentTimeMillis()
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
        updateStats()
    }

    private fun updateStats() {
        val todayMinutes = timeRepository.getTodayUsageMinutes(TimeRepository.TYPE_FLASHLIGHT)
        binding.tvFlashlightTime.text = todayMinutes.toDigitalTime()

        if (timerJob != null) {
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
                    navigateToMain()
                }
            } else {
                binding.progressFlashlight.progress = 0
            }
        } else {
            binding.lastTime.text = "00:00"
            binding.progressFlashlight.progress = 0
        }
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
    }

    private fun saveLevel(level: Int) {
        currentBrightnessLevel = level
        selectBrightnessCard(level)
        // 注意：此处不调用 DataStoreManager.set，保持局部修改
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            DataStoreManager.getFlashlightAutoOffTime(this@FlashlightActivity).collectLatest { minutes ->
                currentAutoOffMinutes = minutes
                binding.tvTotalTime.text = if (minutes >= 114514) getString(R.string.auto_off_never) else minutes.toFloat().toDetailedTime(this@FlashlightActivity)
            }
        }
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
            if (event.action == MotionEvent.ACTION_UP && isInside) {
                v.performClick()
            }
            handleTouchAnimation(v, event)
            true
        }

        binding.btnFlashlight.setOnClickListener { v ->
            v.feedback()
            toggleFlashlight()
        }

        binding.cardLeft.setOnClickListener { saveLevel(0); if (isFlashlightOn) adjustBrightness() }
        binding.cardMiddle.setOnClickListener { saveLevel(1); if (isFlashlightOn) adjustBrightness() }
        binding.cardRight.setOnClickListener { saveLevel(2); if (isFlashlightOn) adjustBrightness() }
    }

    private fun handleTouchAnimation(view: View, event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
        }
    }

    private fun toggleFlashlight() {
        if (cameraId == null) return
        isFlashlightOn = !isFlashlightOn
        updateButtonState()
        if (isFlashlightOn) turnOnFlashlight() else turnOffFlashlight()
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
            val isSelected = index == level
            card.isSelected = isSelected
            card.setBackgroundResource(R.drawable.bg_rounded_selector)
            (card.getChildAt(0) as TextView).setTextColor(if (isSelected) selectedBlueColor else Color.WHITE)
        }
    }

    override fun onMonitorStateChanged(isEnabled: Boolean) {
        runOnUiThread { 
            if (isEnabled) { 
                showTemperatureContainer()
                updateTemperatureDisplay(TemperatureManager.getCurrentTemperature(), TemperatureManager.isOverheating()) 
            } else { hideTemperatureContainer() } 
        }
    }

    override fun onTemperatureUpdate(temperature: Float, isOverheating: Boolean) {
        runOnUiThread { if (binding.temperatureContainer.visibility == View.VISIBLE) updateTemperatureDisplay(temperature, isOverheating) }
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

    override fun onBatteryStatusChanged(info: BatteryRepository.BatteryInfo) {
        binding.tvBatteryPercent.text = info.levelText
        binding.tvBatteryStatus.text = info.status
        binding.ivBatteryIcon.setImageResource(info.iconRes)
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
        stopHaloAnimation()
        if (isFlashlightOn) turnOffFlashlight()
    }
}