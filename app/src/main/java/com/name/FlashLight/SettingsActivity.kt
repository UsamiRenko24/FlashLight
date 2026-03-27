package com.name.FlashLight

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import com.name.FlashLight.databinding.SettingsBinding
import com.name.FlashLight.utils.PageConstants.PAGE_SETTINGS
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import utils.AutoBrightnessManager
import utils.LanguageManager
import utils.SoundManager
import utils.VibrationManager


class SettingsActivity : BaseActivity<SettingsBinding>() {

    private val REQ_FLASHLIGHT = 1001
    private val REQ_BLINK = 1002

    private val modeValues = listOf(
        StartupModeManager.MODE_LAST_USED,
        StartupModeManager.MODE_HOME,
        StartupModeManager.MODE_MOST_USED
    )

    override fun createBinding():SettingsBinding{
      return SettingsBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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

        // 使用 binding 访问 traceback
        binding.traceback.setOnClickListener {
            handleBackPress()
        }
        
        binding.bottomNav.selectedItemId = R.id.nav_settings
    }

    private fun loadSetting() {
        binding.btnVibration.setCheckedSilently(VibrationManager.isVibrationEnabled(this))
        binding.slidingSound.setCheckedSilently(SoundManager.isSoundEnabled(this))
    }

    private fun setupButton() {
        binding.btnVibration.setOnStateChangedListener { isChecked ->
            VibrationManager.setVibrationEnabled(this, isChecked)
            if (isChecked) VibrationManager.vibrate(binding.btnVibration)
        }
        binding.slidingSound.setOnStateChangedListener { isChecked ->
            SoundManager.setSoundEnabled(this, isChecked)
            if (isChecked) SoundManager.playClickSound(this)
        }
    }

    private fun loadAutoBrightnessState() {
        binding.slidingAutoBrightness.setCheckedSilently(AutoBrightnessManager.getAutoBrightnessState(this))
    }

    private fun setupAutoBrightnessListener() {
        binding.slidingAutoBrightness.setOnStateChangedListener { isChecked ->
            AutoBrightnessManager.toggleAutoBrightness(
                activity = this,
                targetState = isChecked,
                onSuccess = { },
                onFailure = { binding.slidingAutoBrightness.setCheckedSilently(!isChecked) }
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        AutoBrightnessManager.handlePermissionResult(this, requestCode) { successState ->
            binding.slidingAutoBrightness.setCheckedSilently(successState)
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { menuItem ->
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
        binding.spinnerStartupMode.adapter = adapter

        val currentMode = StartupModeManager.getStartupMode(this)
        val position = modeValues.indexOf(currentMode)
        if (position >= 0) binding.spinnerStartupMode.setSelection(position)

        binding.spinnerStartupMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                StartupModeManager.setStartupMode(this@SettingsActivity, modeValues[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupBrightnessSpinner() {
        val adapter = WhiteTextAdapter(this, listOf(getString(R.string.brightness_low), getString(R.string.brightness_medium), getString(R.string.brightness_high)))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerBrightness.adapter = adapter

        val prefs = getSharedPreferences("brightness_settings", Context.MODE_PRIVATE)
        val savedBrightness = prefs.getInt("default_brightness", 1)
        val position = listOf(0, 1, 2).indexOf(savedBrightness)
        if (position >= 0) binding.spinnerBrightness.setSelection(position)

        binding.spinnerBrightness.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putInt("default_brightness", listOf(0, 1, 2)[position]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupLanguageSpinner() {
        val languages = LanguageManager.getSupportedLanguages()
        val displayNames = languages.map { it.second }
        val adapter = WhiteTextAdapter(this, displayNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguage.adapter = adapter

        val currentLanguage = LanguageManager.getCurrentLanguage(this)
        val position = languages.indexOfFirst { it.first == currentLanguage }
        if (position >= 0) {
            binding.spinnerLanguage.setSelection(position, false)
        }

        binding.spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLanguage = languages[position].first
                val currentLang = LanguageManager.getCurrentLanguage(this@SettingsActivity)
                if (selectedLanguage != currentLang) {
                    LanguageManager.saveLanguage(this@SettingsActivity, selectedLanguage)
                    LanguageManager.restartApp(this@SettingsActivity)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupClickListeners() {
        binding.arrowClose.setOnClickListener { startActivity(Intent(this, AutomaticActivity::class.java)) }
        binding.arrowStats.setOnClickListener { startActivity(Intent(this, StatsActivity::class.java)) }
    }
}
