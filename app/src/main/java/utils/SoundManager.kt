package utils

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.name.FlashLight.R

object SoundManager {

    private const val PREFS_NAME = "sound_settings"
    private const val KEY_SOUND_ENABLED = "sound_enabled"
    
    // 统一默认值为 true
    private const val DEFAULT_VALUE = true

    private var soundPool: SoundPool? = null
    private var clickSoundId: Int = 0

    fun isSoundEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SOUND_ENABLED, DEFAULT_VALUE)
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }

    fun initSoundPool(context: Context) {
        if (soundPool != null) return
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()
        clickSoundId = soundPool?.load(context, R.raw.click, 1) ?: 0
    }

    fun playClickSound(context: Context) {
        if (!isSoundEnabled(context)) return
        try {
            soundPool?.play(clickSoundId, 1.0f, 1.0f, 0, 0, 1.0f)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
    }
}