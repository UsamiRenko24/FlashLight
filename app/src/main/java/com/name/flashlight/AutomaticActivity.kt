package com.name.flashlight

import android.graphics.Color
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.name.flashlight.databinding.AutomaticBinding
import com.name.flashlight.utils.PageConstants
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.name.flashlight.utils.DataStoreManager
import com.name.flashlight.utils.feedback

/**
 * 工业级模块化自动关闭设置页面
 *
 * 说明：
 * 1. 该页面属于“二级页面”
 * 2. 需要保留历史栈
 * 3. 返回时回到上一级页面
 * 4. 不再强制返回 MainActivity
 */
class AutomaticActivity : BaseActivity<AutomaticBinding>() {

    // =========================
    // BaseActivity 配置
    // =========================

    override val pageTrackName =
        PageConstants.PAGE_AUTOMATIC

    override val isBatteryMonitorEnabled = false

    override val isLowBatteryCheckEnabled = false

    /**
     * 核心：
     * 开启历史栈模式
     *
     * 返回键：
     * finish()
     * 回到上一级页面
     */

    companion object {

        const val TIME_1_MIN = 1

        const val TIME_5_MIN = 5

        const val TIME_10_MIN = 10

        const val TIME_NEVER = 114514
    }

    private val selectedBlueColor =
        Color.parseColor("#2AE1F8")

    override fun createBinding(): AutomaticBinding {

        return AutomaticBinding.inflate(layoutInflater)
    }

    // =========================
    // 初始化
    // =========================

    override fun initViews() {

        // XML 已完成静态 UI
    }

    override fun initListeners() {

        binding.traceback.setOnClickListener {
            finish()
        }

        // BaseActivity 已自动处理返回逻辑

        bindClicks(
            getFlashlightCards(),
            0
        )

        bindClicks(
            getScreenCards(),
            1
        )

        bindClicks(
            getBlinkCards(),
            2
        )
    }

    override fun initObservers() {

        lifecycleScope.launch {

            DataStoreManager
                .getFlashlightAutoOffTime(
                    this@AutomaticActivity
                )
                .collectLatest { time ->

                    refreshUI(
                        getFlashlightCards(),
                        time
                    )
                }
        }

        lifecycleScope.launch {

            DataStoreManager
                .getScreenAutoOffTime(
                    this@AutomaticActivity
                )
                .collectLatest { time ->

                    refreshUI(
                        getScreenCards(),
                        time
                    )
                }
        }

        lifecycleScope.launch {

            DataStoreManager
                .getBlinkAutoOffTime(
                    this@AutomaticActivity
                )
                .collectLatest { time ->

                    refreshUI(
                        getBlinkCards(),
                        time
                    )
                }
        }
    }

    // =========================
    // 点击绑定
    // =========================

    private fun bindClicks(
        cards: Map<Int, LinearLayout>,
        type: Int
    ) {

        cards.forEach { (time, view) ->

            view.setOnClickListener {

                it.feedback()

                saveSetting(type, time)
            }
        }
    }

    // =========================
    // 保存设置
    // =========================

    private fun saveSetting(
        type: Int,
        time: Int
    ) {

        lifecycleScope.launch {

            when (type) {

                0 -> {

                    DataStoreManager
                        .setFlashlightAutoOffTime(
                            this@AutomaticActivity,
                            time
                        )
                }

                1 -> {

                    DataStoreManager
                        .setScreenAutoOffTime(
                            this@AutomaticActivity,
                            time
                        )
                }

                2 -> {

                    DataStoreManager
                        .setBlinkAutoOffTime(
                            this@AutomaticActivity,
                            time
                        )
                }
            }
        }
    }

    // =========================
    // UI 刷新
    // =========================

    private fun refreshUI(
        cards: Map<Int, LinearLayout>,
        selectedTime: Int
    ) {

        cards.forEach { (time, layout) ->

            val isSelected =
                time == selectedTime

            layout.isSelected = isSelected

            for (i in 0 until layout.childCount) {

                val child =
                    layout.getChildAt(i)

                if (child is TextView) {

                    child.setTextColor(
                        if (isSelected) {
                            selectedBlueColor
                        } else {
                            Color.WHITE
                        }
                    )
                }
            }
        }
    }

    // =========================
    // 卡片映射
    // =========================

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