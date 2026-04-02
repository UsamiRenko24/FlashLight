package com.name.FlashLight

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.name.FlashLight.databinding.ScreenBinding
import com.name.FlashLight.utils.PageConstants
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import utils.*

class ScreenLightActivity : BaseActivity<ScreenBinding>() {

    private var localBrightnessLevel = 1  
    private var currentBrightnessValue = 70
    private var localColorLevel = 0
    private var currentColorHex = "#FFFFFFFF"
    private val selectedBlueColor = Color.parseColor("#4786EF")

    private val colorMap = mapOf(0 to "#FFFFFFFF", 1 to "#FFFFF8DC", 2 to "#FFF0F8FF")
    private val brightnessMap = mapOf(0 to 40, 1 to 70, 2 to 100)

    private lateinit var brightnessTextMap: Map<Int, String>
    private lateinit var colorTextMap: Map<Int, String>

    override fun createBinding(): ScreenBinding = ScreenBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        initResources()
        PageUsageRecorder.recordPageVisit(this, PageConstants.PAGE_SCREEN_LIGHT)
        StartupModeManager.recordLastPage(this, PageConstants.PAGE_SCREEN_LIGHT)
        
        // 【核心修复】：持续监听内存仓库的变化，确保无论在哪个页面改，这里都能实时同步
        observeSessionChanges()
        
        lifecycleScope.launch {
            // 1. 初始化：如果内存仓库没数据，先同步一次 DataStore 默认值
            if (savedInstanceState == null) {
                val defaultBrightness = DataStoreManager.getDefaultBrightness(this@ScreenLightActivity).first()

                // 强制更新内存仓库，这会触发 observeSessionChanges 里的 Flow，从而刷新 UI
                ScreenSessionRepository.updateBrightness(defaultBrightness)
                ScreenSessionRepository.updateColor(localColorLevel)
            }
            
            // 2. 加载其余监听逻辑
            setupClickListeners()
            observeGlobalSettings()
        }

        SoundManager.initSoundPool(this)
    }

    /**
     * 响应式同步：关键在于这里！
     * 只要 ScreenSessionRepository 变了，这里会立刻刷新 UI
     */
    private fun observeSessionChanges() {
        // 监听亮度
        lifecycleScope.launch {
            ScreenSessionRepository.brightnessLevel.collectLatest { level ->
                if (level != -1) {
                    localBrightnessLevel = level
                    currentBrightnessValue = brightnessMap[level] ?: 70
                    selectBrightnessCard(level)
                    updatePreview()
                }
            }
        }
        // 监听颜色
        lifecycleScope.launch {
            ScreenSessionRepository.colorLevel.collectLatest { level ->
                if (level != -1) {
                    localColorLevel = level
                    currentColorHex = colorMap[level] ?: "#FFFFFFFF"
                    selectColorCard(level)
                    updatePreview()
                }
            }
        }
    }

    private fun initResources() {
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
    }

    private fun observeGlobalSettings() {
        lifecycleScope.launch {
            AutoBrightnessManager.getAutoBrightnessFlow(this@ScreenLightActivity).collectLatest { isEnabled ->
                binding.slidingAutoBrightness.setCheckedSilently(isEnabled)
            }
        }
        lifecycleScope.launch {
            DataStoreManager.getScreenAutoOffTime(this@ScreenLightActivity).collectLatest { minutes ->
                binding.tvScreenTime.text = if (minutes >= 114514) getString(R.string.auto_off_never) else minutes.toFloat().toDetailedTime(this@ScreenLightActivity)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        binding.traceback.setOnClickListener { handleBackPress() }
        binding.ivSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        // 修改：点击只负责更新仓库，联动逻辑由 observeSessionChanges 处理
        binding.cardLeft.setOnClickListener { ScreenSessionRepository.updateBrightness(0) }
        binding.cardMiddle.setOnClickListener { ScreenSessionRepository.updateBrightness(1) }
        binding.cardRight.setOnClickListener { ScreenSessionRepository.updateBrightness(2) }

        binding.cardLeft1.setOnClickListener { ScreenSessionRepository.updateColor(0) }
        binding.cardMiddle1.setOnClickListener { ScreenSessionRepository.updateColor(1) }
        binding.cardRight1.setOnClickListener { ScreenSessionRepository.updateColor(2) }

        binding.slidingAutoBrightness.setOnStateChangedListener { isEnabled ->
            binding.slidingAutoBrightness.feedback()
            AutoBrightnessManager.toggleAutoBrightness(this, isEnabled, {}, {
                binding.slidingAutoBrightness.setCheckedSilently(!isEnabled)
            })
        }

        binding.card2.setOnTouchListener { view, event ->
            val isInside = event.x >= 0 && event.x <= view.width && event.y >= 0 && event.y <= view.height
            if (event.action == MotionEvent.ACTION_UP && isInside) {
                view.feedback()
                // 无需再传 Intent 参，因为大家共用 ScreenSessionRepository
                startActivity(Intent(this, ScreenLightActiveActivity::class.java))
            }
            handleTouchEffect(view, event)
        }
    }

    private fun handleTouchEffect(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).setInterpolator(OvershootInterpolator()).start()
        }
        return true
    }

    private fun updatePreview() {
        if (!::colorTextMap.isInitialized) return
        binding.tvLightInfo.text = "${colorTextMap[localColorLevel]} ${getString(R.string.brightness)}"
        binding.card1.background = GradientDrawable().apply {
            val brightness = (currentBrightnessValue * 2.55).toInt()
            val color = Color.parseColor(currentColorHex)
            val mixedColor = String.format("#%02X%02X%02X", 
                Color.red(color) * brightness / 255, 
                Color.green(color) * brightness / 255, 
                Color.blue(color) * brightness / 255)
            setColor(Color.parseColor(mixedColor))
            cornerRadius = (12 * resources.displayMetrics.density)
            setStroke((2 * resources.displayMetrics.density).toInt(), Color.parseColor("#374151"))
        }
    }

    private fun selectBrightnessCard(level: Int) {
        val cards = listOf(binding.cardLeft, binding.cardMiddle, binding.cardRight)
        cards.forEachIndexed { index, card ->
            val isSelected = index == level
            card.isSelected = isSelected
            card.setBackgroundResource(R.drawable.bg_rounded_selector)
            (card.getChildAt(0) as? TextView)?.setTextColor(if (isSelected) selectedBlueColor else Color.WHITE)
        }
    }

    private fun selectColorCard(level: Int) {
        val colorCards = listOf(binding.cardLeft1, binding.cardMiddle1, binding.cardRight1)
        colorCards.forEachIndexed { index, card ->
            val isSelected = index == level
            card.isSelected = isSelected
            (card.getChildAt(1) as? TextView)?.setTextColor(if (isSelected) selectedBlueColor else Color.WHITE)
        }
    }
}