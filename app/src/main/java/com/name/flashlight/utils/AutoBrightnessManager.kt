package com.name.flashlight.utils

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.name.flashlight.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart

/**
 * 自动亮度统一管理器
 */
object AutoBrightnessManager {

    /**
     * 自动亮度状态监听
     */
    fun getAutoBrightnessFlow(
        context: Context
    ): Flow<Boolean> = callbackFlow {

        val observer =
            object : ContentObserver(
                Handler(Looper.getMainLooper())
            ) {

                override fun onChange(
                    selfChange: Boolean,
                    uri: Uri?
                ) {

                    trySend(
                        getAutoBrightnessState(context)
                    )
                }
            }

        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(
                Settings.System.SCREEN_BRIGHTNESS_MODE
            ),
            false,
            observer
        )

        awaitClose {
            context.contentResolver
                .unregisterContentObserver(observer)
        }

    }.onStart {

        emit(
            getAutoBrightnessState(context)
        )
    }

    /**
     * 是否拥有修改系统设置权限
     */
    fun hasPermission(
        context: Context
    ): Boolean {

        return Settings.System.canWrite(context)
    }

    /**
     * 切换自动亮度
     */
    fun toggleAutoBrightness(
        activity: AppCompatActivity,
        targetState: Boolean,
        onSuccess: (Boolean) -> Unit,
        onFailure: (() -> Unit)? = null
    ) {

        // 没权限
        if (!hasPermission(activity)) {

            showPermissionDialog(activity)

            onFailure?.invoke()

            return
        }

        val success =
            setAutoBrightness(
                activity,
                targetState
            )

        // 清除当前 Activity 对亮度的覆盖
        if (success && targetState) {

            val lp =
                activity.window.attributes

            lp.screenBrightness =
                WindowManager
                    .LayoutParams
                    .BRIGHTNESS_OVERRIDE_NONE

            activity.window.attributes = lp
        }

        if (success) {

            onSuccess(targetState)

        } else {

            // 某些 ROM 修改失败
            // 引导用户手动进入显示设置
            openDisplaySettings(activity)

            onFailure?.invoke()
        }
    }

    /**
     * 真正设置系统自动亮度
     */
    private fun setAutoBrightness(
        context: Context,
        enabled: Boolean
    ): Boolean {

        return try {

            val mode =
                if (enabled) {

                    Settings.System
                        .SCREEN_BRIGHTNESS_MODE_AUTOMATIC

                } else {

                    Settings.System
                        .SCREEN_BRIGHTNESS_MODE_MANUAL
                }

            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                mode
            )

        } catch (e: Exception) {

            e.printStackTrace()

            false
        }
    }

    /**
     * 获取当前自动亮度状态
     */
    fun getAutoBrightnessState(
        context: Context
    ): Boolean {

        return try {

            val mode =
                Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE
                )

            mode ==
                    Settings.System
                        .SCREEN_BRIGHTNESS_MODE_AUTOMATIC

        } catch (e: Exception) {

            false
        }
    }

    /**
     * 权限说明弹窗
     */
    private fun showPermissionDialog(
        activity: AppCompatActivity
    ) {

        AlertDialog.Builder(activity)

            .setTitle(
                activity.getString(
                    R.string.basic_settings
                )
            )

            .setMessage(
                activity.getString(
                    R.string.notification_info
                )
            )

            .setPositiveButton(
                activity.getString(
                    R.string.toast_to_modify
                )
            ) { _, _ ->

                val intent =
                    Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS
                    ).apply {

                        data =
                            Uri.parse(
                                "package:${activity.packageName}"
                            )
                    }

                activity.startActivity(intent)
            }

            .setNegativeButton(
                android.R.string.cancel,
                null
            )

            .show()
    }

    /**
     * 打开显示设置页
     */
    fun openDisplaySettings(
        context: Context
    ) {

        try {

            context.startActivity(
                Intent(
                    Settings.ACTION_DISPLAY_SETTINGS
                ).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }
            )

        } catch (e: Exception) {

            e.printStackTrace()
        }
    }
}