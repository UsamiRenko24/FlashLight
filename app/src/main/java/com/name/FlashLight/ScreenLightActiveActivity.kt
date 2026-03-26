package com.name.FlashLight

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import utils.TimeRecorder

class ScreenLightActiveActivity : BaseActivity() {

    // 主背景
    private lateinit var mainPage: ConstraintLayout

    // 顶部卡片
    private lateinit var card: LinearLayout
    private lateinit var tvTitle: TextView

    // 底部主控制卡片
    private lateinit var card1: LinearLayout
    private lateinit var sun: ImageView
    private lateinit var palette: ImageView
    private lateinit var settings: ImageView
    private lateinit var close: ImageView

    // 亮度选择卡片（三个太阳）
    private lateinit var card2: LinearLayout
    private lateinit var sun1: ImageView
    private lateinit var sun2: ImageView
    private lateinit var sun3: ImageView

    // 色温选择卡片（三个颜色）
    private lateinit var card3: LinearLayout
    private lateinit var color1: ImageView
    private lateinit var color2: ImageView
    private lateinit var color3: ImageView

    // 当前选中的状态
    private var selectedBrightness = 1  // 0=低, 1=中, 2=高
    private var selectedColor = 0       // 0=纯白, 1=暖白, 2=冷白
    private var currentColorHex = "#FFFFFFFF"  // 当前颜色值
    private var currentBrightnessValue = 70    // 当前亮度值

    private var lastClickTime: Long = 0
    private val DOUBLE_CLICK_TIME = 300

