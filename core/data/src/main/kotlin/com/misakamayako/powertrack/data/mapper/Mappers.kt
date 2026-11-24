package com.misakamayako.powertrack.data.mapper

import com.misakamayako.powertrack.data.json.SpecJson
import com.misakamayako.powertrack.model.BodyMetric
import com.misakamayako.powertrack.model.Equipment
import com.misakamayako.powertrack.model.Exercise
import com.misakamayako.powertrack.model.Muscle
import com.misakamayako.powertrack.model.PersonalRecord
import com.misakamayako.powertrack.model.SessionSetSegment
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * DB row → 领域模型。
 *
 * §4.8：`List → PersistentList` 的转换**只允许出现在 data 层的 mapper 里**。
 */

fun com.misakamayako.powertrack.db.Muscle.toModel(): Muscle = Muscle(
    id = id,
    parentId = parent_id,
    name = name,
    nameEn = name_en,
    region = region,
    depth = depth.toInt(),
    orderIndex = order_index.toInt(),
)

fun com.misakamayako.powertrack.db.Exercise.toModel(): Exercise = Exercise(
    id = id,
    name = name,
    nameEn = name_en,
    equipment = equipmentOf(equipment),
    mechanic = mechanicOf(mechanic),
    forceType = forceTypeOf(force_type),
    laterality = lateralityOf(laterality),
    defaultUnit = weightUnitOf(default_unit),
    instructions = instructions,
    mediaRef = media_ref,
    isCustom = is_custom != 0L,
    createdAt = Instant.fromEpochMilliseconds(created_at),
    videoAsset = video_asset,
    photoAsset = photo_asset,
)

fun com.misakamayako.powertrack.db.Session_set_segment.toModel(): SessionSetSegment = SessionSetSegment(
    id = id,
    sessionSetId = session_set_id,
    orderIndex = order_index.toInt(),
    load = SpecJson.decodeLoad(load_json) ?: com.misakamayako.powertrack.model.LoadSpec.FreeText(load_json),
    reps = SpecJson.decodeReps(rep_json) ?: com.misakamayako.powertrack.model.RepSpec.Fixed(0),
    isFailure = is_failure != 0L,
    partialReps = partial_reps.toInt(),
    side = sideOf(side),
    tempo = tempo,
    holdMs = hold_ms,
    offsetMs = offset_ms,
    resolvedLoadKg = resolved_load_kg,
    resolvedReps = resolved_reps?.toInt(),
    e1rm = e1rm,
)

fun com.misakamayako.powertrack.db.Personal_record.toModel(exerciseName: String?): PersonalRecord = PersonalRecord(
    id = id,
    exerciseId = exercise_id,
    kind = prKindOf(kind),
    value = value_,
    unit = unit,
    sessionSetId = session_set_id,
    achievedAt = Instant.fromEpochMilliseconds(achieved_at),
    exerciseName = exerciseName,
)

fun com.misakamayako.powertrack.db.Body_metric.toModel(): BodyMetric = BodyMetric(
    id = id,
    localDate = LocalDate.parse(local_date),
    weightKg = weight_kg,
    bodyFatPct = body_fat_pct,
    note = note,
)

/** 排期行 → 模型（projection 来自 Schedule.sq 的 SELECT）。 */
fun com.misakamayako.powertrack.db.SelectNextPlanned.toModel(): com.misakamayako.powertrack.model.ScheduledSession =
    toScheduledModel(
        id = id,
        localDate = localDate,
        routineId = routineId,
        routineSnapshot = routineSnapshot,
        status = status,
        notes = notes,
        createdAt = createdAt,
        routineName = routineName,
        completedSessionId = completedSessionId.takeIf { it.isNotBlank() },
    )

fun com.misakamayako.powertrack.db.SelectBetween.toModel(): com.misakamayako.powertrack.model.ScheduledSession =
    toScheduledModel(
        id = id,
        localDate = localDate,
        routineId = routineId,
        routineSnapshot = routineSnapshot,
        status = status,
        notes = notes,
        createdAt = createdAt,
        routineName = routineName,
        completedSessionId = completedSessionId.takeIf { it.isNotBlank() },
    )

fun com.misakamayako.powertrack.db.SelectUpcoming.toModel(): com.misakamayako.powertrack.model.ScheduledSession =
    toScheduledModel(
        id = id,
        localDate = localDate,
        routineId = routineId,
        routineSnapshot = routineSnapshot,
        status = status,
        notes = notes,
        createdAt = createdAt,
        routineName = routineName,
        completedSessionId = completedSessionId.takeIf { it.isNotBlank() },
    )

fun com.misakamayako.powertrack.db.SelectById.toModel(): com.misakamayako.powertrack.model.ScheduledSession =
    toScheduledModel(
        id = id,
        localDate = localDate,
        routineId = routineId,
        routineSnapshot = routineSnapshot,
        status = status,
        notes = notes,
        createdAt = createdAt,
        routineName = routineName,
        completedSessionId = completedSessionId.takeIf { it.isNotBlank() },
    )

fun com.misakamayako.powertrack.db.SelectRecent.toModel(): com.misakamayako.powertrack.model.ScheduledSession =
    toScheduledModel(
        id = id,
        localDate = localDate,
        routineId = routineId,
        routineSnapshot = routineSnapshot,
        status = status,
        notes = notes,
        createdAt = createdAt,
        routineName = routineName,
        completedSessionId = completedSessionId.takeIf { it.isNotBlank() },
    )

private fun toScheduledModel(
    id: String,
    localDate: String,
    routineId: String?,
    routineSnapshot: String?,
    status: String,
    notes: String?,
    createdAt: Long,
    routineName: String?,
    completedSessionId: String?,
): com.misakamayako.powertrack.model.ScheduledSession =
    com.misakamayako.powertrack.model.ScheduledSession(
        id = id,
        localDate = LocalDate.parse(localDate),
        routineId = routineId,
        routineSnapshot = com.misakamayako.powertrack.data.json.SnapshotJson.decodeRoutine(routineSnapshot),
        status = scheduledStatusOf(status),
        notes = notes,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        // 这里拿到的是已由各 projection 规范化过的可空值：空串统一收敛成 null
        completedSessionId = completedSessionId?.takeIf { it.isNotBlank() },
        routineName = routineName,
    )

/** 动作库筛选条目的轻型投影。 */
data class ExerciseRowView(
    val id: String,
    val name: String,
    val equipment: Equipment,
)

fun com.misakamayako.powertrack.db.SelectAll.toModel(): com.misakamayako.powertrack.model.ScheduledSession =
    toScheduledModel(
        id = id,
        localDate = localDate,
        routineId = routineId,
        routineSnapshot = routineSnapshot,
        status = status,
        notes = notes,
        createdAt = createdAt,
        routineName = routineName,
        completedSessionId = completedSessionId.ifBlank { null },
    )

fun com.misakamayako.powertrack.db.SelectAllPrs.toModel(): PersonalRecord = PersonalRecord(
    id = id,
    exerciseId = exercise_id,
    kind = prKindOf(kind),
    value = value_,
    unit = unit,
    sessionSetId = session_set_id,
    achievedAt = Instant.fromEpochMilliseconds(achieved_at),
    exerciseName = exerciseName,
)
