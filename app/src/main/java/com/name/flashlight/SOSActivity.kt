package com.name.flashlight

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.lifecycle.lifecycleScope
import com.name.flashlight.databinding.SosBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.name.flashlight.utils.BatteryRepository
import com.name.flashlight.utils.DataStoreManager
import com.name.flashlight.utils.TimeRepository
import com.name.flashlight.utils.toCountdownDisplay
import com.name.flashlight.utils.toDigitalTime

class SOSActivity : BaseActivity<SosBinding>() {

    private lateinit var cameraManager: CameraManager

    private var cameraId: String? = null

    private var isSosActive = false

    private val sosHandler = Handler(Looper.getMainLooper())

    private var cycleCount = 0

    private var startTime = 0L

    private val timerHandler = Handler(Looper.getMainLooper())

    private var isTimerRunning = false

    private var haloAnimator: AnimatorSet? = null

    private var currentAutoOffMinutes = 5

    companion object {

        private const val DOT_ON = 200L

        private const val DASH_ON = 600L

        private const val CHAR_GAP = 600L

        private const val CYCLE_GAP = 2000L
    }

    private val sosSequence = listOf(
        Signal.DOT,
        Signal.DOT,
        Signal.DOT,

        Signal.GAP,

        Signal.DASH,
        Signal.DASH,
        Signal.DASH,

        Signal.GAP,

        Signal.DOT,
        Signal.DOT,
        Signal.DOT,

        Signal.CYCLE_GAP
    )

    private enum class Signal {

        DOT,
        DASH,
        GAP,
        CYCLE_GAP
    }

    override fun createBinding(): SosBinding {

        return SosBinding.inflate(layoutInflater)
    }

    override fun initViews() {

        binding.siganl.text = ". . . _ _ _ . . ."

        binding.tvSosCycles.text =
            "0 ${getString(R.string.times)}"

        binding.lastTime.text = "00:00"

        initFlashlight()

        startHaloAnimation()
    }

    override fun initListeners() {

        // 不需要写 traceback
        // BaseActivity 已自动处理
    }

    override fun initObservers() {

        lifecycleScope.launch {

            DataStoreManager
                .getFlashlightAutoOffTime(this@SOSActivity)
                .collectLatest { minutes ->

                    currentAutoOffMinutes = minutes
                }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        timeRepository.startRecording(
            TimeRepository.TYPE_BLINK
        )

        startTime = System.currentTimeMillis()

        isTimerRunning = true

        startSOS()

        startTimer()
    }

    private fun startHaloAnimation() {

        if (haloAnimator != null) return

        binding.sosHalo.visibility = android.view.View.VISIBLE

        val scaleX = ObjectAnimator.ofFloat(
            binding.sosHalo,
            "scaleX",
            1.0f,
            1.15f
        )

        val scaleY = ObjectAnimator.ofFloat(
            binding.sosHalo,
            "scaleY",
            1.0f,
            1.15f
        )

        val alpha = ObjectAnimator.ofFloat(
            binding.sosHalo,
            "alpha",
            0.6f,
            1.0f
        )

        listOf(scaleX, scaleY, alpha).forEach {

            it.repeatCount = ValueAnimator.INFINITE

            it.repeatMode = ValueAnimator.REVERSE
        }

        haloAnimator = AnimatorSet().apply {

            playTogether(scaleX, scaleY, alpha)

            duration = 1000

            interpolator =
                AccelerateDecelerateInterpolator()

            start()
        }
    }

    private fun startTimer() {

        val timerRunnable = object : Runnable {

            override fun run() {

                if (
                    isTimerRunning &&
                    !isFinishing
                ) {

                    updateDuration()

                    timerHandler.postDelayed(
                        this,
                        1000
                    )
                }
            }
        }

        timerHandler.post(timerRunnable)
    }

    private fun updateDuration() {

        val elapsedMinutes =
            (System.currentTimeMillis() - startTime) / 60000f

        binding.lastTime.text =
            elapsedMinutes.toDigitalTime()

        val remainingMinutes =
            (currentAutoOffMinutes - elapsedMinutes)
                .coerceAtLeast(0f)

        binding.remainTime.text =
            remainingMinutes.toCountdownDisplay(
                currentAutoOffMinutes,
                this
            )

        if (currentAutoOffMinutes < 114514) {

            val progress =
                (
                        elapsedMinutes *
                                100 /
                                currentAutoOffMinutes
                        )
                    .toInt()
                    .coerceIn(0, 100)

            binding.progressBlink.progress = progress

            if (remainingMinutes <= 0) {

                finish()
            }
        }
    }

    private fun startSOS() {

        if (cameraId == null) return

        isSosActive = true

        val sosRunnable = object : Runnable {

            var sequenceIndex = 0

            override fun run() {

                if (
                    !isSosActive ||
                    isFinishing
                ) return

                val signal =
                    sosSequence[sequenceIndex]

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

                        cycleCount++

                        binding.tvSosCycles.text =
                            "$cycleCount ${getString(R.string.times)}"

                        moveToNextDelayed(CYCLE_GAP)
                    }
                }
            }

            private fun moveToNext() {

                sequenceIndex++

                if (sequenceIndex < sosSequence.size) {

                    sosHandler.post(this)
                }
            }

            private fun moveToNextDelayed(delay: Long) {

                sequenceIndex++

                if (sequenceIndex < sosSequence.size) {

                    sosHandler.postDelayed(
                        this,
                        delay
                    )
                }
            }
        }

        sosHandler.post(sosRunnable)
    }

    private fun setFlashlightAndColor(on: Boolean) {

        if (
            isFinishing ||
            isDestroyed
        ) return

        try {

            cameraId?.let {

                cameraManager.setTorchMode(it, on)
            }

        } catch (_: Exception) {
        }

        runOnUiThread {

            binding.siganl.setTextColor(
                if (on) {
                    Color.WHITE
                } else {
                    Color.parseColor("#666666")
                }
            )

            binding.ivSosIcon.setColorFilter(
                if (on) {
                    Color.parseColor("#FFCE64")
                } else {
                    Color.parseColor("#666666")
                }
            )
        }
    }

    override fun onBatteryStatusChanged(
        info: BatteryRepository.BatteryInfo
    ) {

        binding.apply {

            tvBatteryPercent.text = info.levelText

            tvBatteryStatus.text = info.status

            ivBatteryIcon.setImageResource(info.iconRes)
        }
    }

    private fun initFlashlight() {

        cameraManager =
            getSystemService(CAMERA_SERVICE)
                    as CameraManager

        try {

            cameraId =
                cameraManager.cameraIdList.firstOrNull { id ->

                    val chars =
                        cameraManager
                            .getCameraCharacteristics(id)

                    chars.get(
                        CameraCharacteristics.FLASH_INFO_AVAILABLE
                    ) == true
                }

        } catch (_: Exception) {
        }
    }

    override fun onStop() {

        super.onStop()

        isSosActive = false

        isTimerRunning = false

        sosHandler.removeCallbacksAndMessages(null)

        timerHandler.removeCallbacksAndMessages(null)

        try {

            cameraId?.let {

                cameraManager.setTorchMode(it, false)
            }

        } catch (_: Exception) {
        }

        timeRepository.stopRecording(
            TimeRepository.TYPE_BLINK
        )
    }

    override fun onDestroy() {

        haloAnimator?.cancel()

        super.onDestroy()
    }
}