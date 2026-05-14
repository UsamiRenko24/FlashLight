package com.name.flashlight

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import com.name.flashlight.databinding.ScreenLightBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.name.flashlight.utils.DataStoreManager
import com.name.flashlight.utils.ScreenSessionRepository
import com.name.flashlight.utils.TimeRepository
import com.name.flashlight.utils.feedback

class ScreenLightActiveActivity : BaseActivity<ScreenLightBinding>() {

    private var currentBrightnessLevel = 1
    private var currentColorLevel = 0
    private var isColorMode = false
    private var isOptionsShown = false
    
    private val colorMap = mapOf(0 to "#FFFFFFFF", 1 to "#FFFFF8DC", 2 to "#FFF0F8FF")
    private val brightnessMap = mapOf(0 to 40, 1 to 70, 2 to 100)

    private var lastClickTime: Long = 0
    private val DOUBLE_CLICK_TIME = 300

    private var timerJob: Job? = null
    private var startTime = 0L
    private var totalTimeMinutes: Int = 0

    override fun createBinding(): ScreenLightBinding = ScreenLightBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        observeSessionChanges()

        lifecycleScope.launch {
            totalTimeMinutes = DataStoreManager.getScreenAutoOffTime(this@ScreenLightActiveActivity).first()
            startTimer()
            timeRepository.startRecording(TimeRepository.TYPE_SCREEN_LIGHT)
        }

        setupClickListeners()
        setupBackPressedCallback()

        // 初始隐藏调节选项容器，保持显示底部状态描述
        binding.layoutOptionsContainer.visibility = View.GONE
        binding.layoutBottomDesc.visibility = View.VISIBLE

