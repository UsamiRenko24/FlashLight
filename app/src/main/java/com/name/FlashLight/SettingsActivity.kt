package com.name.FlashLight

import android.content.Intent
import android.widget.AdapterView
import androidx.lifecycle.lifecycleScope
import com.name.FlashLight.databinding.SettingsBinding
import com.name.FlashLight.utils.PageConstants
import com.name.FlashLight.utils.StartupModeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import utils.*

/**
 * 工业级职责分离版 - SettingsActivity
 * 已优化导航堆栈处理，消除白屏
 */
class SettingsActivity : BaseActivity<SettingsBinding>() {

    override val pageTrackName = PageConstants.PAGE_SETTINGS
    override val isBatteryMonitorEnabled = false
    override val isLowBatteryCheckEnabled = true

    private val modeValues = listOf(
        StartupModeManager.MODE_LAST_USED,
        StartupModeManager.MODE_HOME,
        StartupModeManager.MODE_MOST_USED
    )

    override fun createBinding(): SettingsBinding = SettingsBinding.inflate(layoutInflater)

    override fun initViews() {
        SoundManager.initSoundPool(this)
        initAdapters()
        binding.slidingAutoBrightness.setCheckedSilently(AutoBrightnessManager.getAutoBrightnessState(this))
        binding.bottomNav.selectedItemId = R.id.nav_settings
    }

    override fun initListeners() {
        binding.traceback.setOnClickListener { handleBackPress() }

        binding.bottomNav.setOnItemSelectedListener { item -> handleNavigation(item.itemId) }

        binding.btnVibration.setOnStateChangedListener { isEnabled ->
            VibrationManager.vibrate(binding.btnVibration, forceEnabled = true)
            lifecycleScope.launch { DataStoreManager.setVibrationEnabled(this@SettingsActivity, isEnabled) }
        }
        binding.slidingSound.setOnStateChangedListener { isEnabled ->
            SoundManager.playClickSound(this@SettingsActivity, forceEnabled = true)
            lifecycleScope.launch { DataStoreManager.setSoundEnabled(this@SettingsActivity, isEnabled) }
        }

        bindSpinnerListeners()

        binding.slidingAutoBrightness.setOnStateChangedListener { isChecked ->
            AutoBrightnessManager.toggleAutoBrightness(this, isChecked, {}, {
                binding.slidingAutoBrightness.setCheckedSilently(!isChecked)
            })
        }

        binding.arrowClose.setOnClickListener { startActivity(Intent(this, AutomaticActivity::class.java)) }
        binding.arrowStats.setOnClickListener { startActivity(Intent(this, StatsActivity::class.java)) }
    }

    override fun initObservers() {
        lifecycleScope.launch {
            DataStoreManager.isVibrationEnabled(this@SettingsActivity).collectLatest {
                binding.btnVibration.setCheckedSilently(it)
            }
        }
        lifecycleScope.launch {
            DataStoreManager.isSoundEnabled(this@SettingsActivity).collectLatest {
                binding.slidingSound.setCheckedSilently(it)
            }
        }
        lifecycleScope.launch {
            DataStoreManager.getDefaultBrightness(this@SettingsActivity).collectLatest { brightness ->
                val pos = listOf(0, 1, 2).indexOf(brightness)
                if (pos >= 0 && binding.spinnerBrightness.selectedItemPosition != pos) {
                    binding.spinnerBrightness.setSelection(pos)
                }
            }
        }
        loadInitialSyncData()
    }

    private fun initAdapters() {
        val startupModes = listOf(getString(R.string.remember_last_usage), getString(R.string.main_page), getString(R.string.most_usage))
        binding.spinnerStartupMode.adapter = StartupModeAdapter(this, startupModes)

        val brightnessLevels = listOf(getString(R.string.brightness_low), getString(R.string.brightness_medium), getString(R.string.brightness_high))
        binding.spinnerBrightness.adapter = WhiteTextAdapter(this, brightnessLevels)

        val displayNames = LanguageManager.getSupportedLanguages().map { it.second }
        binding.spinnerLanguage.adapter = WhiteTextAdapter(this, displayNames)
    }

    private fun bindSpinnerListeners() {
        binding.spinnerStartupMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                lifecycleScope.launch { DataStoreManager.setStartupMode(this@SettingsActivity, modeValues[pos]) }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        binding.spinnerBrightness.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                lifecycleScope.launch { DataStoreManager.setDefaultBrightness(this@SettingsActivity, pos) }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        binding.spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                val selectedLang = LanguageManager.getSupportedLanguages()[pos].first
                lifecycleScope.launch {
                    val current = DataStoreManager.getLanguage(this@SettingsActivity).first()
                    if (selectedLang != current) {
                        DataStoreManager.setLanguage(this@SettingsActivity, selectedLang)
                        LanguageManager.saveLanguage(this@SettingsActivity, selectedLang)
                        LanguageManager.restartApp(this@SettingsActivity)
                    }
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun loadInitialSyncData() {
        lifecycleScope.launch {
            val mode = DataStoreManager.getStartupMode(this@SettingsActivity).first()
            val modePos = modeValues.indexOf(mode)
            if (modePos >= 0) binding.spinnerStartupMode.setSelection(modePos)

            val lang = DataStoreManager.getLanguage(this@SettingsActivity).first()
            val langPos = LanguageManager.getSupportedLanguages().indexOfFirst { it.first == lang }
            if (langPos >= 0) binding.spinnerLanguage.setSelection(langPos, false)
        }
    }

    /**
     * 【工业级优化】：
     * 解决“白屏”和“多余堆栈”问题的关键。
     */
    private fun handleNavigation(itemId: Int): Boolean {
        if (itemId == R.id.nav_settings) return true
        
        val target = when (itemId) {
            R.id.nav_home -> MainActivity::class.java
            R.id.nav_flashlight -> FlashlightActivity::class.java
            R.id.nav_blink -> BlinkActivity::class.java
            R.id.nav_stats -> StatsActivity::class.java
            else -> null
        }
        
        target?.let {
            val intent = Intent(this, it).apply {
                // 关键：CLEAR_TOP 清理上方 Activity，SINGLE_TOP 复用现有实例防止重启
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            // 立即结束当前页面，确保返回栈里不会留下重复的路径
            if (it == MainActivity::class.java) finish()
        }
        return false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        AutoBrightnessManager.handlePermissionResult(this, requestCode) {
            binding.slidingAutoBrightness.setCheckedSilently(it)
        }
    }
}