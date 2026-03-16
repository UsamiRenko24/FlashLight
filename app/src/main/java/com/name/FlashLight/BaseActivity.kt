package com.name.FlashLight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.TaskStackBuilder
import utils.LowBatteryManager

abstract class BaseActivity : AppCompatActivity() {
    private lateinit var batteryReceiver: BroadcastReceiver
    private var stopFeaturesReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (shouldBuildBackStack()) {
            buildBackStack()
            return
        }

        setupBackButton()
        setupBackHandler()
        registerBatteryReceiver()
        registerStopFeaturesReceiver()
    }

    override fun onResume() {
        super.onResume()
        // 核心功能 1：回到页面时检查，如果处于低电量模式，强制锁定 30% 亮度
        if (LowBatteryManager.isLowBatteryModeActive()) {
            LowBatteryManager.applyLowBatteryBrightness(this)
            
            // 核心功能 2：如果不在低电量页，强制跳转过去
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

                    if (level >= 0 && scale > 0) {
                        val batteryPct = level * 100 / scale
                        // 15% 阈值检查
                        LowBatteryManager.checkBatteryLevel(this@BaseActivity, batteryPct)
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
        // 使用标准广播注册，解决 Unresolved reference 报错
        val filter = IntentFilter("ACTION_STOP_ALL_FEATURES")
        
        // 兼容 Android 14+ 安全规范
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopFeaturesReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stopFeaturesReceiver, filter)
        }
    }

    open fun stopAllFeatures() {
        // 由子类实现具体停止逻辑
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(batteryReceiver)
            stopFeaturesReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}