package com.misakamayako.powertrack.model


/**
 * 应用设置（§3.5 / §4.4 SettingsStore）。
 *
 * `dayOffsetHours` 给熬夜的人准备：默认 0，设置后 00:30 开始的训练可以归到前一天。
 * 归日一律走 `TrainingDate.of(instant, zone, dayOffsetHours)`，不许任何地方自己 `LocalDate.now()`。
 */
data class AppSettings(
    val dayOffsetHours: Int = 0,
    val defaultRestSec: Int = 90,
    val unit: WeightUnit = WeightUnit.KG,
    /** 最近一次录入的体重，用于自重动作的 resolvedLoad 计算。 */
    val bodyweightKg: Double? = null,
) {
    companion object {
        val DEFAULT = AppSettings()
        val DAY_OFFSET_RANGE = -6..6
        val REST_SEC_RANGE = 15..600
    }
}
