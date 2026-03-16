package com.name.FlashLight

import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.name.FlashLight.utils.PageConstants
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager

class LowBatteryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setLowBrightness()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.lowbattery)
        PageUsageRecorder.recordPageVisit(this, PageConstants.PAGE_LOW_BATTERY)
        StartupModeManager.recordLastPage(this, PageConstants.PAGE_LOW_BATTERY)


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
}