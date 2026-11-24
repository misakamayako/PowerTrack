package com.misakamayako.powertrack.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.misakamayako.powertrack.data.db.loadSession
import com.misakamayako.powertrack.data.mapper.muscleRoleOf
import com.misakamayako.powertrack.data.mapper.toModel
import com.misakamayako.powertrack.db.PowerTrackDatabase
import com.misakamayako.powertrack.domain.E1rmPoint
import com.misakamayako.powertrack.domain.MuscleVolume
import com.misakamayako.powertrack.domain.EffectiveVolume
import com.misakamayako.powertrack.domain.StatsRepository
import com.misakamayako.powertrack.domain.SetMetricsCalculator
import com.misakamayako.powertrack.domain.TonnagePoint
import com.misakamayako.powertrack.domain.WeekTonnage
import com.misakamayako.powertrack.domain.TrainingDate
import com.misakamayako.powertrack.model.ExerciseMuscle
import com.misakamayako.powertrack.model.PersonalRecord
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

class StatsRepositoryImpl(
    private val db: PowerTrackDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : StatsRepository {

    private val workoutQueries = db.workoutQueries
    private val metricsQueries = db.metricsQueries
    private val exerciseQueries = db.exerciseQueries

    override fun observePrs(): Flow<PersistentList<PersonalRecord>> =
        metricsQueries.selectAllPrs().asFlow().mapToList(io)
            .map { rows -> rows.map { it.toModel() }.toPersistentList() }

    override suspend fun prs(): PersistentList<PersonalRecord> = withContext(io) {
        metricsQueries.selectAllPrs().executeAsList().map { it.toModel() }.toPersistentList()
    }

    override suspend fun e1rmSeries(exerciseId: String, limit: Int): List<E1rmPoint> = withContext(io) {
        workoutQueries.selectE1rmSeries(exerciseId, limit.toLong()).executeAsList().mapNotNull { row ->
            val endedAt = row.endedAt ?: return@mapNotNull null
            val e1rm = row.e1rm ?: return@mapNotNull null
            E1rmPoint(
                date = TrainingDate.of(Instant.fromEpochMilliseconds(endedAt), timeZone),
                e1rm = e1rm,
                topLoadKg = row.topLoadKg,
                reps = row.reps?.toInt(),
            )
        }
    }

    override suspend fun exerciseTonnageSeries(exerciseId: String, limit: Int): List<TonnagePoint> =
        withContext(io) {
            workoutQueries.selectTonnageSeries(exerciseId, limit.toLong()).executeAsList().mapNotNull { row ->
                val endedAt = row.endedAt ?: return@mapNotNull null
                val tonnage = row.tonnage ?: return@mapNotNull null
                TonnagePoint(
                    date = TrainingDate.of(Instant.fromEpochMilliseconds(endedAt), timeZone),
                    tonnage = tonnage,
                    sets = row.sets.toInt(),
                )
            }
        }

    override suspend fun muscleVolumes(since: LocalDate): List<MuscleVolume> = withContext(io) {
        val sessionRows = workoutQueries.selectAllFinished().executeAsList()
        val sessions = sessionRows.mapNotNull { db.loadSession(it.id) }
            .filter { session ->
                val date = TrainingDate.of(session.endedAt ?: session.startedAt, timeZone)
                date >= since
            }
        val links = exerciseQueries.selectLinks().executeAsList().map {
            com.misakamayako.powertrack.model.ExerciseMuscle(
                exerciseId = it.exercise_id,
                muscleId = it.muscle_id,
                role = com.misakamayako.powertrack.data.mapper.muscleRoleOf(it.role),
                contribution = it.contribution,
            )
        }
        EffectiveVolume.compute(sessions, links, bodyweightKg = null)
    }

    override suspend fun weeklyTonnage(weeks: Int, today: LocalDate): List<WeekTonnage> = withContext(io) {
        val sessionRows = workoutQueries.selectAllFinished().executeAsList()
        val sessions = sessionRows.mapNotNull { db.loadSession(it.id) }
        val weekStarts = TrainingDate.recentWeekStarts(today, weeks)
        weekStarts.map { weekStart ->
            val weekEnd = TrainingDate.weekEnd(weekStart)
            val inWeek = sessions.filter { session ->
                val date = TrainingDate.of(session.endedAt ?: session.startedAt, timeZone)
                date >= weekStart && date <= weekEnd
            }
            var tonnage = 0.0
            var sets = 0
            inWeek.forEach { session ->
                session.exercises.forEach { exercise ->
                    exercise.sets.forEach { set ->
                        val metrics = com.misakamayako.powertrack.domain.SetMetricsCalculator
                            .compute(set.segments, session.bodyweightKg)
                        metrics.tonnage?.let { tonnage += it; sets += 1 }
                    }
                }
            }
            WeekTonnage(weekStart = weekStart, tonnage = tonnage, sessions = inWeek.size, sets = sets)
        }
    }

    override suspend fun totalSessions(): Long = withContext(io) {
        workoutQueries.countFinished().executeAsOne()
    }
}
