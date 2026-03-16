package utils


import android.view.View

object FeedbackManager {

    // 统一的反馈方法
    fun provideFeedback(view: View) {
        // 震动反馈（如果开启）
        VibrationManager.vibrate(view)

        // 声音反馈（如果开启）
        SoundManager.playClickSound(view.context)
    }
}

// 扩展函数
fun View.feedback() {
    FeedbackManager.provideFeedback(this)
}