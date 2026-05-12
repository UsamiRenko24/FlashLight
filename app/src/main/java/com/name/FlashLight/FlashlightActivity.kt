package com.name.FlashLight

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.name.FlashLight.databinding.FlashlightBinding
import com.name.FlashLight.utils.PageConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import utils.*

/**
 * 工业级成熟版 - 手电筒页面
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
    private var isStrengthSupported = false
    private val brightnessLevelMap = mutableMapOf(0 to 1, 1 to 1, 2 to 1)

    private var haloAnimator: AnimatorSet? = null
    private val selectedBlueColor = Color.parseColor("#2AE1F8")

    // 监听系统手电筒状态，确保同步
    private val torchCallback =
        object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, enabled: Boolean) {
                if (id == cameraId) {
                    runOnUiThread {
                        if (isFlashlightOn != enabled) {
                            isFlashlightOn = enabled
                            updateButtonState()
                            if (!enabled) stopTimer()
                        }
                    }
                }
            }
        }

    override fun createBinding(): FlashlightBinding = FlashlightBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initHardware()
    }

    override fun initViews() {
        SoundManager.initSoundPool(this)
        updateButtonState()
        syncTemperatureUI()
        refreshTotalUsageUI()
        binding.bottomNav.selectedItemId = R.id.nav_flashlight
    }

    override fun initListeners() {
        binding.traceback.setOnClickListener { handleBackPress() }
        binding.ivSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        binding.cardLeft.setOnClickListener { changeBrightness(0) }
        binding.cardMiddle.setOnClickListener { changeBrightness(1) }
        binding.cardRight.setOnClickListener { changeBrightness(2) }

        binding.bottomNav.setOnItemSelectedListener { item ->
            handleNavigation(item.itemId)
        }

        binding.btnFlashlight.setOnTouchListener { v, event ->
            val isInside = event.x >= 0 && event.x <= v.width && event.y >= 0 && event.y <= v.height
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start()
                MotionEvent.ACTION_MOVE -> {
                    val scale = if (isInside) 0.92f else 1f
                    v.animate().scaleX(scale).scaleY(scale).setDuration(100).start()
                }
                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
                    if (isInside) {
                        v.feedback()
                        ensureCameraPermission { toggleFlashlight() }
                    }
                }
                MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
            }
            true
        }
    }

    private fun initHardware() {
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            cameraManager.registerTorchCallback(torchCallback, null)

            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

                if (hasFlash) {
                    cameraId = id
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val maxLevel = chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                        if (maxLevel > 1) {
                            isStrengthSupported = true
                            brightnessLevelMap[0] = 1
                            brightnessLevelMap[1] = ((maxLevel + 1) / 2).coerceAtLeast(1)
                            brightnessLevelMap[2] = maxLevel
                        }
                    }
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleNavigation(itemId: Int): Boolean {
        when (itemId) {
            R.id.nav_stats -> {
                startActivity(Intent(this, StatsActivity::class.java))
                finish()
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                return false
            }
            R.id.nav_home -> {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                return false
            }
            R.id.nav_flashlight -> return true
            R.id.nav_blink -> {
                startActivity(Intent(this, BlinkActivity::class.java))
                finish()
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                return false
            }
            R.id.nav_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                finish()
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                return false
            }
        }
        return false
    }

    private fun applyHardwareTorch(on: Boolean) {
        val id = cameraId ?: run {
            return
        }

        try {
            if (on) {
                if (isStrengthSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        val level = brightnessLevelMap[currentBrightnessLevel] ?: 1
                        cameraManager.turnOnTorchWithStrengthLevel(id, level)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        cameraManager.setTorchMode(id, true)
                    }
                } else {
                    cameraManager.setTorchMode(id, true)
                }
            } else {
                cameraManager.setTorchMode(id, false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
//            Toast.makeText(this, "Flashlight failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleFlashlight() {

        if (cameraId == null) {

            return
        }

        val nextState = !isFlashlightOn

        if (nextState) {
            startTorch()
        } else {
            stopTorch()
        }
    }

//    private fun performToggle() {
//        val nextState = !isFlashlightOn
//        if (nextState) startTorch() else stopTorch()
//    }

    private fun startTorch() {

        try {

            applyHardwareTorch(true)

            isFlashlightOn = true

            updateButtonState()

            timeRepository.startRecording(
                TimeRepository.TYPE_FLASHLIGHT
            )

            startTimer()

        } catch (e: Exception) {

            e.printStackTrace()

            Toast.makeText(
                this,
                getString(R.string.flashlight_unavailable),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun stopTorch() {

        try {

            applyHardwareTorch(false)

        } catch (e: Exception) {
            e.printStackTrace()
        }

        isFlashlightOn = false

        updateButtonState()

        timeRepository.stopRecording(
            TimeRepository.TYPE_FLASHLIGHT
        )

        stopTimer()
    }

    private fun updateButtonState() {
        binding.btnFlashlight.setBackgroundResource(if (isFlashlightOn) R.drawable.btn_flashlight_on else R.drawable.btn_flashlight_off)
        binding.tvBtnStatus.apply {
            text = if (isFlashlightOn) getString(R.string.btn_on) else getString(R.string.btn_off)
            setTextColor(if (isFlashlightOn) Color.parseColor("#5B3E00") else Color.parseColor("#D7D7D7"))
        }
        if (isFlashlightOn) startHalo() else stopHalo()
    }

    private fun updateTemperatureDisplay(t: Float, o: Boolean) {
        val prefix = getString(R.string.normal_temperature)
        binding.tvTemperature.text = if (o) getString(R.string.overheat_reminder) else "$prefix: %.1f°C".format(t)
    }

    override fun onDestroy() {

        if (isFlashlightOn) {
            stopTorch()
        }

        try {
            cameraManager.unregisterTorchCallback(torchCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        super.onDestroy()
    }

    override fun initObservers() {
        lifecycleScope.launch {
            currentBrightnessLevel = DataStoreManager.getDefaultBrightness(this@FlashlightActivity).first()
            selectBrightnessCard(currentBrightnessLevel)
            DataStoreManager.getFlashlightAutoOffTime(this@FlashlightActivity).collectLatest { minutes ->
                currentAutoOffMinutes = minutes
                val timeText = if (minutes >= 114514) getString(R.string.auto_off_never)
                else minutes.toFloat().toDetailedTime(this@FlashlightActivity)
                binding.tvTotalTime1.text = timeText
                binding.tvTotalTime2.text = timeText
            }
        }
    }

    private fun changeBrightness(level: Int) {
        currentBrightnessLevel = level
        selectBrightnessCard(level)
        if (isFlashlightOn) applyHardwareTorch(true)
    }

    override fun onBatteryStatusChanged(info: BatteryRepository.BatteryInfo) {
        binding.apply {
            tvBatteryPercent.text = info.levelText
            tvBatteryStatus.text = info.status
            ivBatteryIcon.setImageResource(info.iconRes)
        }
    }

    override fun stopAllFeatures() { if (isFlashlightOn) stopTorch() }

    private fun selectBrightnessCard(level: Int) {
        listOf(binding.cardLeft, binding.cardMiddle, binding.cardRight).forEachIndexed { i, card ->
            val isSelected = i == level
            card.isSelected = isSelected
            val tv = (card as? android.view.ViewGroup)?.getChildAt(0) as? TextView
            tv?.setTextColor(if (isSelected) selectedBlueColor else Color.WHITE)
        }
    }

    private fun startTimer() {

        timerJob?.cancel()

        startTime = System.currentTimeMillis()

        initialTodayMinutes =
            timeRepository.getTodayUsageMinutes(
                TimeRepository.TYPE_FLASHLIGHT
            )

        timerJob = lifecycleScope.launch {

            while (isFlashlightOn) {

                renderTimer()

                if (checkAutoOff()) {
                    break
                }

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
        }
    }

    private fun refreshTotalUsageUI() {
        val today = timeRepository.getTodayUsageMinutes(TimeRepository.TYPE_FLASHLIGHT)
        binding.tvFlashlightTime.text = today.toDigitalTime()
    }

    private fun checkAutoOff(): Boolean {

        val elapsed =
            (System.currentTimeMillis() - startTime) / 60000f

        return if (
            currentAutoOffMinutes < 114514 &&
            elapsed >= currentAutoOffMinutes
        ) {

            stopTorch()

            Toast.makeText(
                this,
                getString(R.string.flashlight_auto_off),
                Toast.LENGTH_SHORT
            ).show()

            true

        } else {

            false
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

    override fun onTemperatureUpdate(temperature: Float, isOverheating: Boolean) {
        runOnUiThread { updateTemperatureDisplay(temperature, isOverheating) }
    }

    override fun onMonitorStateChanged(isEnabled: Boolean) {
        runOnUiThread { syncTemperatureUI() }
    }
}