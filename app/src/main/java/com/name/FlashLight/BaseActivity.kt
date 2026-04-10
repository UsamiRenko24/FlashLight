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
import androidx.lifecycle.lifecycleScope
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

    // --- 1. 模块化配置开关：子类通过 override 这些变量来决定页面能力 ---
    protected open val pageTrackName: String? = null      // 页面追踪名，为 null 则不统计
    protected open val isBatteryMonitorEnabled = true    // 是否需要实时监听电量变化
    protected open val isLowBatteryCheckEnabled = true   // 是否开启低电量自动跳转保护
    protected open val isStopFeatureEnabled = true       // 是否响应“停止所有功能”的全局广播

    // --- 2. 共享资源 ---
    protected val batteryRepository by lazy { BatteryRepository(this) }
    protected val timeRepository by lazy { TimeRepository(this) }

    // --- 3. 初始化模板方法：强制规范子类的代码结构 ---
    protected abstract fun createBinding(): VB
    protected open fun initViews() {}      // 初始化 UI 控件状态
    protected open fun initListeners() {}  // 设置点击事件等监听
    protected open fun initObservers() {}  // 订阅 DataStore/Flow/Repository

    // --- 4. 权限模块化封装 ---
    private var onCameraGranted: (() -> Unit)? = null
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) onCameraGranted?.invoke()
        else onPermissionDenied()
    }

    /**
     * 一键权限申请：子类不再需要管理 Launcher
     */
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
        
        // A. 模块：自动页面追踪
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

        // B. 模块：执行子类业务逻辑（顺序固定，减少冲突）
        initViews()
        initListeners()
        initObservers()
        
        // C. 模块：开启系统级监控
        if (isBatteryMonitorEnabled) observeBatteryStatus()
        if (isStopFeatureEnabled) registerStopFeaturesReceiver()
    }

    /**
     * 解决你的疑问：
     * 父类负责“听电台”(Collect Flow)，子类只需要接收“通知”(onBatteryStatusChanged)。
     * 这样子类就不必每个页面都写一遍协程开启逻辑，极大方便了子类的工作。
     */
    private fun observeBatteryStatus() {
        lifecycleScope.launch {
            batteryRepository.getBatteryFlow().collect { info ->
                // 仅在开关打开时执行低电量自动关闭逻辑
                if (isLowBatteryCheckEnabled) {
                    LowBatteryManager.checkBatteryLevel(this@BaseActivity, info.level.toInt(), info.isCharging)
                }
                
                // 总是通知子类，子类若需显示则重写此方法
                if (!isFinishing && !isDestroyed) {
                    onBatteryStatusChanged(info)
                }
            }
        }
    }

    /**
     * 电池信息钩子函数
     */
    open fun onBatteryStatusChanged(info: BatteryRepository.BatteryInfo) {}

    override fun onResume() {
        super.onResume()
        // 模块：二次校验低电量（防止锁屏回来后状态失效）
        if (isLowBatteryCheckEnabled && LowBatteryManager.isLowBatteryModeActive(this)) {
            LowBatteryManager.applyLowBatteryBrightness(this)
            if (this !is LowBatteryActivity) {
                startActivity(Intent(this, LowBatteryActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
        }
    }

    // --- 导航与系统广播逻辑 ---
    private fun setupBackButton() {
        findViewById<View>(R.id.traceback)?.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handleBackPress() }
        })
    }

    open fun handleBackPress() {
        if (isTaskRoot && this !is MainActivity) {
            startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP })
            finish()
        } else finish()
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
        super.onDestroy()
        stopFeaturesReceiver?.let { unregisterReceiver(it) }
    }
}