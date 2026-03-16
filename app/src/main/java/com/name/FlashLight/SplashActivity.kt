package com.name.FlashLight

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.name.FlashLight.utils.PageNavigator
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Handler(Looper.getMainLooper()).postDelayed({
            // 1. 读取当前设置的启动模式
            val mode = StartupModeManager.getStartupMode(this)
            Log.d("SplashDebug", "当前启动模式: $mode")

            // 2. 根据模式决定跳转
            val intent = when (mode) {
                StartupModeManager.MODE_LAST_USED -> {
                    val lastPage = StartupModeManager.getLastPage(this)
                    Log.d("SplashDebug", "记住上次: $lastPage")
                    PageNavigator.getPageIntent(this, lastPage)
                }
                StartupModeManager.MODE_HOME -> {
                    Log.d("SplashDebug", "跳转到主页")
                    Intent(this, MainActivity::class.java)
                }
                StartupModeManager.MODE_MOST_USED -> {
                    val mostUsed = PageUsageRecorder.getMostUsedPage(this)
                    Log.d("SplashDebug", "最常用: $mostUsed")
                    PageNavigator.getPageIntent(this, mostUsed)
                }
                else -> {
                    Log.d("SplashDebug", "默认跳转到主页")
                    Intent(this, MainActivity::class.java)
                }
            }

            // 3. 执行跳转
            startActivity(intent)
            finish()
        }, 500)
    }
}