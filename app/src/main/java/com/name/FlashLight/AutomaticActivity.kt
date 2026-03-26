package com.name.FlashLight

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast

class AutomaticActivity : BaseActivity() {

    // UI 组件
    private lateinit var ivTraceback: ImageView
    private lateinit var tvTitle: TextView

    // 手电筒 RadioGroup
    private lateinit var flashRadioGroup: RadioGroup
    private lateinit var flash1min: RadioButton
    private lateinit var flash5min: RadioButton
    private lateinit var flash10min: RadioButton
    private lateinit var flashNever: RadioButton

    // 屏幕补光 RadioGroup
    private lateinit var screenRadioGroup: RadioGroup
    private lateinit var screen1min: RadioButton
    private lateinit var screen5min: RadioButton
    private lateinit var screen10min: RadioButton
    private lateinit var screenNever: RadioButton

    // 闪烁 RadioGroup
    private lateinit var blinkRadioGroup: RadioGroup
    private lateinit var blink1min: RadioButton
    private lateinit var blink5min: RadioButton
    private lateinit var blink10min: RadioButton
    private lateinit var blinkNever: RadioButton

    // SharedPreferences
    private lateinit var prefs: SharedPreferences
    private val PREFS_NAME = "auto_off_settings"

    companion object {
        // 键名常量
        const val KEY_FLASHLIGHT_TIME = "flashlight_auto_off"
        const val KEY_SCREEN_LIGHT_TIME = "screen_light_auto_off"
        const val KEY_BLINK_TIME = "blink_auto_off"

        // 时间值常量（分钟）
        const val TIME_1_MIN = 1
        const val TIME_5_MIN = 5
        const val TIME_10_MIN = 10
        const val TIME_NEVER = 114514  // -1 表示永不关闭
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.automatic)

        initViews()
        initSharedPreferences()
        loadSavedSettings()
        setupClickListeners()
    }

    private fun initViews() {
        ivTraceback = findViewById(R.id.traceback)
        tvTitle = findViewById(R.id.tv_title)

        // 手电筒
        flashRadioGroup = findViewById(R.id.flash_radio_group)  // 需要在布局中添加这个ID
        flash1min = findViewById(R.id.flash1min)
        flash5min = findViewById(R.id.flash5min)
        flash10min = findViewById(R.id.flash10min)
        flashNever = findViewById(R.id.flashNever)

        // 屏幕补光
        screenRadioGroup = findViewById(R.id.screen_radio_group)  // 需要在布局中添加这个ID
        screen1min = findViewById(R.id.screen1min)
        screen5min = findViewById(R.id.screen5min)
        screen10min = findViewById(R.id.screen10min)
        screenNever = findViewById(R.id.screenNever)

        // 闪烁
        blinkRadioGroup = findViewById(R.id.blink_radio_group)  // 需要在布局中添加这个ID
        blink1min = findViewById(R.id.blink1min)
        blink5min = findViewById(R.id.blink5min)
        blink10min = findViewById(R.id.blink10min)
        blinkNever = findViewById(R.id.blinkNever)
    }

    private fun initSharedPreferences() {
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun loadSavedSettings() {
        // 加载手电筒设置
        val flashTime = prefs.getInt(KEY_FLASHLIGHT_TIME, TIME_5_MIN)
        selectRadioButton(flashRadioGroup, flashTime)

        // 加载屏幕补光设置
        val screenTime = prefs.getInt(KEY_SCREEN_LIGHT_TIME, TIME_5_MIN)
        selectRadioButton(screenRadioGroup, screenTime)

        // 加载闪烁设置
        val blinkTime = prefs.getInt(KEY_BLINK_TIME, TIME_5_MIN)
        selectRadioButton(blinkRadioGroup, blinkTime)
    }

    private fun selectRadioButton(radioGroup: RadioGroup, timeValue: Int) {
        val radioButtonId = when (timeValue) {
            TIME_1_MIN -> {
                when (radioGroup) {
                    flashRadioGroup -> R.id.flash1min
                    screenRadioGroup -> R.id.screen1min
                    blinkRadioGroup -> R.id.blink1min
                    else -> null
                }
            }
            TIME_5_MIN -> {
                when (radioGroup) {
                    flashRadioGroup -> R.id.flash5min
                    screenRadioGroup -> R.id.screen5min
                    blinkRadioGroup -> R.id.blink5min
                    else -> null
                }
            }
            TIME_10_MIN -> {
                when (radioGroup) {
                    flashRadioGroup -> R.id.flash10min
                    screenRadioGroup -> R.id.screen10min
                    blinkRadioGroup -> R.id.blink10min
                    else -> null
                }
            }
            TIME_NEVER -> {
                when (radioGroup) {
                    flashRadioGroup -> R.id.flashNever
                    screenRadioGroup -> R.id.screenNever
                    blinkRadioGroup -> R.id.blinkNever
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
        // 返回按钮
        ivTraceback.setOnClickListener {
            // 返回前保存设置
            saveAllSettings()
            finish()
        }

        // 设置 RadioGroup 的选中监听
        flashRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            saveFlashlightSetting(checkedId)
        }

        screenRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            saveScreenLightSetting(checkedId)
        }

        blinkRadioGroup.setOnCheckedChangeListener { _, checkedId ->
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
        // 获取当前选中的值并保存
        val flashTime = getTimeFromRadioGroup(flashRadioGroup)
        val screenTime = getTimeFromRadioGroup(screenRadioGroup)
        val blinkTime = getTimeFromRadioGroup(blinkRadioGroup)

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

    // 在 Activity 销毁时保存
    override fun onDestroy() {
        super.onDestroy()
        // 已经在点击返回时保存，这里可以不再保存
    }
}