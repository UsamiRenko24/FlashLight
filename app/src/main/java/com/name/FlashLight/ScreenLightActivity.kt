package com.name.FlashLight

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.lifecycleScope
import com.name.FlashLight.databinding.ScreenBinding
import com.name.FlashLight.utils.PageConstants
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import utils.AutoBrightnessManager
import utils.BatteryRepository
import utils.DataStoreManager
import utils.ScreenSessionRepository
import utils.SoundManager
import utils.feedback
import utils.toDetailedTime

/**
 * 模块化重构后的屏幕补光主页
 * 职责：负责补光配置的展示、局部状态维护及页面联动
 */
class ScreenLightActivity : BaseActivity<ScreenBinding>() {

    // --- 1. 模块化配置 ---
    override val pageTrackName = PageConstants.PAGE_SCREEN_LIGHT
    override val isBatteryMonitorEnabled = false
    override val isLowBatteryCheckEnabled = true

    // --- 2. 映射表与常量 ---
    private val colorMap = mapOf(0 to "#FFFFFFFF", 1 to "#FFFFF8DC", 2 to "#FFF0F8FF")
    private val brightnessMap = mapOf(0 to 40, 1 to 70, 2 to 100)
    private val selectedBlueColor = Color.parseColor("#2AE1F8") // 已修改为：#2AE1F8
    private val selectedColor = Color.parseColor("#0E0E0E")

    private lateinit var colorTextMap: Map<Int, String>

    override fun createBinding(): ScreenBinding = ScreenBinding.inflate(layoutInflater)

    override fun onResume() {
        super.onResume()

        if (
            AutoBrightnessManager
                .getAutoBrightnessState(this)
        ) {

            val lp = window.attributes

            lp.screenBrightness =
                WindowManager.LayoutParams
                    .BRIGHTNESS_OVERRIDE_NONE

            window.attributes = lp
        }
    }
    /**
     * 职责模块 A: 初始化静态 UI 与资源
     */
    override fun initViews() {
        colorTextMap = mapOf(
            0 to getString(R.string.color_pure),
            1 to getString(R.string.color_warm),
            2 to getString(R.string.color_cold)
        )
        SoundManager.initSoundPool(this)
    }

    /**
     * 职责模块 B: 事件监听集中营
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun initListeners() {
        binding.traceback.setOnClickListener { handleBackPress() }
        binding.ivSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        // 滑块点击触发：只需更新仓库，UI 联动由 refreshBrightnessUI 处理
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

        binding.layoutAutoOff.setOnClickListener {
            startActivity(Intent(this, AutomaticActivity::class.java))
        }

        binding.card2.setOnTouchListener { view, event ->
            val isInside = event.x >= 0 && event.x <= view.width && event.y >= 0 && event.y <= view.height
            if (event.action == MotionEvent.ACTION_UP && isInside) {
                view.feedback()
                startActivity(Intent(this, ScreenLightActiveActivity::class.java))
            }
            handleTouchEffect(view, event)
            true
        }
    }

    /**
     * 职责模块 C: 响应式数据观察中心
     */
    override fun initObservers() {
        // 1. 初始化同步逻辑
        lifecycleScope.launch {
            if (ScreenSessionRepository.isUninitialized()) {
                val defB = DataStoreManager.getDefaultBrightness(this@ScreenLightActivity).first()
                ScreenSessionRepository.updateBrightness(defB)
                ScreenSessionRepository.updateColor(0)
            }
        }

        // 2. 联动内存临时仓库 (Session Sync)
        lifecycleScope.launch {
            ScreenSessionRepository.brightnessLevel.collectLatest { level ->
                if (level != -1) {
                    refreshBrightnessUI(level)
                    updatePreview()
                }
            }
        }
        lifecycleScope.launch {
            ScreenSessionRepository.colorLevel.collectLatest { level ->
                if (level != -1) {
                    refreshColorUI(level)
                    updatePreview()
                }
            }
        }

        // 3. 联动系统自动亮度流
        lifecycleScope.launch {
            AutoBrightnessManager.getAutoBrightnessFlow(this@ScreenLightActivity).collectLatest { isEnabled ->
                binding.slidingAutoBrightness.setCheckedSilently(isEnabled)
            }
        }

        // 4. 联动 DataStore 自动关闭时间
        lifecycleScope.launch {
            DataStoreManager.getScreenAutoOffTime(this@ScreenLightActivity).collectLatest { minutes ->
                binding.tvScreenTime.text = if (minutes >= 114514) getString(R.string.auto_off_never) 
                                         else minutes.toFloat().toDetailedTime(this@ScreenLightActivity)
            }
        }
    }

