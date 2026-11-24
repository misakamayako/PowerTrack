package com.misakamayako.powertrack.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 负荷描述（§2.3）。
 *
 * **不是 `Double`。** 计划侧常用 `PercentOf1RM` / `RpeOnly`，实际侧常用 `Weight` / `Bodyweight`。
 * `@SerialName` 显式固定为稳定字符串（§3.3）：类名重构不得破坏数据库里的数据。
 */
@Serializable
sealed interface LoadSpec {

    @Serializable
    @SerialName("weight")
    data class Weight(val kg: Double) : LoadSpec

    @Serializable
    @SerialName("percent_of_1rm")
    data class PercentOf1RM(val pct: Double) : LoadSpec

    @Serializable
    @SerialName("bodyweight")
    data class Bodyweight(val addedKg: Double? = null) : LoadSpec

    /** 助力引体等：重量是减向。 */
    @Serializable
    @SerialName("assisted")
    data class Assisted(val kg: Double) : LoadSpec

    @Serializable
    @SerialName("band")
    data class Band(val level: Int, val color: String? = null) : LoadSpec

    @Serializable
    @SerialName("machine_stack")
    data class MachineStack(val level: Int, val kgPerLevel: Double? = null) : LoadSpec

    @Serializable
    @SerialName("rpe_only")
    data class RpeOnly(val target: Double) : LoadSpec

    @Serializable
    @SerialName("free_text")
    data class FreeText(val text: String) : LoadSpec
}

/**
 * 次数描述（§2.3）。
 */
@Serializable
sealed interface RepSpec {

    @Serializable
    @SerialName("fixed")
    data class Fixed(val n: Int) : RepSpec

    @Serializable
    @SerialName("range")
    data class Range(val min: Int, val max: Int) : RepSpec

    @Serializable
    @SerialName("amrap")
    data object Amrap : RepSpec

    @Serializable
    @SerialName("to_failure")
    data object ToFailure : RepSpec

    @Serializable
    @SerialName("time")
    data class Time(val durationMs: Long) : RepSpec

    @Serializable
    @SerialName("distance")
    data class Distance(val meters: Double) : RepSpec

    @Serializable
    @SerialName("calories")
    data class Calories(val kcal: Int) : RepSpec
}
