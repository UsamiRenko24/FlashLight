package com.name.FlashLight

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.name.FlashLight.utils.PageConstants
import android.view.animation.AccelerateDecelerateInterpolator
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager

class LowBatteryActivity : AppCompatActivity() {
    private lateinit var sosHalo: View
    private var haloAnimator: AnimatorSet? = null // 【新增】动画对象引用
    override fun onCreate(savedInstanceState: Bundle?) {
        setLowBrightness()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.lowbattery)
        PageUsageRecorder.recordPageVisit(this, PageConstants.PAGE_LOW_BATTERY)
        StartupModeManager.recordLastPage(this, PageConstants.PAGE_LOW_BATTERY)
        sosHalo = findViewById(R.id.sos_halo)
        startHaloAnimation()
        // 显示当前电量
        val batteryLevel = intent.getIntExtra("battery_level", 15)
        findViewById<TextView>(R.id.tv_battery_percent).text = "$batteryLevel%"
    }
    private fun setLowBrightness() {
        try {
            val attributes = WindowManager.LayoutParams()
            attributes.screenBrightness = 0.3f  // 30% 亮度
            window.attributes = attributes
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private fun startHaloAnimation() {
        if (haloAnimator != null) return
        sosHalo.visibility = View.VISIBLE

        // 缩放动画 (1.0x -> 1.15x)
        val scaleX = ObjectAnimator.ofFloat(sosHalo, "scaleX", 1.0f, 1.15f)
        val scaleY = ObjectAnimator.ofFloat(sosHalo, "scaleY", 1.0f, 1.15f)
        // 透明度呼吸 (0.4 -> 0.8)
        val alpha = ObjectAnimator.ofFloat(sosHalo, "alpha", 1.0f, 1.2f)

        scaleX.repeatCount = ValueAnimator.INFINITE
        scaleX.repeatMode = ValueAnimator.REVERSE
        scaleY.repeatCount = ValueAnimator.INFINITE
        scaleY.repeatMode = ValueAnimator.REVERSE
        alpha.repeatCount = ValueAnimator.INFINITE
        alpha.repeatMode = ValueAnimator.REVERSE

        haloAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 1000 // 警示频率稍快
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }
}