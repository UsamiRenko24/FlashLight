package utils

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View

object VibrationManager {

    /**
     * 工业级反馈方法：直接根据外部传入的状态决定是否震动
     * 避免了去查旧数据库导致的延迟和逻辑冲突
     */
    fun vibrate(view: View, forceEnabled: Boolean = true) {
        if (!forceEnabled) return

        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 保留旧方法用于兼容其他页面，但标记为废弃或内部使用
    @Deprecated("Use vibrate(view, isEnabled) instead")
    fun isVibrationEnabled(context: Context): Boolean {
        return context.getSharedPreferences("vibration_settings", Context.MODE_PRIVATE)
            .getBoolean("vibration_enabled", true)
    }

    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("vibration_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("vibration_enabled", enabled).apply()
    }
}