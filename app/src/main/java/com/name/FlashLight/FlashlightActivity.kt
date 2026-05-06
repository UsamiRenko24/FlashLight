package com.name.FlashLight

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.name.FlashLight.databinding.FlashlightBinding
import com.name.FlashLight.utils.PageConstants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import utils.*

/**
 * 工业级稳定版 - 手电筒页面
 */
class FlashlightActivity : BaseActivity<FlashlightBinding>(), TemperatureManager.TemperatureListener {

    override val pageTrackName = PageConstants.PAGE_FLASHLIGHT
    override val isBatteryMonitorEnabled = true
    override val isLowBatteryCheckEnabled = true

    private var isFlashlightOn = false
    private var currentBrightnessLevel = 1
    private var currentAutoOffMinutes = 5
    private var startTime = 0L
    private var timerJob: Job? = null
    private var initialTodayMinutes = 0f

    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var maxBrightnessLevel = 1
    private var isStrengthSupported = false

    private var haloAnimator: AnimatorSet? = null
    private val selectedBlueColor = Color.parseColor("#2AE1F8")
    private val brightnessLevelMap = mutableMapOf(0 to 1, 1 to 1, 2 to 1)

    override fun createBinding(): FlashlightBinding = FlashlightBinding.inflate(layoutInflater)

    override fun initViews() {
        SoundManager.initSoundPool(this)
        updateButtonState()
        syncTemperatureUI()
        refreshTotalUsageUI()
    }

    override fun initListeners() {
        binding.traceback.setOnClickListener { handleBackPress() }
        binding.ivSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        binding.btnFlashlight.setOnClickListener { v ->
            v.feedback()
            ensureCameraPermission { toggleFlashlight() }
        }

        binding.cardLeft.setOnClickListener { changeBrightness(0) }
        binding.cardMiddle.setOnClickListener { changeBrightness(1) }
        binding.cardRight.setOnClickListener { changeBrightness(2) }

        binding.btnFlashlight.setOnTouchListener { v, event ->
            handleTouchAnimation(v, event)
            if (event.action == MotionEvent.ACTION_UP) v.performClick()
            true
        }
    }

    override fun initObservers() {
        lifecycleScope.launch {
            currentBrightnessLevel = DataStoreManager.getDefaultBrightness(this@FlashlightActivity).first()
            selectBrightnessCard(currentBrightnessLevel)

            DataStoreManager.getFlashlightAutoOffTime(this@FlashlightActivity).collectLatest { minutes ->
                currentAutoOffMinutes = minutes
                try {
                    binding.tvTotalTime1.text = if (minutes >= 114514) getString(R.string.auto_off_never) 
                                               else minutes.toFloat().toDetailedTime(this@FlashlightActivity)
                } catch (e: Exception) {}
            }
        }
    }

    private fun toggleFlashlight() {
        if (cameraId == null) initHardware()
        isFlashlightOn = !isFlashlightOn
        updateButtonState()
        if (isFlashlightOn) startTorch() else stopTorch()
    }

    private fun startTorch() {
        timeRepository.startRecording(TimeRepository.TYPE_FLASHLIGHT)
        applyHardwareTorch(true)
        startTimer()
    }

