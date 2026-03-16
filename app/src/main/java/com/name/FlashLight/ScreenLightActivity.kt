package com.name.FlashLight

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.name.FlashLight.utils.PageConstants
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import utils.AutoBrightnessManager
import utils.feedback

class ScreenLightActivity : BaseActivity() {

    // 顶部控件
    private lateinit var ivTraceback: ImageView
    private lateinit var ivSettings: ImageView

    private lateinit var tvTitle: TextView

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
    private var currentBrightnessValue = 70  // 亮度百分比 40%,70%,100%

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen)
        initViews()
        PageUsageRecorder.recordPageVisit(this, PageConstants.PAGE_SCREEN_LIGHT)
        StartupModeManager.recordLastPage(this, PageConstants.PAGE_SCREEN_LIGHT)

        setupClickListeners()

        // 默认选中中亮度和纯白光
        selectBrightnessCard(getSharedPreferences("brightness_settings", Context.MODE_PRIVATE).getInt("default_brightness", 1))
        selectColorCard(0)
        updatePreview()  // 更新预览
        loadAutoBrightnessState()
        setupAutoBrightnessListener()
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
        slidingAutoBrightness = findViewById(R.id.btn_switch)
    }

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

        // 开启补光按钮
        btnStart.setOnClickListener {
            it.feedback()
            // 获取当前混合后的颜色
            val mixedColor = getCurrentMixedColor()

            // 创建Intent跳转到补光效果页面
            val intent = Intent(this, ScreenLightActiveActivity::class.java)

            // 传递所有必要的数据
            intent.putExtra("colorHex", mixedColor)
            intent.putExtra("brightnessLevel", currentBrightnessLevel)
            intent.putExtra("colorLevel", currentColorLevel)

            // 启动Activity
            startActivity(intent)

        }
    }
    private fun loadAutoBrightnessState() {
        val isAuto = AutoBrightnessManager.getAutoBrightnessState(this)
        slidingAutoBrightness.setCheckedSilently(isAuto)
    }

    private fun setupAutoBrightnessListener() {
        slidingAutoBrightness.setOnStateChangedListener { isChecked ->
            AutoBrightnessManager.toggleAutoBrightness(
                activity = this,
                targetState = isChecked,
                onSuccess = { /* 状态已由 UI 改变，无需操作 */ },
                onFailure = {
                    // 权限失败或设置失败，静默回滚 UI
                    slidingAutoBrightness.setCheckedSilently(!isChecked)
                }
            )
        }
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
        tvLightInfo.text = "${colorTextMap[currentColorLevel]}白色 - ${brightnessTextMap[currentBrightnessLevel]}亮度"

        // 2. 根据色温和亮度共同决定预览卡片的背景色
        val mixedColor = mixColorWithBrightness(currentColorHex, currentBrightnessValue)
        previewCard.setBackgroundColor(Color.parseColor(mixedColor))

        ScreenTime.text = formatMinutes(getAutoOffTime())
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