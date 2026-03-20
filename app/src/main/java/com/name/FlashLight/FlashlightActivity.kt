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
import utils.SoundManager
import utils.TemperatureManager
import utils.feedback

class FlashlightActivity : BaseActivity(), TemperatureManager.TemperatureListener {

    private var isFlashlightOn = false
    private var currentBrightnessLevel = 0  // 0=低, 1=中, 2=高
    private lateinit var btnFlashlight: Button
    private lateinit var ivTraceback: ImageView
    private lateinit var viewHalo: View // 添加这一行

    private lateinit var ivSettings: ImageView
    private lateinit var ivTemperature: ImageView
    // 亮度档位卡片
    private lateinit var cardLow: LinearLayout
    private lateinit var cardMedium: LinearLayout
    private lateinit var cardHigh: LinearLayout
    private lateinit var temperatureContainer: LinearLayout

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
    private lateinit var tvTemperature: TextView

    private lateinit var progressFlashlight: ProgressBar

    private lateinit var frameLayout: FrameLayout
    // 手电筒控制相关
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var maxBrightnessLevel = 5  // 默认最大亮度等级
    private var handler: Handler? = null
    private var timerRunnable: Runnable? = null
    private var isTimerRunning = false
    private var startTime = 0L

    private var haloAnimator: AnimatorSet? = null // 添加动画对象引用


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
        initTimer()
        PageUsageRecorder.recordPageVisit(this, PageConstants.PAGE_FLASHLIGHT)
        StartupModeManager.recordLastPage(this, PageConstants.PAGE_FLASHLIGHT)

