package utils

import android.content.Context
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object FeedbackManager {
    // 使用独立的协程作用域，生命周期与应用进程一致
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 缓存最新的开关状态，默认开启
    private var isVibrationEnabled = true
    private var isSoundEnabled = true
    private var isInitialized = false

    /**
     * 初始化监听逻辑（建议在 Application 中调用，或者由 provideFeedback 自动触发）
     */
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val appContext = context.applicationContext

        // 持续观察震动设置
        scope.launch {
            DataStoreManager.isVibrationEnabled(appContext).collectLatest {
                isVibrationEnabled = it
            }
        }

        // 持续观察声音设置
        scope.launch {
            DataStoreManager.isSoundEnabled(appContext).collectLatest {
                isSoundEnabled = it
            }
        }
    }

    /**
     * 统一的反馈执行入口
     */
    fun provideFeedback(view: View) {
        // 兜底初始化
        if (!isInitialized) init(view.context)

        // 直接使用缓存状态，无需等待异步读取，保证点击瞬间即有反馈
        VibrationManager.vibrate(view, forceEnabled = isVibrationEnabled)
        SoundManager.playClickSound(view.context, forceEnabled = isSoundEnabled)
    }
}

// 扩展函数
fun View.feedback() {
    FeedbackManager.provideFeedback(this)
}