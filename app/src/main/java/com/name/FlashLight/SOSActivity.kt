package com.name.FlashLight

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.name.FlashLight.utils.PageConstants
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SOSActivity : BaseActivity() {

    // UI组件
    private lateinit var tvSosPattern: TextView
    private lateinit var tvSosCycles: TextView  // 显示循环次数
    private lateinit var tvDuration: TextView   // 显示持续时间

    // 手电筒控制
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null

    // SOS闪烁控制
    private var isSosActive = false
    private var sosHandler = Handler(Looper.getMainLooper())
    private var sosRunnable: Runnable? = null

    // 统计相关
    private var cycleCount = 0  // 当前会话的循环次数
    private var startTime = 0L  // 开始时间

    private lateinit var tvBatteryPercent: TextView
    private lateinit var tvBatteryStatus: TextView
    private lateinit var tvRemainTime: TextView

    private  lateinit var tvBlinkProgress: ProgressBar


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

        // 记录 blink 时间
        TimeRecorder.startRecording(this, "blink")
        startTime = System.currentTimeMillis()

        // 恢复之前保存的循环次数
        cycleCount = TimeRecorder.getSOSCycles(this)
        tvSosCycles.text = "${cycleCount}次"

        // 恢复之前保存的持续时间
        val savedDuration = TimeRecorder.getSOSDuration(this)
        if (savedDuration > 0) {
            val minutes = savedDuration / 60
            val seconds = savedDuration % 60
            tvDuration.text = String.format("%02d:%02d", minutes, seconds)
            // 调整 startTime 以匹配保存的持续时间
            startTime = System.currentTimeMillis() - (savedDuration * 1000)
        }

        startSOS()
        startTimer()
    }

    private fun initViews() {
        tvSosPattern = findViewById(R.id.siganl)
        tvSosCycles = findViewById(R.id.tv_sos_cycles)
        tvDuration = findViewById(R.id.last_time)

        tvSosPattern.text = ". . . _ _ _\n. . ."
        tvSosCycles.text = "0次"
        tvDuration.text = "00:00"

        tvBatteryPercent = findViewById(R.id.tv_battery_percent)
        tvBatteryStatus = findViewById(R.id.tv_battery_status)
        tvRemainTime = findViewById(R.id.remain_time)
        tvBlinkProgress = findViewById(R.id.progress_blink)
    }


    private fun updateBatteryInfo() {     //在这个地方扩展progressbar
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
                status == BatteryManager.BATTERY_STATUS_FULL -> "已充满"
                isCharging -> "充电中"
                else -> "未充电"
            }

            // 更新UI
            tvBatteryPercent.text = String.format("%.0f%%", batteryPct)
            tvBatteryStatus.text = statusText

            val usedTime = TimeRecorder.getTodayTime(this, "blink")  // 已使用时间
            val totalTime = getAutoOffTime().toFloat()  // 总时长

            // 计算进度百分比
            val progress = ((usedTime / totalTime) * 100).toInt().coerceIn(0, 100)
            tvBlinkProgress.progress = progress

            tvRemainTime.text = formatTime(totalTime - usedTime)
        }
    }
    private fun getAutoOffTime(): Int {
        // ✅ 直接从 SharedPreferences 读取
        val prefs = getSharedPreferences("auto_off_settings", Context.MODE_PRIVATE)
        return prefs.getInt(AutomaticActivity.KEY_BLINK_TIME, 5)
    }
    private fun formatTime(minutes: Float): String {
        val totalSeconds = (minutes * 60).toInt()

        // 计算分钟和秒
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60

        // 格式化为两位数，不足补0
        return String.format("%02d:%02d", mins, secs)
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

    private fun startSOS() {
        if (cameraId == null) return

        isSosActive = true

        sosRunnable = object : Runnable {
            var sequenceIndex = 0

            override fun run() {
                if (!isSosActive) return

                val signal = sosSequence[sequenceIndex]

                when (signal) {
                    Signal.DOT -> {
                        setFlashlightAndColor(true)
                        sosHandler.postDelayed({
                            setFlashlightAndColor(false)
                            moveToNextSignal()
                        }, DOT_ON)
                    }
                    Signal.DASH -> {
                        setFlashlightAndColor(true)
                        sosHandler.postDelayed({
                            setFlashlightAndColor(false)
                            moveToNextSignal()
                        }, DASH_ON)
                    }
                    Signal.GAP -> {
                        setFlashlightAndColor(false)
                        moveToNextSignalDelayed(CHAR_GAP)
                    }
                    Signal.CYCLE_GAP -> {
                        setFlashlightAndColor(false)
                        sequenceIndex = -1
                        cycleCount++

                        runOnUiThread {
                            tvSosCycles.text = "${cycleCount}次"
                        }

                        // ✅ 保存循环次数
                        TimeRecorder.addSOSCycles(this@SOSActivity, 1)

                        moveToNextSignalDelayed(CYCLE_GAP)
                    }
                }

            }

            private fun moveToNextSignal() {
                sequenceIndex++
                if (sequenceIndex < sosSequence.size) {
                    sosHandler.post(this)
                }
            }

            private fun moveToNextSignalDelayed(delay: Long) {
                sequenceIndex++
                if (sequenceIndex < sosSequence.size) {
                    sosHandler.postDelayed(this, delay)
                }
            }
        }

        sosRunnable?.let { sosHandler.post(it) }
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

                    if (duration % 10 == 0L) {
                        TimeRecorder.saveSOSDuration(this@SOSActivity, duration)
                    }
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
                else Color.parseColor("#A0A0A0")
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
        val duration = (System.currentTimeMillis() - startTime) / 1000
        TimeRecorder.saveSOSDuration(this, duration)

        TimeRecorder.stopRecording(this, "blink")

        super.onPause()
        isSosActive = false
        sosHandler.removeCallbacksAndMessages(null)
        try {
            cameraManager.setTorchMode(cameraId!!, false)
        } catch (e: Exception) {
            // 忽略错误
        }
    }

    override fun onDestroy() {
        // ✅ 确保退出时也保存
        if (isSosActive) {
            TimeRecorder.stopRecording(this, "blink")
            saveSOSCycles()
        }

        super.onDestroy()
        isSosActive = false
        sosHandler.removeCallbacksAndMessages(null)
    }

    private fun saveSOSCycles() {
        val prefs = getSharedPreferences("usage_stats", Context.MODE_PRIVATE)
        val todayKey = "sos_cycles_${getTodayDate()}"
        val currentCycles = prefs.getInt(todayKey, 0)
        prefs.edit().putInt(todayKey, currentCycles + cycleCount).apply()

        println("✅ SOS循环次数已保存: 本次 +$cycleCount, 累计 ${currentCycles + cycleCount}")
    }

    private fun getTodayDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }
}