        initFlashlight()  // 初始化手电筒
        setupClickListeners()
        selectBrightnessCard(getSharedPreferences("brightness_settings", Context.MODE_PRIVATE).getInt("default_brightness", 1))  // 默认选中低档位
        updateBatteryDisplay()
        updateStats()
        SoundManager.initSoundPool(this)
        tvTotalTime.text = formatMinutes(getAutoOffTime())
        startBatteryMonitor()
        updateButtonState()
        if (TemperatureManager.isEnabled()) {
            showTemperatureContainer()
            // 如果有当前温度，立即显示
            updateTemperatureDisplay(
                TemperatureManager.getCurrentTemperature(),
                TemperatureManager.isOverheating()
            )
        } else {
            hideTemperatureContainer()
        }
    }

    private fun initViews() {
        btnFlashlight = findViewById(R.id.btn_flashlight)
        ivTraceback = findViewById(R.id.traceback)
        ivSettings = findViewById(R.id.iv_settings)
        viewHalo = findViewById(R.id.view_halo) // 初始化

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
        temperatureContainer = findViewById(R.id.temperature_container)
        tvTemperature = findViewById(R.id.tv_temperature)
        ivTemperature = findViewById(R.id.iv_temperature)
    }
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
                // 立即显示当前温度
                updateTemperatureDisplay(
                    TemperatureManager.getCurrentTemperature(),
                    TemperatureManager.isOverheating()
                )
            } else {
                hideTemperatureContainer()
            }
        }
    }

    override fun onTemperatureUpdate(temperature: Float, isOverheating: Boolean) {
        runOnUiThread {
            // 只有在显示状态时才更新UI
            if (temperatureContainer.visibility == LinearLayout.VISIBLE) {
                updateTemperatureDisplay(temperature, isOverheating)
            }
        }
    }

    private fun showTemperatureContainer() {
        if (temperatureContainer.visibility != LinearLayout.VISIBLE) {
            temperatureContainer.visibility = LinearLayout.VISIBLE
            // 淡入动画
            temperatureContainer.alpha = 0f
            temperatureContainer.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
        }
    }

    private fun hideTemperatureContainer() {
        if (temperatureContainer.visibility == LinearLayout.VISIBLE) {
            temperatureContainer.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    temperatureContainer.visibility = LinearLayout.GONE
                }
                .start()
        }
    }
    private fun updateTemperatureDisplay(temperature: Float, isOverheating: Boolean) {
        // 显示温度数值
        tvTemperature.text = String.format("%.1f°C", temperature)

        // 根据是否过热，显示不同样式
        if (isOverheating) {
            // 温度 ≥ 45°C：过热状态
            tvTemperature.text = "⚠️ 温度过高，建议暂停使用"
            temperatureContainer.setBackgroundResource(R.drawable.bg_temperature_warning)  // 红色背景
            ivTemperature.setColorFilter(Color.RED)  // 图标变红
        } else {
            // 温度 < 45°C：正常状态
            tvTemperature.text = "设备温度正常"
            temperatureContainer.setBackgroundResource(R.drawable.bg_rounded_corner)  // 正常背景
            ivTemperature.setColorFilter(Color.WHITE)  // 图标白色
        }

        // 调试日志
        println("🌡️ 温度显示: $temperature°C, 过热: $isOverheating")
    }
    override fun onResume() {
        super.onResume()
        // 回到页面时重新检查开关状态
        if (TemperatureManager.isEnabled()) {
            showTemperatureContainer()
            // 刷新温度显示
            updateTemperatureDisplay(
                TemperatureManager.getCurrentTemperature(),
                TemperatureManager.isOverheating()
            )
        } else {
            hideTemperatureContainer()
        }
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

    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        ivTraceback.setOnClickListener { handleBackPress() }

        ivSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // 使用增强的 TouchListener
        btnFlashlight.setOnTouchListener { view, event ->
            // 实时计算手指是否在按钮范围内
            val isInside = event.x >= 0 && event.x <= view.width && event.y >= 0 && event.y <= view.height

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate()
                        .scaleX(0.9f)
                        .scaleY(0.9f)
                        .setDuration(100)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // 如果移动到范围外，自动回弹；移回来再次缩小
                    val targetScale = if (isInside) 0.9f else 1.0f
                    view.animate()
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .setDuration(100)
                        .start()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200)
                        .setInterpolator(OvershootInterpolator())
                        .start()

                    // 核心修复：只有在抬起且在范围内时才触发
                    if (isInside) {
                        view.feedback()
                        toggleFlashlight()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                    true
                }
                else -> false
            }
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
    private fun startTimer() {
        // 如果已经在运行，先停止旧的
        stopTimer()

        // 重置开始时间
        startTime = System.currentTimeMillis()
        isTimerRunning = true

        // 创建新的 Runnable
        timerRunnable = object : Runnable {
            override fun run() {
                if (isTimerRunning) {
                    updateStats()
                    handler?.postDelayed(this, 1000)
                }
            }
        }
    }

    private fun stopTimer() {
        tvFlashlightTime.text = "00:00"
        tvLastTime.text = "00:00"
        progressFlashlight.progress = 0
        isTimerRunning = false
        timerRunnable?.let {
            handler?.removeCallbacks(it)
        }
        timerRunnable = null
    }
    private fun turnOnFlashlight() {
        TimeRecorder.startRecording(this, "flashlight")
        startTimer()
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
        stopTimer()
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
            btnFlashlight.elevation = 20f
            btnFlashlight.translationZ = 12f // 提升按钮高度
            startHaloAnimation()
        } else {
            btnFlashlight.text = "关闭"
            btnFlashlight.setBackgroundResource(R.drawable.btn_flashlight_off)
            btnFlashlight.elevation = 0f
            btnFlashlight.translationZ = 0f
            stopHaloAnimation()
        }
    }

    private fun startHaloAnimation() {
        if (haloAnimator != null) return
        viewHalo.visibility = View.VISIBLE
        viewHalo.translationZ = 6f // 高于背景，低于按钮
        
        val scaleX = ObjectAnimator.ofFloat(viewHalo, "scaleX", 1.0f, 1.35f)
        val scaleY = ObjectAnimator.ofFloat(viewHalo, "scaleY", 1.0f, 1.35f)
        val alpha = ObjectAnimator.ofFloat(viewHalo, "alpha", 0.3f, 0.9f)

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

    private fun stopHaloAnimation() {
        haloAnimator?.cancel()
        haloAnimator = null
        viewHalo.animate().alpha(0f).setDuration(300).withEndAction {
            viewHalo.visibility = View.GONE
        }.start()
    }

    private fun updateStats() {
        if (!isTimerRunning || startTime == 0L) return
        // 计算已使用时间
        val elapsedMs = System.currentTimeMillis() - startTime
        val usedTime = elapsedMs / 60000F
        val totalTime = getAutoOffTime().toFloat()  // 总时长
        if (elapsedMs < 0) {
            stopTimer()
            return
        }
        // 计算进度百分比
        val progress = ((usedTime / totalTime) * 100).toInt().coerceIn(0, 100)
        progressFlashlight.progress = progress

        tvTotalTime.text = formatMinutes(getAutoOffTime())

        // 更新两个 TextView
        tvFlashlightTime.text = formatTime(usedTime)
        tvLastTime.text = formatTime(usedTime)

        // 更新文字位置
        updateTimeIndicatorPosition(progress)


        // 如果剩余时间为0，自动关闭
        if (usedTime >= totalTime && isFlashlightOn) {
            navigateToMain()
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
        stopTimer()
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
        stopTimer()
        updateStats()
        TemperatureManager.removeListener(this)
        handler?.removeCallbacksAndMessages(null)
        haloAnimator?.cancel()
    }
}