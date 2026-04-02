package com.name.FlashLight

import android.os.Bundle
import android.widget.RadioGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.name.FlashLight.databinding.AutomaticBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import utils.DataStoreManager

class AutomaticActivity : BaseActivity<AutomaticBinding>() {

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

        // 1. 【核心：监听】使用 DataStore 实时同步 UI
        observeSettings()

        // 2. 设置点击监听
        setupClickListeners()
    }

    /**
     * 响应式监听：从 DataStore 读取数据并自动勾选 RadioButton
     */
    private fun observeSettings() {
        // 监听手电筒自动关闭时间
        lifecycleScope.launch {
            DataStoreManager.getFlashlightAutoOffTime(this@AutomaticActivity).collectLatest { time ->
                selectRadioButton(binding.flashRadioGroup, time)
            }
        }
        // 监听屏幕补光自动关闭时间
        lifecycleScope.launch {
            DataStoreManager.getScreenAutoOffTime(this@AutomaticActivity).collectLatest { time ->
                selectRadioButton(binding.screenRadioGroup, time)
            }
        }
        // 监听闪烁自动关闭时间
        lifecycleScope.launch {
            DataStoreManager.getBlinkAutoOffTime(this@AutomaticActivity).collectLatest { time ->
                selectRadioButton(binding.blinkRadioGroup, time)
            }
        }
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
            finish()
        }

        binding.flashRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            saveSetting(KEY_TYPE_FLASH, checkedId)
        }

        binding.screenRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            saveSetting(KEY_TYPE_SCREEN, checkedId)
        }

        binding.blinkRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            saveSetting(KEY_TYPE_BLINK, checkedId)
        }
    }

    private fun saveSetting(type: Int, checkedId: Int) {
        val timeValue = when (checkedId) {
            R.id.flash1min, R.id.screen1min, R.id.blink1min -> TIME_1_MIN
            R.id.flash5min, R.id.screen5min, R.id.blink5min -> TIME_5_MIN
            R.id.flash10min, R.id.screen10min, R.id.blink10min -> TIME_10_MIN
            R.id.flashNever, R.id.screenNever, R.id.blinkNever -> TIME_NEVER
            else -> TIME_5_MIN
        }

        // 3. 【核心：写入】在协程中异步保存
        lifecycleScope.launch {
            when (type) {
                KEY_TYPE_FLASH -> {
                    DataStoreManager.setFlashlightAutoOffTime(this@AutomaticActivity, timeValue)
                    showSaveToast(getString(R.string.title_flashlight), timeValue)
                }
                KEY_TYPE_SCREEN -> {
                    DataStoreManager.setScreenAutoOffTime(this@AutomaticActivity, timeValue)
                    showSaveToast(getString(R.string.title_screen_light), timeValue)
                }
                KEY_TYPE_BLINK -> {
                    DataStoreManager.setBlinkAutoOffTime(this@AutomaticActivity, timeValue)
                    showSaveToast(getString(R.string.title_blink), timeValue)
                }
            }
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
    private val KEY_TYPE_FLASH = 0
    private val KEY_TYPE_SCREEN = 1
    private val KEY_TYPE_BLINK = 2
}