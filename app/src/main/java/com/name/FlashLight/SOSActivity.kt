package com.name.FlashLight

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.name.FlashLight.databinding.SosBinding
import utils.TimeRecorder

class SOSActivity : BaseActivity<SosBinding>() {

    // 手电筒控制
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null

    // SOS闪烁控制
    private var isSosActive = false
    private var sosHandler = Handler(Looper.getMainLooper())

    // 统计相关
    private var cycleCount = 0  // 当前会话的循环次数
    private var startTime = 0L  // 开始时间

    private val Timerhandler = Handler(Looper.getMainLooper())
    private lateinit var timerRunnable: Runnable
    private var isTimerRunning = false

    private var haloAnimator: AnimatorSet? = null 

    // SOS摩斯码时序
    companion object {
        private const val DOT_ON = 200L
        private const val DASH_ON = 600L
        private const val CHAR_GAP = 600L
        private const val CYCLE_GAP = 2000L
    }

    private val sosSequence = listOf(
        Signal.DOT, Signal.DOT, Signal.DOT,
        Signal.GAP,
        Signal.DASH, Signal.DASH, Signal.DASH,
        Signal.GAP,
        Signal.DOT, Signal.DOT, Signal.DOT,
        Signal.CYCLE_GAP
    )

    private enum class Signal { DOT, DASH, GAP, CYCLE_GAP }

    override fun createBinding(): SosBinding {
        return SosBinding.inflate(layoutInflater)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initViews()
        initFlashlight()
        updateBatteryInfo()
        startBatteryMonitor()
        initTimer()
        
        TimeRecorder.startRecording(this, "blink")
        startRecording()
        startTime = System.currentTimeMillis()

        startSOS()
        startTimer()
        startHaloAnimation() 
    }

    private fun initViews() {
        binding.siganl.text = ". . . _ _ _\n. . ."
        binding.tvSosCycles.text = "0${getString(R.string.times)}"
        binding.lastTime.text = "00:00"
    }

    private fun startHaloAnimation() {
        if (haloAnimator != null) return
        binding.sosHalo.visibility = View.VISIBLE
        binding.sosHalo.translationZ = 4f 

        val scaleX = ObjectAnimator.ofFloat(binding.sosHalo, "scaleX", 1.0f, 1.15f)
        val scaleY = ObjectAnimator.ofFloat(binding.sosHalo, "scaleY", 1.0f, 1.15f)
        val alpha = ObjectAnimator.ofFloat(binding.sosHalo, "alpha", 1.0f, 1.2f)

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

    private fun initTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (isTimerRunning) {
                    updateDuration()  
                    Timerhandler.postDelayed(this, 1000)
                }
            }
        }
    }

    private fun startRecording() {
        startTime = System.currentTimeMillis()
        isTimerRunning = true
        cycleCount = 0  
        binding.tvSosCycles.text = "0${getString(R.string.times)}"
        Timerhandler.post(timerRunnable)
    }

    private fun stopRecording() {
        isTimerRunning = false
        Timerhandler.removeCallbacks(timerRunnable)
    }

    private fun updateDuration() {
        val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
        val displayMinutes = elapsedSeconds / 60
        val displaySeconds = elapsedSeconds % 60
        binding.lastTime.text = String.format("%02d:%02d", displayMinutes, displaySeconds)

        val autoOffMinutes = getAutoOffTime()
        if (autoOffMinutes >= 114514) {
            binding.remainTime.text = getString(R.string.auto_off_never)
        } else {
            val totalSeconds = autoOffMinutes * 60
            val progress = (elapsedSeconds * 100 / totalSeconds).toInt().coerceIn(0, 100)
            binding.progressBlink.progress = progress
            
            val remainingSeconds = (totalSeconds - elapsedSeconds).toInt().coerceAtLeast(0)
            val remainMinutes = remainingSeconds / 60
            val remainSeconds = remainingSeconds % 60
            binding.remainTime.text = String.format("%02d:%02d", remainMinutes, remainSeconds)
            
            if (remainingSeconds <= 0) {
                navigateToMain()
            }
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

    private fun startSOS() {
        if (cameraId == null) return

        isSosActive = true
        val sosRunnable = object : Runnable {
            var sequenceIndex = 0

            override fun run() {
                if (!isSosActive) return
                val signal = sosSequence[sequenceIndex]

                when (signal) {
                    Signal.DOT -> {
                        setFlashlightAndColor(true)
                        sosHandler.postDelayed({
                            setFlashlightAndColor(false)
                            moveToNext()
                        }, DOT_ON)
                    }
                    Signal.DASH -> {
                        setFlashlightAndColor(true)
                        sosHandler.postDelayed({
                            setFlashlightAndColor(false)
                            moveToNext()
                        }, DASH_ON)
                    }
                    Signal.GAP -> {
                        setFlashlightAndColor(false)
                        moveToNextDelayed(CHAR_GAP)
                    }
                    Signal.CYCLE_GAP -> {
                        setFlashlightAndColor(false)
                        sequenceIndex = -1
                        runOnUiThread {
                            cycleCount++
                            binding.tvSosCycles.text = "$cycleCount ${getString(R.string.times)}"
                        }
                        moveToNextDelayed(CYCLE_GAP)
                    }
                }
            }

            private fun moveToNext() {
                sequenceIndex++
                if (sequenceIndex < sosSequence.size) sosHandler.post(this)
            }

            private fun moveToNextDelayed(delay: Long) {
                sequenceIndex++
                if (sequenceIndex < sosSequence.size) sosHandler.postDelayed(this, delay)
            }
        }
        sosHandler.post(sosRunnable)
    }

    private fun updateBatteryInfo() {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryIntent?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

            val batteryPct = if (level > 0 && scale > 0) level * 100f / scale else 0f
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            val statusText = when {
                status == BatteryManager.BATTERY_STATUS_FULL -> getString(R.string.battery_status_full)
                isCharging -> getString(R.string.battery_charging)
                else -> getString(R.string.battery_not_charging)
            }

            binding.tvBatteryPercent.text = String.format("%.0f%%", batteryPct)
            binding.tvBatteryStatus.text = statusText
        }
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
                characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
            }
        } catch (e: Exception) { }
    }

    private fun startTimer() {
        val timerHandler = Handler(Looper.getMainLooper())
        val timerRunnable = object : Runnable {
            override fun run() {
                if (isSosActive) {
                    updateDuration()
                    timerHandler.postDelayed(this, 1000)
                }
            }
        }
        timerHandler.post(timerRunnable)
    }

    private fun setFlashlightAndColor(on: Boolean) {
        try { cameraManager.setTorchMode(cameraId!!, on) } catch (e: Exception) { }
        runOnUiThread {
            binding.siganl.setTextColor(if (on) Color.WHITE else Color.parseColor("#FF9E9E9E"))
        }
    }

    private fun startBatteryMonitor() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                updateBatteryInfo()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(runnable)
    }

    override fun onPause() {
        TimeRecorder.stopRecording(this, "blink")
        stopRecording()
        super.onPause()
        isSosActive = false
        sosHandler.removeCallbacksAndMessages(null)
        try { cameraManager.setTorchMode(cameraId!!, false) } catch (e: Exception) { }
    }

    override fun onResume() {
        super.onResume()
        if (!isTimerRunning) startRecording()
        if (!isSosActive) startSOS()
    }

    override fun onDestroy() {
        if (isSosActive) stopRecording()
        super.onDestroy()
        isSosActive = false
        sosHandler.removeCallbacksAndMessages(null)
        haloAnimator?.cancel()
    }
}