package com.name.flashlight

import android.content.Intent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.name.flashlight.databinding.SettingsBinding
import com.name.flashlight.utils.DataStoreManager
import com.name.flashlight.utils.LanguageManager
import com.name.flashlight.utils.PageConstants
import com.name.flashlight.utils.SoundManager
import com.name.flashlight.utils.StartupModeManager
import com.name.flashlight.utils.VibrationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 整合版 - SettingsActivity
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
        lifecycleScope.launch {

            val enabled =
                DataStoreManager
                    .getUseSystemAutoBrightness(this@SettingsActivity)
                    .first()

            binding.slidingAutoBrightness
                .setCheckedSilently(enabled)
        }
        binding.bottomNav.selectedItemId = R.id.nav_settings
    }

    override fun initListeners() {
        binding.bottomNav.setOnItemSelectedListener { item -> handleNavigation(item.itemId) }

        // 震动与声音开关
        binding.btnVibration.setOnStateChangedListener { isEnabled ->
            VibrationManager.vibrate(binding.btnVibration, forceEnabled = true)
            lifecycleScope.launch { DataStoreManager.setVibrationEnabled(this@SettingsActivity, isEnabled) }
        }
        binding.slidingSound.setOnStateChangedListener { isEnabled ->
            SoundManager.playClickSound(this@SettingsActivity, forceEnabled = true)
            lifecycleScope.launch { DataStoreManager.setSoundEnabled(this@SettingsActivity, isEnabled) }
        }

        binding.tvStartupMode.setOnClickListener {
            val items = listOf(getString(R.string.remember_last_usage), getString(R.string.main_page), getString(R.string.most_usage))
            showCenteredPopup(it, items) { pos ->
                lifecycleScope.launch { DataStoreManager.setStartupMode(this@SettingsActivity, modeValues[pos]) }
            }
        }

        binding.tvBrightness.setOnClickListener {
            val items = listOf(getString(R.string.brightness_low), getString(R.string.brightness_medium), getString(R.string.brightness_high))
            showCenteredPopup(it, items) { pos ->
                lifecycleScope.launch { DataStoreManager.setDefaultBrightness(this@SettingsActivity, pos) }
            }
        }

        binding.tvLanguage.setOnClickListener {
            val languages = LanguageManager.getSupportedLanguages()
            val displayNames = languages.map { it.second }
            showCenteredPopup(it, displayNames) { pos ->
                val selectedLang = languages[pos].first
                lifecycleScope.launch {
                    val current = DataStoreManager.getLanguage(this@SettingsActivity).first()
                    if (selectedLang != current) {
                        DataStoreManager.setLanguage(this@SettingsActivity, selectedLang)
                        lifecycleScope.launch {
                            DataStoreManager.setLanguage(this@SettingsActivity, selectedLang)
                            delay(200) // 等 DataStore 落地
                            LanguageManager.restartApp(this@SettingsActivity)
                        }
                    }
                }
            }
        }

        binding.slidingAutoBrightness.setOnStateChangedListener { isChecked ->

            lifecycleScope.launch {

                DataStoreManager.setUseSystemAutoBrightness(
                    this@SettingsActivity,
                    isChecked
                )
            }
        }

        // 拓宽触发区域：点击整栏进入
        binding.layoutAutoOff.setOnClickListener {
            startActivity(Intent(this, AutomaticActivity::class.java))
        }
        binding.privacyPolicy.setOnClickListener {

            startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.baidu.com"))
            )
        }
    }

    private fun showCenteredPopup(anchor: View, items: List<String>, onSelected: (Int) -> Unit) {
        val popup = android.widget.ListPopupWindow(this).apply {
            if (anchor.id == R.id.tv_startup_mode) {
                setAdapter(StartupModeAdapter(this@SettingsActivity, items))
            } else {
                setAdapter(WhiteTextAdapter(this@SettingsActivity, items))
            }
            anchorView = anchor
            val popupWidth = 400
            width = popupWidth
            setBackgroundDrawable(ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_spinner_popup_gold))
            isModal = true
            horizontalOffset = (anchor.width - popupWidth) / 2
            verticalOffset = 10
            setOnItemClickListener { _, _, position, _ ->
                onSelected(position)
                dismiss()
            }
        }
        popup.show()
    }

    override fun initObservers() {
        lifecycleScope.launch {

            DataStoreManager
                .getUseSystemAutoBrightness(this@SettingsActivity)
                .collectLatest { enabled ->

                    binding.slidingAutoBrightness
                        .setCheckedSilently(enabled)
                }
        }
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
            DataStoreManager.getStartupMode(this@SettingsActivity).collectLatest { mode ->
                val index = modeValues.indexOf(mode).coerceAtLeast(0)
                val startupShorts = listOf(getString(R.string.remember_last_usage_short), getString(R.string.main_page_short), getString(R.string.most_usage_short))
                binding.tvStartupMode.text = startupShorts[index]
            }
        }
        lifecycleScope.launch {
            DataStoreManager.getDefaultBrightness(this@SettingsActivity).collectLatest { brightness ->
                val brightnessTexts = listOf(getString(R.string.brightness_low), getString(R.string.brightness_medium), getString(R.string.brightness_high))
                binding.tvBrightness.text = brightnessTexts.getOrElse(brightness) { "" }
            }
        }
        lifecycleScope.launch {
            DataStoreManager.getLanguage(this@SettingsActivity).collectLatest { lang ->
                val langName = LanguageManager.getSupportedLanguages().find { it.first == lang }?.second ?: ""
                binding.tvLanguage.text = langName
            }
        }
    }

    private fun handleNavigation(itemId: Int): Boolean {
        if (itemId == R.id.nav_settings) return true

        val intent = when (itemId) {
            R.id.nav_home -> Intent(this, MainActivity::class.java)
            R.id.nav_flashlight -> Intent(this, FlashlightActivity::class.java)
            R.id.nav_blink -> Intent(this, BlinkActivity::class.java)
            R.id.nav_stats -> Intent(this, StatsActivity::class.java)
            else -> null
        }

        intent?.let {
            startActivity(it)
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            return false
        }
        return false
    }
}