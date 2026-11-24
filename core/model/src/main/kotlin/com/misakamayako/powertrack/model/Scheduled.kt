package com.misakamayako.powertrack.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * 排期实例（§2.1）：挂在某个日期上的「应该练这个」。
 *
 * **与实际的关联只存在于 `WorkoutSession` 侧**（铁律 3）。
 * `completedSessionId` 是由 `workout_session.source_scheduled_id` 反查出来的**只读投影**，
 * 数据库里没有这一列，写回时与 session 结束在同一事务里完成。
 */
data class ScheduledSession(
    val id: String,
    val localDate: LocalDate,
    val routineId: String? = null,
    val routineSnapshot: Routine? = null,
    val status: ScheduledStatus = ScheduledStatus.PLANNED,
    val notes: String? = null,
    val createdAt: Instant = Instant.fromEpochMilliseconds(0),
    /** 只读投影：覆盖这条排期的 session id（可为空）。 */
    val completedSessionId: String? = null,
    /** 只读投影：排期用的计划名。 */
    val routineName: String? = null,
) {
    val isPlanned: Boolean get() = status == ScheduledStatus.PLANNED
    val plannedSetCount: Int get() = routineSnapshot?.plannedSetCount ?: 0
}
