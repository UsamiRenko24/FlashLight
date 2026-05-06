package com.name.FlashLight

import android.graphics.Color
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.name.FlashLight.databinding.AutomaticBinding
import com.name.FlashLight.utils.PageConstants
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import utils.DataStoreManager
import utils.feedback

/**
 * 工业级模块化自动关闭设置页面
 */
class AutomaticActivity : BaseActivity<AutomaticBinding>() {

    // --- 1. 声明式配置 (父类会自动调用 init 方法，无需在 onCreate 重复) ---
    override val pageTrackName = PageConstants.PAGE_AUTOMATIC
    override val isBatteryMonitorEnabled = false
    override val isLowBatteryCheckEnabled = false

    companion object {
        const val TIME_1_MIN = 1
        const val TIME_5_MIN = 5
        const val TIME_10_MIN = 10
        const val TIME_NEVER = 114514
    }

    private val selectedBlueColor = Color.parseColor("#2AE1F8")

    override fun createBinding(): AutomaticBinding = AutomaticBinding.inflate(layoutInflater)

    override fun initViews() {
        // UI 静态表现已由 XML 渲染
    }

    override fun initListeners() {
        binding.traceback.setOnClickListener { handleBackPress() }

        // 绑定各组卡片点击 (0: Flash, 1: Screen, 2: Blink)
        bindClicks(getFlashlightCards(), 0)
        bindClicks(getScreenCards(), 1)
        bindClicks(getBlinkCards(), 2)
    }

    override fun initObservers() {
        // 监听 DataStore 变化并实时高亮卡片
        lifecycleScope.launch {
            DataStoreManager.getFlashlightAutoOffTime(this@AutomaticActivity).collectLatest { time ->
                refreshUI(getFlashlightCards(), time)
            }
        }
        lifecycleScope.launch {
            DataStoreManager.getScreenAutoOffTime(this@AutomaticActivity).collectLatest { time ->
                refreshUI(getScreenCards(), time)
            }
        }
        lifecycleScope.launch {
            DataStoreManager.getBlinkAutoOffTime(this@AutomaticActivity).collectLatest { time ->
                refreshUI(getBlinkCards(), time)
            }
        }
    }

    private fun bindClicks(cards: Map<Int, LinearLayout>, type: Int) {
        cards.forEach { (time, view) ->
            view.setOnClickListener {
                it.feedback()
                saveSetting(type, time)
            }
        }
    }

    private fun saveSetting(type: Int, time: Int) {
        lifecycleScope.launch {
            when (type) {
                0 -> DataStoreManager.setFlashlightAutoOffTime(this@AutomaticActivity, time)
                1 -> DataStoreManager.setScreenAutoOffTime(this@AutomaticActivity, time)
                2 -> DataStoreManager.setBlinkAutoOffTime(this@AutomaticActivity, time)
            }
        }
    }

    private fun refreshUI(cards: Map<Int, LinearLayout>, selectedTime: Int) {
        cards.forEach { (time, layout) ->
            val isSelected = (time == selectedTime)
            layout.isSelected = isSelected
            // 遍历子 View 寻找所有 TextView 变色
            for (i in 0 until layout.childCount) {
                val child = layout.getChildAt(i)
                if (child is TextView) {
                    child.setTextColor(if (isSelected) selectedBlueColor else Color.WHITE)
                }
            }
        }
    }

    // --- ID 映射 (确保与 XML 保持 100% 一致) ---
    private fun getFlashlightCards() = mapOf(
        TIME_1_MIN to binding.flash1min,
        TIME_5_MIN to binding.flash5min,
        TIME_10_MIN to binding.flash10min,
        TIME_NEVER to binding.flashNever
    )

    private fun getScreenCards() = mapOf(
        TIME_1_MIN to binding.screen1min,
        TIME_5_MIN to binding.screen5min,
        TIME_10_MIN to binding.screen10min,
        TIME_NEVER to binding.screenNever
    )

    private fun getBlinkCards() = mapOf(
        TIME_1_MIN to binding.blink1min,
        TIME_5_MIN to binding.blink5min,
        TIME_10_MIN to binding.blink10min,
        TIME_NEVER to binding.blinkNever
    )
}