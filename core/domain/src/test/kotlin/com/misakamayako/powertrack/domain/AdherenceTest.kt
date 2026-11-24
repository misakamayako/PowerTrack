package com.misakamayako.powertrack.domain

import com.misakamayako.powertrack.model.ScheduledSession
import com.misakamayako.powertrack.model.ScheduledStatus
import com.misakamayako.powertrack.model.SetPrescription
import com.misakamayako.powertrack.model.Routine
import com.misakamayako.powertrack.model.RoutineSlot
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §5.3 依从率：周维度 attended/scheduled；单次维度 completedSets/plannedSets。
 */
class AdherenceTest {

    private fun scheduled(date: LocalDate, status: ScheduledStatus, completed: Boolean = false) =
        ScheduledSession(
            id = "$date-$status",
            localDate = date,
            status = status,
            completedSessionId = if (completed) "s" else null,
        )

    @Test
    fun `周的依从率只统计该周排期`() {
        val today = LocalDate(2026, 11, 18)
        val sessions = listOf(
            scheduled(LocalDate(2026, 11, 16), ScheduledStatus.COMPLETED, completed = true),
            scheduled(LocalDate(2026, 11, 18), ScheduledStatus.PLANNED),
            scheduled(LocalDate(2026, 11, 10), ScheduledStatus.COMPLETED, completed = true),
        )
        val weeks = Adherence.weekly(sessions, today, weeks = 2)
        val thisWeek = weeks.last()
        assertEquals(2, thisWeek.scheduled)
        assertEquals(1, thisWeek.attended)
        assertEquals(0.5, thisWeek.rate)
        val lastWeek = weeks.first()
        assertEquals(1, lastWeek.scheduled)
        assertEquals(1.0, lastWeek.rate)
    }

    @Test
    fun `没计划的那周依从率为 null 而不是 0`() {
        val weeks = Adherence.weekly(emptyList(), LocalDate(2026, 11, 18), weeks = 1)
        assertNull(weeks.single().rate)
    }

    @Test
    fun `跳过的不算出席但算排期`() {
        val today = LocalDate(2026, 11, 18)
        val sessions = listOf(scheduled(LocalDate(2026, 11, 17), ScheduledStatus.SKIPPED))
        val week = Adherence.weekly(sessions, today, weeks = 1).single()
        assertEquals(1, week.scheduled)
        assertEquals(0, week.attended)
        assertEquals(0.0, week.rate)
    }

    @Test
    fun `单次完成度来自计划快照`() {
        val prescription = SetPrescription(id = "p", slotId = "slot", orderIndex = 0)
        val routine = Routine(
            id = "r",
            name = "推 A",
            slots = persistentListOf(
                RoutineSlot(
                    id = "slot",
                    routineId = "r",
                    orderIndex = 0,
                    exerciseId = "barbell_bench_press",
                    prescriptions = persistentListOf(prescription, prescription.copy(id = "p2", orderIndex = 1)),
                ),
            ),
        )
        val session = Fixtures.session(exercises = listOf(Fixtures.exercise())).copy(routineSnapshot = routine)
        assertEquals(2, session.plannedSetCount)
        assertEquals(1, session.completedSetCount)
        assertEquals(0.5, Adherence.sessionCompletion(session))
    }

    @Test
    fun `没有计划时单次完成度为 null`() {
        val session = Fixtures.session().copy(routineSnapshot = null)
        assertNull(Adherence.sessionCompletion(session))
        assertTrue(session.plannedSetCount == 0)
    }
}
