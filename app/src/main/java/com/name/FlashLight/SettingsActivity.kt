package com.name.FlashLight

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.Spinner
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.name.FlashLight.utils.PageConstants.PAGE_SETTINGS
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import utils.AutoBrightnessManager
import utils.SoundManager
import utils.VibrationManager


class SettingsActivity : BaseActivity() {

    // 控件定义
    private lateinit var spinnerStartupMode: Spinner
    private lateinit var ivArrowClose: ImageView
    private lateinit var ivArrowStats: ImageView
    private lateinit var spinnerBrightness: Spinner
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var slidingVibration: SlidingButton
    private lateinit var slidingSound: SlidingButton
    private lateinit var slidingAutoBrightness: SlidingButton

    // 修改：规范 RequestCode 命名，避免冲突
    private val REQ_FLASHLIGHT = 1001
    private val REQ_BLINK = 1002
    
    private val modeValues = listOf(
        StartupModeManager.MODE_LAST_USED,
        StartupModeManager.MODE_HOME,
        StartupModeManager.MODE_MOST_USED
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings)
        
        initViews()
        PageUsageRecorder.recordPageVisit(this, PAGE_SETTINGS)
        StartupModeManager.recordLastPage(this, PAGE_SETTINGS)

        setupBottomNavigation()
        setupSpinner()
        setupClickListeners()
        setupBrightnessSpinner()
        loadSetting()
        setupButton()
        
        // 加载初始状态并设置监听器
        loadAutoBrightnessState()
        setupAutoBrightnessListener()
        
        findViewById<View>(R.id.traceback)?.setOnClickListener {
            handleBackPress()
        }
        
        bottomNav.selectedItemId = R.id.nav_settings
    }

    private fun initViews() {
        bottomNav = findViewById(R.id.bottom_nav)
        spinnerStartupMode = findViewById(R.id.spinner_startup_mode)
        spinnerBrightness = findViewById(R.id.spinner_brightness)
        ivArrowClose = findViewById(R.id.arrow_close)
        ivArrowStats = findViewById(R.id.arrow_stats)
        
        slidingVibration = findViewById(R.id.btn_vibration)
        slidingSound = findViewById(R.id.sliding_sound)
        slidingAutoBrightness = findViewById(R.id.sliding_auto_brightness)
    }

    private fun loadAutoBrightnessState() {
        val isAuto = AutoBrightnessManager.getAutoBrightnessState(this)
        slidingAutoBrightness.setCheckedSilently(isAuto)
    }

    private fun setupAutoBrightnessListener() {
        slidingAutoBrightness.setOnStateChangedListener { isChecked ->
            AutoBrightnessManager.toggleAutoBrightness(
                activity = this,
                targetState = isChecked,
                onSuccess = { /* 状态已由 UI 改变，无需操作 */ },
                onFailure = {
                    // 权限失败或设置失败，静默回滚 UI
                    slidingAutoBrightness.setCheckedSilently(!isChecked)
                }
            )
        }
    }

    // ✅ 修复：处理权限申请后的返回结果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        // 调用管理器来处理权限结果
        AutoBrightnessManager.handlePermissionResult(this, requestCode) { successState ->
            // 如果授权成功并自动应用了设置，同步 UI 状态
            slidingAutoBrightness.setCheckedSilently(successState)
        }
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_settings -> true
                R.id.nav_home -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    false
                }
                R.id.nav_flashlight -> {
                    startActivityForResult(Intent(this, FlashlightActivity::class.java), REQ_FLASHLIGHT)
                    false
                }
                R.id.nav_blink -> {
                    startActivityForResult(Intent(this, BlinkActivity::class.java), REQ_BLINK)
                    false
                }
                R.id.nav_stats -> {
                    startActivity(Intent(this, StatsActivity::class.java))
                    false
                }
                else -> false
            }
        }
    }

    private val brightnessOptions = listOf("低亮度", "中亮度", "高亮度")
    private val brightnessValues = listOf(0, 1, 2)

    private fun setupSpinner() {
        val startupModes = listOf("记住上次使用的功能", "总是启动到主页面", "启动到最常用功能")
        val adapter = StartupModeAdapter(this, startupModes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStartupMode.adapter = adapter

        val currentMode = StartupModeManager.getStartupMode(this)
        val position = modeValues.indexOf(currentMode)
        if (position >= 0) {
            spinnerStartupMode.setSelection(position)
        }

        spinnerStartupMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                StartupModeManager.setStartupMode(this@SettingsActivity, modeValues[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupBrightnessSpinner() {
        val adapter = WhiteTextAdapter(this, brightnessOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerBrightness.adapter = adapter

        val prefs = getSharedPreferences("brightness_settings", Context.MODE_PRIVATE)
        val savedBrightness = prefs.getInt("default_brightness", 1)
        val position = brightnessValues.indexOf(savedBrightness)
        if (position >= 0) {
            spinnerBrightness.setSelection(position)
        }

        spinnerBrightness.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putInt("default_brightness", brightnessValues[position]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadSetting() {
        slidingVibration.isChecked = VibrationManager.isVibrationEnabled(this)
        slidingSound.isChecked = SoundManager.isSoundEnabled(this)
    }

    private fun setupButton() {
        slidingVibration.setOnStateChangedListener { isChecked ->
            VibrationManager.setVibrationEnabled(this, isChecked)
            if (isChecked) {
                VibrationManager.vibrate(slidingVibration)
            }
        }
        slidingSound.setOnStateChangedListener { isChecked ->
            SoundManager.setSoundEnabled(this, isChecked)
            if (isChecked) {
                SoundManager.playClickSound(this@SettingsActivity)
            }
        }
    }

    private fun setupClickListeners() {
        ivArrowClose.setOnClickListener {
            startActivity(Intent(this, AutomaticActivity::class.java))
        }
        ivArrowStats.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }
    }
}