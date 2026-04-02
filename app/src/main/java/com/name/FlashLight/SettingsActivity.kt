package com.name.FlashLight

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import androidx.lifecycle.lifecycleScope
import com.name.FlashLight.databinding.SettingsBinding
import com.name.FlashLight.utils.PageConstants.PAGE_SETTINGS
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import utils.AutoBrightnessManager
import utils.DataStoreManager
import utils.DataStoreManager.setDefaultBrightness
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

    override fun createBinding(): SettingsBinding {
        return SettingsBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        PageUsageRecorder.recordPageVisit(this, PAGE_SETTINGS)
        StartupModeManager.recordLastPage(this, PAGE_SETTINGS)

        setupBottomNavigation()
        setupStartupModeSpinner()
        setupClickListeners()
        setupBrightnessSpinner()
        
        // 【核心修改】：观察 DataStore 里的配置
        observeSettings()
        
        setupButtons()
        setupLanguageSpinner()

        loadAutoBrightnessState()
        setupAutoBrightnessListener()

        binding.traceback.setOnClickListener {
            handleBackPress()
        }
        
        binding.bottomNav.selectedItemId = R.id.nav_settings
    }

    /**
     * 【工业级建议】
     * 对于开关（Switch/SlidingButton），建议用 collectLatest 保持实时同步。
     * 对于下拉框（Spinner），由于它们通常涉及页面跳转或重启，用 collectLatest 反而容易干扰用户操作。
     */
    private fun observeSettings() {
        // 1. 监听震动
        lifecycleScope.launch {
            DataStoreManager.isVibrationEnabled(this@SettingsActivity).collectLatest {
                binding.btnVibration.setCheckedSilently(it)
            }
        }
        // 2. 监听声音
        lifecycleScope.launch {
            DataStoreManager.isSoundEnabled(this@SettingsActivity).collectLatest {
                binding.slidingSound.setCheckedSilently(it)
            }
        }
        // 3. 监听亮度（可选：如果想实时同步，也可以放在这里）
        lifecycleScope.launch {
            DataStoreManager.getDefaultBrightness(this@SettingsActivity).collectLatest { brightness ->
                val pos = listOf(0, 1, 2).indexOf(brightness)
                if (pos >= 0 && binding.spinnerBrightness.selectedItemPosition != pos) {
                    binding.spinnerBrightness.setSelection(pos)
                }
            }
        }
    }

    private fun setupButtons() {
        // 震动开关逻辑
        binding.btnVibration.setOnStateChangedListener { isEnabled ->
            // 开关操作建议强制反馈，给用户最直接的“确认感”
            VibrationManager.vibrate(binding.btnVibration, forceEnabled = true)

            lifecycleScope.launch {
                DataStoreManager.setVibrationEnabled(this@SettingsActivity, isEnabled)
            }
        }
        
        // 声音开关逻辑
        binding.slidingSound.setOnStateChangedListener { isEnabled ->
            SoundManager.playClickSound(this@SettingsActivity, forceEnabled = true)

            lifecycleScope.launch {
                DataStoreManager.setSoundEnabled(this@SettingsActivity, isEnabled)
            }
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
    private fun setupStartupModeSpinner() {
        val startupModes = listOf(getString(R.string.remember_last_usage), getString(R.string.main_page), getString(R.string.most_usage))
        val adapter = StartupModeAdapter(this, startupModes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerStartupMode.adapter = adapter

        // 从 DataStore 读取初始值
        lifecycleScope.launch {
            val currentMode = DataStoreManager.getStartupMode(this@SettingsActivity).first()
            val position = modeValues.indexOf(currentMode)
            if (position >= 0) binding.spinnerStartupMode.setSelection(position)
        }

        binding.spinnerStartupMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newMode = modeValues[position]
                lifecycleScope.launch {
                    DataStoreManager.setStartupMode(this@SettingsActivity, newMode)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupBrightnessSpinner() {
        val adapter = WhiteTextAdapter(this, listOf(getString(R.string.brightness_low), getString(R.string.brightness_medium), getString(R.string.brightness_high)))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerBrightness.adapter = adapter

        binding.spinnerBrightness.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val levels = listOf(0, 1, 2)
                lifecycleScope.launch {
                    DataStoreManager.setDefaultBrightness(this@SettingsActivity, levels[position])
                }
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

        lifecycleScope.launch {
            val currentLang = DataStoreManager.getLanguage(this@SettingsActivity).first()
            val position = languages.indexOfFirst { it.first == currentLang }
            if (position >= 0) binding.spinnerLanguage.setSelection(position, false)
        }

        binding.spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLanguage = languages[position].first
                lifecycleScope.launch {
                    val currentStored = DataStoreManager.getLanguage(this@SettingsActivity).first()
                    if (selectedLanguage != currentStored) {
                        DataStoreManager.setLanguage(this@SettingsActivity, selectedLanguage)
                        // 语言切换通常需要重启 App 刷新 Resources
                        LanguageManager.saveLanguage(this@SettingsActivity, selectedLanguage) // 暂时保留 Manager 用于兼容重启逻辑
                        LanguageManager.restartApp(this@SettingsActivity)
                    }
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
