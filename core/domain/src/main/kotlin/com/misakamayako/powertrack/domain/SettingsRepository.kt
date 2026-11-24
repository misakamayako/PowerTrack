package com.misakamayako.powertrack.domain

import com.misakamayako.powertrack.model.AppSettings
import com.misakamayako.powertrack.model.BodyMetric
import com.misakamayako.powertrack.model.PersonalRecord
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * 设置（§3.5 / §4.4）。
 */
interface SettingsRepository {
    fun observe(): Flow<AppSettings>

    suspend fun get(): AppSettings

    suspend fun update(settings: AppSettings)

    suspend fun setDayOffsetHours(hours: Int)

    suspend fun setDefaultRestSec(seconds: Int)

    suspend fun setBodyweight(kg: Double?)

    fun observeBodyMetrics(): Flow<PersistentList<BodyMetric>>

    suspend fun addBodyMetric(metric: BodyMetric)
}

/**
 * 统计读取（§5.5 / M5）。
 */
interface StatsRepository {
    fun observePrs(): Flow<PersistentList<PersonalRecord>>

    suspend fun prs(): PersistentList<PersonalRecord>

    suspend fun e1rmSeries(exerciseId: String, limit: Int = 120): List<E1rmPoint>

    suspend fun exerciseTonnageSeries(exerciseId: String, limit: Int = 120): List<TonnagePoint>

    suspend fun muscleVolumes(since: LocalDate): List<MuscleVolume>

    suspend fun weeklyTonnage(weeks: Int, today: LocalDate): List<WeekTonnage>

    suspend fun totalSessions(): Long
}

data class E1rmPoint(val date: LocalDate, val e1rm: Double, val topLoadKg: Double?, val reps: Int?)

data class TonnagePoint(val date: LocalDate, val tonnage: Double, val sets: Int)

data class WeekTonnage(val weekStart: LocalDate, val tonnage: Double, val sessions: Int, val sets: Int)

/**
 * JSON 导出 / 导入（§0 同步 → 只做本地 JSON 导出；M5）。
 */
interface ExportRepository {
    suspend fun exportJson(): String

    suspend fun importJson(json: String): ImportResult
}

data class ImportResult(val exercises: Int, val routines: Int, val sessions: Int, val warnings: List<String>)
