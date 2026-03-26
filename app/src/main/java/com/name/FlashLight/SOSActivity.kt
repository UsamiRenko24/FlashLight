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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import utils.TimeRecorder

class SOSActivity : BaseActivity() {

    // UI组件
    private lateinit var tvSosPattern: TextView
    private lateinit var tvSosCycles: TextView  // 显示循环次数
    private lateinit var tvDuration: TextView   // 显示持续时间
    private lateinit var sosHalo: View          // 【新增】定义光晕View

    // 手电筒控制
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null

    // SOS闪烁控制
    private var isSosActive = false
    private var sosHandler = Handler(Looper.getMainLooper())

    // 统计相关
    private var cycleCount = 0  // 当前会话的循环次数
    private var startTime = 0L  // 开始时间

    private lateinit var tvBatteryPercent: TextView
    private lateinit var tvBatteryStatus: TextView
    private lateinit var tvRemainTime: TextView

    private  lateinit var tvBlinkProgress: ProgressBar

    private val Timerhandler = Handler(Looper.getMainLooper())
    private lateinit var timerRunnable: Runnable
    private var isTimerRunning = false

    private var haloAnimator: AnimatorSet? = null // 【新增】动画对象引用

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sos)
        initViews()

        initFlashlight()
        updateBatteryInfo()
        startBatteryMonitor()
        initTimer()
        // 记录 blink 时间
        TimeRecorder.startRecording(this, "blink")
        startRecording()
        startTime = System.currentTimeMillis()

        // 恢复之前保存的循环次数
        tvSosCycles.text = "${cycleCount}"+getString(R.string.times)

        startSOS()
        startTimer()
        startHaloAnimation() // 【新增】开启呼吸光晕
    }

    private fun initViews() {
        tvSosPattern = findViewById(R.id.siganl)
        tvSosCycles = findViewById(R.id.tv_sos_cycles)
        tvDuration = findViewById(R.id.last_time)
        sosHalo = findViewById(R.id.sos_halo) // 【修复】初始化光晕视图

        tvSosPattern.text = ". . . _ _ _\n. . ."
        tvSosCycles.text = "0"+getString(R.string.times)
        tvDuration.text = "00:00"

        tvBatteryPercent = findViewById(R.id.tv_battery_percent)
        tvBatteryStatus = findViewById(R.id.tv_battery_status)
        tvRemainTime = findViewById(R.id.remain_time)
        tvBlinkProgress = findViewById(R.id.progress_blink)
    }

    private fun startHaloAnimation() {
        if (haloAnimator != null) return
        sosHalo.visibility = View.VISIBLE
        sosHalo.translationZ = 4f // 低于卡片的 elevation

        // 缩放动画 (1.0x -> 1.15x)
        val scaleX = ObjectAnimator.ofFloat(sosHalo, "scaleX", 1.0f, 1.15f)
        val scaleY = ObjectAnimator.ofFloat(sosHalo, "scaleY", 1.0f, 1.15f)
        // 透明度呼吸 (0.4 -> 0.8)
        val alpha = ObjectAnimator.ofFloat(sosHalo, "alpha", 1.0f, 1.2f)

        scaleX.repeatCount = ValueAnimator.INFINITE
        scaleX.repeatMode = ValueAnimator.REVERSE
        scaleY.repeatCount = ValueAnimator.INFINITE
        scaleY.repeatMode = ValueAnimator.REVERSE
        alpha.repeatCount = ValueAnimator.INFINITE
        alpha.repeatMode = ValueAnimator.REVERSE

        haloAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 1000 // 警示频率稍快
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun initTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (isTimerRunning) {
                    updateDuration()  // 更新时间显示
                    Timerhandler.postDelayed(this, 1000)
                }
            }
        }
    }
    private fun startRecording() {
        startTime = System.currentTimeMillis()
        isTimerRunning = true
        cycleCount = 0  // 重置循环次数
        tvSosCycles.text = "0"+getString(R.string.times)
        Timerhandler.post(timerRunnable)
    }
    private fun stopRecording() {
        isTimerRunning = false
        Timerhandler.removeCallbacks(timerRunnable)

    }
    private fun updateDuration() {
        // 1. 计算经过的时间（秒）
        val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000

        // 2. 显示当前使用时间（用于 UI）
        val displayMinutes = elapsedSeconds / 60
        val displaySeconds = elapsedSeconds % 60
        tvDuration.text = String.format("%02d:%02d", displayMinutes, displaySeconds)
        if (getAutoOffTime() >= 114514) {
            tvRemainTime.text = getString(R.string.auto_off_never)
        } else {
            // 3. 获取总时长（转换为秒）
            val totalMinutes = getAutoOffTime()  // 例如 5
            val totalSeconds = totalMinutes * 60  // 300秒
            // 4. 计算进度（直接用秒）
            val progress = (elapsedSeconds * 100 / totalSeconds).toInt().coerceIn(0, 100)
            tvBlinkProgress.progress = progress
            // 5. 计算剩余时间（秒转分钟:秒）
            val remainingSeconds = (totalSeconds - elapsedSeconds).toInt().coerceAtLeast(0)
            val remainMinutes = remainingSeconds / 60
            val remainSeconds = remainingSeconds % 60
            tvRemainTime.text = String.format("%02d:%02d", remainMinutes, remainSeconds)
            // 6. 自动关闭
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
        val sosHandler = Handler(Looper.getMainLooper())

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

                        // ✅ 每次完成一个完整 SOS 循环，次数 +1
                        runOnUiThread {
                            cycleCount++
                            tvSosCycles.text = "$cycleCount "+getString(R.string.times)
                        }

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
                    sosHandler.postDelayed(this, delay)
                }
            }
        }
        sosHandler.post(sosRunnable)
    }


    private fun updateBatteryInfo() {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryIntent?.let { intent ->
            // 获取电池数据
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

            // 计算电量
            val batteryPct = if (level > 0 && scale > 0) {
                level * 100f / scale
            } else {
                0f
            }

            // 判断充电状态
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            // 生成状态文本
            val statusText = when {
                status == BatteryManager.BATTERY_STATUS_FULL -> getString(R.string.battery_status_full)
                isCharging -> getString(R.string.battery_charging)
                else -> getString(R.string.battery_not_charging)
            }

            // 更新UI
            tvBatteryPercent.text = String.format("%.0f%%", batteryPct)
            tvBatteryStatus.text = statusText

        }
    }
    private fun getAutoOffTime(): Int {
        // ✅ 直接从 SharedPreferences 读取
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
            if (cameraId == null) {
                Toast.makeText(this, "设备不支持闪光灯", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "闪光灯初始化失败", Toast.LENGTH_LONG).show()
        }
    }

    private fun startTimer() {
        val timerHandler = Handler(Looper.getMainLooper())
        val timerRunnable = object : Runnable {
            override fun run() {
                if (isSosActive) {
                    val duration = (System.currentTimeMillis() - startTime) / 1000
                    val minutes = duration / 60
                    val seconds = duration % 60
                    tvDuration.text = String.format("%02d:%02d", minutes, seconds)
                    updateDuration()
                    timerHandler.postDelayed(this, 1000)
                }
            }
        }
        timerHandler.post(timerRunnable)
    }

    private fun setFlashlightAndColor(on: Boolean) {
        try {
            cameraManager.setTorchMode(cameraId!!, on)
        } catch (e: Exception) {
            // 忽略错误
        }

        runOnUiThread {
            tvSosPattern.setTextColor(
                if (on) Color.parseColor("#FFFFFF")
                else Color.parseColor("#FF9E9E9E")
            )
        }
    }
    private fun startBatteryMonitor() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                updateBatteryInfo() //在这个地方更新
                handler.postDelayed(this, 1000) // 每秒更新一次
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
        try {
            cameraManager.setTorchMode(cameraId!!, false)
        } catch (e: Exception) {
            // 忽略错误
        }
    }
    override fun onResume() {
        super.onResume()
        // 回到页面时重新开始记录
        if (!isTimerRunning) {
            startRecording()
        }

        // 重新开始 SOS
        if (!isSosActive) {
            startSOS()
        }
    }
    override fun onDestroy() {
        // ✅ 确保退出时也保存
        if (isSosActive) {
            stopRecording()
        }

        super.onDestroy()
        isSosActive = false
        sosHandler.removeCallbacksAndMessages(null)
        haloAnimator?.cancel() // 【新增】销毁动画
    }
}