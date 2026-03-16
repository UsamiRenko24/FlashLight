package com.name.FlashLight

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.name.FlashLight.utils.PageConstants
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import utils.feedback

class FlashlightActivity : BaseActivity() {

    private var isFlashlightOn = false
    private var currentBrightnessLevel = 0  // 0=低, 1=中, 2=高
    private lateinit var btnFlashlight: Button
    private lateinit var ivTraceback: ImageView

    private lateinit var ivSettings: ImageView
    // 亮度档位卡片
    private lateinit var cardLow: LinearLayout
    private lateinit var cardMedium: LinearLayout
    private lateinit var cardHigh: LinearLayout

    // 档位文字
    private lateinit var tvLow: TextView
    private lateinit var tvMedium: TextView
    private lateinit var tvHigh: TextView

    private lateinit var tvBatteryPercent: TextView
    private lateinit var tvBatteryStatus: TextView
    private lateinit var ivBatteryIcon: ImageView
    private lateinit var tvFlashlightTime: TextView
    private lateinit var tvLastTime: TextView
    private lateinit var tvTotalTime: TextView

    private lateinit var progressFlashlight: ProgressBar

    private lateinit var frameLayout: FrameLayout
    // 手电筒控制相关
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var maxBrightnessLevel = 5  // 默认最大亮度等级



    // 亮度等级映射（根据设备实际支持的最大等级动态调整）
    private val brightnessLevelMap = mutableMapOf(
        0 to 1,  // 低档位：等级1
        1 to 3,  // 中档位：等级3
        2 to 5   // 高档位：等级5
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.flashlight)
        initViews()
        PageUsageRecorder.recordPageVisit(this, PageConstants.PAGE_FLASHLIGHT)
        StartupModeManager.recordLastPage(this, PageConstants.PAGE_FLASHLIGHT)

