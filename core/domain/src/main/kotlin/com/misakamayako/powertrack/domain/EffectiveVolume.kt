package com.misakamayako.powertrack.domain

import com.misakamayako.powertrack.model.ExerciseMuscle
import com.misakamayako.powertrack.model.WorkoutSession

/**
 * 某个肌群在某段时间内的有效容量（§5.2）。
 *
 * **必须同时报告"计入的组数 / 总组数"**，让用户知道有多少数据是估算不出来的。
 */
data class MuscleVolume(
    val muscleId: String,
    val effectiveVolumeKg: Double,
    val includedSets: Int,
    val totalSets: Int,
) {
    val coverage: Double get() = if (totalSets == 0) 0.0 else includedSets.toDouble() / totalSets
}

/**
 * 有效容量（§5.2）：
 *
 * ```
 * effectiveVolume(muscle) = Σ over (非热身 segment) of resolvedLoadKg × resolvedReps × contribution
 * ```
 *
 * `resolvedLoadKg` 或 `resolvedReps` 为 null 的 segment **跳过，不按 0 算**。
 */
object EffectiveVolume {

    fun compute(
        sessions: List<WorkoutSession>,
        links: List<ExerciseMuscle>,
        bodyweightKg: Double?,
    ): List<MuscleVolume> {
        val contributionByExercise = links.groupBy { it.exerciseId }

        val volume = LinkedHashMap<String, Double>()
        val included = LinkedHashMap<String, Int>()
        val total = LinkedHashMap<String, Int>()

        for (session in sessions) {
            for (exercise in session.exercises) {
                val muscles = contributionByExercise[exercise.exerciseId].orEmpty()
                if (muscles.isEmpty()) continue
                for (set in exercise.sets) {
                    if (set.isWarmup) continue
                    val metrics = SetMetricsCalculator.compute(set.segments, bodyweightKg)
                    val tonnage = metrics.tonnage
                    for (link in muscles) {
                        total[link.muscleId] = (total[link.muscleId] ?: 0) + 1
                        if (tonnage != null) {
                            included[link.muscleId] = (included[link.muscleId] ?: 0) + 1
                            volume[link.muscleId] =
                                (volume[link.muscleId] ?: 0.0) + tonnage * link.contribution
                        }
                    }
                }
            }
        }

        return (total.keys + volume.keys).distinct().map { muscleId ->
            MuscleVolume(
                muscleId = muscleId,
                effectiveVolumeKg = volume[muscleId] ?: 0.0,
                includedSets = included[muscleId] ?: 0,
                totalSets = total[muscleId] ?: 0,
            )
        }.sortedByDescending { it.effectiveVolumeKg }
    }
}
