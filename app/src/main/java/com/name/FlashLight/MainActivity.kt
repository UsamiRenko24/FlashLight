package com.name.FlashLight

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomnavigation.BottomNavigationView
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
                    // ✅ 修复：先创建 intent 变量
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
    private fun setupClickListeners() {
        // 点击中间的大手电筒按钮
        findViewById<Button>(R.id.flashlight).setOnClickListener {
            it.feedback()
            val intent = Intent(this, FlashlightActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE1)
        }

        // 点击屏幕补光卡片
        findViewById<LinearLayout>(R.id.layout_screen_light).setOnClickListener {
            it.feedback()
            startActivity(Intent(this, ScreenLightActivity::class.java))
        }

        // 点击闪烁卡片
        findViewById<LinearLayout>(R.id.layout_blink).setOnClickListener {
            it.feedback()
            // ✅ 修复：先创建 intent 变量
            val intent = Intent(this, BlinkActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE2)
        }

        // 点击设置按钮
        findViewById<ImageView>(R.id.iv_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
    private fun updateStats() {
        // 获取各功能今日使用时间（分钟）
        val flashlightTime = TimeRecorder.getTodayTime(this, "flashlight")
        tvFlashlightTime.text = formatTime(flashlightTime)
    }
    private fun formatTime(minutes: Float): String {
        val totalSeconds = (minutes * 60).toInt()

        // 计算分钟和秒
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60

        // 格式化为两位数，不足补0
        return String.format("%02d:%02d", mins, secs)
    }
    private fun updateBatteryDisplay() {
        // ✅ 一行代码更新所有电池UI
        BatteryHelper.updateBatteryUI(this, tvBatteryPercent, tvBatteryStatus, ivBatteryIcon)
    }
    private fun startBatteryMonitor() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                updateBatteryDisplay()
                updateStats()
                handler.postDelayed(this, 5000) // 每5秒更新一次
            }
        }
        handler.post(runnable)
    }
}