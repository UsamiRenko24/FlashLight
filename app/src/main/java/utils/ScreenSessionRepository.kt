package utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 屏幕补光会话仓库 (内存单例)
 * 职责：负责在 ScreenLightActivity 和 ScreenLightActiveActivity 之间同步临时状态
 */
object ScreenSessionRepository {
    
    // 初始化为 -1，表示尚未从 DataStore 同步
    private val _brightnessLevel = MutableStateFlow(-1)
    val brightnessLevel = _brightnessLevel.asStateFlow()

    private val _colorLevel = MutableStateFlow(-1)
    val colorLevel = _colorLevel.asStateFlow()

    fun updateBrightness(level: Int) {
        _brightnessLevel.value = level
    }

    fun updateColor(level: Int) {
        _colorLevel.value = level
    }

    /**
     * 判断是否需要从 DataStore 恢复默认值
     */
    fun isUninitialized() = _brightnessLevel.value == -1
}