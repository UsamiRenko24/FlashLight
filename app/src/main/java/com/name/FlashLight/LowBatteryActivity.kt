package com.name.FlashLight

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.name.FlashLight.databinding.LowbatteryBinding
import com.name.FlashLight.utils.PageConstants
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager

class LowBatteryActivity : BaseActivity<LowbatteryBinding>() {
    private var haloAnimator: AnimatorSet? = null

    private val exitReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_EXIT_LOW_BATTERY_DISPLAY") {
                val mainIntent = Intent(this@LowBatteryActivity, MainActivity::class.java)
                startActivity(mainIntent)
                finish()
            }
        }
    }
    override fun createBinding():LowbatteryBinding{
        return LowbatteryBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setLowBrightness()

        super.onCreate(savedInstanceState)

        PageUsageRecorder.recordPageVisit(this, PageConstants.PAGE_LOW_BATTERY)
        StartupModeManager.recordLastPage(this, PageConstants.PAGE_LOW_BATTERY)
        
        // 注册退出广播
        val filter = IntentFilter("ACTION_EXIT_LOW_BATTERY_DISPLAY")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(exitReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(exitReceiver, filter)
        }

        // 电池图标动画
        ObjectAnimator.ofFloat(binding.ivBatteryIcon, "scaleX", 0.9f, 1.1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        ObjectAnimator.ofFloat(binding.ivBatteryIcon, "scaleY", 0.9f, 1.1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        startHaloAnimation()
        
        // 显示当前电量
        val batteryLevel = intent.getIntExtra("battery_level", 15)
        binding.tvBatteryPercent.text = "$batteryLevel%"
    }


    private fun setLowBrightness() {
        try {
            val attributes = window.attributes
            attributes.screenBrightness = 0.3f
            window.attributes = attributes
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startHaloAnimation() {
        if (haloAnimator != null) return
        binding.sosHalo.visibility = View.VISIBLE

        val scaleX = ObjectAnimator.ofFloat(binding.sosHalo, "scaleX", 1.0f, 1.15f)
        val scaleY = ObjectAnimator.ofFloat(binding.sosHalo, "scaleY", 1.0f, 1.15f)
        val alpha = ObjectAnimator.ofFloat(binding.sosHalo, "alpha", 0.4f, 0.8f)

        scaleX.repeatCount = ValueAnimator.INFINITE
        scaleX.repeatMode = ValueAnimator.REVERSE
        scaleY.repeatCount = ValueAnimator.INFINITE
        scaleY.repeatMode = ValueAnimator.REVERSE
        alpha.repeatCount = ValueAnimator.INFINITE
        alpha.repeatMode = ValueAnimator.REVERSE

        haloAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 1000
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(exitReceiver)
        haloAnimator?.cancel()
    }
}