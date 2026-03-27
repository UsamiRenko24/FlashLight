package utils

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View

object VibrationManager {

    private const val PREFS_NAME = "vibration_settings"
    private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
    
    // 建议：统一默认值为 true (或者你想要的默认状态)
    private const val DEFAULT_VALUE = true

    fun isVibrationEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_VIBRATION_ENABLED, DEFAULT_VALUE)
    }

    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
    }

    fun vibrate(view: View) {
        if (!isVibrationEnabled(view.context)) return

        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}