    private var handler: Handler? = null
    private var timerRunnable: Runnable? = null
    private var startTime = 0L
    private var totalTimeMinutes: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_light)

        // 1. 【核心修复】首先初始化 Handler 和 获取自动关闭时长
        handler = Handler(Looper.getMainLooper())
        totalTimeMinutes = getAutoOffTime()

        // 2. 接收 Intent 参数
        receiveIntentData()

        // 3. 数据就绪后，再启动计时和记录
        startTimer()
        TimeRecorder.startRecording(this, "screen_light")

        // 4. 初始化视图和点击监听
        initViews()
        setupInitialState()
        setupClickListeners()

        // 5. 应用初始设置
        applyInitialSettings()

        setupBackPressedCallback()
    }
    /**
     * 接收从ScreenLightActivity传递过来的参数
     */
    private fun receiveIntentData() {
        // 获取传递过来的数据，如果没有传递则使用默认值
        selectedBrightness = intent.getIntExtra("brightnessLevel", 1)
        selectedColor = intent.getIntExtra("colorLevel", 0)
        currentColorHex = intent.getStringExtra("colorHex") ?: "#FFFFFFFF"
        currentBrightnessValue = when (selectedBrightness) {
            0 -> 40
            1 -> 70
            2 -> 100
            else -> 70
        }

    }

    /**
     * 应用接收到的设置初始化界面
     */
    private fun applyInitialSettings() {
        // 1. 根据色温和亮度设置背景颜色
        updateBackgroundColor()

        // 2. 更新顶部标题文字
        updateTitleText()

        // 3. 更新选中状态（太阳和颜色的高亮）
        updateSunSelection()
        updateColorSelection()
    }

    private fun initViews() {
        // 主背景
        mainPage = findViewById(R.id.mainPage)

        // 顶部卡片
        card = findViewById(R.id.card)
        tvTitle = findViewById(R.id.tv_title)

        // 底部主控制卡片
        card1 = findViewById(R.id.card1)
        sun = findViewById(R.id.sun)
        palette = findViewById(R.id.palette)
        settings = findViewById(R.id.settings)
        close = findViewById(R.id.close)

        // 亮度选择卡片
        card2 = findViewById(R.id.card2)
        sun1 = findViewById(R.id.sun1)
        sun2 = findViewById(R.id.sun2)
        sun3 = findViewById(R.id.sun3)

        // 色温选择卡片
        card3 = findViewById(R.id.card3)
        color1 = findViewById(R.id.color1)
        color2 = findViewById(R.id.color2)
        color3 = findViewById(R.id.color3)
    }

    private fun setupInitialState() {
        // 开始时隐藏所有需要隐藏的组件
        card2.visibility = View.GONE
        card3.visibility = View.GONE
    }

    private fun setupClickListeners() {
        // 点击太阳图标 - 显示亮度选择卡片
        sun.setOnClickListener {
            toggleCard2()
        }

        // 点击调色板图标 - 显示色温选择卡片
        palette.setOnClickListener {
            toggleCard3()
        }

        // 点击设置图标
        settings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 点击关闭图标 - 返回上一页
        close.setOnClickListener {
            returnWithSettings()
            finish()
        }

        // 亮度选择
        sun1.setOnClickListener {
            selectBrightness(0)
//            Toast.makeText(this, "已选择低亮度", Toast.LENGTH_SHORT).show()
        }

        sun2.setOnClickListener {
            selectBrightness(1)
//            Toast.makeText(this, "已选择中亮度", Toast.LENGTH_SHORT).show()
        }

        sun3.setOnClickListener {
            selectBrightness(2)
//            Toast.makeText(this, "已选择高亮度", Toast.LENGTH_SHORT).show()
        }

        // 色温选择
        color1.setOnClickListener {
            selectColor(0)
//            Toast.makeText(this, "已选择纯白光", Toast.LENGTH_SHORT).show()
        }

        color2.setOnClickListener {
            selectColor(1)
//            Toast.makeText(this, "已选择暖白光", Toast.LENGTH_SHORT).show()
        }

        color3.setOnClickListener {
            selectColor(2)
//            Toast.makeText(this, "已选择冷白光", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleCard2() {
        if (card2.visibility == View.GONE) {
            card2.visibility = View.VISIBLE
            card3.visibility = View.GONE
            updateSunSelection()
        } else {
            card2.visibility = View.GONE
        }
    }

    private fun toggleCard3() {
        if (card3.visibility == View.GONE) {
            card3.visibility = View.VISIBLE
            card2.visibility = View.GONE
            updateColorSelection()
        } else {
            card3.visibility = View.GONE
        }
    }

    private fun selectBrightness(level: Int) {
        selectedBrightness = level
        currentBrightnessValue = when (level) {
            0 -> 40
            1 -> 70
            2 -> 100
            else -> 70
        }
        updateSunSelection()
        updateTitleText()
        updateBackgroundColor()  // 亮度变化时更新背景
    }

    private fun selectColor(level: Int) {
        selectedColor = level
        currentColorHex = when (level) {
            0 -> "#FFFFFFFF"  // 纯白
            1 -> "#FFFFF8DC"  // 暖白
            2 -> "#FFF0F8FF"  // 冷白
            else -> "#FFFFFFFF"
        }
        updateColorSelection()
        updateTitleText()
        updateBackgroundColor()  // 色温变化时更新背景
    }

    /**
     * 更新背景颜色 - 根据色温和亮度混合
     */
    private fun updateBackgroundColor() {
        val mixedColor = mixColorWithBrightness(currentColorHex, currentBrightnessValue)
        mainPage.setBackgroundColor(Color.parseColor(mixedColor))
    }

    /**
     * 将颜色与亮度混合
     */
    private fun mixColorWithBrightness(colorHex: String, brightnessPercent: Int): String {
        val brightness = (brightnessPercent * 2.55).toInt()
        val color = Color.parseColor(colorHex)
        val red = Color.red(color) * brightness / 255
        val green = Color.green(color) * brightness / 255
        val blue = Color.blue(color) * brightness / 255
        return String.format("#%02X%02X%02X", red, green, blue)
    }

    private fun updateSunSelection() {
        sun1.alpha = 0.5f
        sun2.alpha = 0.5f
        sun3.alpha = 0.5f

        when (selectedBrightness) {
            0 -> sun1.alpha = 1.0f
            1 -> sun2.alpha = 1.0f
            2 -> sun3.alpha = 1.0f
        }
    }

    private fun updateColorSelection() {
        color1.alpha = 0.5f
        color2.alpha = 0.5f
        color3.alpha = 0.5f

        when (selectedColor) {
            0 -> color1.alpha = 1.0f
            1 -> color2.alpha = 1.0f
            2 -> color3.alpha = 1.0f
        }
    }

    private fun updateTitleText() {
        val brightnessText = when (selectedBrightness) {
            0 -> getString(R.string.brightness_low)
            1 -> getString(R.string.brightness_medium)
            2 -> getString(R.string.brightness_high)
            else -> getString(R.string.brightness_medium)
        }

        val colorText = when (selectedColor) {
            0 -> getString(R.string.color_pure)
            1 -> getString(R.string.color_warm)
            2 -> getString(R.string.color_cold)
            else -> getString(R.string.color_pure)
        }

        tvTitle.text = "$colorText - ${brightnessText}"
    }
    /**
     * 返回设置并退出
     */
    private fun startTimer() {
        // 如果设置为永不关闭 (114514) 或无效时间，则不启动倒计时任务
        if (totalTimeMinutes >= 114514 || totalTimeMinutes <= 0) return

        startTime = System.currentTimeMillis()
        // 保存开始时间供返回时校验
        getSharedPreferences("timer_prefs", MODE_PRIVATE).edit()
            .putLong("timer_start_time_${totalTimeMinutes}", startTime).apply()

        stopTimer()
        timerRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= totalTimeMinutes * 60 * 1000) {
                    navigateToMain()
                } else {
                    handler?.postDelayed(this, 1000)
                }
            }
        }
        handler?.post(timerRunnable!!)
    }

    private fun stopTimer() { timerRunnable?.let { handler?.removeCallbacks(it) }; timerRunnable = null }

    private fun navigateToMain() {
        stopTimer()
        startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
        finish()
    }
    private fun getAutoOffTime() = getSharedPreferences("auto_off_settings", MODE_PRIVATE).getInt(AutomaticActivity.KEY_SCREEN_LIGHT_TIME, 5)
        private fun returnWithSettings() {
        val resultIntent = Intent().apply {
            putExtra("brightnessLevel", selectedBrightness)
            putExtra("colorLevel", selectedColor)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    // 双击退出
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < DOUBLE_CLICK_TIME) {
                returnWithSettings()
                return true
            }
            lastClickTime = currentTime
        }
        return super.onTouchEvent(event)
    }

    private fun setupBackPressedCallback() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                returnWithSettings()
            }
        })
    }
    override fun onPause() {
        super.onPause()
        // 页面不可见时停止记录
        TimeRecorder.stopRecording(this, "screen_light")
    }

    override fun onDestroy() {
        super.onDestroy()
        // 确保退出时停止
        TimeRecorder.stopRecording(this, "screen_light")
        stopTimer()
    }
}