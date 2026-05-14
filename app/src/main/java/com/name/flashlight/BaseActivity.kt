package com.name.flashlight

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import com.name.flashlight.utils.BatteryRepository
import com.name.flashlight.utils.DataStoreManager
import com.name.flashlight.utils.LowBatteryManager
import com.name.flashlight.utils.PageUsageRecorder
import com.name.flashlight.utils.StartupModeManager
import com.name.flashlight.utils.TimeRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale

abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    protected lateinit var binding: VB

    // ------------------------
    // config
    // ------------------------
    protected open val pageTrackName: String? = null
    protected open val isBatteryMonitorEnabled = true
    protected open val isLowBatteryCheckEnabled = true
    protected open val isStopFeatureEnabled = true

    // ------------------------
    // language
    // ------------------------
    override fun attachBaseContext(newBase: Context) {

        val lang = runCatching {

            runBlocking {

                DataStoreManager
                    .getLanguage(newBase)
                    .first()
            }

        }.getOrDefault("en")

        val locale = when (lang) {

            "zh" ->
                Locale.SIMPLIFIED_CHINESE

            else ->
                Locale.ENGLISH
        }

        Locale.setDefault(locale)

        val config = Configuration(
            newBase.resources.configuration
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

            config.setLocale(locale)

            config.setLocales(
                LocaleList(locale)
            )

        } else {

            @Suppress("DEPRECATION")
            config.locale = locale
        }

        val context =
            newBase.createConfigurationContext(
                config
            )

        super.attachBaseContext(context)
    }

    // ------------------------
    // repositories
    // ------------------------
    protected val batteryRepository by lazy {
        BatteryRepository(this)
    }

    protected val timeRepository by lazy {
        TimeRepository(this)
    }

    // ------------------------
    // permission
    // ------------------------
    private var pendingCameraAction: (() -> Unit)? = null

    protected fun ensureCameraPermission(
        onGranted: () -> Unit
    ) {

        pendingCameraAction = onGranted

        requestPermissions(
            arrayOf(Manifest.permission.CAMERA),
            1001
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode != 1001) return

        val granted =
            grantResults.isNotEmpty() &&
                    grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED

        if (granted) {
            pendingCameraAction?.invoke()
        }
    }

    // ------------------------
    // brightness
    // ------------------------
    private fun observeBrightness() {

        lifecycleScope.launch {

            repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {

                DataStoreManager
                    .getUseSystemAutoBrightness(
                        this@BaseActivity
                    )
                    .collectLatest { useSystem ->

                        val brightness =
                            DataStoreManager
                                .getDefaultBrightness(
                                    this@BaseActivity
                                )
                                .first()

                        applyAppBrightness(
                            brightness,
                            useSystem
                        )
                    }
            }
        }

        lifecycleScope.launch {

            repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {

                DataStoreManager
                    .getDefaultBrightness(
                        this@BaseActivity
                    )
                    .collectLatest { brightness ->

                        val useSystem =
                            DataStoreManager
                                .getUseSystemAutoBrightness(
                                    this@BaseActivity
                                )
                                .first()

                        applyAppBrightness(
                            brightness,
                            useSystem
                        )
                    }
            }
        }
    }

    protected fun applyAppBrightness(
        brightnessLevel: Int,
        useSystemBrightness: Boolean
    ) {

        val lp = window.attributes

        if (useSystemBrightness) {

            lp.screenBrightness =
                WindowManager.LayoutParams
                    .BRIGHTNESS_OVERRIDE_NONE

        } else {

            lp.screenBrightness = when (brightnessLevel) {

                0 -> 0.15f

                1 -> 0.5f

                2 -> 1.0f

                else -> 0.5f
            }
        }

        window.attributes = lp
    }

    private fun applyBrightnessImmediately() {

        lifecycleScope.launch {

            val useSystem =
                DataStoreManager
                    .getUseSystemAutoBrightness(
                        this@BaseActivity
                    )
                    .first()

            val brightness =
                DataStoreManager
                    .getDefaultBrightness(
                        this@BaseActivity
                    )
                    .first()

            applyAppBrightness(
                brightness,
                useSystem
            )
        }
    }

    // ------------------------
    // lifecycle
    // ------------------------
    protected abstract fun createBinding(): VB

    protected open fun initViews() {}

    protected open fun initListeners() {}

    protected open fun initObservers() {}

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle =
                SystemBarStyle.dark(
                    Color.TRANSPARENT
                ),
            navigationBarStyle =
                SystemBarStyle.dark(
                    Color.TRANSPARENT
                )
        )

        binding = createBinding()

        applyBrightnessImmediately()

        setContentView(binding.root)

        pageTrackName?.let {

            PageUsageRecorder.recordPageVisit(
                this,
                it
            )

            StartupModeManager.recordLastPage(
                this,
                it
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(
            binding.root
        ) { view, insets ->

            val statusBarHeight =
                insets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                ).top

            view.setPadding(
                0,
                statusBarHeight,
                0,
                0
            )

            insets
        }

        setupBackHandler()

        initViews()

        initListeners()

        initObservers()

        if (isBatteryMonitorEnabled) {
            observeBatteryStatus()
        }

        observeBrightness()

        if (isStopFeatureEnabled) {
            registerStopFeaturesReceiver()
        }
    }

    // ------------------------
    // battery
    // ------------------------
    private fun observeBatteryStatus() {

        lifecycleScope.launch {

            batteryRepository
                .getBatteryFlow()
                .collect { info ->

                    if (isLowBatteryCheckEnabled) {

                        LowBatteryManager.checkBatteryLevel(
                            this@BaseActivity,
                            info.level.toInt(),
                            info.isCharging
                        )
                    }

                    if (
                        !isFinishing &&
                        !isDestroyed
                    ) {

                        onBatteryStatusChanged(info)
                    }
                }
        }
    }

    open fun onBatteryStatusChanged(
        info: BatteryRepository.BatteryInfo
    ) {}

    // ------------------------
    // back
    // ------------------------
    private fun setupBackHandler() {

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {

                    finish()
                }
            }
        )
    }

    // ------------------------
    // stop receiver
    // ------------------------
    private var stopFeaturesReceiver:
            BroadcastReceiver? = null

    private fun registerStopFeaturesReceiver() {

        stopFeaturesReceiver =
            object : BroadcastReceiver() {

                override fun onReceive(
                    context: Context?,
                    intent: Intent?
                ) {

                    if (
                        intent?.action ==
                        "ACTION_STOP_ALL_FEATURES"
                    ) {

                        stopAllFeatures()
                    }
                }
            }

        val filter =
            IntentFilter(
                "ACTION_STOP_ALL_FEATURES"
            )

        val flags =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {

                RECEIVER_NOT_EXPORTED

            } else {

                0
            }

        registerReceiver(
            stopFeaturesReceiver,
            filter,
            flags
        )
    }

    open fun stopAllFeatures() {}

    override fun onDestroy() {

        try {

            stopFeaturesReceiver?.let {

                unregisterReceiver(it)
            }

        } catch (_: Exception) {
        }

        super.onDestroy()
    }
}