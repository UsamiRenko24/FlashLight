package com.name.flashlight

import android.content.Intent
import android.view.View
import android.widget.ListPopupWindow
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.name.flashlight.databinding.SettingsBinding
import com.name.flashlight.integration.language.MultiLanguages
import com.name.flashlight.utils.DataStoreManager
import com.name.flashlight.utils.PageConstants
import com.name.flashlight.utils.SoundManager
import com.name.flashlight.utils.StartupModeManager
import com.name.flashlight.utils.VibrationManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.PopupWindow

/**
 * SettingsActivity
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

    /**
     * 语言列表
     */
    private val languages = listOf(
        "en" to "English",
        "de" to "Deutsch",
        "el" to "Ελληνικά",
        "es" to "Español",
        "fr" to "Français",
        "hi" to "हिन्दी",
        "hu" to "Magyar",
        "it" to "Italiano",
        "ja" to "日本語",
        "ko" to "한국어",
        "nl" to "Nederlands",
        "pl" to "Polski",
        "pt" to "Português",
        "ru" to "Русский",
        "sv" to "Svenska",
        "th" to "ไทย",
        "tr" to "Türkçe",
        "vi" to "Tiếng Việt",
        "zh" to "繁體中文",
        "fa" to "فارسی"
    )

    override fun createBinding(): SettingsBinding {
        return SettingsBinding.inflate(layoutInflater)
    }

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

        binding.bottomNav.selectedItemId =
            R.id.nav_settings
    }

    override fun initListeners() {

        binding.bottomNav.setOnItemSelectedListener {
            handleNavigation(it.itemId)
        }

        /**
         * 震动
         */
        binding.btnVibration.setOnStateChangedListener { isEnabled ->

            VibrationManager.vibrate(
                binding.btnVibration,
                forceEnabled = true
            )

            lifecycleScope.launch {

                DataStoreManager.setVibrationEnabled(
                    this@SettingsActivity,
                    isEnabled
                )
            }
        }

        /**
         * 声音
         */
        binding.slidingSound.setOnStateChangedListener { isEnabled ->

            SoundManager.playClickSound(
                this@SettingsActivity,
                forceEnabled = true
            )

            lifecycleScope.launch {

                DataStoreManager.setSoundEnabled(
                    this@SettingsActivity,
                    isEnabled
                )
            }
        }

        /**
         * 启动模式
         */
        binding.tvStartupMode.setOnClickListener {

            val items = listOf(
                getString(R.string.remember_last_usage),
                getString(R.string.main_page),
                getString(R.string.most_usage)
            )


        }

        /**
         * 默认亮度
         */
        binding.tvBrightness.setOnClickListener {

            val items = listOf(
                getString(R.string.brightness_low),
                getString(R.string.brightness_medium),
                getString(R.string.brightness_high)
            )

            showCenteredPopup(it, items) { pos ->

                lifecycleScope.launch {

                    DataStoreManager.setDefaultBrightness(
                        this@SettingsActivity,
                        pos
                    )
                }
            }
        }

        /**
         * 语言切换
         */
        val openLanguageSelector: (View) -> Unit = { anchor ->

            val labels =
                languages.map { it.second }

            showCenteredPopup(anchor, labels) { pos ->

                lifecycleScope.launch {

                    val languageCode =
                        languages[pos].first

                    /**
                     * 保存语言
                     */
                    DataStoreManager.setLanguage(
                        this@SettingsActivity,
                        languageCode
                    )

                    /**
                     * 转 Locale
                     */
                    val locale = when (languageCode) {

                        "de" -> Locale("de")
                        "el" -> Locale("el")
                        "es" -> Locale("es")
                        "fr" -> Locale("fr")
                        "hi" -> Locale("hi")
                        "hu" -> Locale("hu")
                        "it" -> Locale("it")
                        "ja" -> Locale("ja")
                        "ko" -> Locale("ko")
                        "nl" -> Locale("nl")
                        "pl" -> Locale("pl")
                        "pt" -> Locale("pt")
                        "ru" -> Locale("ru")
                        "sv" -> Locale("sv")
                        "th" -> Locale("th")
                        "tr" -> Locale("tr")
                        "vi" -> Locale("vi")
                        "fa" -> Locale("fa")

                        /**
                         * 繁体中文
                         */
                        "zh-rTW" -> Locale("zh", "TW")

                        /**
                         * 简体中文
                         */
                        "zh" -> Locale.SIMPLIFIED_CHINESE

                        else -> Locale.ENGLISH
                    }

                    /**
                     * 应用语言
                     */
                    MultiLanguages.setAppLanguage(
                        this@SettingsActivity,
                        locale
                    )

                    /**
                     * 重启 Activity
                     */
                    recreate()
                }
            }
        }

        binding.cardContainer5.setOnClickListener {
            openLanguageSelector(binding.tvLanguage)
        }

        binding.tvLanguage.setOnClickListener(
            openLanguageSelector
        )

        /**
         * 自动亮度
         */
        binding.slidingAutoBrightness
            .setOnStateChangedListener { isChecked ->

                lifecycleScope.launch {

                    DataStoreManager.setUseSystemAutoBrightness(
                        this@SettingsActivity,
                        isChecked
                    )
                }
            }

        /**
         * 自动关闭页面
         */
        binding.layoutAutoOff.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    AutomaticActivity::class.java
                )
            )
        }

        /**
         * 隐私政策
         */
        binding.privacyPolicy.setOnClickListener {

            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://www.baidu.com")
                )
            )
        }
    }

    /**
     * Popup
     */
    private fun showCenteredPopup(
        anchor: View,
        items: List<String>,
        onSelected: (Int) -> Unit
    ) {

        val listView = ListView(this)

        val adapter = if (anchor.id == R.id.tv_startup_mode) {

            StartupModeAdapter(
                this,
                items
            )

        } else {

            WhiteTextAdapter(
                this,
                items
            )
        }

        listView.adapter = adapter

        val popupWidth = 400

        val popupWindow = PopupWindow(
            listView,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.setBackgroundDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.bg_spinner_popup_gold
            )
        )

        popupWindow.isOutsideTouchable = true

        listView.setOnItemClickListener { _, _, position, _ ->

            onSelected(position)

            popupWindow.dismiss()
        }

        /**
         * 始终从下方弹出
         */
        popupWindow.showAsDropDown(
            anchor,
            (anchor.width - popupWidth) / 2,
            10
        )
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

            DataStoreManager
                .isVibrationEnabled(this@SettingsActivity)
                .collectLatest {

                    binding.btnVibration
                        .setCheckedSilently(it)
                }
        }

        lifecycleScope.launch {

            DataStoreManager
                .isSoundEnabled(this@SettingsActivity)
                .collectLatest {

                    binding.slidingSound
                        .setCheckedSilently(it)
                }
        }

        lifecycleScope.launch {

            DataStoreManager
                .getStartupMode(this@SettingsActivity)
                .collectLatest { mode ->

                    val index =
                        modeValues.indexOf(mode)
                            .coerceAtLeast(0)

                    val startupShorts = listOf(
                        getString(R.string.remember_last_usage_short),
                        getString(R.string.main_page_short),
                        getString(R.string.most_usage_short)
                    )

                    binding.tvStartupMode.text =
                        startupShorts[index]
                }
        }

        lifecycleScope.launch {

            DataStoreManager
                .getDefaultBrightness(this@SettingsActivity)
                .collectLatest { brightness ->

                    val brightnessTexts = listOf(
                        getString(R.string.brightness_low),
                        getString(R.string.brightness_medium),
                        getString(R.string.brightness_high)
                    )

                    binding.tvBrightness.text =
                        brightnessTexts.getOrElse(brightness) { "" }
                }
        }

        /**
         * 当前语言显示
         */
        lifecycleScope.launch {

            DataStoreManager
                .getLanguage(this@SettingsActivity)
                .collectLatest { lang ->

                    val langName =
                        languages.find {
                            it.first == lang
                        }?.second ?: "English"

                    binding.tvLanguage.text =
                        langName
                }
        }
    }

    /**
     * BottomNavigation
     */
    private fun handleNavigation(
        itemId: Int
    ): Boolean {

        if (itemId == R.id.nav_settings) {
            return true
        }

        val intent = when (itemId) {

            R.id.nav_home ->
                Intent(this, MainActivity::class.java)

            R.id.nav_flashlight ->
                Intent(this, FlashlightActivity::class.java)

            R.id.nav_blink ->
                Intent(this, BlinkActivity::class.java)

            R.id.nav_stats ->
                Intent(this, StatsActivity::class.java)

            else -> null
        }

        intent?.let {

            startActivity(it)

            finish()

            overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )

            return false
        }

        return false
    }
}