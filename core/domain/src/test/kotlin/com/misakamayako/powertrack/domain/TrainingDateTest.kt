package com.misakamayako.powertrack.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * §3.5：归日必须走统一入口，训练日偏移让熬夜的人把 00:30 的训练算到前一天。
 */
class TrainingDateTest {

    private val shanghai = TimeZone.of("Asia/Shanghai")

    @Test
    fun `默认按本地日历日归档`() {
        // 2026-11-17T17:00:00Z == 2026-11-18 01:00 +08:00
        val instant = Instant.parse("2026-11-17T17:00:00Z")
        assertEquals(LocalDate(2026, 11, 18), TrainingDate.of(instant, shanghai))
    }

    @Test
    fun `日偏移为 4 小时时凌晨训练归到前一天`() {
        val instant = Instant.parse("2026-11-17T16:30:00Z") // 2026-11-18 00:30 +08:00
        assertEquals(LocalDate(2026, 11, 17), TrainingDate.of(instant, shanghai, dayOffsetHours = 4))
    }

    @Test
    fun `日偏移不能把白天训练挪走`() {
        val instant = Instant.parse("2026-11-18T10:00:00Z") // 18:00 +08:00
        assertEquals(LocalDate(2026, 11, 18), TrainingDate.of(instant, shanghai, dayOffsetHours = 4))
    }

    @Test
    fun `ISO 周标签使用周一起点`() {
        assertEquals(LocalDate(2026, 11, 16), TrainingDate.weekStart(LocalDate(2026, 11, 18)))
        assertEquals(LocalDate(2026, 11, 22), TrainingDate.weekEnd(LocalDate(2026, 11, 18)))
        assertEquals("2026-W47", TrainingDate.isoWeekLabel(LocalDate(2026, 11, 18)))
    }

    @Test
    fun `周一自己也是本周起点`() {
        val monday = LocalDate(2026, 11, 16)
        assertEquals(monday, TrainingDate.weekStart(monday))
        assertEquals("2026-W47", TrainingDate.isoWeekLabel(monday))
    }

    @Test
    fun `最近 N 周从旧到新且跨度正确`() {
        val weeks = TrainingDate.recentWeekStarts(LocalDate(2026, 11, 18), weeks = 3)
        assertEquals(
            listOf(LocalDate(2026, 11, 2), LocalDate(2026, 11, 9), LocalDate(2026, 11, 16)),
            weeks,
        )
    }
}
