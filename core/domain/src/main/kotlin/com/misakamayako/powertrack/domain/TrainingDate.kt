package com.misakamayako.powertrack.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * 归日工具（§3.5）。**所有"今天"的判断都必须经过这里**，不许任何地方自己 `LocalDate.now()`。
 */
object TrainingDate {

    /**
     * 把时间点换算成本地日历日。
     *
     * @param dayOffsetHours 训练日偏移：正数表示"一天从更晚的时刻开始"，例如 4 时，
     *   00:30 的训练归到前一天。
     */
    fun of(instant: Instant, zone: TimeZone = TimeZone.currentSystemDefault(), dayOffsetHours: Int = 0): LocalDate {
        val shifted = if (dayOffsetHours == 0) instant else instant - dayOffsetHours.hours
        return shifted.toLocalDateTime(zone).date
    }

    fun today(now: Instant, zone: TimeZone = TimeZone.currentSystemDefault(), dayOffsetHours: Int = 0): LocalDate =
        of(now, zone, dayOffsetHours)

    /** 该日期所在 ISO 周（周一为第一天）。 */
    fun weekStart(date: LocalDate): LocalDate = date.plus(1 - date.dayOfWeek.isoDayNumber, DateTimeUnit.DAY)

    fun weekEnd(date: LocalDate): LocalDate = weekStart(date).plus(6, DateTimeUnit.DAY)

    /** ISO 周编号，返回 `2026-W47` 这种稳定字符串。 */
    fun isoWeekLabel(date: LocalDate): String {
        val thursday = weekStart(date).plus(3, DateTimeUnit.DAY)
        val jan4 = LocalDate(thursday.year, 1, 4)
        val firstThursday = jan4.plus(4 - jan4.dayOfWeek.isoDayNumber, DateTimeUnit.DAY)
        val week = (thursday.toEpochDays() - firstThursday.toEpochDays()) / 7 + 1
        return "${thursday.year}-W${week.toString().padStart(2, '0')}"
    }

    /** 最近 n 周（含本周）的周一起点，从旧到新。 */
    fun recentWeekStarts(today: LocalDate, weeks: Int = 6): List<LocalDate> {
        val thisWeek = weekStart(today)
        return (weeks - 1 downTo 0).map { thisWeek.plus(-7 * it, DateTimeUnit.DAY) }
    }
}
