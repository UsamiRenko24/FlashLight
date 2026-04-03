package com.name.FlashLight

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.activity.OnBackPressedCallback
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
        
        // 【核心修复】：响应式监听内存仓库的变化
        observeSessionChanges()

        lifecycleScope.launch {
            // 1. 获取自动关闭时长
            totalTimeMinutes = DataStoreManager.getScreenAutoOffTime(this@ScreenLightActiveActivity).first()
            startTimer()
            timeRepository.startRecording(TimeRepository.TYPE_SCREEN_LIGHT)
        }

        setupInitialState()
        setupClickListeners()
        setupBackPressedCallback()
    }

    private fun observeSessionChanges() {
        lifecycleScope.launch {
            ScreenSessionRepository.brightnessLevel.collectLatest { level ->
                if (level != -1) {
                    currentBrightnessLevel = level
                    updateUI()
                }
            }
        }
        lifecycleScope.launch {
            ScreenSessionRepository.colorLevel.collectLatest { level ->
                if (level != -1) {
                    currentColorLevel = level
                    updateUI()
                }
            }
        }
    }

    private fun updateUI() {
        val brightnessValue = brightnessMap[currentBrightnessLevel] ?: 70
        val colorHex = colorMap[currentColorLevel] ?: "#FFFFFFFF"
        
        // 混合背景色
        val brightness = (brightnessValue * 2.55).toInt()
        val color = Color.parseColor(colorHex)
        val mixedColor = String.format("#%02X%02X%02X", 
            Color.red(color) * brightness / 255, 
            Color.green(color) * brightness / 255, 
            Color.blue(color) * brightness / 255)
        
        binding.mainPage.setBackgroundColor(Color.parseColor(mixedColor))
        
        // 更新标题
        val bText = when (currentBrightnessLevel) { 0 -> getString(R.string.brightness_low) 1 -> getString(R.string.brightness_medium) else -> getString(R.string.brightness_high) }
        val cText = when (currentColorLevel) { 0 -> getString(R.string.color_pure) 1 -> getString(R.string.color_warm) else -> getString(R.string.color_cold) }
        binding.tvTitle.text = "$cText - $bText"
        
        // 更新选中项透明度
        binding.sun1.alpha = if (currentBrightnessLevel == 0) 1.0f else 0.5f
        binding.sun2.alpha = if (currentBrightnessLevel == 1) 1.0f else 0.5f
        binding.sun3.alpha = if (currentBrightnessLevel == 2) 1.0f else 0.5f
        
        binding.color1.alpha = if (currentColorLevel == 0) 1.0f else 0.5f
        binding.color2.alpha = if (currentColorLevel == 1) 1.0f else 0.5f
        binding.color3.alpha = if (currentColorLevel == 2) 1.0f else 0.5f
    }

    private fun setupInitialState() {
        binding.card2.visibility = View.GONE
        binding.card3.visibility = View.GONE
    }

    private fun setupClickListeners() {
        binding.sun.setOnClickListener { binding.card2.visibility = if (binding.card2.visibility == View.VISIBLE) View.GONE else View.VISIBLE; binding.card3.visibility = View.GONE }
        binding.palette.setOnClickListener { binding.card3.visibility = if (binding.card3.visibility == View.VISIBLE) View.GONE else View.VISIBLE; binding.card2.visibility = View.GONE }
        binding.settings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        binding.close.setOnClickListener { finish() }

        // 点击调节：只改内存仓库
        binding.sun1.setOnClickListener { it.feedback(); ScreenSessionRepository.updateBrightness(0) }
        binding.sun2.setOnClickListener { it.feedback(); ScreenSessionRepository.updateBrightness(1) }
        binding.sun3.setOnClickListener { it.feedback(); ScreenSessionRepository.updateBrightness(2) }

        binding.color1.setOnClickListener { it.feedback(); ScreenSessionRepository.updateColor(0) }
        binding.color2.setOnClickListener { it.feedback(); ScreenSessionRepository.updateColor(1) }
        binding.color3.setOnClickListener { it.feedback(); ScreenSessionRepository.updateColor(2) }
    }

    private fun startTimer() {
        timerJob?.cancel()
        startTime = System.currentTimeMillis()
        timerJob = lifecycleScope.launch { // 启动协程
            while (true) { // 只要任务没被取消，就一直循环
                val elapsedMinutes = (System.currentTimeMillis() - startTime)/ 60000f
                if (elapsedMinutes < 114514 && elapsedMinutes >= totalTimeMinutes){
                    stopTimer()
                    navigateToMain()
                    break
                }
                updateUI() // 逻辑入口
                delay(1000)   // 挂起 1 秒，不卡界面
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        updateUI()
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