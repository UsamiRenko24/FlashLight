package com.name.FlashLight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.TaskStackBuilder
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import kotlinx.coroutines.launch
import utils.BatteryRepository
import utils.LanguageManager
import utils.LowBatteryManager
import utils.TimeRepository

abstract class BaseActivity<VB: ViewBinding> : AppCompatActivity() {
    private var stopFeaturesReceiver: BroadcastReceiver? = null

    protected lateinit var binding: VB
    
    // 【彻底修复】使用 this (当前Activity) 而不是 applicationContext
    // 这样 Repository 拿到的 Resources 永远是最新翻译过的
    protected val batteryRepository by lazy { BatteryRepository(this) }
    protected val timeRepository by lazy { TimeRepository(this) }

    protected abstract fun createBinding(): VB

    override fun attachBaseContext(newBase: Context) {
        val languageCode = LanguageManager.getCurrentLanguage(newBase)
        val context = LanguageManager.applyLanguage(newBase, languageCode)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))

        binding = createBinding()
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        if (shouldBuildBackStack()) {
            buildBackStack()
            return
        }

        setupBackButton()
        setupBackHandler()
        observeBatteryStatus()
        registerStopFeaturesReceiver()
    }

    private fun observeBatteryStatus() {
        lifecycleScope.launch {
            // 这里监听到的 info 现在保证是带有最新语言配置的
            batteryRepository.getBatteryFlow().collect { info ->
                LowBatteryManager.checkBatteryLevel(this@BaseActivity, info.level.toInt(), info.isCharging)
                
                if (!isFinishing && !isDestroyed) {
                    onBatteryStatusChanged(info)
                }
            }
        }
    }

    open fun onBatteryStatusChanged(info: BatteryRepository.BatteryInfo) {
        // 子类可选实现
    }

    override fun onResume() {
        super.onResume()
        if (LowBatteryManager.isLowBatteryModeActive(this)) {
            LowBatteryManager.applyLowBatteryBrightness(this)
            if (this !is LowBatteryActivity) {
                val intent = Intent(this, LowBatteryActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
        }
    }

    open fun shouldBuildBackStack(): Boolean {
        return isTaskRoot && this !is MainActivity
    }

    open fun buildBackStack() {
        val targetIntent = Intent(this, this::class.java)
        TaskStackBuilder.create(this)
            .addNextIntent(Intent(this, MainActivity::class.java))
            .addNextIntent(targetIntent)
            .startActivities()
        finish()
    }

    private fun setupBackButton() {
        try {
            findViewById<View>(R.id.traceback)?.setOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }
        } catch (e: Exception) {}
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    open fun handleBackPress() {
        if (isTaskRoot && this !is MainActivity) {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        } else {
            finish()
        }
    }

    private fun registerStopFeaturesReceiver() {
        stopFeaturesReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "ACTION_STOP_ALL_FEATURES") {
                    stopAllFeatures()
                }
            }
        }
        val filter = IntentFilter("ACTION_STOP_ALL_FEATURES")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopFeaturesReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stopFeaturesReceiver, filter)
        }
    }

    open fun stopAllFeatures() {}

    override fun onDestroy() {
        super.onDestroy()
        try {
            stopFeaturesReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}