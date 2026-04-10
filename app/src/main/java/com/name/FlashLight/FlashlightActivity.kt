package com.name.FlashLight

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.view.MotionEvent
import android.view.View
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
import utils.BatteryRepository
import utils.DataStoreManager
import utils.SoundManager
import utils.TemperatureManager
import utils.TimeRepository
import utils.feedback
import utils.toDetailedTime
import utils.toDigitalTime

class FlashlightActivity : BaseActivity<FlashlightBinding>(), TemperatureManager.TemperatureListener {

    // --- 1. 声明式配置 (插拔式功能开关) ---
    override val pageTrackName = PageConstants.PAGE_FLASHLIGHT
    override val isBatteryMonitorEnabled = true
    override val isLowBatteryCheckEnabled = true

    // --- 2. 局部会话状态 ---
    private var isFlashlightOn = false
    private var currentBrightnessLevel = 1
    private var currentAutoOffMinutes = 5
    private var startTime = 0L
    private var timerJob: Job? = null

    // --- 3. 硬件管理 ---
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var maxBrightnessLevel = 1
    private var isStrengthSupported = false

    // --- 4. UI 辅助状态 ---
    private var haloAnimator: AnimatorSet? = null
    private val selectedBlueColor = Color.parseColor("#4786EF")
    private val brightnessLevelMap = mutableMapOf(0 to 1, 1 to 1, 2 to 1)

    override fun createBinding(): FlashlightBinding = FlashlightBinding.inflate(layoutInflater)

    /**
     * 模块 A: UI 初始化 (静态赋值)
     */
    override fun initViews() {
        SoundManager.initSoundPool(this)
        if (cameraId == null) initHardware()
        updateButtonState()
        syncTemperatureUI()
        refreshTodayUsage()
    }

    /**
     * 模块 B: 交互监听 (统一入口)
     */
    override fun initListeners() {
        binding.traceback.setOnClickListener { handleBackPress() }
        binding.ivSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        // 使用父类封装的“一键权限”模块
        binding.btnFlashlight.setOnClickListener { v ->
            v.feedback()
            ensureCameraPermission { toggleFlashlight() }
        }

        binding.cardLeft.setOnClickListener { changeBrightness(0) }
        binding.cardMiddle.setOnClickListener { changeBrightness(1) }
        binding.cardRight.setOnClickListener { changeBrightness(2) }

        // 绑定触摸动画 (复用逻辑)
        binding.btnFlashlight.setOnTouchListener { v, event ->
            handleTouchAnimation(v, event)
            if (event.action == MotionEvent.ACTION_UP) v.performClick()
            true
        }
    }

    /**
     * 模块 C: 响应式观察 (DataStore 联动)
     */
    override fun initObservers() {
        lifecycleScope.launch {
            // 1. 初始化亮度 (只同步一次)
            currentBrightnessLevel = DataStoreManager.getDefaultBrightness(this@FlashlightActivity).first()
            selectBrightnessCard(currentBrightnessLevel)

            // 2. 持续监听自动关闭时间 (实时同步)
            DataStoreManager.getFlashlightAutoOffTime(this@FlashlightActivity).collectLatest { minutes ->
                currentAutoOffMinutes = minutes
                binding.tvTotalTime.text = if (minutes >= 114514) getString(R.string.auto_off_never)
                else minutes.toFloat().toDetailedTime(this@FlashlightActivity)
            }
        }
    }

    // --- 核心业务逻辑 (内聚化管理) ---

    private fun toggleFlashlight() {
        isFlashlightOn = !isFlashlightOn
        updateButtonState()
        if (isFlashlightOn) startTorchSession() else stopTorchSession()
    }

    private fun startTorchSession() {
        timeRepository.startRecording(TimeRepository.TYPE_FLASHLIGHT)
        applyHardwareTorch(true)
        startTimerLoop()
    }

    private fun stopTorchSession() {
        applyHardwareTorch(false)
        timeRepository.stopRecording(TimeRepository.TYPE_FLASHLIGHT)
        stopTimerLoop()
    }

    private fun startTimerLoop() {
        timerJob?.cancel()
        startTime = System.currentTimeMillis()
        timerJob = lifecycleScope.launch {
            while (true) {
                refreshTimerUI()
                refreshTodayUsage()
                checkAutoOffTrigger()
                delay(1000)
            }
        }
    }

    private fun stopTimerLoop() {
        timerJob?.cancel()
        timerJob = null
        binding.lastTime.text = "00:00"
        binding.progressFlashlight.progress = 0
        refreshTodayUsage()
    }

