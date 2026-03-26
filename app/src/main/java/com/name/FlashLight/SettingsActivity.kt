package com.name.FlashLight

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.name.FlashLight.utils.PageConstants.PAGE_SETTINGS
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import utils.AutoBrightnessManager
import utils.LanguageManager
import utils.SoundManager
import utils.VibrationManager


class SettingsActivity : BaseActivity() {

    private lateinit var spinnerStartupMode: Spinner
    private lateinit var ivArrowClose: ImageView
    private lateinit var ivArrowStats: ImageView
    private lateinit var spinnerBrightness: Spinner
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var slidingVibration: SlidingButton
    private lateinit var slidingSound: SlidingButton
    private lateinit var slidingAutoBrightness: SlidingButton

    private lateinit var spinnerLanguage: Spinner

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
        setupLanguageSpinner()
        
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
        spinnerLanguage = findViewById(R.id.spinner_language)
    }

    private fun loadSetting() {
        slidingVibration.setCheckedSilently(VibrationManager.isVibrationEnabled(this))
        slidingSound.setCheckedSilently(SoundManager.isSoundEnabled(this))
    }
    private fun setupLanguageSpinner() {
        val languages = LanguageManager.getSupportedLanguages()
        val languageNames = languages.map { it.second }
        val languageCodes = languages.map { it.first }

        val adapter = WhiteTextAdapter(this, languageNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguage.adapter = adapter

        val currentLanguage = LanguageManager.getCurrentLanguage(this)
        val currentPosition = languageCodes.indexOf(currentLanguage)
        if (currentPosition >= 0) {
            spinnerLanguage.setSelection(currentPosition)
        }

        spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLanguageCode = languageCodes[position]
                val currentLang = LanguageManager.getCurrentLanguage(this@SettingsActivity)

                println("当前语言: $currentLang, 选择语言: $selectedLanguageCode")

                if (selectedLanguageCode == currentLang) {
                    println("语言未变化，跳过")
                    return
                }

                // 保存并应用语言
                LanguageManager.applyLanguage(this@SettingsActivity, selectedLanguageCode)

                // 显示提示
                val message = if (selectedLanguageCode == LanguageManager.LANGUAGE_CHINESE) {
                    "语言已切换为中文，应用将重启"
                } else {
                    "Language switched to English, app will restart"
                }
                Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_SHORT).show()

                // 延迟一点再重启，让 Toast 显示
                Handler(Looper.getMainLooper()).postDelayed({
                    LanguageManager.restartApp(this@SettingsActivity)
                }, 500)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupButton() {
        slidingVibration.setOnStateChangedListener { isChecked ->
            VibrationManager.setVibrationEnabled(this, isChecked)
            if (isChecked) VibrationManager.vibrate(slidingVibration)
        }
        slidingSound.setOnStateChangedListener { isChecked ->
            SoundManager.setSoundEnabled(this, isChecked)
            if (isChecked) SoundManager.playClickSound(this)
        }
    }

    private fun loadAutoBrightnessState() {
        slidingAutoBrightness.setCheckedSilently(AutoBrightnessManager.getAutoBrightnessState(this))
    }

    private fun setupAutoBrightnessListener() {
        slidingAutoBrightness.setOnStateChangedListener { isChecked ->
            AutoBrightnessManager.toggleAutoBrightness(
                activity = this,
                targetState = isChecked,
                onSuccess = { },
                onFailure = { slidingAutoBrightness.setCheckedSilently(!isChecked) }
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        AutoBrightnessManager.handlePermissionResult(this, requestCode) { successState ->
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

    private fun setupSpinner() {
        val startupModes = listOf(getString(R.string.remember_last_usage), getString(R.string.main_page), getString(R.string.most_usage))
        val adapter = StartupModeAdapter(this, startupModes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStartupMode.adapter = adapter

        val currentMode = StartupModeManager.getStartupMode(this)
        val position = modeValues.indexOf(currentMode)
        if (position >= 0) spinnerStartupMode.setSelection(position)

        spinnerStartupMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                StartupModeManager.setStartupMode(this@SettingsActivity, modeValues[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupBrightnessSpinner() {
        val adapter = WhiteTextAdapter(this, listOf(getString(R.string.brightness_low), getString(R.string.brightness_medium), getString(R.string.brightness_high)))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerBrightness.adapter = adapter

        val prefs = getSharedPreferences("brightness_settings", Context.MODE_PRIVATE)
        val savedBrightness = prefs.getInt("default_brightness", 1)
        val position = listOf(0, 1, 2).indexOf(savedBrightness)
        if (position >= 0) spinnerBrightness.setSelection(position)

        spinnerBrightness.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putInt("default_brightness", listOf(0, 1, 2)[position]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupClickListeners() {
        ivArrowClose.setOnClickListener { startActivity(Intent(this, AutomaticActivity::class.java)) }
        ivArrowStats.setOnClickListener { startActivity(Intent(this, StatsActivity::class.java)) }
    }
}