package utils

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * 自动亮度统一管理器
 * 整合了权限处理和亮度设置
 */
object AutoBrightnessManager {

    private const val REQUEST_CODE_WRITE_SETTINGS = 1001
    private var pendingState: Boolean? = null  // 记录等待权限的状态

    /**
     * 检查是否有权限
     */
    fun hasPermission(activity: AppCompatActivity): Boolean {
        return Settings.System.canWrite(activity)
    }

    /**
     * 切换自动亮度（自动处理权限）
     * @param activity 当前Activity
     * @param targetState 想要设置的状态
     * @param onSuccess 成功后的回调
     * @param onFailure 失败后的回调
     */
    fun toggleAutoBrightness(
        activity: AppCompatActivity,
        targetState: Boolean,
        onSuccess: (Boolean) -> Unit,
        onFailure: (() -> Unit)? = null
    ) {
        if (!hasPermission(activity)) {
            // 没有权限，记录想要的状态并请求权限
            pendingState = targetState
            requestPermission(activity)
            onFailure?.invoke()
            return
        }

        // 有权限，直接设置
        val success = setAutoBrightness(activity, targetState)
        if (success) {
            onSuccess(targetState)
        } else {
            onFailure?.invoke()
            Toast.makeText(activity, "设置失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 请求权限
     */
    private fun requestPermission(activity: AppCompatActivity) {
        AlertDialog.Builder(activity)
            .setTitle("需要权限")
            .setMessage("自动亮度调节需要修改系统设置的权限，请允许")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = Uri.parse("package:${activity.packageName}")
                activity.startActivityForResult(intent, REQUEST_CODE_WRITE_SETTINGS)
            }
            .setNegativeButton("取消") { _, _ ->
                pendingState = null  // 取消，清空等待状态
            }
            .show()
    }

    /**
     * 处理权限结果
     * 在 Activity 的 onActivityResult 中调用
     */
    fun handlePermissionResult(
        activity: AppCompatActivity,
        requestCode: Int,
        onSuccess: (Boolean) -> Unit
    ) {
        if (requestCode == REQUEST_CODE_WRITE_SETTINGS) {
            if (hasPermission(activity)) {
                // 如果有等待的状态，应用它
                pendingState?.let { state ->
                    val success = setAutoBrightness(activity, state)
                    if (success) {
                        onSuccess(state)
                    }
                    pendingState = null
                }
            } else {
                Toast.makeText(activity, "需要权限才能使用自动亮度", Toast.LENGTH_LONG).show()
                pendingState = null
            }
        }
    }

    /**
     * 设置自动亮度
     */
    private fun setAutoBrightness(activity: AppCompatActivity, enabled: Boolean): Boolean {
        return try {
            val mode = if (enabled) {
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            } else {
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            }
            Settings.System.putInt(
                activity.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                mode
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取当前自动亮度状态
     */
    fun getAutoBrightnessState(activity: AppCompatActivity): Boolean {
        return try {
            val mode = Settings.System.getInt(
                activity.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE
            )
            mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (e: Exception) {
            false
        }
    }
}