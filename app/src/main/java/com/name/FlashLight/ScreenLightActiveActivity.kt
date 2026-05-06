package com.name.FlashLight

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.lifecycleScope
import com.name.FlashLight.databinding.ScreenLightBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import utils.DataStoreManager
import utils.ScreenSessionRepository
import utils.TimeRepository
import utils.feedback

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

        // 初始隐藏调节选项
        binding.layoutOptionsContainer.visibility = View.GONE
        binding.layoutBottomDesc.visibility = View.VISIBLE

        binding.root.post {
            updateUI()
            updateColorSelectionUI(currentColorLevel)
            updateModeUI(0.0f) // 初始亮度模式
        }
    }

    private fun observeSessionChanges() {
        lifecycleScope.launch {
            ScreenSessionRepository.brightnessLevel.collectLatest { level ->
                if (level != -1) {
                    currentBrightnessLevel = level
                    updateUI()
                    if (isOptionsShown && !isColorMode) updateSliderThumb(level)
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
        binding.ivSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        binding.traceback.setOnClickListener { finish() }

        // 亮度切换
        binding.cardLeft.setOnClickListener { it.feedback(); ScreenSessionRepository.updateBrightness(0) }
        binding.cardMiddle.setOnClickListener { it.feedback(); ScreenSessionRepository.updateBrightness(1) }
        binding.cardRight.setOnClickListener { it.feedback(); ScreenSessionRepository.updateBrightness(2) }

        // 颜色切换
        binding.cardLeft1.setOnClickListener { it.feedback(); ScreenSessionRepository.updateColor(0) }
        binding.cardMiddle1.setOnClickListener { it.feedback(); ScreenSessionRepository.updateColor(1) }
        binding.cardRight1.setOnClickListener { it.feedback(); ScreenSessionRepository.updateColor(2) }

        // 模式切换
        binding.btnModeBrightness.setOnClickListener {
            showOptionsIfNeeded()
            if (isColorMode) {
                isColorMode = false
                updateModeUI(0.0f)
                updateSliderThumb(currentBrightnessLevel)
                binding.viewBrightnessThumb.visibility = View.VISIBLE
                binding.viewSliderTrack.visibility = View.VISIBLE
                binding.tvBrightnessLabel.text = "Brightness"
            }
        }
        binding.btnModeColor.setOnClickListener {
            showOptionsIfNeeded()
            if (!isColorMode) {
                isColorMode = true
                updateModeUI(1.0f)
                binding.viewBrightnessThumb.visibility = View.GONE
                binding.viewSliderTrack.visibility = View.GONE
                binding.tvBrightnessLabel.text = "Color"
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
        val constraintSet = ConstraintSet()
        constraintSet.clone(binding.layoutModeSwitcher)
        constraintSet.setHorizontalBias(R.id.view_mode_thumb, bias)
        
        TransitionManager.beginDelayedTransition(binding.layoutModeSwitcher, AutoTransition().apply {
            duration = 200
            interpolator = DecelerateInterpolator()
        })
        constraintSet.applyTo(binding.layoutModeSwitcher)

        binding.groupBrightnessOptions.visibility = if (isColorMode) View.GONE else View.VISIBLE
        binding.groupColorOptions.visibility = if (isColorMode) View.VISIBLE else View.GONE
        
        binding.btnModeBrightness.alpha = if (isColorMode) 0.5f else 1.0f
        binding.btnModeColor.alpha = if (isColorMode) 1.0f else 0.5f
    }

    private fun updateSliderThumb(level: Int) {
        val bias = when (level) {
            0 -> 0.0f
            1 -> 0.5f
            else -> 1.0f
        }
        val constraintSet = ConstraintSet()
        constraintSet.clone(binding.layoutOptionsContainer)
        constraintSet.setHorizontalBias(R.id.view_brightness_thumb, bias)
        
        TransitionManager.beginDelayedTransition(binding.layoutOptionsContainer, AutoTransition().apply {
            duration = 250
            interpolator = DecelerateInterpolator()
        })
        constraintSet.applyTo(binding.layoutOptionsContainer)
    }

    private fun updateColorSelectionUI(level: Int) {
        val views = listOf(binding.cardLeft1, binding.cardMiddle1, binding.cardRight1)
        views.forEachIndexed { index, view ->
            view.alpha = if (index == level) 1.0f else 0.5f
            view.animate().scaleX(if (index == level) 1.2f else 1.0f)
                .scaleY(if (index == level) 1.2f else 1.0f)
                .setDuration(200).start()
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
                    navigateToMain()
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

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
        finish()
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
