package com.name.flashlight

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
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.lifecycleScope
import com.name.flashlight.databinding.ScreenBinding
import com.name.flashlight.utils.BatteryRepository
import com.name.flashlight.utils.DataStoreManager
import com.name.flashlight.utils.PageConstants
import com.name.flashlight.utils.ScreenSessionRepository
import com.name.flashlight.utils.SoundManager
import com.name.flashlight.utils.feedback
import com.name.flashlight.utils.toDetailedTime
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 模块化重构后的屏幕补光主页
 */
class ScreenLightActivity : BaseActivity<ScreenBinding>() {

    override val pageTrackName = PageConstants.PAGE_SCREEN_LIGHT
    override val isBatteryMonitorEnabled = false
    override val isLowBatteryCheckEnabled = true

    private val colorMap = mapOf(
        0 to "#FFFFFFFF",
        1 to "#FFFFF8DC",
        2 to "#FFF0F8FF"
    )

    private val brightnessMap = mapOf(
        0 to 40,
        1 to 70,
        2 to 100
    )

    private val selectedBlueColor =
        Color.parseColor("#2AE1F8")

    private val selectedColor =
        Color.parseColor("#0E0E0E")

    private lateinit var colorTextMap: Map<Int, String>

    override fun createBinding(): ScreenBinding {
        return ScreenBinding.inflate(layoutInflater)
    }

    override fun initViews() {

        colorTextMap = mapOf(
            0 to getString(R.string.color_pure),
            1 to getString(R.string.color_warm),
            2 to getString(R.string.color_cold)
        )

        SoundManager.initSoundPool(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun initListeners() {

        binding.traceback.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // 亮度选择
        binding.cardLeft.setOnClickListener {

            ScreenSessionRepository.updateBrightness(0)

            lifecycleScope.launch {
                applyCurrentBrightness()
            }
        }

        binding.cardMiddle.setOnClickListener {

            ScreenSessionRepository.updateBrightness(1)

            lifecycleScope.launch {
                applyCurrentBrightness()
            }
        }

        binding.cardRight.setOnClickListener {

            ScreenSessionRepository.updateBrightness(2)

            lifecycleScope.launch {
                applyCurrentBrightness()
            }
        }

        // 色温选择
        binding.cardLeft1.setOnClickListener {
            ScreenSessionRepository.updateColor(0)
        }

        binding.cardMiddle1.setOnClickListener {
            ScreenSessionRepository.updateColor(1)
        }

        binding.cardRight1.setOnClickListener {
            ScreenSessionRepository.updateColor(2)
        }

        // 自动亮度开关
        binding.slidingAutoBrightness
            .setOnStateChangedListener { isEnabled ->

                binding.slidingAutoBrightness.feedback()

                lifecycleScope.launch {

                    DataStoreManager
                        .setUseSystemAutoBrightness(
                            this@ScreenLightActivity,
                            isEnabled
                        )

                    applyCurrentBrightness()
                }
            }

        // 自动关闭
        binding.layoutAutoOff.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    AutomaticActivity::class.java
                )
            )
        }

