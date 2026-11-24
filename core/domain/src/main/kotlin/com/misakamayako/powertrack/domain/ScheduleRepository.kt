package com.misakamayako.powertrack.domain

import com.misakamayako.powertrack.model.Routine
import com.misakamayako.powertrack.model.ScheduledSession
import com.misakamayako.powertrack.model.ScheduledStatus
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * 排期仓储（§2.1 / §2.6）。
 *
 * 与实际的关联只存在于 `WorkoutSession.sourceScheduledId` 一侧（铁律 3），
 * `COMPLETED` 状态由 `markCompletedIfLinked` 反查回填。
 */
interface ScheduleRepository {

    fun observeBetween(from: LocalDate, to: LocalDate): Flow<PersistentList<ScheduledSession>>

    /** 下一次计划：`local_date >= today` 且 `status = PLANNED` 的最近一条（§8）。 */
    fun observeNextPlanned(from: LocalDate): Flow<ScheduledSession?>

    fun observeAll(): Flow<PersistentList<ScheduledSession>>

    suspend fun byId(id: String): ScheduledSession?

    /** `local_date` 落在区间内的排期。 */
    suspend fun between(from: LocalDate, to: LocalDate): PersistentList<ScheduledSession>

    suspend fun recent(limit: Int = 30): PersistentList<ScheduledSession>

    suspend fun schedule(routine: Routine, date: LocalDate, notes: String? = null): String

    suspend fun setStatus(id: String, status: ScheduledStatus)

    suspend fun markStarted(id: String)

    /** 训练结束时在同一事务里调用：把 `source_scheduled_id` 指向该排期并置为 COMPLETED。 */
    suspend fun markCompletedSession(scheduledId: String, sessionId: String)

    /** 日期已过、仍是 PLANNED 的排期 → SKIPPED（M4 依从率计算前执行）。 */
    suspend fun skipOverdue(today: LocalDate): Int

    suspend fun delete(id: String)
}
