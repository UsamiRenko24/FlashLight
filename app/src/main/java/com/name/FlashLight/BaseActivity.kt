package com.name.FlashLight

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import com.name.FlashLight.utils.PageUsageRecorder
import com.name.FlashLight.utils.StartupModeManager
import kotlinx.coroutines.launch
import utils.BatteryRepository
import utils.LanguageManager
import utils.LowBatteryManager
import utils.TimeRepository

/**
 * 工业级高度模块化基类
 */
abstract class BaseActivity<VB: ViewBinding> : AppCompatActivity() {
    protected lateinit var binding: VB

    // --- 1. 模块化配置开关 ---
    protected open val pageTrackName: String? = null
    protected open val isBatteryMonitorEnabled = true
    protected open val isLowBatteryCheckEnabled = true
    protected open val isStopFeatureEnabled = true

    // --- 2. 共享资源 ---
    protected val batteryRepository by lazy { BatteryRepository(this) }
    protected val timeRepository by lazy { TimeRepository(this) }

    // --- 3. 初始化模板方法 ---
    protected abstract fun createBinding(): VB
    protected open fun initViews() {}
    protected open fun initListeners() {}
    protected open fun initObservers() {}

    // --- 4. 权限模块化封装 ---
    private var onCameraGranted: (() -> Unit)? = null
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) onCameraGranted?.invoke()
        else onPermissionDenied()
    }

    protected fun ensureCameraPermission(onGranted: () -> Unit) {
        onCameraGranted = onGranted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    protected open fun onPermissionDenied() {
        Toast.makeText(this, getString(R.string.toast_failed), Toast.LENGTH_SHORT).show()
    }

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
        
        pageTrackName?.let {
            PageUsageRecorder.recordPageVisit(this, it)
            StartupModeManager.recordLastPage(this, it)
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        setupBackButton()
        setupBackHandler()

        initViews()
        initListeners()
        initObservers()
        
        if (isBatteryMonitorEnabled) observeBatteryStatus()
        if (isStopFeatureEnabled) registerStopFeaturesReceiver()
    }

    private fun observeBatteryStatus() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                batteryRepository.getBatteryFlow().collect { info ->
                    if (isLowBatteryCheckEnabled) {
                        LowBatteryManager.checkBatteryLevel(this@BaseActivity, info.level.toInt(), info.isCharging)
                    }
                    
                    if (!isFinishing && !isDestroyed) {
                        onBatteryStatusChanged(info)
                    }
                }
            }
        }
    }

    open fun onBatteryStatusChanged(info: BatteryRepository.BatteryInfo) {}

    override fun onResume() {
        super.onResume()
        if (isLowBatteryCheckEnabled && LowBatteryManager.isLowBatteryModeActive(this)) {
            if (this !is LowBatteryActivity) {
                val intent = Intent(this, LowBatteryActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
                return
            }
            LowBatteryManager.applyLowBatteryBrightness(this)
        }
    }

    private fun setupBackButton() {
        findViewById<View>(R.id.traceback)?.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handleBackPress() }
        })
    }

    /**
     * 核心逻辑修改：除了主页面，其他页面的返回统一指向主页面
     */
    open fun handleBackPress() {
        if (this !is MainActivity) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        } else {
            finish()
        }
    }

    private var stopFeaturesReceiver: BroadcastReceiver? = null
    private fun registerStopFeaturesReceiver() {
        stopFeaturesReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "ACTION_STOP_ALL_FEATURES") stopAllFeatures()
            }
        }
        val filter = IntentFilter("ACTION_STOP_ALL_FEATURES")
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            RECEIVER_NOT_EXPORTED
        } else 0
        registerReceiver(stopFeaturesReceiver, filter, flags)
    }

    open fun stopAllFeatures() {}

    override fun onDestroy() {
        try {
            stopFeaturesReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
}