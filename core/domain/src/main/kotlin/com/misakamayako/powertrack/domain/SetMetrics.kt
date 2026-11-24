package com.misakamayako.powertrack.domain

import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.RepSpec
import com.misakamayako.powertrack.model.SessionSetSegment

/**
 * 冗余列的计算结果（§3.4）。`core:domain` 纯函数算出，`core:data` 写库时一并写入。
 */
data class SetMetrics(
    val resolvedLoadKg: Double? = null,
    val resolvedReps: Int? = null,
    val e1rm: Double? = null,
    val tonnage: Double? = null,
) {
    /** 有没有至少一个可解析的数值。 */
    val hasAnyValue: Boolean get() = resolvedLoadKg != null || resolvedReps != null
}

/**
 * §3.4 的全部规则。**全部纯函数**，不碰 DB、不碰 Android。
 *
 * 关键点：`e1rm` 必须**逐 segment** 用自己的 load/reps 配对后取最大，
 * 绝不能拿 `resolvedLoadKg`（最大重量）配 `resolvedReps`（总次数）。
 */
object SetMetricsCalculator {

    /**
     * 把 LoadSpec 解析成 kg。无法在没有上下文时解析的（%1RM / 弹力带 / RPE / 文本）返回 null。
     */
    fun loadOf(load: LoadSpec, bodyweightKg: Double?): Double? = when (load) {
        is LoadSpec.Weight -> load.kg
        is LoadSpec.Bodyweight -> bodyweightKg?.plus(load.addedKg ?: 0.0)
        is LoadSpec.Assisted -> bodyweightKg?.minus(load.kg)
        is LoadSpec.MachineStack -> load.kgPerLevel?.let { load.level * it }
        is LoadSpec.PercentOf1RM -> null
        is LoadSpec.Band -> null
        is LoadSpec.RpeOnly -> null
        is LoadSpec.FreeText -> null
    }

    /** 把 RepSpec 解析成次数。未知（力竭 / AMRAP / 计时 / 距离 / 卡路里）返回 null。 */
    fun repsOf(reps: RepSpec): Int? = when (reps) {
        is RepSpec.Fixed -> reps.n
        is RepSpec.Range -> maxOf(reps.min, reps.max)
        RepSpec.Amrap -> null
        RepSpec.ToFailure -> null
        is RepSpec.Time -> null
        is RepSpec.Distance -> null
        is RepSpec.Calories -> null
    }

    fun compute(segments: List<SessionSetSegment>, bodyweightKg: Double?): SetMetrics {
        if (segments.isEmpty()) return SetMetrics()

        var maxLoad: Double? = null
        var repSum: Int? = null
        var maxE1rm: Double? = null
        var tonnage: Double? = null

        for (segment in segments) {
            val load = loadOf(segment.load, bodyweightKg)
            val reps = repsOf(segment.reps)

            if (load != null) maxLoad = maxLoad?.let { maxOf(it, load) } ?: load

            if (reps != null) repSum = (repSum ?: 0) + reps

            // e1RM：同一 segment 的 load 与 reps 配对，取最大
            if (load != null && reps != null) {
                val estimate = E1rm.reliableEpley(load, reps)
                if (estimate != null) maxE1rm = maxE1rm?.let { maxOf(it, estimate) } ?: estimate
            }

            if (load != null && reps != null) {
                val segmentTonnage = load * reps
                tonnage = (tonnage ?: 0.0) + segmentTonnage
            }
        }

        return SetMetrics(
            resolvedLoadKg = maxLoad,
            resolvedReps = repSum,
            e1rm = maxE1rm,
            tonnage = tonnage,
        )
    }
}
