package com.misakamayako.powertrack.domain

import com.misakamayako.powertrack.model.ScheduledSession
import com.misakamayako.powertrack.model.ScheduledStatus
import com.misakamayako.powertrack.model.WorkoutSession
import kotlinx.datetime.LocalDate

/**
 * 周维度的依从率（§5.3）。
 */
data class WeekAdherence(
    val weekStart: LocalDate,
    val scheduled: Int,
    val attended: Int,
) {
    val rate: Double? get() = if (scheduled == 0) null else attended.toDouble() / scheduled
}

/**
 * 依从率（§5.3）。
 *
 * - 周维度：`attended / scheduled`，`attended` = 该周有 COMPLETED 的排期数
 * - 单次维度：`completedSets / plannedSets`
 */
object Adherence {

    fun weekly(sessions: List<ScheduledSession>, today: LocalDate, weeks: Int = 6): List<WeekAdherence> =
        TrainingDate.recentWeekStarts(today, weeks).map { weekStart ->
            val weekEnd = TrainingDate.weekEnd(weekStart)
            val inWeek = sessions.filter { it.localDate >= weekStart && it.localDate <= weekEnd }
            WeekAdherence(
                weekStart = weekStart,
                // 还没到期（日期在今天之后）的 PLANNED 不算"该做没做"，不计入分母
                scheduled = inWeek.count { it.status != ScheduledStatus.PLANNED || it.localDate <= today },
                attended = inWeek.count { it.status == ScheduledStatus.COMPLETED || it.completedSessionId != null },
            )
        }

    /** 单次训练的完成度。计划为空时返回 null（无法计算，不当 0 处理）。 */
    fun sessionCompletion(session: WorkoutSession): Double? {
        val planned = session.plannedSetCount
        if (planned <= 0) return null
        return (session.completedSetCount.toDouble() / planned).coerceIn(0.0, 1.0)
    }
}
