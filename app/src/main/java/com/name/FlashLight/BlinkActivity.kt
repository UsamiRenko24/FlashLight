package com.name.FlashLight

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.name.FlashLight.databinding.BlinkBinding
import com.name.FlashLight.utils.PageConstants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import utils.*

/**
 * 工业级模块化闪烁页面
 * 职责：负责闪烁功能的配置控制及硬件 Session 生命周期管理
 */
class BlinkActivity : BaseActivity<BlinkBinding>() {

    // --- 1. 模块化声明 (声明式配置) ---
    override val pageTrackName = PageConstants.PAGE_BLINK
    override val isBatteryMonitorEnabled = true
    override val isLowBatteryCheckEnabled = true

    // --- 2. 局部会话状态 ---
    private var isBlinking = false
    private var selectedFrequency = 1  
    private var currentAutoOffMinutes = 5
    private var startTime = 0L
    
    private var blinkJob: Job? = null
    private var timerJob: Job? = null

    // --- 3. 硬件管理与 UI 常量 ---
    private var isScreenLightSelected = false
    private var isFlashlightSelected = true   
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private val selectedBlueColor = Color.parseColor("#4786EF")

    override fun createBinding(): BlinkBinding = BlinkBinding.inflate(layoutInflater)

    /**
     * 职责模块 A: 初始化 UI 静态表现
     */
    override fun initViews() {
        SoundManager.initSoundPool(this)
        initHardware()
        
        // 设置初始选中状态
        selectFrequencyUI(1)
        binding.layoutBlink.isSelected = true
        updateSourceLayoutUI(binding.layoutBlink, true)
    }

    /**
     * 职责模块 B: 事件监听集中营
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun initListeners() {
        binding.traceback.setOnClickListener { stopBlinkingSession(); handleBackPress() }
        binding.ivSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        // SOS 跳转逻辑
        binding.SOS.setOnTouchListener { v, event ->
            handleTouchAnimation(v, event)
            if (event.action == MotionEvent.ACTION_UP) {
                if (isBlinking) Toast.makeText(this, getString(R.string.please_stop_blink), Toast.LENGTH_SHORT).show()
                else startActivity(Intent(this, SOSActivity::class.java))
            }
            true
        }

        // 光源选择
        binding.layoutScreenLight.setOnClickListener { if (!isBlinking) toggleSourceSelection(true) }
        binding.layoutBlink.setOnClickListener { if (!isBlinking) toggleSourceSelection(false) }

        // 频率卡片点击
        binding.cardLeft.setOnClickListener { if (!isBlinking) selectFrequencyUI(0) }
        binding.cardMiddle.setOnClickListener { if (!isBlinking) selectFrequencyUI(1) }
        binding.cardRight.setOnClickListener { if (!isBlinking) selectFrequencyUI(2) }

        // 主开关控制 (利用基类模块化权限请求)
        binding.btnStartBlink.setOnTouchListener { v, event ->
            handleTouchAnimation(v, event)
            if (event.action == MotionEvent.ACTION_UP) {
                ensureCameraPermission { 
                    if (isBlinking) stopBlinkingSession() else startBlinkingSession() 
                }
            }
            true
        }
    }

    /**
     * 职责模块 C: 响应式观察
     */
    override fun initObservers() {
        lifecycleScope.launch {
            // 实时同步 DataStore 里的自动关闭时长
            DataStoreManager.getBlinkAutoOffTime(this@BlinkActivity).collectLatest { minutes ->
                currentAutoOffMinutes = minutes
            }
        }
    }

    // --- 核心业务会话 (Session) ---

    private fun startBlinkingSession() {
        if (!isScreenLightSelected && !isFlashlightSelected) {
            Toast.makeText(this, getString(R.string.at_least_choose_one_light_source), Toast.LENGTH_SHORT).show()
            return
        }
        isBlinking = true
        updateActionUI(true)

        val interval = when (selectedFrequency) { 0 -> 1000L; 1 -> 500L; 2 -> 200L; else -> 500L }
        
        // A. 开启硬件闪烁协程 (Blink Job)
        blinkJob?.cancel()
        blinkJob = lifecycleScope.launch {
            var isOn = false
            while (isBlinking) {
                isOn = !isOn
                applyHardwareLightState(isOn)
                delay(interval)
            }
        }

        // B. 开启计时与判定协程 (Timer Job)
        startTimerJob()
    }

