package com.misakamayako.powertrack.domain

import com.misakamayako.powertrack.model.PersonalRecord
import com.misakamayako.powertrack.model.PrKind
import com.misakamayako.powertrack.model.WorkoutSession
import kotlin.time.Instant

/**
 * 待写入的 PR（§5.4）。`id` 用确定性字符串，保证同一 `(exerciseId, kind)` 只有一行，
 * 因此重复 finish 不会产生重复 PR（幂等）。
 */
data class PrCandidate(
    val exerciseId: String,
    val kind: PrKind,
    val value: Double,
    val unit: String,
    val sessionSetId: String? = null,
    val achievedAt: Instant,
) {
    val id: String get() = prId(exerciseId, kind)

    companion object {
        fun prId(exerciseId: String, kind: PrKind): String = "pr:$exerciseId:${kind.name.lowercase()}"
    }
}

/**
 * PR 检测（§5.4）。`Finish` 时执行。
 */
object PrDetector {

    fun candidates(session: WorkoutSession): List<PrCandidate> {
        val achievedAt = session.endedAt ?: session.startedAt
        val result = mutableListOf<PrCandidate>()

        for (exercise in session.exercises) {
            var maxWeight: Double? = null
            var maxWeightSetId: String? = null
            var maxE1rm: Double? = null
            var maxE1rmSetId: String? = null
            var tonnageSum = 0.0
            var tonnageKnown = false
            // 同重量下的最大次数：key 归一化到 0.001 精度
            val repsAtWeight = LinkedHashMap<Long, Pair<Int, String>>()

            for (set in exercise.sets) {
                val metrics = SetMetricsCalculator.compute(set.segments, session.bodyweightKg)
                val load = metrics.resolvedLoadKg
                val reps = metrics.resolvedReps

                if (load != null && reps != null && reps >= 1) {
                    if (maxWeight == null) {
                        maxWeight = load
                        maxWeightSetId = set.id
                    } else if (load > maxWeight) {
                        maxWeight = load
                        maxWeightSetId = set.id
                    }
                    val key = Math.round(load * 1000.0)
                    val current = repsAtWeight[key]
                    if (current == null || reps > current.first) {
                        repsAtWeight[key] = reps to set.id
                    }
                }

                val e1rm = metrics.e1rm
                if (e1rm != null) {
                    if (maxE1rm == null) {
                        maxE1rm = e1rm
                        maxE1rmSetId = set.id
                    } else if (e1rm > maxE1rm) {
                        maxE1rm = e1rm
                        maxE1rmSetId = set.id
                    }
                }

                metrics.tonnage?.let {
                    tonnageSum += it
                    tonnageKnown = true
                }
            }

            maxWeight?.let {
                result += PrCandidate(
                    exerciseId = exercise.exerciseId,
                    kind = PrKind.MAX_WEIGHT,
                    value = it,
                    unit = "kg",
                    sessionSetId = maxWeightSetId,
                    achievedAt = achievedAt,
                )
            }

            repsAtWeight.maxByOrNull { it.value.first }?.let { (key, pair) ->
                val kg = key / 1000.0
                result += PrCandidate(
                    exerciseId = exercise.exerciseId,
                    kind = PrKind.MAX_REPS_AT_WEIGHT,
                    value = pair.first.toDouble(),
                    unit = "reps@${trimNumber(kg)}kg",
                    sessionSetId = pair.second,
                    achievedAt = achievedAt,
                )
            }

            maxE1rm?.let {
                result += PrCandidate(
                    exerciseId = exercise.exerciseId,
                    kind = PrKind.MAX_E1RM,
                    value = it,
                    unit = "kg",
                    sessionSetId = maxE1rmSetId,
                    achievedAt = achievedAt,
                )
            }

            if (tonnageKnown && tonnageSum > 0.0) {
                result += PrCandidate(
                    exerciseId = exercise.exerciseId,
                    kind = PrKind.MAX_SESSION_TONNAGE,
                    value = tonnageSum,
                    unit = "kg",
                    sessionSetId = null,
                    achievedAt = achievedAt,
                )
            }
        }

        return result
    }

    /**
     * 只返回真正破纪录的候选：**带容差比较**（§7.1），并且同一次训练的重复 finish 不会重复产出。
     */
    fun improved(candidates: List<PrCandidate>, existing: List<PersonalRecord>): List<PrCandidate> {
        val best = existing.associate { (it.exerciseId to it.kind) to it.value }
        return candidates.filter { candidate ->
            val previous = best[candidate.exerciseId to candidate.kind]
            previous == null || Tolerance.gt(candidate.value, previous)
        }
    }

    private fun trimNumber(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString() else value.toString()
}