    private fun stopTorch() {
        applyHardwareTorch(false)
        timeRepository.stopRecording(TimeRepository.TYPE_FLASHLIGHT)
        stopTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        startTime = System.currentTimeMillis()
        initialTodayMinutes = timeRepository.getTodayUsageMinutes(TimeRepository.TYPE_FLASHLIGHT)
        timerJob = lifecycleScope.launch {
            while (true) {
                renderTimer()
                checkAutoOff()
                delay(1000)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        binding.lastTime.text = "00:00"
        binding.progressFlashlight.progress = 0
        refreshTotalUsageUI()
    }

    private fun renderTimer() {
        val elapsedMinutes = (System.currentTimeMillis() - startTime) / 60000f
        binding.lastTime.text = elapsedMinutes.toDigitalTime()
        binding.tvFlashlightTime.text = (initialTodayMinutes + elapsedMinutes).toDigitalTime()

        if (currentAutoOffMinutes < 114514) {
            val progress = (elapsedMinutes * 100 / currentAutoOffMinutes).toInt().coerceIn(0, 100)
            binding.progressFlashlight.progress = progress
            updateTimeIndicatorPosition(progress)
        }
    }

    private fun refreshTotalUsageUI() {
        val today = timeRepository.getTodayUsageMinutes(TimeRepository.TYPE_FLASHLIGHT)
        binding.tvFlashlightTime.text = today.toDigitalTime()
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

    private fun applyHardwareTorch(on: Boolean) {
        try {
            if (on && cameraId != null) {
                val level = brightnessLevelMap[currentBrightnessLevel] ?: 1
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    cameraManager.turnOnTorchWithStrengthLevel(cameraId!!, level)
                } else {
                    cameraManager.setTorchMode(cameraId!!, true)
                }
            } else {
                cameraId?.let { cameraManager.setTorchMode(it, false) }
            }
        } catch (e: Exception) { }
    }

    private fun changeBrightness(level: Int) {
        currentBrightnessLevel = level
        selectBrightnessCard(level)
        if (isFlashlightOn) applyHardwareTorch(true)
    }

    override fun onBatteryStatusChanged(info: BatteryRepository.BatteryInfo) {
        // 修正 ID 引用：camelCase 风格
        binding.apply {
            tvBatteryPercent.text = info.levelText
            tvBatteryStatus.text = info.status
            ivBatteryIcon.setImageResource(info.iconRes)
        }
    }

    override fun stopAllFeatures() { if (isFlashlightOn) toggleFlashlight() }

    private fun selectBrightnessCard(level: Int) {
        listOf(binding.cardLeft, binding.cardMiddle, binding.cardRight).forEachIndexed { i, card ->
            val isSelected = i == level
            card.isSelected = isSelected
            card.setBackgroundResource(R.drawable.bg_rounded_selector)
            val tv = (card as? android.view.ViewGroup)?.getChildAt(0) as? TextView
            tv?.setTextColor(if (isSelected) selectedBlueColor else Color.WHITE)
        }
    }

    private fun updateButtonState() {
        binding.btnFlashlight.apply {
            setBackgroundResource(if (isFlashlightOn) R.drawable.btn_flashlight_on else R.drawable.btn_flashlight_off)
        }
        binding.tvBtnStatus.apply {
            text = if (isFlashlightOn) getString(R.string.btn_on) else getString(R.string.btn_off)
            setTextColor(if (isFlashlightOn) Color.parseColor("#5B3E00") else Color.parseColor("#D7D7D7"))
        }
        if (isFlashlightOn) startHalo() else stopHalo()
    }

    private fun checkAutoOff() {
        val elapsed = (System.currentTimeMillis() - startTime) / 60000f
        if (currentAutoOffMinutes < 114514 && elapsed >= currentAutoOffMinutes) {
            toggleFlashlight()
            Toast.makeText(this, getString(R.string.flashlight_auto_off), Toast.LENGTH_SHORT).show()
        }
    }

    private fun startHalo() {
        if (haloAnimator != null) return
        binding.viewHalo.visibility = View.VISIBLE
        val sX = ObjectAnimator.ofFloat(binding.viewHalo, "scaleX", 1.0f, 1.2f)
        val sY = ObjectAnimator.ofFloat(binding.viewHalo, "scaleY", 1.0f, 1.2f)
        val alpha = ObjectAnimator.ofFloat(binding.viewHalo, "alpha", 0.3f, 0.6f)
        listOf(sX, sY, alpha).forEach { it.repeatCount = ValueAnimator.INFINITE; it.repeatMode = ValueAnimator.REVERSE }
        haloAnimator = AnimatorSet().apply { playTogether(sX, sY, alpha); duration = 1500; start() }
    }

    private fun stopHalo() {
        haloAnimator?.cancel(); haloAnimator = null
        binding.viewHalo.visibility = View.GONE
    }

    private fun syncTemperatureUI() {
        if (TemperatureManager.isEnabled()) {
            binding.temperatureContainer.visibility = View.VISIBLE
            updateTemperatureDisplay(TemperatureManager.getCurrentTemperature(), TemperatureManager.isOverheating())
        } else binding.temperatureContainer.visibility = View.GONE
    }

    private fun updateTemperatureDisplay(t: Float, o: Boolean) {
        binding.tvTemperature.text = if (o) getString(R.string.overheat_reminder) else String.format("%.1f°C", t)
        binding.temperatureContainer.setBackgroundResource(if (o) R.drawable.bg_temperature_warning else R.drawable.bg_rounded_corner)
        binding.ivTemperature.setColorFilter(if (o) Color.RED else Color.WHITE)
    }

    override fun onTemperatureUpdate(temperature: Float, isOverheating: Boolean) {
        runOnUiThread { updateTemperatureDisplay(temperature, isOverheating) }
    }

    override fun onMonitorStateChanged(isEnabled: Boolean) {
        runOnUiThread { syncTemperatureUI() }
    }

    private fun handleTouchAnimation(v: View, event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
        }
    }

    private fun updateTimeIndicatorPosition(progress: Int) {
        binding.progressFlashlight.post {
            val margin = (binding.progressFlashlight.width * progress / 100f) - (binding.lastTime.width / 2f)
            binding.lastTime.translationX = margin.coerceIn(0f, (binding.progressFlashlight.width - binding.lastTime.width).toFloat())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTorch()
    }
}