    /**
     * 【工业级修复】：滑块平滑移动动效
     */
    private fun refreshBrightnessUI(level: Int) {
        val bias = when (level) {
            0 -> 0.0f
            1 -> 0.5f
            2 -> 1.0f
            else -> 0.0f
        }

        // A. 动画移动滑块背景 (通过控制 HorizontalBias)
        val constraintLayout = binding.layoutBrightnessSlider
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)
        constraintSet.setHorizontalBias(R.id.view_brightness_thumb, bias)
        
        TransitionManager.beginDelayedTransition(constraintLayout, AutoTransition().apply {
            duration = 250
            interpolator = DecelerateInterpolator()
        })
        constraintSet.applyTo(constraintLayout)

        // B. 更新文字颜色 (直接操作 TextView，修复 getChildAt 报错)
        val brightnessTexts = listOf(binding.cardLeft, binding.cardMiddle, binding.cardRight)
        brightnessTexts.forEachIndexed { i, tv ->
            tv.setTextColor(if (i == level) selectedColor else Color.WHITE)
        }
    }

    private fun updatePreview() {
        if (!::colorTextMap.isInitialized) return
        
        val bLevel = ScreenSessionRepository.brightnessLevel.value.coerceAtLeast(0)
        val cLevel = ScreenSessionRepository.colorLevel.value.coerceAtLeast(0)
        val bValue = brightnessMap[bLevel] ?: 70
        val cHex = colorMap[cLevel] ?: "#FFFFFFFF"

        binding.tvLightInfo.text = "${colorTextMap[cLevel]} ${getString(R.string.brightness)}"
        
        binding.card1.background = GradientDrawable().apply {
            val brightness = (bValue * 2.55).toInt()
            val color = Color.parseColor(cHex)
            val mixedColor = String.format("#%02X%02X%02X", 
                Color.red(color) * brightness / 255, 
                Color.green(color) * brightness / 255, 
                Color.blue(color) * brightness / 255)
            
            setColor(Color.parseColor(mixedColor))
            cornerRadius = (12 * resources.displayMetrics.density)
            setStroke((2 * resources.displayMetrics.density).toInt(), Color.parseColor("#374151"))
        }
    }

    private fun refreshColorUI(level: Int) {

        val cards = listOf(
            binding.cardLeft1,
            binding.cardMiddle1,
            binding.cardRight1
        )

        val checks = listOf(
            binding.ivCheckPure,
            binding.ivCheckWarm,
            binding.ivCheckCold
        )

        val texts = listOf(
            binding.tvColorPure,
            binding.tvColorWarm,
            binding.tvColorCold
        )

        cards.forEachIndexed { i, card ->
            val isSelected = i == level

            // 1. 触发 selector
            card.isSelected = isSelected

            // 2. 勾选显示
            checks[i].visibility = if (isSelected) View.VISIBLE else View.GONE

            // 3. 文字高亮
            texts[i].setTextColor(
                if (isSelected) selectedBlueColor else Color.WHITE
            )
        }
    }

    private fun handleTouchEffect(view: View, event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
        }
    }

    override fun onBatteryStatusChanged(info: BatteryRepository.BatteryInfo) {}
}