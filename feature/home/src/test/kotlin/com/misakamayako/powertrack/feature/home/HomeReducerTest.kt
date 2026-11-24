package com.misakamayako.powertrack.feature.home

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §4.4：Reducer 全部能在 JVM 单测里跑，不需要 Robolectric。
 */
class HomeReducerTest {

    private val today = LocalDate(2026, 11, 18)

    @Test
    fun `Loading 清掉错误并把 loading 置位`() {
        val initial = HomeStore.State(loading = false, error = "boom")
        val next = HomeReducer.run { initial.reduce(HomeStore.Message.Loading) }
        assertTrue(next.loading)
        assertNull(next.error)
    }

    @Test
    fun `Loaded 一次性写入三张卡的数据`() {
        val loaded = HomeStore.Message.Loaded(
            today = today,
            nextPlanned = null,
            lastSession = HomeStore.LastSessionSummary(
                sessionId = "s1",
                date = today,
                exerciseCount = 3,
                setCount = 12,
                tonnageKg = 5200.0,
                duration = null,
            ),
            activeSessionId = "active",
            week = HomeStore.WeekProgress(attended = 2, scheduled = 3, completedSets = 20, tonnageKg = 8000.0),
        )
        val state = HomeReducer.run { HomeStore.State().reduce(loaded) }
        assertEquals(today, state.today)
        assertEquals("active", state.activeSessionId)
        assertEquals(2, state.week.attended)
        assertEquals(3, state.week.scheduled)
        assertEquals("s1", state.lastSession?.sessionId)
    }

    @Test
    fun `Failed 保留旧数据只标记错误`() {
        val previous = HomeStore.State(loading = false, today = today, activeSessionId = "a")
        val next = HomeReducer.run { previous.reduce(HomeStore.Message.Failed("网络错误")) }
        assertEquals("网络错误", next.error)
        assertEquals(today, next.today, "失败不能把已有内容清空")
        assertEquals("a", next.activeSessionId)
    }

    @Test
    fun `hasPlannedToday 只在日期相等时为真`() {
        val plannedToday = HomeStore.State(
            today = today,
            nextPlanned = com.misakamayako.powertrack.model.ScheduledSession(
                id = "x",
                localDate = today,
            ),
        )
        assertTrue(plannedToday.hasPlannedToday)

        val plannedTomorrow = plannedToday.copy(
            nextPlanned = plannedToday.nextPlanned!!.copy(localDate = LocalDate(2026, 11, 19)),
        )
        assertTrue(!plannedTomorrow.hasPlannedToday)
    }

    @Test
    fun `依从率为 null 而不是 0 当没有排期时`() {
        assertNull(HomeStore.WeekProgress().rate)
        assertEquals(0.5, HomeStore.WeekProgress(attended = 1, scheduled = 2).rate)
    }
}
