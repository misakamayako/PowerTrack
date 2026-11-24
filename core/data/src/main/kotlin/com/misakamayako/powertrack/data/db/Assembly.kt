package com.misakamayako.powertrack.data.db

import com.misakamayako.powertrack.data.json.SnapshotJson
import com.misakamayako.powertrack.data.mapper.finishKindOf
import com.misakamayako.powertrack.data.mapper.toModel
import com.misakamayako.powertrack.db.PowerTrackDatabase
import com.misakamayako.powertrack.model.Routine
import com.misakamayako.powertrack.model.RoutineSlot
import com.misakamayako.powertrack.model.SessionExercise
import com.misakamayako.powertrack.model.SessionSet
import com.misakamayako.powertrack.model.WorkoutSession
import kotlinx.collections.immutable.toPersistentList
import kotlin.time.Instant

/**
 * 聚合装配：把一个聚合的多张表拼回领域模型。
 *
 * 只给 data 层内部用，**不做 withContext**（在事务里被调用时不能切线程）。
 */
internal fun PowerTrackDatabase.loadRoutine(id: String): Routine? {
    val row = routineQueries.selectById(id).executeAsOneOrNull() ?: return null
    val slotRows = routineQueries.selectSlots(id).executeAsList()
    val prescriptionRows = routineQueries.selectPrescriptionsForRoutine(id).executeAsList()
    val prescriptionsBySlot = prescriptionRows.groupBy { it.slotId }

    return Routine(
        id = row.id,
        name = row.name,
        notes = row.notes,
        tags = row.tags?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toPersistentList()
            ?: kotlinx.collections.immutable.persistentListOf(),
        archivedAt = row.archived_at?.let { Instant.fromEpochMilliseconds(it) },
        createdAt = Instant.fromEpochMilliseconds(row.created_at),
        updatedAt = Instant.fromEpochMilliseconds(row.updated_at),
        slots = slotRows.map { slot ->
            RoutineSlot(
                id = slot.id,
                routineId = slot.routineId,
                orderIndex = slot.orderIndex.toInt(),
                exerciseId = slot.exerciseId,
                supersetGroup = slot.supersetGroup,
                restSec = slot.restSec?.toInt(),
                notes = slot.notes,
                exerciseName = slot.exerciseName,
                prescriptions = (prescriptionsBySlot[slot.id] ?: emptyList())
                    .map { it.toModel() }
                    .toPersistentList(),
            )
        }.toPersistentList(),
    )
}

internal fun PowerTrackDatabase.loadSession(id: String): WorkoutSession? {
    val row = workoutQueries.selectById(id).executeAsOneOrNull() ?: return null
    val exerciseRows = workoutQueries.selectExercises(id).executeAsList()
    val setRows = workoutQueries.selectSetsForSession(id).executeAsList()
    val segmentRows = workoutQueries.selectSegmentsForSession(id).executeAsList()

    val setsByExercise = setRows.groupBy { it.sessionExerciseId }
    val segmentsBySet = segmentRows.groupBy { it.sessionSetId }

    return WorkoutSession(
        id = row.id,
        startedAt = Instant.fromEpochMilliseconds(row.started_at),
        endedAt = row.ended_at?.let { Instant.fromEpochMilliseconds(it) },
        sourceScheduledId = row.source_scheduled_id,
        routineSnapshot = SnapshotJson.decodeRoutine(row.routine_snapshot),
        bodyweightKg = row.bodyweight_kg,
        sessionRpe = row.session_rpe,
        notes = row.notes,
        finishKind = finishKindOf(row.finish_kind),
        exercises = exerciseRows.map { exerciseRow ->
            SessionExercise(
                id = exerciseRow.id,
                sessionId = exerciseRow.sessionId,
                exerciseId = exerciseRow.exerciseId,
                orderIndex = exerciseRow.orderIndex.toInt(),
                supersetGroup = exerciseRow.supersetGroup,
                notes = exerciseRow.notes,
                exerciseName = exerciseRow.exerciseName,
                sets = (setsByExercise[exerciseRow.id] ?: emptyList()).map { setRow ->
                    setRow.toModel(segmentsBySet[setRow.id] ?: emptyList())
                }.toPersistentList(),
            )
        }.toPersistentList(),
    )
}

internal fun PowerTrackDatabase.loadSet(id: String): SessionSet? {
    val setRow = workoutQueries.selectSetById(id).executeAsOneOrNull() ?: return null
    val segmentRows = workoutQueries.selectSegmentsForSet(id).executeAsList()
    return setRow.toModel(segmentRows)
}
