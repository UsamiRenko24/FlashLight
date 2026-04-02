//package com.name.FlashLight
//
//import android.content.Context
//import androidx.appcompat.app.AppCompatActivity
//import androidx.lifecycle.ViewModel
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.flow.asStateFlow
//import utils.AutoBrightnessManager
//import utils.BatteryRepository
//import utils.LowBatteryManager
//import utils.TemperatureManager
//import utils.TimeRepository
//import utils.toDetailedTime
//import kotlin.math.abs
//
//class StatsViewModel(
//    private val batteryRepo: BatteryRepository,
//    private val timeRepo: TimeRepository
//) : ViewModel() {
//
//    data class StatsUiState(
//        val batteryLevelText: String = "",
//        val batteryStatus: String = "",
//        val batteryIconRes: Int = 0,
//        val batteryCardBgRes: Int = R.drawable.bg_green_card,
//        val timeEstimateLabel: String = "",
//        val timeEstimateValue: String = "",
//
//        val totalTimeText: String = "",
//        val flashlightProgress: Int = 0,
//        val screenLightProgress: Int = 0,
//        val blinkProgress: Int = 0,
//
//        val flashlightTimeText: String = "",
//        val screenLightTimeText: String = "",
//        val blinkTimeText: String = "",
//
//        val isLowBatteryEnabled: Boolean = true,
//        val isTemperatureEnabled: Boolean = true,
//        val isAutoBrightnessEnabled: Boolean = false,
//
//        val batteryHealth: String = ""
//    )
//
//    private val _uiState = MutableStateFlow(StatsUiState())
//    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()
//
//    fun refreshAllData(context: Context) {
//        val fTime = timeRepo.getTodayUsageMinutes(TimeRepository.TYPE_FLASHLIGHT)
//        val sTime = timeRepo.getTodayUsageMinutes(TimeRepository.TYPE_SCREEN_LIGHT)
//        val bTime = timeRepo.getTodayUsageMinutes(TimeRepository.TYPE_BLINK)
//        val total = timeRepo.getTodayTotalUsageMinutes()
//
//        // 修正：确保在这里使用传入的 context 获取翻译
//        _uiState.value = _uiState.value.copy(
//            totalTimeText = total.toDetailedTime(context),
//            flashlightTimeText = fTime.toDetailedTime(context),
//            screenLightTimeText = sTime.toDetailedTime(context),
//            blinkTimeText = bTime.toDetailedTime(context),
//
//            flashlightProgress = calculateProgress(fTime, total),
//            screenLightProgress = calculateProgress(sTime, total),
//            blinkProgress = calculateProgress(bTime, total),
//
//            batteryHealth = batteryRepo.getBatteryHealthDescription(context),
//
//            isLowBatteryEnabled = LowBatteryManager.isProtectionEnabled(context),
//            isTemperatureEnabled = TemperatureManager.isEnabled(),
//            isAutoBrightnessEnabled = if (context is AppCompatActivity) AutoBrightnessManager.getAutoBrightnessState(context) else false
//        )
//    }
//
//    fun handleBatteryUpdate(context: Context, info: BatteryRepository.BatteryInfo) {
//        val cardBg = when {
//            info.level <= 25 -> R.drawable.bg_sos_card_red
//            info.level <= 50 -> R.drawable.bg_yellow_card
//            else -> R.drawable.bg_green_card
//        }
//
//        val (label, value) = calculateEstimate(context, info)
//
//        _uiState.value = _uiState.value.copy(
//            batteryLevelText = info.levelText,
//            batteryStatus = info.status,
//            batteryIconRes = info.iconRes,
//            batteryCardBgRes = cardBg,
//            timeEstimateLabel = label,
//            timeEstimateValue = value
//        )
//    }
//
//    fun toggleLowBattery(context: Context, enabled: Boolean) {
//        LowBatteryManager.setProtectionEnabled(context, enabled)
//        _uiState.value = _uiState.value.copy(isLowBatteryEnabled = enabled)
//    }
//
//    fun toggleTemperature(enabled: Boolean) {
//        TemperatureManager.setEnabled(enabled)
//        _uiState.value = _uiState.value.copy(isTemperatureEnabled = enabled)
//    }
//
//    private fun calculateProgress(time: Float, total: Float) =
//        if (total > 0) (time / total * 100).toInt().coerceIn(0, 100) else 0
//
//    private fun calculateEstimate(context: Context, info: BatteryRepository.BatteryInfo): Pair<String, String> {
//        return if (info.isCharging) {
//            context.getString(R.string.time_to_full) to (if (info.estimateMinutes > 0) info.estimateMinutes.toFloat().toDetailedTime(context) else context.getString(R.string.calculating))
//        } else {
//            context.getString(R.string.time_remaining) to (if (info.estimateMinutes > 0) info.estimateMinutes.toFloat() else (info.level * 10)).toDetailedTime(context)
//        }
//    }
//}