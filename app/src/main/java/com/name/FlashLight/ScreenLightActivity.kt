package com.name.FlashLight

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.name.FlashLight.utils.PageConstants
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import utils.AutoBrightnessManager
import utils.SoundManager
import utils.VibrationManager
import utils.feedback

class ScreenLightActivity : BaseActivity() {

    // 顶部控件
    private lateinit var ivTraceback: ImageView
    private lateinit var ivSettings: ImageView

    private lateinit var tvTitle: TextView

    private var handler: Handler? = null
    private var timerRunnable: Runnable? = null
    private var startTime = 0L
    private var totalTimeMinutes: Int = 0

    // 预览区域
    private lateinit var tvLightInfo: TextView  // "白光-亮度" 文字
    private lateinit var previewCard: LinearLayout // 预览卡片背景

    // 亮度档位卡片
    private lateinit var cardLow: LinearLayout
    private lateinit var cardMedium: LinearLayout
    private lateinit var cardHigh: LinearLayout
    private lateinit var tvLow: TextView
    private lateinit var tvMedium: TextView
    private lateinit var tvHigh: TextView

    // 色温选择卡片
    private lateinit var cardWhite: LinearLayout  // 纯白
    private lateinit var cardWarm: LinearLayout   // 暖白
    private lateinit var cardCold: LinearLayout   // 冷白

    // 开启补光按钮
    private lateinit var btnStart: Button

    private lateinit var ScreenTime: TextView

    // 当前选择的状态
    private var currentBrightnessLevel = 1  // 0=低, 1=中, 2=高
    private var currentBrightnessValue = 70

    private var currentColorHex = "#FFFFFFFF"  // 对应的色值
    private var currentColorLevel = 0  // 0=纯白, 1=暖白, 2=冷白
    private lateinit var slidingAutoBrightness: SlidingButton

    // 色温对应的颜色值
    private val colorMap = mapOf(
        0 to "#FFFFFFFF",
        1 to "#FFFFF8DC",
        2 to "#FFF0F8FF"
    )

    // 亮度对应的百分比
    private val brightnessMap = mapOf(
        0 to 40,   // 低亮度 40%
        1 to 70,   // 中亮度 70%
        2 to 100   // 高亮度 100%
    )

    // 亮度对应的文字
    private val brightnessTextMap = mapOf(
        0 to "低",
        1 to "中",
        2 to "高"
    )

    private val colorTextMap = mapOf(
        0 to "纯",
        1 to "暖",
        2 to "冷"
    )
    private val screenLightLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                val newBrightness = data.getIntExtra("brightnessLevel", -1)
                val newColor = data.getIntExtra("colorLevel", -1)

                if (newBrightness != -1 && newColor != -1) {
                    // ✅ 只更新当前显示，不保存到 Preference
                    updateFromActiveActivity(newBrightness, newColor)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen)
        initViews()
        PageUsageRecorder.recordPageVisit(this, PageConstants.PAGE_SCREEN_LIGHT)
        StartupModeManager.recordLastPage(this, PageConstants.PAGE_SCREEN_LIGHT)

        setupClickListeners()

        // 默认选中中亮度和纯白光
        selectBrightnessCard(getSharedPreferences("brightness_settings", Context.MODE_PRIVATE).getInt("default_brightness", 1))
        val savedLevel = getSharedPreferences("brightness_settings", Context.MODE_PRIVATE).getInt("default_brightness", 1)
        currentBrightnessValue = brightnessMap[savedLevel] ?: 70
        currentBrightnessLevel = savedLevel
        handler = Handler(Looper.getMainLooper())
        // 获取总时间（只获取一次，防止变化）
        totalTimeMinutes = getAutoOffTime()


