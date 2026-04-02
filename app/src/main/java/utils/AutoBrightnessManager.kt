package utils

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart

/**
 * 自动亮度统一管理器
 * 职责：处理系统设置交互、权限请求，并提供响应式数据流
 */
object AutoBrightnessManager {

    private const val REQUEST_CODE_WRITE_SETTINGS = 1001
    private var pendingState: Boolean? = null

    /**
     * 【工业级】提供系统设置监听流
     * 只要用户在任何地方修改了自动亮度，订阅者都能实时收到
     */
    fun getAutoBrightnessFlow(context: Context): Flow<Boolean> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(getAutoBrightnessState(context))
            }
        }
        
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
            false,
            observer
        )

        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }.onStart {
        emit(getAutoBrightnessState(context))
    }

    fun hasPermission(context: Context): Boolean = Settings.System.canWrite(context)

    fun toggleAutoBrightness(
        activity: AppCompatActivity,
        targetState: Boolean,
        onSuccess: (Boolean) -> Unit,
        onFailure: (() -> Unit)? = null
    ) {
        if (!hasPermission(activity)) {
            pendingState = targetState
            requestPermission(activity)
            onFailure?.invoke()
            return
        }

        val success = setAutoBrightness(activity, targetState)
        if (success) onSuccess(targetState) else onFailure?.invoke()
    }

    private fun requestPermission(activity: AppCompatActivity) {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(com.name.FlashLight.R.string.basic_settings))
            .setMessage(activity.getString(com.name.FlashLight.R.string.auto_brightness_adjustment))
            .setPositiveButton(activity.getString(com.name.FlashLight.R.string.toast_to_modify)) { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = Uri.parse("package:${activity.packageName}")
                activity.startActivityForResult(intent, REQUEST_CODE_WRITE_SETTINGS)
            }
            .show()
    }

    private fun setAutoBrightness(context: Context, enabled: Boolean): Boolean {
        return try {
            val mode = if (enabled) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
            true
        } catch (e: Exception) { false }
    }

    fun getAutoBrightnessState(context: Context): Boolean {
        return try {
            val mode = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (e: Exception) { false }
    }

    fun handlePermissionResult(activity: AppCompatActivity, requestCode: Int, onSuccess: (Boolean) -> Unit) {
        if (requestCode == REQUEST_CODE_WRITE_SETTINGS && hasPermission(activity)) {
            pendingState?.let { setAutoBrightness(activity, it); onSuccess(it) }
            pendingState = null
        }
    }
}