        // 开始补光
        binding.card2.setOnTouchListener { view, event ->

            val isInside =
                event.x >= 0 &&
                        event.x <= view.width &&
                        event.y >= 0 &&
                        event.y <= view.height

            if (
                event.action == MotionEvent.ACTION_UP &&
                isInside
            ) {

                view.feedback()

                startActivity(
                    Intent(
                        this,
                        ScreenLightActiveActivity::class.java
                    )
                )
            }

            handleTouchEffect(view, event)

            true
        }
    }

    override fun initObservers() {

        // 色温：仅首次进入补光流程时默认纯白
        lifecycleScope.launch {

            if (ScreenSessionRepository.colorLevel.value == -1) {

                ScreenSessionRepository.updateColor(0)
            }
        }

        // 亮度：与设置里的「默认亮度」同一数据源，随 DataStore 变化同步到会话
        lifecycleScope.launch {

            DataStoreManager
                .getDefaultBrightness(this@ScreenLightActivity)
                .collectLatest { level ->

                    ScreenSessionRepository.updateBrightness(level)
                }
        }

        // 亮度观察
        lifecycleScope.launch {

            ScreenSessionRepository
                .brightnessLevel
                .collectLatest { level ->

                    if (level != -1) {

                        refreshBrightnessUI(level)

                        updatePreview()
                    }
                }
        }

        // 色温观察
        lifecycleScope.launch {

            ScreenSessionRepository
                .colorLevel
                .collectLatest { level ->

                    if (level != -1) {

                        refreshColorUI(level)

                        updatePreview()
                    }
                }
        }

        // 自动亮度状态
        lifecycleScope.launch {

            DataStoreManager
                .getUseSystemAutoBrightness(this@ScreenLightActivity)
                .collectLatest { enabled ->

                    binding

                        .slidingAutoBrightness
                        .setCheckedSilently(enabled)
                }
        }

        // 自动关闭时间
        lifecycleScope.launch {

            DataStoreManager
                .getScreenAutoOffTime(this@ScreenLightActivity)
                .collectLatest { minutes ->

                    binding.tvScreenTime.text =
                        if (minutes >= 114514) {

                            getString(R.string.auto_off_never)

                        } else {

                            minutes
                                .toFloat()
                                .toDetailedTime(
                                    this@ScreenLightActivity
                                )
                        }
                }
        }
    }

    /**
     * 应用当前亮度策略
     */
    private suspend fun applyCurrentBrightness() {

        val useSystemAutoBrightness =
            DataStoreManager
                .getUseSystemAutoBrightness(this)
                .first()

        val lp = window.attributes

        if (useSystemAutoBrightness) {

            // 跟随系统亮度
            lp.screenBrightness =
                WindowManager.LayoutParams
                    .BRIGHTNESS_OVERRIDE_NONE

        } else {

            // 使用 App 内亮度
            val level =
                ScreenSessionRepository
                    .brightnessLevel.value
                    .coerceAtLeast(0)

            lp.screenBrightness = when (level) {

                0 -> 0.4f

                1 -> 0.7f

                2 -> 1.0f

                else -> 0.7f
            }
        }

        window.attributes = lp
    }

    /**
     * 刷新亮度 UI
     */
    private fun refreshBrightnessUI(level: Int) {

        val bias = when (level) {

            0 -> 0.0f

            1 -> 0.5f

            2 -> 1.0f

            else -> 0.0f
        }

        val constraintLayout =
            binding.layoutBrightnessSlider

        val constraintSet = ConstraintSet()

        constraintSet.clone(constraintLayout)

        constraintSet.setHorizontalBias(
            R.id.view_brightness_thumb,
            bias
        )

        TransitionManager.beginDelayedTransition(
            constraintLayout,
            AutoTransition().apply {

                duration = 250

                interpolator =
                    DecelerateInterpolator()
            }
        )

        constraintSet.applyTo(constraintLayout)

        val brightnessTexts = listOf(
            binding.cardLeft,
            binding.cardMiddle,
            binding.cardRight
        )

        brightnessTexts.forEachIndexed { i, tv ->

            tv.setTextColor(
                if (i == level)
                    selectedColor
                else
                    Color.WHITE
            )
        }
    }

    /**
     * 更新预览
     */
    private fun updatePreview() {

        if (!::colorTextMap.isInitialized) return

        val brightnessLevel =
            ScreenSessionRepository
                .brightnessLevel.value
                .coerceAtLeast(0)

        val colorLevel =
            ScreenSessionRepository
                .colorLevel.value
                .coerceAtLeast(0)

        val brightness =
            brightnessMap[brightnessLevel] ?: 70

        val colorHex =
            colorMap[colorLevel] ?: "#FFFFFFFF"

        binding.tvLightInfo.text =
            "${colorTextMap[colorLevel]} ${getString(R.string.brightness)}"

        binding.card1.background =
            GradientDrawable().apply {

                val brightnessValue =
                    (brightness * 2.55).toInt()

                val color =
                    Color.parseColor(colorHex)

                val mixedColor =
                    String.format(
                        "#%02X%02X%02X",
                        Color.red(color) * brightnessValue / 255,
                        Color.green(color) * brightnessValue / 255,
                        Color.blue(color) * brightnessValue / 255
                    )

                setColor(
                    Color.parseColor(mixedColor)
                )

                cornerRadius =
                    12 * resources.displayMetrics.density

                setStroke(
                    (2 * resources.displayMetrics.density).toInt(),
                    Color.parseColor("#374151")
                )
            }
    }

    /**
     * 刷新色温 UI
     */
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

            card.isSelected = isSelected

            checks[i].visibility =
                if (isSelected)
                    View.VISIBLE
                else
                    View.GONE

            texts[i].setTextColor(
                if (isSelected)
                    selectedBlueColor
                else
                    Color.WHITE
            )
        }
    }

    /**
     * 按压动画
     */
    private fun handleTouchEffect(
        view: View,
        event: MotionEvent
    ) {

        when (event.action) {

            MotionEvent.ACTION_DOWN -> {

                view.animate()
                    .scaleX(0.92f)
                    .scaleY(0.92f)
                    .setDuration(100)
                    .start()
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {

                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(200)
                    .start()
            }
        }
    }

    override fun onBatteryStatusChanged(
        info: BatteryRepository.BatteryInfo
    ) {
    }
}