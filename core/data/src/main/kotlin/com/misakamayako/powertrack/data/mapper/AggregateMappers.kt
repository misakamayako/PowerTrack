package com.misakamayako.powertrack.data.mapper

import com.misakamayako.powertrack.data.json.SnapshotJson
import com.misakamayako.powertrack.data.json.SpecJson
import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.RepSpec
import com.misakamayako.powertrack.model.SessionSet
import com.misakamayako.powertrack.model.SessionSetSegment
import com.misakamayako.powertrack.model.SetPrescription
import kotlinx.collections.immutable.toPersistentList

/** 计划侧 prescription 行（Routine.sq 的 projection）→ 模型。 */
fun com.misakamayako.powertrack.db.SelectPrescriptionsForRoutine.toModel(): SetPrescription = SetPrescription(
    id = id,
    slotId = slotId,
    orderIndex = orderIndex.toInt(),
    setType = setTypeOf(setType),
    isWarmup = isWarmup != 0L,
    loadSpec = SpecJson.decodeLoad(loadJson) ?: LoadSpec.FreeText(loadJson),
    repSpec = SpecJson.decodeReps(repJson) ?: RepSpec.Fixed(0),
    restSec = restSec?.toInt(),
    rpe = rpe,
    progression = progression,
)

/** 训练中的组行（Workout.sq 的 session 级 projection）→ 模型。 */
fun com.misakamayako.powertrack.db.SelectSetsForSession.toModel(
    segments: List<com.misakamayako.powertrack.db.SelectSegmentsForSession>,
): SessionSet = SessionSet(
    id = id,
    sessionExerciseId = sessionExerciseId,
    orderIndex = orderIndex.toInt(),
    setType = setTypeOf(setType),
    isWarmup = isWarmup != 0L,
    prescribed = SnapshotJson.decodePrescription(prescribedJson),
    restBeforeSec = restBeforeSec?.toInt(),
    rpe = rpe,
    notes = notes,
    segments = segments.map { it.toModel() }.toPersistentList(),
)

/** 训练中的组行（表类型本身）→ 模型。 */
fun com.misakamayako.powertrack.db.Session_set.toModel(
    segments: List<com.misakamayako.powertrack.db.Session_set_segment>,
): SessionSet = SessionSet(
    id = id,
    sessionExerciseId = session_exercise_id,
    orderIndex = order_index.toInt(),
    setType = setTypeOf(set_type),
    isWarmup = is_warmup != 0L,
    prescribed = SnapshotJson.decodePrescription(prescribed_json),
    restBeforeSec = rest_before_sec?.toInt(),
    rpe = rpe,
    notes = notes,
    segments = segments.map { it.toModel() }.toPersistentList(),
)

/** 训练中的 segment 行（session 级 projection）→ 模型。 */
fun com.misakamayako.powertrack.db.SelectSegmentsForSession.toModel(): SessionSetSegment = SessionSetSegment(
    id = id,
    sessionSetId = sessionSetId,
    orderIndex = orderIndex.toInt(),
    load = SpecJson.decodeLoad(loadJson) ?: LoadSpec.FreeText(loadJson),
    reps = SpecJson.decodeReps(repJson) ?: RepSpec.Fixed(0),
    isFailure = isFailure != 0L,
    partialReps = partialReps.toInt(),
    side = sideOf(side),
    tempo = tempo,
    holdMs = holdMs,
    offsetMs = offsetMs,
    resolvedLoadKg = resolvedLoadKg,
    resolvedReps = resolvedReps?.toInt(),
    e1rm = e1rm,
)
