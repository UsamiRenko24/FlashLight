package utils


import android.view.View

object FeedbackManager {

    // 统一的反馈方法
    fun provideFeedback(view: View) {
        val context = view.context

        // ✅ 应该检查振动开关
        if (VibrationManager.isVibrationEnabled(context)) {
            VibrationManager.vibrate(view)
        }

        // ✅ 应该检查声音开关
        if (SoundManager.isSoundEnabled(context)) {
            SoundManager.playClickSound(context)
        }
    }
}

// 扩展函数
fun View.feedback() {
    FeedbackManager.provideFeedback(this)
}