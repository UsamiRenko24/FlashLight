package com.name.FlashLight

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.name.FlashLight.utils.PageConstants
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import utils.AutoBrightnessManager
import utils.SoundManager
import utils.VibrationManager
import utils.feedback

class ScreenLightActivity : BaseActivity() {

    private lateinit var ivTraceback: ImageView
    private lateinit var ivSettings: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvLightInfo: TextView  
    private lateinit var previewCard: LinearLayout 
    private lateinit var cardLow: LinearLayout
    private lateinit var cardMedium: LinearLayout
    private lateinit var cardHigh: LinearLayout
    private lateinit var tvLow: TextView
    private lateinit var tvMedium: TextView
    private lateinit var tvHigh: TextView
    private lateinit var cardWhite: LinearLayout  
    private lateinit var cardWarm: LinearLayout   
    private lateinit var cardCold: LinearLayout   
    private lateinit var btnStart: Button
    private lateinit var ScreenTime: TextView
    private lateinit var slidingAutoBrightness: SlidingButton

    private var currentBrightnessLevel = 1  
    private var currentBrightnessValue = 70
    private var currentColorHex = "#FFFFFFFF"  
    private var currentColorLevel = 0  
    private val selectedBlueColor = Color.parseColor("#4786EF")

    private lateinit var brightnessTextMap: Map<Int, String>
    private lateinit var colorTextMap: Map<Int, String>

    private val colorMap = mapOf(0 to "#FFFFFFFF", 1 to "#FFFFF8DC", 2 to "#FFF0F8FF")
    private val brightnessMap = mapOf(0 to 40, 1 to 70, 2 to 100)

