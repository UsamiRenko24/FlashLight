package utils

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View

object VibrationManager {

    private const val PREFS_NAME = "vibration_settings"
    private const val KEY_VIBRATION_ENABLED = "vibration_enabled"

    fun isVibrationEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_VIBRATION_ENABLED, false)
    }

    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
    }

    fun vibrate(view: View) {
        // 如果震动关闭，直接返回
        if (!isVibrationEnabled(view.context)) return

        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (e: Exception) {
            // 忽略错误
        }
    }
}