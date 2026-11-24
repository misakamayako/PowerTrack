package com.misakamayako.powertrack.data

import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.RepSpec
import com.misakamayako.powertrack.model.ScheduledStatus
import com.misakamayako.powertrack.model.SetType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §2.1 / §2.6 排期：三种入口 + COMPLETED 回填 + 依从率用的跳过。
 */
class ScheduleRepositoryTest : DataTestBase() {

    private val today = LocalDate(2026, 11, 18)

    @Test
    fun `排期后用快照启动训练，结束回填 COMPLETED`() = runTest {
        seed()
        val routine = TestRoutines.pushDay()
        routineRepository.create(routine)
        val scheduledId = scheduleRepository.schedule(routine, today)

        val scheduled = scheduleRepository.byId(scheduledId)!!
        assertEquals(today, scheduled.localDate)
        assertEquals(ScheduledStatus.PLANNED, scheduled.status)
        assertEquals("推 A", scheduled.routineName)
        assertNotNull(scheduled.routineSnapshot, "排期必须带计划快照")

        val session = workoutRepository.startFromScheduled(scheduledId)!!
        assertEquals(scheduledId, session.sourceScheduledId)
        assertEquals(ScheduledStatus.STARTED, scheduleRepository.byId(scheduledId)!!.status)

        // 从计划克隆：2 个动作，7 组，prescription 快照已写入
        assertEquals(2, session.exercises.size)
        assertEquals(4, session.exercises[0].sets.size)
        assertEquals(7, session.plannedSetCount)
        assertNotNull(session.exercises[0].sets[0].prescribed)
        assertNotNull(session.routineSnapshot)

        // 录一组再结束
        val target = session.exercises[0]
        workoutRepository.addSet(target.id, setType = SetType.NORMAL, load = LoadSpec.Weight(100.0), reps = RepSpec.Fixed(5))
        workoutRepository.finish(session.id)

        val after = scheduleRepository.byId(scheduledId)!!
        assertEquals(ScheduledStatus.COMPLETED, after.status)
        assertEquals(session.id, after.completedSessionId)
    }

    @Test
    fun `没计划临时练不污染任何排期`() = runTest {
        seed()
        val routine = TestRoutines.pushDay()
        routineRepository.create(routine)
        val scheduledId = scheduleRepository.schedule(routine, today.plusDays(1))

        val session = workoutRepository.startBlank()
        assertNull(session.sourceScheduledId)
        val exercise = workoutRepository.addExercise(session.id, "barbell_bench_press")
        workoutRepository.addSet(exercise.id, load = LoadSpec.Weight(80.0), reps = RepSpec.Fixed(10))
        workoutRepository.finish(session.id)

        val scheduled = scheduleRepository.byId(scheduledId)!!
        assertEquals(ScheduledStatus.PLANNED, scheduled.status, "临时训练不能把排期标成完成")
        assertNull(scheduled.completedSessionId)
    }

    @Test
    fun `重复上次会带上上一次的组和段`() = runTest {
        seed()
        val first = workoutRepository.startBlank()
        val exercise = workoutRepository.addExercise(first.id, "barbell_bench_press")
        val set = workoutRepository.addSet(exercise.id, load = LoadSpec.Weight(100.0), reps = RepSpec.Fixed(8))
        workoutRepository.appendSegment(set.id, LoadSpec.Weight(80.0), RepSpec.Fixed(6))
        workoutRepository.finish(first.id)

        val repeated = workoutRepository.repeatLast()!!
        assertNull(repeated.sourceScheduledId)
        val sets = repeated.exercises.single().sets
        assertEquals(1, sets.size)
        assertEquals(2, sets.single().segments.size)
        assertEquals(80.0, sets.single().segments[1].resolvedLoadKg)
        assertTrue(sets.single().id != set.id, "重复上次必须用新 id")
    }

    @Test
    fun `过期的 PLANNED 会被标记为 SKIPPED`() = runTest {
        seed()
        val routine = TestRoutines.pushDay()
        routineRepository.create(routine)
        val overdueId = scheduleRepository.schedule(routine, LocalDate(2026, 11, 10))
        val futureId = scheduleRepository.schedule(routine, LocalDate(2026, 11, 25))

        val skipped = scheduleRepository.skipOverdue(today)
        assertEquals(1, skipped)
        assertEquals(ScheduledStatus.SKIPPED, scheduleRepository.byId(overdueId)!!.status)
        assertEquals(ScheduledStatus.PLANNED, scheduleRepository.byId(futureId)!!.status)
    }

    @Test
    fun `下一次计划只取最近的 PLANNED`() = runTest {
        seed()
        val routine = TestRoutines.pushDay()
        routineRepository.create(routine)
        scheduleRepository.schedule(routine, LocalDate(2026, 11, 20))
        val nearest = scheduleRepository.schedule(routine, LocalDate(2026, 11, 19))
        scheduleRepository.schedule(routine, LocalDate(2026, 11, 10)) // 过去的

        val next = scheduleRepository.observeNextPlanned(today).first()
        assertNotNull(next)
        assertEquals(nearest, next.id)
    }

    @Test
    fun `discard 会把排期退回 PLANNED`() = runTest {
        seed()
        val routine = TestRoutines.pushDay()
        routineRepository.create(routine)
        val scheduledId = scheduleRepository.schedule(routine, today)
        val session = workoutRepository.startFromScheduled(scheduledId)!!
        workoutRepository.discard(session.id)
        assertEquals(ScheduledStatus.PLANNED, scheduleRepository.byId(scheduledId)!!.status)
    }
}

/** §3.5 归日：测试里固定时区。 */
private fun LocalDate.plusDays(days: Int): LocalDate = this.plus(days, kotlinx.datetime.DateTimeUnit.DAY)