    private val screenLightLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                val newBrightness = data.getIntExtra("brightnessLevel", -1)
                val newColor = data.getIntExtra("colorLevel", -1)
                if (newBrightness != -1 && newColor != -1) updateFromActiveActivity(newBrightness, newColor)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen)
        
        // 关键修复：在资源环境就绪后初始化映射表
        brightnessTextMap = mapOf(
            0 to getString(R.string.brightness_card_low),
            1 to getString(R.string.brightness_card_medium),
            2 to getString(R.string.brightness_card_high)
        )
        colorTextMap = mapOf(
            0 to getString(R.string.color_pure),
            1 to getString(R.string.color_warm),
            2 to getString(R.string.color_cold)
        )

        initViews()
        PageUsageRecorder.recordPageVisit(this, PageConstants.PAGE_SCREEN_LIGHT)
        StartupModeManager.recordLastPage(this, PageConstants.PAGE_SCREEN_LIGHT)
        setupClickListeners()

        val savedLevel = getSharedPreferences("brightness_settings", Context.MODE_PRIVATE).getInt("default_brightness", 1)
        currentBrightnessValue = brightnessMap[savedLevel] ?: 70
        currentBrightnessLevel = savedLevel
        selectBrightnessCard(savedLevel)
        selectColorCard(0)

        SoundManager.initSoundPool(this)
        updatePreview()
        loadAutoBrightnessState()
        setupAutoBrightnessListener()
    }

    private fun initViews() {
        ivTraceback = findViewById(R.id.traceback)
        ivSettings = findViewById(R.id.iv_settings)
        tvTitle = findViewById(R.id.tv_title)
        previewCard = findViewById(R.id.card1)
        tvLightInfo = findViewById(R.id.tv_light_info)
        cardLow = findViewById(R.id.card_left)
        cardMedium = findViewById(R.id.card_middle)
        cardHigh = findViewById(R.id.card_right)
        tvLow = cardLow.getChildAt(0) as TextView
        tvMedium = cardMedium.getChildAt(0) as TextView
        tvHigh = cardHigh.getChildAt(0) as TextView
        cardWhite = findViewById(R.id.card_left1)
        cardWarm = findViewById(R.id.card_middle1)
        cardCold = findViewById(R.id.card_right1)
        btnStart = findViewById(R.id.card2)
        ScreenTime = findViewById(R.id.tv_screen_time)
        slidingAutoBrightness = findViewById(R.id.sliding_auto_brightness)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        ivTraceback.setOnClickListener { handleBackPress() }
        ivSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        cardLow.setOnClickListener { selectBrightnessCard(0); currentBrightnessLevel = 0; currentBrightnessValue = 40; updatePreview() }
        cardMedium.setOnClickListener { selectBrightnessCard(1); currentBrightnessLevel = 1; currentBrightnessValue = 70; updatePreview() }
        cardHigh.setOnClickListener { selectBrightnessCard(2); currentBrightnessLevel = 2; currentBrightnessValue = 100; updatePreview() }

        cardWhite.setOnClickListener { selectColorCard(0); currentColorHex = colorMap[0]!!; currentColorLevel = 0; updatePreview() }
        cardWarm.setOnClickListener { selectColorCard(1); currentColorHex = colorMap[1]!!; currentColorLevel = 1; updatePreview() }
        cardCold.setOnClickListener { selectColorCard(2); currentColorHex = colorMap[2]!!; currentColorLevel = 2; updatePreview() }

        btnStart.setOnTouchListener { view, event ->
            val isInside = event.x >= 0 && event.x <= view.width && event.y >= 0 && event.y <= view.height
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start(); true }
                MotionEvent.ACTION_MOVE -> { val scale = if (isInside) 0.92f else 1.0f; view.animate().scaleX(scale).scaleY(scale).setDuration(100).start(); true }
                MotionEvent.ACTION_UP -> {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).setInterpolator(OvershootInterpolator()).start()
                    if (isInside) {
                        view.feedback()
                        val intent = Intent(this, ScreenLightActiveActivity::class.java).apply {
                            putExtra("brightnessLevel", currentBrightnessLevel)
                            putExtra("colorLevel", currentColorLevel)
                            putExtra("colorHex", getCurrentMixedColor())
                        }
                        screenLightLauncher.launch(intent)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> { view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start(); true }
                else -> false
            }
        }
    }

    private fun updateFromActiveActivity(brightness: Int, color: Int) {
        currentBrightnessLevel = brightness
        currentBrightnessValue = brightnessMap[brightness] ?: 70
        currentColorLevel = color
        currentColorHex = colorMap[color] ?: "#FFFFFFFF"
        selectBrightnessCard(brightness); selectColorCard(color); updatePreview()
    }

    private fun updatePreview() {
        tvLightInfo.text = "${colorTextMap[currentColorLevel]}${getString(R.string.brightness)}"
        previewCard.background = GradientDrawable().apply {
            setColor(Color.parseColor(getCurrentMixedColor()))
            cornerRadius = dpToPx(12).toFloat()
            setStroke(dpToPx(2), Color.parseColor("#374151"))
        }
        if (getAutoOffTime() >= 114514) {
            ScreenTime.text = getString(R.string.auto_off_never)
        } else {
            ScreenTime.text = formatMinutes(getAutoOffTime())
        }
    }

    private fun getCurrentMixedColor(): String {
        val brightness = (currentBrightnessValue * 2.55).toInt()
        val color = Color.parseColor(currentColorHex)
        return String.format("#%02X%02X%02X", Color.red(color) * brightness / 255, Color.green(color) * brightness / 255, Color.blue(color) * brightness / 255)
    }

    private fun selectBrightnessCard(level: Int) {
        cardLow.isSelected = false; cardMedium.isSelected = false; cardHigh.isSelected = false
        tvLow.setTextColor(Color.WHITE); tvMedium.setTextColor(Color.WHITE); tvHigh.setTextColor(Color.WHITE)
        when(level) { 
            0 -> { cardLow.isSelected = true; tvLow.setTextColor(selectedBlueColor) }
            1 -> { cardMedium.isSelected = true; tvMedium.setTextColor(selectedBlueColor) }
            2 -> { cardHigh.isSelected = true; tvHigh.setTextColor(selectedBlueColor) }
        }
    }

    private fun selectColorCard(color: Int) {
        cardWhite.isSelected = false; cardWarm.isSelected = false; cardCold.isSelected = false
        val tvWhite = cardWhite.getChildAt(1) as TextView
        val tvWarm = cardWarm.getChildAt(1) as TextView
        val tvCold = cardCold.getChildAt(1) as TextView
        listOf(tvWhite, tvWarm, tvCold).forEach { it.setTextColor(Color.WHITE) }
        when(color) { 
            0 -> { cardWhite.isSelected = true; tvWhite.setTextColor(selectedBlueColor) }
            1 -> { cardWarm.isSelected = true; tvWarm.setTextColor(selectedBlueColor) }
            2 -> { cardCold.isSelected = true; tvCold.setTextColor(selectedBlueColor) }
        }
    }

    private fun getAutoOffTime() = getSharedPreferences("auto_off_settings", MODE_PRIVATE).getInt(AutomaticActivity.KEY_SCREEN_LIGHT_TIME, 5)
    private fun formatMinutes(minutes: Int) = if (minutes >= 60) "${minutes / 60}${getString(R.string.hour)}${minutes % 60}${getString(R.string.minute)}" else "$minutes${getString(R.string.minute)}"
    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
    private fun loadAutoBrightnessState() = slidingAutoBrightness.setCheckedSilently(AutoBrightnessManager.getAutoBrightnessState(this))
    private fun setupAutoBrightnessListener() { slidingAutoBrightness.setOnStateChangedListener { isChecked -> VibrationManager.setVibrationEnabled(this, isChecked); SoundManager.setSoundEnabled(this, isChecked); AutoBrightnessManager.toggleAutoBrightness(this, isChecked, {}, { slidingAutoBrightness.setCheckedSilently(!isChecked) }) } }
}