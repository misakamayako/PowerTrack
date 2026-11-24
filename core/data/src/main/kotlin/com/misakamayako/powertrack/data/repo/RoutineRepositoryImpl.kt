package com.misakamayako.powertrack.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.misakamayako.powertrack.db.PowerTrackDatabase
import com.misakamayako.powertrack.data.db.loadRoutine
import com.misakamayako.powertrack.data.json.SpecJson
import com.misakamayako.powertrack.data.mapper.wire
import com.misakamayako.powertrack.domain.RoutineRepository
import com.misakamayako.powertrack.model.Routine
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

class RoutineRepositoryImpl(
    private val db: PowerTrackDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = Clock.System,
) : RoutineRepository {

    private val queries = db.routineQueries

    override fun observeRoutines(includeArchived: Boolean): Flow<PersistentList<Routine>> {
        val query = if (includeArchived) queries.selectAll() else queries.selectActive()
        return query.asFlow().mapToList(io).map { rows ->
            rows.mapNotNull { db.loadRoutine(it.id) }.toPersistentList()
        }
    }

    override suspend fun routines(includeArchived: Boolean): PersistentList<Routine> = withContext(io) {
        val rows = if (includeArchived) queries.selectAll().executeAsList() else queries.selectActive().executeAsList()
        rows.mapNotNull { db.loadRoutine(it.id) }.toPersistentList()
    }

    override suspend fun routine(id: String): Routine? = withContext(io) { db.loadRoutine(id) }

    override suspend fun create(routine: Routine): String = withContext(io) {
        val now = clock.now()
        db.transaction {
            queries.insert(
                id = routine.id,
                name = routine.name,
                notes = routine.notes,
                tags = routine.tags.joinToString(",").ifBlank { null },
                archived_at = routine.archivedAt?.toEpochMilliseconds(),
                created_at = now.toEpochMilliseconds(),
                updated_at = now.toEpochMilliseconds(),
            )
            writeChildren(routine, now)
        }
        routine.id
    }

    /**
     * 整份保存：先 UPDATE 主行（§3.7 禁止对带子表的行用 REPLACE），
     * 再在一个事务里重写 slot / prescription，保证 order_index 重写原子。
     */
    override suspend fun save(routine: Routine): Unit = withContext(io) {
        val now = clock.now()
        db.transaction {
            queries.update(
                name = routine.name,
                notes = routine.notes,
                tags = routine.tags.joinToString(",").ifBlank { null },
                archived_at = routine.archivedAt?.toEpochMilliseconds(),
                updated_at = now.toEpochMilliseconds(),
                id = routine.id,
            )
            queries.deleteSlotsForRoutine(routine.id)
            writeChildren(routine, now)
        }
    }

    private fun writeChildren(routine: Routine, now: Instant) {
        routine.slots.forEach { slot ->
            queries.insertSlot(
                id = slot.id,
                routine_id = routine.id,
                order_index = slot.orderIndex.toLong(),
                exercise_id = slot.exerciseId,
                superset_group = slot.supersetGroup,
                rest_sec = slot.restSec?.toLong(),
                notes = slot.notes,
            )
            slot.prescriptions.forEach { prescription ->
                queries.insertPrescription(
                    id = prescription.id,
                    slot_id = slot.id,
                    order_index = prescription.orderIndex.toLong(),
                    set_type = prescription.setType.wire(),
                    is_warmup = if (prescription.isWarmup) 1L else 0L,
                    load_json = SpecJson.encodeLoad(prescription.loadSpec),
                    rep_json = SpecJson.encodeReps(prescription.repSpec),
                    rest_sec = prescription.restSec?.toLong(),
                    rpe = prescription.rpe,
                    progression = prescription.progression,
                )
            }
        }
    }

    override suspend fun rename(id: String, name: String): Unit = withContext(io) {
        queries.updateName(name = name, updated_at = clock.now().toEpochMilliseconds(), id = id)
    }

    override suspend fun setArchived(id: String, archived: Boolean): Unit = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        queries.updateArchived(
            archived_at = if (archived) now else null,
            updated_at = now,
            id = id,
        )
    }

    override suspend fun delete(id: String): Unit = withContext(io) {
        queries.deleteById(id)
    }

    /** 空计划占位，避免 UI 判空到处写。 */
    fun emptyRoutine(): Routine = Routine(
        id = "",
        name = "",
        slots = persistentListOf(),
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
    )
}
