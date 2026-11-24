package com.misakamayako.powertrack.domain

import kotlin.math.abs

/**
 * 浮点比较工具（§7.1 第 4 条）：重量比较和 PR 判定用 `0.001` 容差，不用 `==`。
 */
object Tolerance {
    const val EPSILON = 0.001

    fun eq(a: Double, b: Double): Boolean = abs(a - b) < EPSILON

    fun gt(a: Double, b: Double): Boolean = a - b > EPSILON

    fun gte(a: Double, b: Double): Boolean = a - b > -EPSILON

    fun lt(a: Double, b: Double): Boolean = b - a > EPSILON
}

/**
 * e1RM 估算（§5.1）。
 *
 * Epley：`w * (1 + reps / 30)`，`reps = 1` 时等于 `w`。
 * reps > 12 可以算，但标记为"估算不可靠"，不参与 PR 判定。
 */
object E1rm {

    /** 超过这个次数，Epley 估算不可靠（§5.1 / §3.4 的 1..12 区间）。 */
    const val MAX_RELIABLE_REPS = 12

    fun isReliable(reps: Int): Boolean = reps in 1..MAX_RELIABLE_REPS

    /** 返回 null 表示无法估算（次数 <= 0）。 */
    fun epley(loadKg: Double, reps: Int): Double? {
        if (reps <= 0) return null
        if (loadKg <= 0.0) return null
        return loadKg * (1.0 + reps / 30.0)
    }

    /**
     * 只对可靠区间（1..12 次）估算，供冗余列与 PR 使用。
     */
    fun reliableEpley(loadKg: Double, reps: Int): Double? =
        if (isReliable(reps)) epley(loadKg, reps) else null
}
