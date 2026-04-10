package com.name.FlashLight

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
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
    private val selectedBlueColor = Color.parseColor("#4786EF")

    private lateinit var brightnessTextMap: Map<Int, String>
    private lateinit var colorTextMap: Map<Int, String>

    override fun createBinding(): ScreenBinding = ScreenBinding.inflate(layoutInflater)

    /**
     * 职责模块 A: 初始化静态 UI 与资源
     */
    override fun initViews() {
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
        SoundManager.initSoundPool(this)
    }

    /**
     * 职责模块 B: 事件监听集中营
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun initListeners() {
        binding.traceback.setOnClickListener { handleBackPress() }
        binding.ivSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        // 点击只负责更新仓库，联动逻辑由 initObservers 处理
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
        // 1. 初始化同步逻辑：新鲜进入时拉取全局默认值
        lifecycleScope.launch {
            // 使用 savedInstanceState 确保旋转屏幕不重置
            // 这里判断 -1 是为了兼容单例尚未从 DataStore 种子化的情况
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

    // --- 内部 UI 渲染逻辑 (私有) ---

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

    private fun refreshBrightnessUI(level: Int) {
        listOf(binding.cardLeft, binding.cardMiddle, binding.cardRight).forEachIndexed { i, card ->
            val isSelected = i == level
            card.isSelected = isSelected
            card.setBackgroundResource(R.drawable.bg_rounded_selector)
            (card.getChildAt(0) as? TextView)?.setTextColor(if (isSelected) selectedBlueColor else Color.WHITE)
        }
    }

    private fun refreshColorUI(level: Int) {
        listOf(binding.cardLeft1, binding.cardMiddle1, binding.cardRight1).forEachIndexed { i, card ->
            val isSelected = i == level
            card.isSelected = isSelected
            (card.getChildAt(1) as? TextView)?.setTextColor(if (isSelected) selectedBlueColor else Color.WHITE)
        }
    }

    private fun handleTouchEffect(view: View, event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
        }
    }

    // --- 行为钩子重写 ---
    override fun onBatteryStatusChanged(info: BatteryRepository.BatteryInfo) {
        // 如果页面有电量显示逻辑，在此处处理
    }
}