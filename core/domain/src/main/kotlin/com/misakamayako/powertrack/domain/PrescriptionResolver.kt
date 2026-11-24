package com.misakamayako.powertrack.domain

import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.RepSpec
import com.misakamayako.powertrack.model.SessionSetSegment
import com.misakamayako.powertrack.model.SetPrescription

/**
 * 解析计划目标时需要的上下文（§2.5）。
 */
data class PrescriptionContext(
    /** 同动作、同 setType 的上一次实际 segment。 */
    val lastSameExerciseSegment: SessionSetSegment? = null,
    /** 该动作历史最高 e1RM。 */
    val bestE1rm: Double? = null,
    val bodyweightKg: Double? = null,
)

/**
 * 解析出来的建议值。`estimated = true` 时 UI **必须标注"估算"**（§5.5）。
 */
data class SuggestedSegment(
    val load: LoadSpec,
    val reps: RepSpec,
    val estimated: Boolean = false,
)

/**
 * 把 `SetPrescription` 解析成建议的初始 segment（§2.5）。纯函数。
 *
 * 优先级：`Weight` 直接给值 → `PercentOf1RM` 用 `bestE1rm * pct`（标注估算）→
 * `RpeOnly` 用上次同类型组的重量。
 */
object PrescriptionResolver {

    fun resolve(prescription: SetPrescription, ctx: PrescriptionContext): SuggestedSegment {
        val reps = prescription.repSpec
        return when (val load = prescription.loadSpec) {
            is LoadSpec.Weight -> SuggestedSegment(load = load, reps = reps, estimated = false)

            is LoadSpec.PercentOf1RM -> {
                val best = ctx.bestE1rm
                if (best == null || best <= 0.0) {
                    SuggestedSegment(load = load, reps = reps, estimated = true)
                } else {
                    val kg = roundToHalf(best * load.pct / 100.0)
                    SuggestedSegment(load = LoadSpec.Weight(kg), reps = reps, estimated = true)
                }
            }

            is LoadSpec.RpeOnly -> {
                val lastLoad = ctx.lastSameExerciseSegment?.load
                if (lastLoad == null) {
                    SuggestedSegment(load = load, reps = reps, estimated = false)
                } else {
                    SuggestedSegment(load = lastLoad, reps = reps, estimated = false)
                }
            }

            is LoadSpec.Bodyweight -> SuggestedSegment(load = load, reps = reps, estimated = false)
            is LoadSpec.Assisted -> SuggestedSegment(load = load, reps = reps, estimated = false)
            is LoadSpec.Band -> SuggestedSegment(load = load, reps = reps, estimated = false)
            is LoadSpec.MachineStack -> SuggestedSegment(load = load, reps = reps, estimated = false)
            is LoadSpec.FreeText -> SuggestedSegment(load = load, reps = reps, estimated = false)
        }
    }

    /** 杠铃最小步进 2.5kg（中国健身房常见的 1.25kg 配重片两侧）。 */
    fun roundToHalf(value: Double): Double = Math.round(value * 2.0) / 2.0
}