        binding.root.post {

            updateUI()

            updateBrightnessSelectionUI(currentBrightnessLevel)

            updateColorSelectionUI(currentColorLevel)

            updateModeUI(0.0f)
        }
    }

    private fun observeSessionChanges() {
        lifecycleScope.launch {
            ScreenSessionRepository.brightnessLevel.collectLatest { level ->
                if (level != -1) {
                    currentBrightnessLevel = level
                    updateUI()
                    if (isOptionsShown && !isColorMode) updateBrightnessSelectionUI(level)
                }
            }
        }
        lifecycleScope.launch {
            ScreenSessionRepository.colorLevel.collectLatest { level ->
                if (level != -1) {
                    currentColorLevel = level
                    updateUI()
                    updateColorSelectionUI(level)
                }
            }
        }
    }

    private fun updateUI() {
        val brightnessValue = brightnessMap[currentBrightnessLevel] ?: 70
        val colorHex = colorMap[currentColorLevel] ?: "#FFFFFFFF"
        
        val brightness = (brightnessValue * 2.55).toInt()
        val color = Color.parseColor(colorHex)
        val mixedColor = Color.rgb(
            Color.red(color) * brightness / 255,
            Color.green(color) * brightness / 255,
            Color.blue(color) * brightness / 255
        )
        
        binding.card2.backgroundTintList = ColorStateList.valueOf(mixedColor)
        
        val bText = when (currentBrightnessLevel) { 
            0 -> getString(R.string.brightness_low) 
            1 -> getString(R.string.brightness_medium) 
            else -> getString(R.string.brightness_high) 
        }
        val cText = when (currentColorLevel) { 
            0 -> getString(R.string.color_pure) 
            1 -> getString(R.string.color_warm) 
            else -> getString(R.string.color_cold) 
        }
        
        binding.tvLightInfo.text = "$cText\n$bText"
    }

    private fun setupClickListeners() {

        binding.traceback.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        // 亮度圆点区域点击
        binding.cardLeft.setOnClickListener { it.feedback(); ScreenSessionRepository.updateBrightness(0) }
        binding.cardMiddle.setOnClickListener { it.feedback(); ScreenSessionRepository.updateBrightness(1) }
        binding.cardRight.setOnClickListener { it.feedback(); ScreenSessionRepository.updateBrightness(2) }

        // 颜色圆点点击
        binding.cardLeft1.setOnClickListener { it.feedback(); ScreenSessionRepository.updateColor(0) }
        binding.cardMiddle1.setOnClickListener { it.feedback(); ScreenSessionRepository.updateColor(1) }
        binding.cardRight1.setOnClickListener { it.feedback(); ScreenSessionRepository.updateColor(2) }

        // 模式切换
        binding.btnModeBrightness.setOnClickListener {
            showOptionsIfNeeded()
            if (isColorMode) {
                isColorMode = false
                updateModeUI(0.0f)
                updateBrightnessSelectionUI(currentBrightnessLevel)
            }
        }
        binding.btnModeColor.setOnClickListener {
            showOptionsIfNeeded()
            if (!isColorMode) {
                isColorMode = true
                updateModeUI(1.0f)
            }
        }
    }

    private fun showOptionsIfNeeded() {
        if (!isOptionsShown) {
            isOptionsShown = true
            binding.layoutOptionsContainer.visibility = View.VISIBLE
        }
    }

    private fun updateModeUI(bias: Float) {

        // =========================
        // 先切换内容
        // =========================

        val isBrightnessMode = !isColorMode

        binding.groupBrightnessOptions.visibility =
            if (isBrightnessMode) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.groupColorOptions.visibility =
            if (isColorMode) {
                View.VISIBLE
            } else {
                View.GONE
            }

        // =========================
        // 再移动滑块
        // =========================

        binding.layoutModeSwitcher.post {

            val constraintSet = ConstraintSet()

            constraintSet.clone(binding.layoutModeSwitcher)

            constraintSet.setHorizontalBias(
                R.id.view_mode_thumb,
                bias
            )

            TransitionManager.beginDelayedTransition(
                binding.layoutModeSwitcher,
                AutoTransition().apply {

                    duration = 220

                    interpolator =
                        DecelerateInterpolator()
                }
            )

            constraintSet.applyTo(
                binding.layoutModeSwitcher
            )
        }

        // =========================
        // 按钮透明度
        // =========================

        binding.btnModeBrightness.alpha =
            if (isColorMode) {
                0.5f
            } else {
                1.0f
            }

        binding.btnModeColor.alpha =
            if (isColorMode) {
                1.0f
            } else {
                0.5f
            }
    }


    private fun updateBrightnessSelectionUI(level: Int) {

        val icons = listOf(
            binding.ivBrightnessLow,
            binding.ivBrightnessMedium,
            binding.ivBrightnessHigh
        )

        icons.forEachIndexed { index, imageView ->

            val isSelected = index == level

            // 选中高亮
            imageView.imageTintList =
                ColorStateList.valueOf(
                    if (isSelected) {
                        Color.parseColor("#2AE1F8")
                    } else {
                        Color.WHITE
                    }
                )

            // 未选中透明一点
            imageView.alpha =
                if (isSelected) 1.0f else 0.45f

            // 选中放大
            val scale =
                if (isSelected) 1.18f else 1.0f

            imageView.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(180)
                .start()
        }
    }
    private fun updateColorSelectionUI(level: Int) {
        val views = listOf(binding.cardLeft1, binding.cardMiddle1, binding.cardRight1)
        views.forEachIndexed { index, view ->
            val isSelected = index == level
            view.alpha = if (isSelected) 1.0f else 0.5f
            val scale = if (isSelected) 1.2f else 1.0f
            view.animate().scaleX(scale).scaleY(scale).setDuration(200).start()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        startTime = System.currentTimeMillis()
        timerJob = lifecycleScope.launch {
            while (true) {
                val elapsedMinutes = (System.currentTimeMillis() - startTime)/ 60000f
                if (elapsedMinutes < 114514 && elapsedMinutes >= totalTimeMinutes){
                    stopTimer()
                    finish()
                    break
                }
                delay(1000)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < DOUBLE_CLICK_TIME) { finish(); return true }
            lastClickTime = currentTime
        }
        return super.onTouchEvent(event)
    }

    private fun setupBackPressedCallback() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
    }

    override fun onPause() { super.onPause(); timeRepository.stopRecording(TimeRepository.TYPE_SCREEN_LIGHT) }
    override fun onDestroy() { super.onDestroy(); stopTimer() }
}
