package com.suiyu.ledger.ui.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全链路触感反馈。
 *
 * 设计原则（参考调研结论「触感要分层且可整体关闭」）：
 *  - 不同语义用不同节奏：轻点、切换、确认、成功、警示、导入完成；
 *  - 强度可在设置里三档调节，也可一键关闭；
 *  - 任何机型（无马达 / 不支持振幅控制）都不会崩溃，静默降级。
 */
enum class HapticEffect {
    /** 键盘 / 列表轻点 */
    Tick,

    /** 轻触、hover 感 */
    Soft,

    /** 选择 chip / tab 切换 */
    Select,

    /** 保存、提交 */
    Confirm,

    /** 成功（记账完成、导入完成） */
    Success,

    /** 警示（额度、重复账单） */
    Warning,

    /** 错误 / 删除 */
    Error,

    /** 长按 */
    LongPress,

    /** 开关切换 */
    Toggle,

    /** 批量导入完成的波纹感 */
    Wave,
}

object Haptics {

    private var vibrator: Vibrator? = null
    private var amplitudeControl: Boolean = false

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** 1 轻 / 2 标准 / 3 明显 */
    private var strength: Int = 2

    fun init(context: Context) {
        vibrator = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
        amplitudeControl = runCatching { vibrator?.hasAmplitudeControl() == true }.getOrDefault(false)
    }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
    }

    fun isEnabled(): Boolean = _enabled.value

    fun setStrength(value: Int) {
        strength = value.coerceIn(1, 3)
    }

    fun strength(): Int = strength

    /** 播放一个语义化触感；未初始化 / 已关闭 / 无马达时静默跳过 */
    fun play(effect: HapticEffect) {
        if (!_enabled.value) return
        val target = vibrator ?: return
        if (!target.hasVibrator()) return
        val pattern = patternOf(effect)
        runCatching {
            val effectObj = if (amplitudeControl) {
                VibrationEffect.createWaveform(pattern.first, pattern.second, -1)
            } else {
                VibrationEffect.createWaveform(pattern.first, -1)
            }
            target.vibrate(effectObj)
        }
    }

    private fun scale(amplitude: Int): Int {
        val factor = when (strength) {
            1 -> 0.5
            3 -> 1.0
            else -> 0.78
        }
        return (amplitude * factor).toInt().coerceIn(1, 255)
    }

    /** 返回 timings / amplitudes（首项 0 表示立即开始） */
    private fun patternOf(effect: HapticEffect): Pair<LongArray, IntArray> {
        return when (effect) {
            HapticEffect.Tick ->
                longArrayOf(0, 6) to intArrayOf(0, scale(58))

            HapticEffect.Soft ->
                longArrayOf(0, 10) to intArrayOf(0, scale(38))

            HapticEffect.Select ->
                longArrayOf(0, 7, 18, 5) to intArrayOf(0, scale(46), 0, scale(32))

            HapticEffect.Confirm ->
                longArrayOf(0, 12, 26, 16) to intArrayOf(0, scale(120), 0, scale(70))

            HapticEffect.Success ->
                longArrayOf(0, 10, 34, 14, 34, 22) to intArrayOf(0, scale(90), 0, scale(120), 0, scale(150))

            HapticEffect.Warning ->
                longArrayOf(0, 22, 44, 22) to intArrayOf(0, scale(160), 0, scale(160))

            HapticEffect.Error ->
                longArrayOf(0, 34, 56, 34) to intArrayOf(0, scale(220), 0, scale(220))

            HapticEffect.LongPress ->
                longArrayOf(0, 22) to intArrayOf(0, scale(120))

            HapticEffect.Toggle ->
                longArrayOf(0, 8) to intArrayOf(0, scale(70))

            HapticEffect.Wave ->
                longArrayOf(0, 8, 28, 8, 28, 8, 28, 10, 40, 26) to intArrayOf(
                    0, scale(60), 0, scale(90), 0, scale(110), 0, scale(130), 0, scale(160),
                )
        }
    }
}