        initFlashlight()  // 初始化手电筒
        setupClickListeners()
        selectBrightnessCard(getSharedPreferences("brightness_settings", Context.MODE_PRIVATE).getInt("default_brightness", 1))  // 默认选中低档位
        updateBatteryDisplay()
        updateStats()
        startBatteryMonitor()
        updateButtonState()
    }

    private fun initViews() {
        btnFlashlight = findViewById(R.id.btn_flashlight)
        ivTraceback = findViewById(R.id.traceback)
        ivSettings = findViewById(R.id.iv_settings)

        cardLow = findViewById(R.id.card_left)
        cardMedium = findViewById(R.id.card_middle)
        cardHigh = findViewById(R.id.card_right)

        tvLow = cardLow.getChildAt(0) as TextView
        tvMedium = cardMedium.getChildAt(0) as TextView
        tvHigh = cardHigh.getChildAt(0) as TextView

        tvBatteryPercent = findViewById(R.id.tv_battery_percent)
        tvBatteryStatus = findViewById(R.id.tv_battery_status)
        ivBatteryIcon = findViewById(R.id.iv_battery_icon)
        tvFlashlightTime = findViewById(R.id.tv_flashlight_time)
        tvTotalTime =findViewById(R.id.tv_total_time)
        progressFlashlight = findViewById(R.id.progress_flashlight)
        tvLastTime = findViewById(R.id.last_time)
        frameLayout = findViewById(R.id.frameLayout)
    }

    private fun initFlashlight() {
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            // 找到支持闪光灯的后置摄像头
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                flashAvailable && lensFacing == CameraCharacteristics.LENS_FACING_BACK
            }

            if (cameraId == null) {
                Toast.makeText(this, "设备不支持闪光灯", Toast.LENGTH_LONG).show()
                btnFlashlight.isEnabled = false
                return
            }

            // 检查是否支持亮度调节（Android 13+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId!!)
                val maxLevel = characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)

                if (maxLevel != null && maxLevel > 1) {
                    maxBrightnessLevel = maxLevel
                    updateBrightnessMap()
                    Toast.makeText(this, "支持亮度调节，最大等级: $maxBrightnessLevel", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "设备不支持亮度调节，将使用默认亮度", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "当前Android版本不支持亮度调节，将使用默认亮度", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Toast.makeText(this, "初始化失败: ${e.message}", Toast.LENGTH_SHORT).show()
            btnFlashlight.isEnabled = false
        }
    }
    private fun updateBrightnessMap() {
        // 根据设备支持的最大亮度等级动态调整档位值
        val rawStep = maxBrightnessLevel / 3

        brightnessLevelMap[0] = rawStep.coerceAtLeast(1)
        brightnessLevelMap[1] = (rawStep * 2).coerceAtMost(maxBrightnessLevel - 1).coerceAtLeast(brightnessLevelMap[0]!! + 1)
        brightnessLevelMap[2] = maxBrightnessLevel

        // 更新显示的文字
        tvLow.text = "低\n${brightnessLevelMap[0]}"
        tvMedium.text = "中\n${brightnessLevelMap[1]}"
        tvHigh.text = "高\n${brightnessLevelMap[2]}"

    }
    private fun getAutoOffTime(): Int {
        // ✅ 直接从 SharedPreferences 读取
        val prefs = getSharedPreferences("auto_off_settings", Context.MODE_PRIVATE)
        return prefs.getInt(AutomaticActivity.KEY_FLASHLIGHT_TIME, 5)
    }
    private fun updateTimeIndicatorPosition(progress: Int) {
        // 获取进度条的宽度
        progressFlashlight.post {
            val progressBarWidth = progressFlashlight.width
            val indicatorWidth = tvLastTime.width

            // 计算文字应该出现的X位置
            // 公式：进度条左边缘 + (进度条宽度 * 进度百分比) - (文字宽度 / 2)
            val translationX = (progressBarWidth * progress / 100f) - (indicatorWidth / 2f)

            // 边界检查：不让文字超出屏幕
            val maxTranslation = progressBarWidth - indicatorWidth
            val finalTranslation = translationX.coerceIn(0f, maxTranslation.toFloat())

            // 移动文字
            tvLastTime.translationX = finalTranslation

            // 调试日志
            println("📊 进度: $progress%, 位置: $finalTranslation")
        }
    }
    private fun formatMinutes(minutes: Int): String {
        return if (minutes >= 60) {
            "${minutes / 60}小时${minutes % 60}分钟"
        } else {
            "${minutes}分钟"
        }
    }
    private fun setupClickListeners() {
        ivTraceback.setOnClickListener { handleBackPress() }

        ivSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
        btnFlashlight.setOnClickListener {
            it.feedback()
            toggleFlashlight()
        }

        cardLow.setOnClickListener {
            selectBrightnessCard(0)
            currentBrightnessLevel = 0
            if (isFlashlightOn) {
                adjustBrightness()
                Toast.makeText(this, "已切换至低亮度", Toast.LENGTH_SHORT).show()
            }
        }

        cardMedium.setOnClickListener {
            selectBrightnessCard(1)
            currentBrightnessLevel = 1
            if (isFlashlightOn) {
                adjustBrightness()
                Toast.makeText(this, "已切换至中亮度", Toast.LENGTH_SHORT).show()
            }
        }

        cardHigh.setOnClickListener {
            selectBrightnessCard(2)
            currentBrightnessLevel = 2
            if (isFlashlightOn) {
                adjustBrightness()
                Toast.makeText(this, "已切换至高亮度", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleFlashlight() {
        if (cameraId == null) {
            Toast.makeText(this, "闪光灯不可用", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            isFlashlightOn = !isFlashlightOn
            updateButtonState()

            if (isFlashlightOn) {
                turnOnFlashlight()
            } else {
                turnOffFlashlight()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
            isFlashlightOn = !isFlashlightOn
            updateButtonState()
        }
    }

    private fun turnOnFlashlight() {
        TimeRecorder.startRecording(this, "flashlight")
        try {
            // 1. 从映射表中获取用户期望的档位对应的硬件等级
            val targetLevel = brightnessLevelMap[currentBrightnessLevel] ?: 1

            // 2. 确保这个值不会超过硬件的最大限制，也不会小于1
            val safeLevel = targetLevel.coerceIn(1, maxBrightnessLevel)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ 支持亮度调节
                cameraManager.turnOnTorchWithStrengthLevel(cameraId!!, safeLevel)

                val brightnessText = when (currentBrightnessLevel) {
                    0 -> "低"
                    1 -> "中"
                    2 -> "高"
                    else -> "低"
                }
                Toast.makeText(this, "手电筒已打开（${brightnessText}亮度，等级$safeLevel）", Toast.LENGTH_SHORT).show()

            } else {
                // 低版本只能开关
                cameraManager.setTorchMode(cameraId!!, true)
                Toast.makeText(this, "手电筒已打开", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "开启失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun turnOffFlashlight() {
        try {
            cameraManager.setTorchMode(cameraId!!, false)
            Toast.makeText(this, "手电筒已关闭", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "关闭失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        TimeRecorder.stopRecording(this, "flashlight")
        updateStats()
    }

    private fun adjustBrightness() {
        if (!isFlashlightOn || cameraId == null) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val brightnessLevel = brightnessLevelMap[currentBrightnessLevel] ?: 1
                cameraManager.turnOnTorchWithStrengthLevel(cameraId!!, brightnessLevel)

                val brightnessText = when (currentBrightnessLevel) {
                    0 -> "低"
                    1 -> "中"
                    2 -> "高"
                    else -> "低"
                }
                Toast.makeText(this, "亮度已调整为: ${brightnessText}（等级$brightnessLevel）", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "亮度调整失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectBrightnessCard(level: Int) {
        // 重置所有卡片样式
        cardLow.isSelected = false
        cardMedium.isSelected = false
        cardHigh.isSelected = false

        // 重置文字颜色
        tvLow.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        tvMedium.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        tvHigh.setTextColor(ContextCompat.getColor(this, android.R.color.white))

        // 设置选中卡片
        when (level) {
            0 -> {
                cardLow.isSelected = true
                tvLow.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
            }
            1 -> {
                cardMedium.isSelected = true
                tvMedium.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
            }
            2 -> {
                cardHigh.isSelected = true
                tvHigh.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
            }
        }
    }

    private fun updateButtonState() {
        if (isFlashlightOn) {
            btnFlashlight.text = "开启"
            btnFlashlight.setBackgroundResource(R.drawable.btn_flashlight_on)
        } else {
            btnFlashlight.text = "关闭"
            btnFlashlight.setBackgroundResource(R.drawable.btn_flashlight_off)
        }
    }
    private fun updateStats() {
        // 获取各功能今日使用时间（分钟）
        val flashlightTime = TimeRecorder.getTodayTime(this, "flashlight")
        tvFlashlightTime.text = formatTime(flashlightTime)
        tvLastTime.text = formatTime(flashlightTime)
        // 获取数据
        val usedTime = TimeRecorder.getTodayTime(this, "flashlight")  // 已使用时间
        val totalTime = getAutoOffTime().toFloat()  // 总时长

        // 计算进度百分比
        val progress = ((usedTime / totalTime) * 100).toInt().coerceIn(0, 100)
        progressFlashlight.progress = progress

        tvTotalTime.text = formatMinutes(getAutoOffTime())

        // 更新文字位置
        updateTimeIndicatorPosition(progress)

        // 更新文字内容
        tvFlashlightTime.text = formatTime(usedTime)
    }
    private fun formatTime(minutes: Float): String {
        val totalSeconds = (minutes * 60).toInt()

        // 计算分钟和秒
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60

        // 格式化为两位数，不足补0
        return String.format("%02d:%02d", mins, secs)
    }
    private fun updateBatteryDisplay() {
        // ✅ 一行代码更新所有电池UI
        BatteryHelper.updateBatteryUI(this, tvBatteryPercent, tvBatteryStatus, ivBatteryIcon)
    }
    private fun startBatteryMonitor() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                updateBatteryDisplay()
                updateStats()
                handler.postDelayed(this, 1000) // 每秒更新一次
            }
        }
        handler.post(runnable)
    }
    override fun onPause() {
        super.onPause()
        // 退出时关闭手电筒，避免耗电
        if (isFlashlightOn && cameraId != null) {
            try {
                cameraManager.setTorchMode(cameraId!!, false)
                isFlashlightOn = false
                updateButtonState()
            } catch (e: Exception) {
                // 忽略错误
            }
        }
        TimeRecorder.stopRecording(this, "flashlight")
        updateStats()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 确保退出时手电筒关闭
        if (cameraId != null) {
            try {
                cameraManager.setTorchMode(cameraId!!, false)
            } catch (e: Exception) {
                // 忽略错误
            }
        }
        TimeRecorder.stopRecording(this, "flashlight")
        updateStats()
    }
}