    private fun stopBlinkingSession() {
        isBlinking = false
        blinkJob?.cancel(); blinkJob = null
        stopTimerJob()

        updateActionUI(false)
        applyHardwareLightState(false)
    }

    private fun startTimerJob() {
        timerJob?.cancel()
        startTime = System.currentTimeMillis()
        timeRepository.startRecording(TimeRepository.TYPE_BLINK)
        timerJob = lifecycleScope.launch {
            while (true) {
                if (checkAutoOffReached()) break
                delay(1000)
            }
        }
    }

    private fun stopTimerJob() {
        timerJob?.cancel(); timerJob = null
        timeRepository.stopRecording(TimeRepository.TYPE_BLINK)
    }

    private fun checkAutoOffReached(): Boolean {
        val elapsed = (System.currentTimeMillis() - startTime) / 60000f
        return if (currentAutoOffMinutes < 114514 && elapsed >= currentAutoOffMinutes) {
            stopBlinkingSession()
            Toast.makeText(this, getString(R.string.blink_auto_off), Toast.LENGTH_SHORT).show()
            navigateToMain()
            true
        } else false
    }

    // --- 硬件驱动层 ---

    private fun initHardware() {
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (e: Exception) { }
    }

    private fun applyHardwareLightState(on: Boolean) {
        if (isScreenLightSelected) {
            val lp = window.attributes
            lp.screenBrightness = if (on) 1.0f else -1.0f
            window.attributes = lp
        }
        if (isFlashlightSelected) {
            try { cameraId?.let { cameraManager.setTorchMode(it, on) } } catch (e: Exception) { }
        }
    }

    private fun toggleSourceSelection(isScreen: Boolean) {
        if (isScreen) {
            if (isScreenLightSelected && !isFlashlightSelected) return
            isScreenLightSelected = !isScreenLightSelected
            binding.layoutScreenLight.isSelected = isScreenLightSelected
            updateSourceLayoutUI(binding.layoutScreenLight, isScreenLightSelected)
        } else {
            if (isFlashlightSelected && !isScreenLightSelected) return
            isFlashlightSelected = !isFlashlightSelected
            binding.layoutBlink.isSelected = isFlashlightSelected
            updateSourceLayoutUI(binding.layoutBlink, isFlashlightSelected)
        }
    }

    // --- 辅助 UI 逻辑 (保持纯净) ---

    private fun updateSourceLayoutUI(layout: LinearLayout, selected: Boolean) {
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            if (child is TextView) child.setTextColor(if (selected) selectedBlueColor else Color.WHITE)
            if (child is ImageView) child.setColorFilter(if (selected) selectedBlueColor else Color.WHITE)
        }
    }

    private fun selectFrequencyUI(level: Int) {
        selectedFrequency = level
        val cards = listOf(binding.cardLeft, binding.cardMiddle, binding.cardRight)
        cards.forEachIndexed { i, card ->
            card.isSelected = (i == level)
            // 【核心修复】：遍历卡片内所有子 View，确保所有 TextView 都能正确变色
            for (j in 0 until card.childCount) {
                val child = card.getChildAt(j)
                if (child is TextView) {
                    child.setTextColor(if (i == level) selectedBlueColor else Color.WHITE)
                }
            }
        }
    }

    private fun updateActionUI(active: Boolean) {
        binding.btnStartBlink.text = if (active) getString(R.string.btn_blink) else getString(R.string.btn_blink)
        binding.btnStartBlink.alpha = if (active) 0.3f else 1.0f
        binding.SOS.isEnabled = !active
        binding.SOS.alpha = if (active) 0.3f else 1.0f
    }

    private fun handleTouchAnimation(view: View, event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).setInterpolator(OvershootInterpolator()).start()
                if (event.action == MotionEvent.ACTION_UP) view.feedback()
            }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
        finish()
    }

    override fun stopAllFeatures() { if (isBlinking) stopBlinkingSession() }
    override fun onPause() { super.onPause(); if (isBlinking) stopBlinkingSession() }
    
    // 行为钩子实现
    override fun onBatteryStatusChanged(info: utils.BatteryRepository.BatteryInfo) {
        // 子类接收电池信息通知
    }
}