        // 检查是否有正在进行的计时
        checkExistingTimer()
        SoundManager.initSoundPool(this)
        selectColorCard(0)
        updatePreview()  // 更新预览
        loadAutoBrightnessState()
        setupAutoBrightnessListener()
        saveAutoOffTime(totalTimeMinutes)
    }
    private fun saveAutoOffTime(time: Int) {
        getSharedPreferences("timer_prefs", MODE_PRIVATE)
            .edit()
            .putInt("auto_off_time", time)
            .apply()
    }

    private fun initViews() {
        // 顶部控件
        ivTraceback = findViewById(R.id.traceback)
        ivSettings = findViewById(R.id.iv_settings)
        tvTitle = findViewById(R.id.tv_title)

        // 预览区域
        previewCard = findViewById(R.id.card1)
        tvLightInfo = findViewById(R.id.tv_light_info)

        // 亮度档位卡片
        cardLow = findViewById(R.id.card_left)
        cardMedium = findViewById(R.id.card_middle)
        cardHigh = findViewById(R.id.card_right)

        tvLow = cardLow.getChildAt(0) as TextView
        tvMedium = cardMedium.getChildAt(0) as TextView
        tvHigh = cardHigh.getChildAt(0) as TextView

        // 色温选择卡片
        cardWhite = findViewById(R.id.card_left1)
        cardWarm = findViewById(R.id.card_middle1)
        cardCold = findViewById(R.id.card_right1)

        // 开启补光按钮
        btnStart = findViewById(R.id.card2)
        ScreenTime = findViewById(R.id.tv_screen_time)
        slidingAutoBrightness = findViewById(R.id.sliding_auto_brightness)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        // 返回按钮
        ivTraceback.setOnClickListener { handleBackPress() }

        // 设置按钮
        ivSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 亮度档位点击
        cardLow.setOnClickListener {
            selectBrightnessCard(0)
            currentBrightnessLevel = 0
            currentBrightnessValue = brightnessMap[0] ?: 40
            updatePreview()
            Toast.makeText(this, "已选择低亮度", Toast.LENGTH_SHORT).show()
        }

        cardMedium.setOnClickListener {
            selectBrightnessCard(1)
            currentBrightnessLevel = 1
            currentBrightnessValue = brightnessMap[1] ?: 70
            updatePreview()
            Toast.makeText(this, "已选择中亮度", Toast.LENGTH_SHORT).show()
        }

        cardHigh.setOnClickListener {
            selectBrightnessCard(2)
            currentBrightnessLevel = 2
            currentBrightnessValue = brightnessMap[2] ?: 100
            updatePreview()
            Toast.makeText(this, "已选择高亮度", Toast.LENGTH_SHORT).show()
        }

        // 色温选择点击
        cardWhite.setOnClickListener {
            selectColorCard(0)
            currentColorHex = colorMap[0] ?: "#FFFFFFFF"
            currentColorLevel = 0
            updatePreview()
            Toast.makeText(this, "已选择纯白光", Toast.LENGTH_SHORT).show()
        }

        cardWarm.setOnClickListener {
            selectColorCard(1)
            currentColorHex = colorMap[1] ?: "#FFFFF8DC"
            currentColorLevel = 1
            updatePreview()
            Toast.makeText(this, "已选择暖白光", Toast.LENGTH_SHORT).show()
        }

        cardCold.setOnClickListener {
            selectColorCard(2)
            currentColorHex = colorMap[2] ?: "#FFF0F8FF"
            currentColorLevel = 2
            updatePreview()
            Toast.makeText(this, "已选择冷白光", Toast.LENGTH_SHORT).show()
        }

            // 【改造核心】开启补光按钮：改用 TouchListener 实现持续缩小回弹效果
        btnStart.setOnTouchListener { view, event ->
            // 实时计算手指是否在按钮范围内
            val isInside = event.x >= 0 && event.x <= view.width && event.y >= 0 && event.y <= view.height
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 按下：缩小
                    view.animate()
                        .scaleX(0.92f)
                        .scaleY(0.92f)
                        .setDuration(100)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val targetScale = if (isInside) 0.9f else 1.0f
                    view.animate()
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .setDuration(100)
                        .start()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (event.action == MotionEvent.ACTION_UP) {
                        view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(200)
                            .setInterpolator(OvershootInterpolator())
                            .start()

                        // 核心修复：只有在抬起且在范围内时才触发
                        if (isInside) {
                            view.feedback()
                            view.feedback()
                            // 执行原本的开启逻辑
                            startTime = System.currentTimeMillis()
                            getSharedPreferences("timer_prefs", MODE_PRIVATE)
                                .edit()
                                .putLong("timer_start_time_${totalTimeMinutes}", startTime)
                                .apply()

                            startTimer()
                            TimeRecorder.startRecording(this, "screen_light")

                            val intent = Intent(this, ScreenLightActiveActivity::class.java).apply {
                                putExtra("brightnessLevel", currentBrightnessLevel)
                                putExtra("colorLevel", currentColorLevel)
                                putExtra("colorHex", getCurrentMixedColor())
                            }
                            screenLightLauncher.launch(intent)
                        }
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
    }
    private fun loadAutoBrightnessState() {
        slidingAutoBrightness.setCheckedSilently(AutoBrightnessManager.getAutoBrightnessState(this))
    }


    private fun setupAutoBrightnessListener() {
        slidingAutoBrightness.setOnStateChangedListener { isChecked ->
            // 1. 振动反馈
            VibrationManager.setVibrationEnabled(this, isChecked)

            // 2. 声音反馈
            SoundManager.setSoundEnabled(this, isChecked)

            // 3. 自动亮度逻辑

            AutoBrightnessManager.toggleAutoBrightness(
                activity = this,
                targetState = isChecked,
                onSuccess = { /* 状态已由 UI 改变，无需操作 */ },
                onFailure = {
                    slidingAutoBrightness.setCheckedSilently(!isChecked)
                }
            )
        }
    }
    private fun updateFromActiveActivity(brightness: Int, color: Int) {
        // ✅ 更新亮度（只影响当前，不保存到 Preference）
        currentBrightnessLevel = brightness
        currentBrightnessValue = brightnessMap[brightness] ?: 70

        // ✅ 更新色温
        currentColorLevel = color
        currentColorHex = colorMap[color] ?: "#FFFFFFFF"

        // ✅ 更新 UI
        selectBrightnessCard(brightness)
        selectColorCard(color)
        updatePreview()

        println("📥 从 ActiveActivity 接收: brightness=$brightness, color=$color")
    }
    private fun checkExistingTimer() {
        val prefs = getSharedPreferences("timer_prefs", MODE_PRIVATE)
        val savedStartTime = prefs.getLong("timer_start_time_${totalTimeMinutes}", 0)

        if (savedStartTime > 0) {
            val elapsed = System.currentTimeMillis() - savedStartTime
            if (elapsed >= totalTimeMinutes * 60 * 1000) {
                prefs.edit().remove("timer_start_time_${totalTimeMinutes}").apply()
                startTime = 0L
            } else {
                startTime = savedStartTime
                startTimer()
            }
        }
    }

    private fun startTimer() {
        if (totalTimeMinutes <= 0 || startTime == 0L) return
        stopTimer()
        timerRunnable = object : Runnable {
            override fun run() {
                try {
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed >= totalTimeMinutes * 60 * 1000) {
                        getSharedPreferences("timer_prefs", MODE_PRIVATE)
                            .edit()
                            .remove("timer_start_time_${totalTimeMinutes}")
                            .apply()
                        navigateToMain()
                        return
                    }
                    handler?.postDelayed(this, 1000)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        handler?.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let {
            handler?.removeCallbacks(it)
        }
        timerRunnable = null
    }

    private fun navigateToMain() {
        stopTimer()  // 停止计时器
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
    // ✅ 修复：处理权限申请后的返回结果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // 调用管理器来处理权限结果
        AutoBrightnessManager.handlePermissionResult(this, requestCode) { successState ->
            // 如果授权成功并自动应用了设置，同步 UI 状态
            slidingAutoBrightness.setCheckedSilently(successState)
        }
    }

    /**
     * 更新预览界面
     * 色温和亮度共同作用
     */
    private fun updatePreview() {
        // 1. 更新文字
        tvLightInfo.text = "${colorTextMap[currentColorLevel]}白光 - ${brightnessTextMap[currentBrightnessLevel]}亮度"

        // 2. 根据色温和亮度共同决定预览卡片的背景色
        val mixedColor = mixColorWithBrightness(currentColorHex, currentBrightnessValue)
        previewCard.setBackgroundColor(Color.parseColor(mixedColor))

        // 3. 创建带边框的 GradientDrawable
        previewCard.background = createCardBackground(mixedColor)

        ScreenTime.text = formatMinutes(totalTimeMinutes)
    }
    private fun createCardBackground(colorHex: String): GradientDrawable {
        val drawable = GradientDrawable()

        // 设置背景色
        drawable.setColor(Color.parseColor(colorHex))

        // 设置圆角
        drawable.cornerRadius = dpToPx(12).toFloat()

        // 设置边框（宽度和颜色）
        drawable.setStroke(dpToPx(2), Color.parseColor("#374151"))

        return drawable
    }
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
    private fun mixColorWithBrightness(colorHex: String, brightnessPercent: Int): String {
        // 将亮度从百分比转换为 0-255 的值
        val brightness = (brightnessPercent * 2.55).toInt()  // 40% -> 102, 70% -> 178, 100% -> 255

        // 解析原始颜色
        val color = Color.parseColor(colorHex)
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)

        // 根据亮度调整RGB值
        val adjustedRed = red * brightness / 255
        val adjustedGreen = green * brightness / 255
        val adjustedBlue = blue * brightness / 255

        // 返回新的颜色
        return String.format("#%02X%02X%02X", adjustedRed, adjustedGreen, adjustedBlue)
    }

    /**
     * 获取当前混合后的颜色
     */
    private fun getCurrentMixedColor(): String {
        return mixColorWithBrightness(currentColorHex, currentBrightnessValue)
    }


    private fun selectBrightnessCard(level: Int) {
        // 重置所有卡片样式
        cardLow.isSelected = false
        cardMedium.isSelected = false
        cardHigh.isSelected = false

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

    private fun selectColorCard(color: Int) {
        // 重置所有卡片样式
        cardWhite.isSelected = false
        cardWarm.isSelected = false
        cardCold.isSelected = false

        // 设置选中卡片
        when (color) {
            0 -> cardWhite.isSelected = true
            1 -> cardWarm.isSelected = true
            2 -> cardCold.isSelected = true
        }
    }
    private fun getAutoOffTime(): Int {
        // ✅ 直接从 SharedPreferences 读取
        val prefs = getSharedPreferences("auto_off_settings", Context.MODE_PRIVATE)
        return prefs.getInt(AutomaticActivity.KEY_SCREEN_LIGHT_TIME, 5)
    }
    private fun formatMinutes(minutes: Int): String {
        return if (minutes >= 60) {
            "${minutes / 60}小时${minutes % 60}分钟"
        } else {
            "${minutes}分钟"
        }
    }
}