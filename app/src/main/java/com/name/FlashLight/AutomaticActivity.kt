package com.name.FlashLight

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.RadioGroup
import android.widget.Toast
import com.name.FlashLight.databinding.AutomaticBinding
import com.name.FlashLight.databinding.SettingsBinding

class AutomaticActivity : BaseActivity<AutomaticBinding>() {

    private lateinit var prefs: SharedPreferences
    private val PREFS_NAME = "auto_off_settings"

    companion object {
        const val KEY_FLASHLIGHT_TIME = "flashlight_auto_off"
        const val KEY_SCREEN_LIGHT_TIME = "screen_light_auto_off"
        const val KEY_BLINK_TIME = "blink_auto_off"

        const val TIME_1_MIN = 1
        const val TIME_5_MIN = 5
        const val TIME_10_MIN = 10
        const val TIME_NEVER = 114514
    }

    override fun createBinding():AutomaticBinding{
        return AutomaticBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. 初始化 ViewBinding
        binding = AutomaticBinding.inflate(layoutInflater)

        initSharedPreferences()
        loadSavedSettings()
        setupClickListeners()
    }

    private fun initSharedPreferences() {
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun loadSavedSettings() {
        // 加载手电筒设置
        val flashTime = prefs.getInt(KEY_FLASHLIGHT_TIME, TIME_5_MIN)
        selectRadioButton(binding.flashRadioGroup, flashTime)

        // 加载屏幕补光设置
        val screenTime = prefs.getInt(KEY_SCREEN_LIGHT_TIME, TIME_5_MIN)
        selectRadioButton(binding.screenRadioGroup, screenTime)

        // 加载闪烁设置
        val blinkTime = prefs.getInt(KEY_BLINK_TIME, TIME_5_MIN)
        selectRadioButton(binding.blinkRadioGroup, blinkTime)
    }

    private fun selectRadioButton(radioGroup: RadioGroup, timeValue: Int) {
        val radioButtonId = when (timeValue) {
            TIME_1_MIN -> {
                when (radioGroup) {
                    binding.flashRadioGroup -> R.id.flash1min
                    binding.screenRadioGroup -> R.id.screen1min
                    binding.blinkRadioGroup -> R.id.blink1min
                    else -> null
                }
            }
            TIME_5_MIN -> {
                when (radioGroup) {
                    binding.flashRadioGroup -> R.id.flash5min
                    binding.screenRadioGroup -> R.id.screen5min
                    binding.blinkRadioGroup -> R.id.blink5min
                    else -> null
                }
            }
            TIME_10_MIN -> {
                when (radioGroup) {
                    binding.flashRadioGroup -> R.id.flash10min
                    binding.screenRadioGroup -> R.id.screen10min
                    binding.blinkRadioGroup -> R.id.blink10min
                    else -> null
                }
            }
            TIME_NEVER -> {
                when (radioGroup) {
                    binding.flashRadioGroup -> R.id.flashNever
                    binding.screenRadioGroup -> R.id.screenNever
                    binding.blinkRadioGroup -> R.id.blinkNever
                    else -> null
                }
            }
            else -> null
        }

        radioButtonId?.let { id ->
            radioGroup.check(id)
        }
    }

    private fun setupClickListeners() {
        binding.traceback.setOnClickListener {
            saveAllSettings()
            finish()
        }

        binding.flashRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            saveFlashlightSetting(checkedId)
        }

        binding.screenRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            saveScreenLightSetting(checkedId)
        }

        binding.blinkRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            saveBlinkSetting(checkedId)
        }
    }

    private fun saveFlashlightSetting(checkedId: Int) {
        val timeValue = when (checkedId) {
            R.id.flash1min -> TIME_1_MIN
            R.id.flash5min -> TIME_5_MIN
            R.id.flash10min -> TIME_10_MIN
            R.id.flashNever -> TIME_NEVER
            else -> TIME_5_MIN
        }
        prefs.edit().putInt(KEY_FLASHLIGHT_TIME, timeValue).apply()
        showSaveToast(getString(R.string.title_flashlight), timeValue)
    }

    private fun saveScreenLightSetting(checkedId: Int) {
        val timeValue = when (checkedId) {
            R.id.screen1min -> TIME_1_MIN
            R.id.screen5min -> TIME_5_MIN
            R.id.screen10min -> TIME_10_MIN
            R.id.screenNever -> TIME_NEVER
            else -> TIME_5_MIN
        }
        prefs.edit().putInt(KEY_SCREEN_LIGHT_TIME, timeValue).apply()
        showSaveToast(getString(R.string.title_screen_light), timeValue)
    }

    private fun saveBlinkSetting(checkedId: Int) {
        val timeValue = when (checkedId) {
            R.id.blink1min -> TIME_1_MIN
            R.id.blink5min -> TIME_5_MIN
            R.id.blink10min -> TIME_10_MIN
            R.id.blinkNever -> TIME_NEVER
            else -> TIME_5_MIN
        }
        prefs.edit().putInt(KEY_BLINK_TIME, timeValue).apply()
        showSaveToast(getString(R.string.title_blink), timeValue)
    }

    private fun saveAllSettings() {
        val flashTime = getTimeFromRadioGroup(binding.flashRadioGroup)
        val screenTime = getTimeFromRadioGroup(binding.screenRadioGroup)
        val blinkTime = getTimeFromRadioGroup(binding.blinkRadioGroup)

        prefs.edit().apply {
            putInt(KEY_FLASHLIGHT_TIME, flashTime)
            putInt(KEY_SCREEN_LIGHT_TIME, screenTime)
            putInt(KEY_BLINK_TIME, blinkTime)
            apply()
        }

        Toast.makeText(this, getString(R.string.toast_success), Toast.LENGTH_SHORT).show()
    }

    private fun getTimeFromRadioGroup(radioGroup: RadioGroup): Int {
        val checkedId = radioGroup.checkedRadioButtonId
        return when (checkedId) {
            R.id.flash1min, R.id.screen1min, R.id.blink1min -> TIME_1_MIN
            R.id.flash5min, R.id.screen5min, R.id.blink5min -> TIME_5_MIN
            R.id.flash10min, R.id.screen10min, R.id.blink10min -> TIME_10_MIN
            R.id.flashNever, R.id.screenNever, R.id.blinkNever -> TIME_NEVER
            else -> TIME_5_MIN
        }
    }

    private fun showSaveToast(feature: String, timeValue: Int) {
        val timeText = when (timeValue) {
            TIME_1_MIN -> getString(R.string.auto_off_1)
            TIME_5_MIN -> getString(R.string.auto_off_5)
            TIME_10_MIN -> getString(R.string.auto_off_10)
            TIME_NEVER -> getString(R.string.auto_off_never)
            else -> getString(R.string.auto_off_5)
        }
        Toast.makeText(this, "$feature 已设为 $timeText", Toast.LENGTH_SHORT).show()
    }
}