package utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.name.FlashLight.R

object SoundManager {

    private var soundPool: SoundPool? = null
    private var clickSoundId: Int = -1
    private var isLoaded = false

    /**
     * 初始化声音池
     * 建议在 Application 或第一个 Activity 中尽早调用
     */
    fun initSoundPool(context: Context) {
        if (soundPool != null) return

        val audioAttributes = AudioAttributes.Builder()
            // 工业级建议：使用 USAGE_MEDIA 确保声音跟随媒体音量，不受系统静音模式干扰
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()

        // 监听加载完成
        soundPool?.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) isLoaded = true
        }

        // 加载资源
        clickSoundId = soundPool?.load(context, R.raw.click, 1) ?: -1
    }

    /**
     * 播放点击音效
     */
    fun playClickSound(context: Context, forceEnabled: Boolean = true) {
        // 如果外部逻辑（DataStore）关闭了声音且不是强制播放，则退出
        if (!forceEnabled) return
        
        // 兜底：如果还没初始化，立即初始化
        if (soundPool == null) initSoundPool(context)
        
        // 只有加载成功了才播放，防止报错
        if (isLoaded && clickSoundId != -1) {
            try {
                // 1.0f 代表最大音量（相对于当前的媒体音量百分比）
                soundPool?.play(clickSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        isLoaded = false
    }
}