    private fun checkAutoOffTrigger() {
        val elapsed = (System.currentTimeMillis() - startTime) / 60000f
        if (currentAutoOffMinutes < 114514 && elapsed >= currentAutoOffMinutes) {
            stopTorchSession()
            updateButtonState()
            Toast.makeText(this, getString(R.string.flashlight_auto_off), Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshTimerUI() {
        val elapsed = (System.currentTimeMillis() - startTime) / 60000f
        binding.lastTime.text = elapsed.toDigitalTime()
        if (currentAutoOffMinutes < 114514) {
            val progress = (elapsed * 100 / currentAutoOffMinutes).toInt().coerceIn(0, 100)
            binding.progressFlashlight.progress = progress
            updateTimeIndicatorPosition(progress)
        }
    }

    private fun refreshTodayUsage() {
        val today = timeRepository.getTodayUsageMinutes(TimeRepository.TYPE_FLASHLIGHT)
        binding.tvFlashlightTime.text = today.toDigitalTime()
    }

    // --- 硬件与权限逻辑 ---

    private fun initHardware() {
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                        chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }

            cameraId?.let { id ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val max = chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                    if (max > 1) {
                        maxBrightnessLevel = max
                        isStrengthSupported = true
                        updateBrightnessMap()
                    } else hideBrightnessUI()
                } else hideBrightnessUI()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun applyHardwareTorch(on: Boolean) {
        try {
            if (on && cameraId != null) {
                val level = brightnessLevelMap[currentBrightnessLevel] ?: 1
                if (isStrengthSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    cameraManager.turnOnTorchWithStrengthLevel(cameraId!!, level)
                } else {
                    cameraManager.setTorchMode(cameraId!!, true)
                }
            } else {
                cameraId?.let { cameraManager.setTorchMode(it, false) }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun changeBrightness(level: Int) {
        currentBrightnessLevel = level
        selectBrightnessCard(level)
        if (isFlashlightOn) applyHardwareTorch(true)
    }

    // --- 行为钩子 (Hooks) 重写 ---

    override fun onBatteryStatusChanged(info: BatteryRepository.BatteryInfo) {
        binding.apply {
            tvBatteryPercent.text = info.levelText
            tvBatteryStatus.text = info.status
            ivBatteryIcon.setImageResource(info.iconRes)
        }
    }

    override fun stopAllFeatures() {
        if (isFlashlightOn) toggleFlashlight()
    }

    // --- 辅助 UI 逻辑 (保持纯净) ---

    private fun handleTouchAnimation(v: View, event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
        }
    }

    private fun hideBrightnessUI() {
        isStrengthSupported = false
        listOf(binding.cardLeft, binding.cardMiddle, binding.cardRight).forEach {
            it.alpha = 0.5f; it.isEnabled = false
        }
    }

    private fun updateBrightnessMap() {
        brightnessLevelMap[0] = (maxBrightnessLevel * 0.1).toInt().coerceAtLeast(1)
        brightnessLevelMap[1] = (maxBrightnessLevel * 0.5).toInt().coerceAtLeast(2)
        brightnessLevelMap[2] = maxBrightnessLevel

        val cards = listOf(binding.cardLeft, binding.cardMiddle, binding.cardRight)
        val labels = listOf(R.string.brightness_card_low, R.string.brightness_card_medium, R.string.brightness_card_high)
        cards.forEachIndexed { i, card ->
            (card.getChildAt(0) as TextView).text = "${getString(labels[i])}: ${brightnessLevelMap[i]}"
        }
    }

    private fun selectBrightnessCard(level: Int) {
        listOf(binding.cardLeft, binding.cardMiddle, binding.cardRight).forEachIndexed { i, card ->
            val isSelected = i == level
            card.isSelected = isSelected
            card.setBackgroundResource(R.drawable.bg_rounded_selector)
            (card.getChildAt(0) as TextView).setTextColor(if (isSelected) selectedBlueColor else Color.WHITE)
        }
    }

    private fun updateButtonState() {
        binding.btnFlashlight.apply {
            setBackgroundResource(if (isFlashlightOn) R.drawable.btn_flashlight_on else R.drawable.btn_flashlight_off)
            text = getString(if (isFlashlightOn) R.string.btn_on else R.string.btn_off)
        }
        if (isFlashlightOn) startHalo() else stopHalo()
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

    private fun updateTemperatureDisplay(temperature: Float, isOverheating: Boolean) {
        binding.tvTemperature.text = if (isOverheating) getString(R.string.overheat_reminder) else getString(R.string.normal_temperature)
        binding.temperatureContainer.setBackgroundResource(if (isOverheating) R.drawable.bg_temperature_warning else R.drawable.bg_rounded_corner)
        binding.ivTemperature.setColorFilter(if (isOverheating) Color.RED else Color.WHITE)
    }

    override fun onTemperatureUpdate(temperature: Float, isOverheating: Boolean) {
        runOnUiThread { updateTemperatureDisplay(temperature, isOverheating) }
    }

    override fun onMonitorStateChanged(isEnabled: Boolean) {
        runOnUiThread { syncTemperatureUI() }
    }

    private fun updateTimeIndicatorPosition(progress: Int) {
        binding.progressFlashlight.post {
            val margin = (binding.progressFlashlight.width * progress / 100f) - (binding.lastTime.width / 2f)
            binding.lastTime.translationX = margin.coerceIn(0f, (binding.progressFlashlight.width - binding.lastTime.width).toFloat())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTorchSession()
    }
}