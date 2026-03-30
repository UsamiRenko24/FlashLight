package com.name.FlashLight

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.name.FlashLight.databinding.ScreenBinding
import com.name.FlashLight.utils.PageConstants
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import utils.AutoBrightnessManager
import utils.SoundManager
import utils.VibrationManager
import utils.feedback

class ScreenLightActivity : BaseActivity<ScreenBinding>() {

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

    override fun createBinding(): ScreenBinding {
        return ScreenBinding.inflate(layoutInflater)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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


    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        binding.traceback.setOnClickListener { handleBackPress() }
        binding.ivSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        binding.cardLeft.setOnClickListener { selectBrightnessCard(0); currentBrightnessLevel = 0; currentBrightnessValue = 40; updatePreview() }
        binding.cardMiddle.setOnClickListener { selectBrightnessCard(1); currentBrightnessLevel = 1; currentBrightnessValue = 70; updatePreview() }
        binding.cardRight.setOnClickListener { selectBrightnessCard(2); currentBrightnessLevel = 2; currentBrightnessValue = 100; updatePreview() }

        binding.cardLeft1.setOnClickListener { selectColorCard(0); currentColorHex = colorMap[0]!!; currentColorLevel = 0; updatePreview() }
        binding.cardMiddle1.setOnClickListener { selectColorCard(1); currentColorHex = colorMap[1]!!; currentColorLevel = 1; updatePreview() }
        binding.cardRight1.setOnClickListener { selectColorCard(2); currentColorHex = colorMap[2]!!; currentColorLevel = 2; updatePreview() }

        binding.card2.setOnTouchListener { view, event ->
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
        binding.tvLightInfo.text = "${colorTextMap[currentColorLevel]} ${getString(R.string.brightness)}"
        binding.card1.background = GradientDrawable().apply {
            setColor(Color.parseColor(getCurrentMixedColor()))
            cornerRadius = dpToPx(12).toFloat()
            setStroke(dpToPx(2), Color.parseColor("#374151"))
        }
        if (getAutoOffTime() >= 114514) {
            binding.tvScreenTime.text = getString(R.string.auto_off_never)
        } else {
            binding.tvScreenTime.text = formatMinutes(getAutoOffTime())
        }
    }

    private fun getCurrentMixedColor(): String {
        val brightness = (currentBrightnessValue * 2.55).toInt()
        val color = Color.parseColor(currentColorHex)
        return String.format("#%02X%02X%02X", Color.red(color) * brightness / 255, Color.green(color) * brightness / 255, Color.blue(color) * brightness / 255)
    }

    private fun selectBrightnessCard(level: Int) {
        val cards = listOf(binding.cardLeft, binding.cardMiddle, binding.cardRight)
        cards.forEachIndexed { index, card ->
            if (index == level) {
                card.isSelected = true
                card.setBackgroundResource(R.drawable.bg_rounded_selector)
                (card.getChildAt(0) as TextView).setTextColor(selectedBlueColor)
            } else {
                card.isSelected = false
                card.setBackgroundResource(R.drawable.bg_rounded_selector)
                (card.getChildAt(0) as TextView).setTextColor(Color.WHITE)
            }
        }
    }

    private fun selectColorCard(color: Int) {
        binding.cardLeft1.isSelected = false; binding.cardMiddle1.isSelected = false; binding.cardRight1.isSelected = false
        val tvWhite = binding.cardLeft1.getChildAt(1) as TextView
        val tvWarm = binding.cardMiddle1.getChildAt(1) as TextView
        val tvCold = binding.cardRight1.getChildAt(1) as TextView
        listOf(tvWhite, tvWarm, tvCold).forEach { it.setTextColor(Color.WHITE) }
        when(color) { 
            0 -> { binding.cardLeft1.isSelected = true; tvWhite.setTextColor(selectedBlueColor) }
            1 -> { binding.cardMiddle1.isSelected = true; tvWarm.setTextColor(selectedBlueColor) }
            2 -> { binding.cardRight1.isSelected = true; tvCold.setTextColor(selectedBlueColor) }
        }
    }

    private fun getAutoOffTime() = getSharedPreferences("auto_off_settings", MODE_PRIVATE).getInt(AutomaticActivity.KEY_SCREEN_LIGHT_TIME, 5)
    private fun formatMinutes(minutes: Int) = if (minutes >= 60) "${minutes / 60}${getString(R.string.hour)}${minutes % 60}${getString(R.string.minute)}" else "$minutes${getString(R.string.minute)}"
    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
    private fun loadAutoBrightnessState() = binding.slidingAutoBrightness.setCheckedSilently(AutoBrightnessManager.getAutoBrightnessState(this))
    private fun setupAutoBrightnessListener() { binding.slidingAutoBrightness.setOnStateChangedListener { isChecked -> VibrationManager.setVibrationEnabled(this, isChecked); SoundManager.setSoundEnabled(this, isChecked); AutoBrightnessManager.toggleAutoBrightness(this, isChecked, {}, { binding.slidingAutoBrightness.setCheckedSilently(!isChecked) }) } }
}