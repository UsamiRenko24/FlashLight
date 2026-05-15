package com.name.flashlight

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.name.flashlight.databinding.ActivitySplashBinding
import com.name.flashlight.utils.PageNavigator
import com.name.flashlight.utils.PageUsageRecorder
import com.name.flashlight.utils.StartupModeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.name.flashlight.utils.AppLaunchManager
import com.name.flashlight.utils.DataStoreManager

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        hideSystemUI()

        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. 启动您的全屏大图 (launcher_bg) 的淡入动画
        startSplashAnimation()

        lifecycleScope.launch {
            // 停留 800ms 展示大图，然后根据逻辑分发跳转
            delay(800)

            if (isFinishing || isDestroyed) return@launch

            val intent = resolveLaunchIntent()
            startActivity(intent)
            finish()
            // 使用淡入淡出切换，避免生硬感
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
    private fun hideSystemUI() {

        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        val controller =
            WindowInsetsControllerCompat(
                window,
                window.decorView
            )

        controller.hide(
            WindowInsetsCompat.Type.systemBars()
        )

        controller.systemBarsBehavior =
            WindowInsetsControllerCompat
                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    private fun startSplashAnimation() {
        binding.ivSplash.alpha = 0f
        binding.ivSplash.animate()
            .alpha(1f)
            .setDuration(600)
            .start()
    }

    private suspend fun resolveLaunchIntent(): Intent {
        val isFirstLaunch = AppLaunchManager.isFirstLaunch(this)
        if (isFirstLaunch) {
            return Intent(this, OnboardingActivity::class.java)
        }

        val mode = DataStoreManager.getStartupMode(this).first()
        return when (mode) {
            StartupModeManager.MODE_LAST_USED -> {
                val lastPage = StartupModeManager.getLastPage(this)
                PageNavigator.getPageIntent(this, lastPage)
            }
            StartupModeManager.MODE_MOST_USED -> {
                val mostUsed = PageUsageRecorder.getMostUsedPage(this)
                PageNavigator.getPageIntent(this, mostUsed)
            }
            else -> Intent(this, MainActivity::class.java)
        }
    }
}