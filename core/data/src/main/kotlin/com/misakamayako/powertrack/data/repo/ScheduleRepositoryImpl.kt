package com.misakamayako.powertrack.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.misakamayako.powertrack.db.PowerTrackDatabase
import com.misakamayako.powertrack.data.json.SnapshotJson
import com.misakamayako.powertrack.data.mapper.toModel
import com.misakamayako.powertrack.data.mapper.wire
import com.misakamayako.powertrack.domain.ScheduleRepository
import com.misakamayako.powertrack.model.Ids
import com.misakamayako.powertrack.model.Routine
import com.misakamayako.powertrack.model.ScheduledSession
import com.misakamayako.powertrack.model.ScheduledStatus
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

class ScheduleRepositoryImpl(
    private val db: PowerTrackDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = Clock.System,
) : ScheduleRepository {

    private val queries = db.scheduleQueries

    override fun observeBetween(from: LocalDate, to: LocalDate): Flow<PersistentList<ScheduledSession>> =
        queries.selectBetween(from.toString(), to.toString()).asFlow().mapToList(io)
            .map { rows -> rows.map { it.toModel() }.toPersistentList() }

    override fun observeNextPlanned(from: LocalDate): Flow<ScheduledSession?> =
        queries.selectNextPlanned(from.toString()).asFlow().mapToOneOrNull(io)
            .map { row -> row?.toModel() }

    override fun observeAll(): Flow<PersistentList<ScheduledSession>> =
        queries.selectAll().asFlow().mapToList(io)
            .map { rows -> rows.map { it.toModel() }.toPersistentList() }

    override suspend fun byId(id: String): ScheduledSession? = withContext(io) {
        queries.selectById(id).executeAsOneOrNull()?.toModel()
    }

    override suspend fun between(from: LocalDate, to: LocalDate): PersistentList<ScheduledSession> = withContext(io) {
        queries.selectBetween(from.toString(), to.toString()).executeAsList().map { it.toModel() }.toPersistentList()
    }

    override suspend fun recent(limit: Int): PersistentList<ScheduledSession> = withContext(io) {
        queries.selectRecent(limit.toLong()).executeAsList().map { it.toModel() }.toPersistentList()
    }

    override suspend fun schedule(routine: Routine, date: LocalDate, notes: String?): String = withContext(io) {
        val id = Ids.new()
        queries.insert(
            id = id,
            local_date = date.toString(),
            routine_id = routine.id,
            routine_snapshot = SnapshotJson.encodeRoutine(routine),
            status = ScheduledStatus.PLANNED.wire(),
            notes = notes,
            created_at = clock.now().toEpochMilliseconds(),
        )
        id
    }

    override suspend fun setStatus(id: String, status: ScheduledStatus): Unit = withContext(io) {
        queries.updateStatus(status.wire(), id)
    }

    override suspend fun markStarted(id: String): Unit = withContext(io) {
        queries.updateStatus(ScheduledStatus.STARTED.wire(), id)
    }

    /** 与训练结束在同一事务里回填 COMPLETED（铁律 3）。 */
    override suspend fun markCompletedSession(scheduledId: String, sessionId: String): Unit = withContext(io) {
        db.transaction {
            db.workoutQueries.updateSourceScheduled(sessionId, scheduledId)
            queries.updateStatus(ScheduledStatus.COMPLETED.wire(), scheduledId)
        }
    }

    override suspend fun skipOverdue(today: LocalDate): Int = withContext(io) {
        queries.skipOverdue(today.toString()).value.toInt()
    }

    override suspend fun delete(id: String): Unit = withContext(io) {
        queries.deleteById(id)
    }
}
