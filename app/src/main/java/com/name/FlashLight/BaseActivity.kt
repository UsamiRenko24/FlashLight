package com.name.FlashLight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.TaskStackBuilder
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewbinding.ViewBinding
import utils.LanguageManager
import utils.LowBatteryManager

abstract class BaseActivity<VB: ViewBinding> : AppCompatActivity() {
    private var batteryReceiver: BroadcastReceiver? = null
    private var stopFeaturesReceiver: BroadcastReceiver? = null

    protected lateinit var binding:VB

    protected abstract fun createBinding():VB

    override fun attachBaseContext(newBase: Context) {
        val languageCode = LanguageManager.getCurrentLanguage(newBase)
        val context = LanguageManager.applyLanguage(newBase, languageCode)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))

        binding= createBinding()
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
        registerBatteryReceiver()
        registerStopFeaturesReceiver()
    }

    /**
     * 当电池状态发生物理变化时触发，子类可重写此方法以实时更新 UI
     */
    open fun onBatteryStatusChanged() {
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

    private fun registerBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL

                    if (level >= 0 && scale > 0) {
                        val batteryPct = level * 100 / scale
                        LowBatteryManager.checkBatteryLevel(this@BaseActivity, batteryPct, isCharging)
                        // 核心优化：电池变化时，立即通知子类更新 UI
                        onBatteryStatusChanged()
                    }
                }
            }
        }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
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

    open fun stopAllFeatures() {
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            batteryReceiver?.let { unregisterReceiver(it) }
            stopFeaturesReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}