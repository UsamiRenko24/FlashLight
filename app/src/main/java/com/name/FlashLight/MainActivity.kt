package com.name.FlashLight

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomnavigation.BottomNavigationView
import utils.BatteryHelper
import utils.TimeRecorder
import utils.VibrationManager
import utils.feedback

class MainActivity : BaseActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private val REQUEST_CODE1 = 1001
    private val REQUEST_CODE2 = 1002

    // 电池相关 UI
    private lateinit var tvBatteryPercent: TextView
    private lateinit var tvBatteryStatus: TextView
    private lateinit var ivBatteryIcon: ImageView
    private lateinit var tvFlashlightTime: TextView
    private lateinit var slidingVibration: SlidingButton
    private lateinit var tvTotalTime: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        updateBatteryDisplay()
        updateStats()
        startBatteryMonitor()
        setupBottomNavigation()
        setupClickListeners()
        loadVibrationSetting()
        setupVibrationButton()

        // 设置默认选中首页
        bottomNav.selectedItemId = R.id.nav_home
    }

    private fun initViews() {
        bottomNav = findViewById(R.id.bottom_nav)

        tvBatteryPercent = findViewById(R.id.tv_battery_percent)
        tvBatteryStatus = findViewById(R.id.tv_battery_status)
        ivBatteryIcon = findViewById(R.id.iv_battery_icon)
        tvFlashlightTime = findViewById(R.id.tv_flashlight_time)
        slidingVibration = findViewById(R.id.btn_switch)
        tvTotalTime = findViewById(R.id.tv_time)
    }

    private fun syncVibrationUI() {
        val enabled = VibrationManager.isVibrationEnabled(this)
        slidingVibration.setCheckedSilently(enabled)
    }

    override fun onResume() {
        super.onResume()
        syncVibrationUI() 
    }

    private fun formatMinutes(minutes: Int): String {
        return if (minutes >= 60) {
            "${minutes / 60}${getString(R.string.hour)}${minutes % 60}${getString(R.string.minute)}"
        } else {
            "${minutes}${getString(R.string.minute)}"
        }
    }

    private fun getAutoOffTime(): Int {
        val prefs = getSharedPreferences("auto_off_settings", Context.MODE_PRIVATE)
        return prefs.getInt(AutomaticActivity.KEY_FLASHLIGHT_TIME, 5)
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> true
                R.id.nav_flashlight -> {
                    val intent = Intent(this, FlashlightActivity::class.java)
                    startActivityForResult(intent, REQUEST_CODE1)
                    false
                }
                R.id.nav_blink -> {
                    val intent = Intent(this, BlinkActivity::class.java)
                    startActivityForResult(intent, REQUEST_CODE2)
                    false
                }
                R.id.nav_stats -> {
                    startActivity(Intent(this, StatsActivity::class.java))
                    false
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    false
                }
                else -> false
            }
        }
    }

    private fun loadVibrationSetting() {
        slidingVibration.isChecked = VibrationManager.isVibrationEnabled(this)
    }

    private fun setupVibrationButton() {
        slidingVibration.setOnStateChangedListener { isChecked ->
            VibrationManager.setVibrationEnabled(this, isChecked)
            if (isChecked) {
                VibrationManager.vibrate(slidingVibration)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        val button = findViewById<Button>(R.id.flashlight)

        // 【修复】集成物理动效与 Accessibility 支持
        button.setOnTouchListener { view, event ->
            val isInside = event.x >= 0 && event.x <= view.width && event.y >= 0 && event.y <= view.height
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start()
                    true // 开始拦截触摸流
                }
                MotionEvent.ACTION_MOVE -> {
                    val targetScale = if (isInside) 0.92f else 1.0f
                    view.animate().scaleX(targetScale).scaleY(targetScale).setDuration(100).start()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(200)
                        .setInterpolator(OvershootInterpolator()).start()
                    if (isInside) {
                        // 【核心修复】手动触发 performClick 以支持无障碍并执行 setOnClickListener
                        view.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                    true
                }
                else -> false
            }
        }

        // 点击监听逻辑保持不变
        button.setOnClickListener {
            it.feedback()
            startActivity(Intent(this, FlashlightActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.layout_screen_light).setOnClickListener {
            it.feedback()
            startActivity(Intent(this, ScreenLightActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.layout_blink).setOnClickListener {
            it.feedback()
            val intent = Intent(this, BlinkActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE2)
        }

        findViewById<ImageView>(R.id.iv_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun updateStats() {
        val flashlightTime = TimeRecorder.getTodayTime(this, "flashlight")
        tvFlashlightTime.text = formatTime(flashlightTime)
        if (getAutoOffTime() >= 114514) {
            tvTotalTime.text = getString(R.string.auto_off_never)
        }
    }

    private fun formatTime(minutes: Float): String {
        val totalSeconds = (minutes * 60).toInt()
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    private fun updateBatteryDisplay() {
        BatteryHelper.updateBatteryUI(this, tvBatteryPercent, tvBatteryStatus, ivBatteryIcon)
    }

    private fun startBatteryMonitor() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                updateBatteryDisplay()
                updateStats()
                handler.postDelayed(this, 5000)
            }
        }
        handler.post(runnable)
    }
}