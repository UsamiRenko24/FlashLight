package com.name.FlashLight

import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import com.name.FlashLight.utils.PageConstants
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import utils.feedback

class BlinkActivity : BaseActivity() {

    private lateinit var ivTraceback: ImageView
    private lateinit var ivSettings: ImageView
    private lateinit var btnSos: Button
    private lateinit var btnStartBlink: Button
    private lateinit var layoutScreenLight: LinearLayout
    private lateinit var layoutBlink: LinearLayout
    private lateinit var layoutLeft: LinearLayout
    private lateinit var layoutMiddle: LinearLayout
    private lateinit var layoutRight: LinearLayout

    // 光源选择状态
    private var isScreenLightSelected = false
    private var isFlashlightSelected = true   // 手电筒（默认）

    // 频率选择状态
    private var selectedFrequency = 1  // 0=低频, 1=中频, 2=高频

    // 模式状态
    private var isBlinking = false

    // 闪烁控制
    private var blinkHandler = Handler(Looper.getMainLooper())
    private var blinkRunnable: Runnable? = null

    // 手电筒控制
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.blink)
        initViews()
        PageUsageRecorder.recordPageVisit(this, PageConstants.PAGE_BLINK)
        StartupModeManager.recordLastPage(this, PageConstants.PAGE_BLINK)

        initFlashlight()
        setupClickListeners()

        // 默认选中中频
        selectFrequency(1)
    }

    private fun initViews() {
        ivTraceback = findViewById(R.id.traceback)
        ivSettings = findViewById(R.id.iv_settings)
        btnSos = findViewById(R.id.SOS)
        btnStartBlink = findViewById(R.id.btn_start_blink)
        layoutScreenLight = findViewById(R.id.layout_screen_light)
        layoutBlink = findViewById(R.id.layout_blink)
        layoutLeft = findViewById(R.id.card_left)
        layoutMiddle = findViewById(R.id.card_middle)
        layoutRight = findViewById(R.id.card_right)

        // 设置默认选中状态
        layoutScreenLight.setBackgroundResource(R.drawable.bg_rounded)
        layoutBlink.setBackgroundResource(R.drawable.bg_rounded_corner_selected)  // 手电筒默认
    }

    private fun initFlashlight() {
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
            }
        } catch (e: Exception) {
            // 设备可能不支持闪光灯
        }
    }
    private fun setupClickListeners() {
        // 返回按钮
        ivTraceback.setOnClickListener {
            stopBlinking()
            handleBackPress()
        }

        // 设置按钮
        ivSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // SOS按钮 - 直接跳转到SOSActivity
        btnSos.setOnClickListener {
            it.feedback()
            if (isBlinking) {
                Toast.makeText(this, "请先停止闪烁", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, SOSActivity::class.java)
                startActivity(intent)
            }
        }

        // 屏幕补光选项
        layoutScreenLight.setOnClickListener {
            if (!isBlinking) {
                toggleScreenLight()
            } else {
                Toast.makeText(this, "请先停止闪烁", Toast.LENGTH_SHORT).show()
            }
        }

        // 手电筒选项
        layoutBlink.setOnClickListener {
            if (!isBlinking) {
                toggleFlashlight()
            } else {
                Toast.makeText(this, "请先停止闪烁", Toast.LENGTH_SHORT).show()
            }
        }

        // 低频卡片
        layoutLeft.setOnClickListener {
            if (!isBlinking) {
                selectFrequency(0)
            } else {
                Toast.makeText(this, "请先停止闪烁", Toast.LENGTH_SHORT).show()
            }
        }

        // 中频卡片
        layoutMiddle.setOnClickListener {
            if (!isBlinking) {
                selectFrequency(1)
            } else {
                Toast.makeText(this, "请先停止闪烁", Toast.LENGTH_SHORT).show()
            }
        }

        // 高频卡片
        layoutRight.setOnClickListener {
            if (!isBlinking) {
                selectFrequency(2)
            } else {
                Toast.makeText(this, "请先停止闪烁", Toast.LENGTH_SHORT).show()
            }
        }

        // 开始闪烁按钮
        btnStartBlink.setOnClickListener {
            it.feedback()
            if (isBlinking) {
                stopBlinking()
                TimeRecorder.stopRecording(this, "blink")
            } else {
                startBlinking()
                TimeRecorder.startRecording(this, "blink")
            }
        }
    }

    private fun toggleScreenLight() {
        if (isScreenLightSelected) {
            // 尝试取消屏幕补光
            if (isFlashlightSelected) {
                isScreenLightSelected = false
                layoutScreenLight.setBackgroundResource(R.drawable.bg_rounded)
                Toast.makeText(this, "屏幕补光已关闭", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "至少需要选择一种光源", Toast.LENGTH_SHORT).show()
            }
        } else {
            // 选中屏幕补光
            isScreenLightSelected = true
            layoutScreenLight.setBackgroundResource(R.drawable.bg_rounded_corner_selected)
            Toast.makeText(this, "屏幕补光已开启", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleFlashlight() {
        if (isFlashlightSelected) {
            // 尝试取消手电筒
            if (isScreenLightSelected) {
                isFlashlightSelected = false
                layoutBlink.setBackgroundResource(R.drawable.bg_rounded)
                Toast.makeText(this, "手电筒已关闭", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "至少需要选择一种光源", Toast.LENGTH_SHORT).show()
            }
        } else {
            // 选中手电筒
            isFlashlightSelected = true
            layoutBlink.setBackgroundResource(R.drawable.bg_rounded_corner_selected)
            Toast.makeText(this, "手电筒已开启", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectFrequency(level: Int) {
        selectedFrequency = level

        // 更新UI
        layoutLeft.setBackgroundResource(R.drawable.bg_rounded)
        layoutMiddle.setBackgroundResource(R.drawable.bg_rounded)
        layoutRight.setBackgroundResource(R.drawable.bg_rounded)

        when (level) {
            0 -> {
                layoutLeft.setBackgroundResource(R.drawable.bg_rounded_corner_selected)
                Toast.makeText(this, "低频模式", Toast.LENGTH_SHORT).show()
            }
            1 -> {
                layoutMiddle.setBackgroundResource(R.drawable.bg_rounded_corner_selected)
                Toast.makeText(this, "中频模式", Toast.LENGTH_SHORT).show()
            }
            2 -> {
                layoutRight.setBackgroundResource(R.drawable.bg_rounded_corner_selected)
                Toast.makeText(this, "高频模式", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startBlinking() {
        // 检查是否至少选择了一种光源
        if (!isScreenLightSelected && !isFlashlightSelected) {
            Toast.makeText(this, "请至少选择一种光源", Toast.LENGTH_SHORT).show()
            return
        }

        isBlinking = true
        btnStartBlink.text = "⬜停止闪烁"
        btnSos.isEnabled = false
        btnSos.alpha = 0.8f

        // 根据频率设置闪烁间隔
        val interval = when (selectedFrequency) {
            0 -> 1000L  // 低频：1秒
            1 -> 500L   // 中频：0.5秒
            2 -> 200L   // 高频：0.2秒
            else -> 500L
        }

        val sourceText = buildString {
            if (isScreenLightSelected) append("屏幕补光 ")
            if (isFlashlightSelected) append("手电筒")
        }

        Toast.makeText(this, "开始闪烁：$sourceText", Toast.LENGTH_SHORT).show()

        // 创建闪烁任务
        blinkRunnable = object : Runnable {
            var isOn = false
            override fun run() {
                if (!isBlinking) return

                // 切换状态
                isOn = !isOn

                // 控制选中的光源
                if (isScreenLightSelected) {
                    controlScreenBrightness(isOn)
                }

                if (isFlashlightSelected) {
                    controlFlashlight(isOn)
                }

                // 继续下一轮闪烁
                blinkHandler.postDelayed(this, interval)
            }
        }

        blinkRunnable?.let { blinkHandler.post(it) }
    }

    private fun stopBlinking() {
        isBlinking = false
        btnStartBlink.text = "开始闪烁"
        btnSos.isEnabled = true
        btnSos.alpha = 1.0f
        blinkHandler.removeCallbacksAndMessages(null)

        // 关闭所有光源
        controlScreenBrightness(false)
        controlFlashlight(false)

        Toast.makeText(this, "闪烁已停止", Toast.LENGTH_SHORT).show()
    }

    private fun controlFlashlight(on: Boolean) {
        try {
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId!!, on)
            }
        } catch (e: Exception) {
            // 忽略错误
        }
    }

    private fun controlScreenBrightness(on: Boolean) {
        val layoutParams = window.attributes
        if (on) {
            layoutParams.screenBrightness = 1.0f
        } else {
            layoutParams.screenBrightness = -1.0f
        }
        window.attributes = layoutParams
    }

    override fun onPause() {
        super.onPause()
        // 页面暂停时停止闪烁
        if (isBlinking) {
            stopBlinking()
            TimeRecorder.stopRecording(this, "blink")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBlinking) {
            stopBlinking()
            TimeRecorder.stopRecording(this, "blink")
        }
    }
}