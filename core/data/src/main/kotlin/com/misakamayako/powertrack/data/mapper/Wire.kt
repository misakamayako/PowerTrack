package com.misakamayako.powertrack.data.mapper

import com.misakamayako.powertrack.model.Equipment
import com.misakamayako.powertrack.model.ForceType
import com.misakamayako.powertrack.model.Laterality
import com.misakamayako.powertrack.model.Mechanic
import com.misakamayako.powertrack.model.MuscleRole
import com.misakamayako.powertrack.model.PrKind
import com.misakamayako.powertrack.model.ScheduledStatus
import com.misakamayako.powertrack.model.SessionFinishKind
import com.misakamayako.powertrack.model.SetType
import com.misakamayako.powertrack.model.Side
import com.misakamayako.powertrack.model.WeightUnit

/**
 * 枚举 ↔ 数据库字符串（§3.3）。
 *
 * 存储用**稳定小写字符串**，与 `@SerialName` 一致；解析时对未知值给安全默认，不抛异常。
 */

fun Equipment.wire(): String = when (this) {
    Equipment.BARBELL -> "barbell"
    Equipment.DUMBBELL -> "dumbbell"
    Equipment.MACHINE -> "machine"
    Equipment.CABLE -> "cable"
    Equipment.BODYWEIGHT -> "bodyweight"
    Equipment.BAND -> "band"
    Equipment.KETTLEBELL -> "kettlebell"
    Equipment.OTHER -> "other"
}

fun equipmentOf(raw: String?): Equipment = when (raw?.lowercase()) {
    "barbell" -> Equipment.BARBELL
    "dumbbell" -> Equipment.DUMBBELL
    "machine" -> Equipment.MACHINE
    "cable" -> Equipment.CABLE
    "bodyweight" -> Equipment.BODYWEIGHT
    "band" -> Equipment.BAND
    "kettlebell" -> Equipment.KETTLEBELL
    else -> Equipment.OTHER
}

fun Mechanic.wire(): String = when (this) {
    Mechanic.COMPOUND -> "compound"
    Mechanic.ISOLATION -> "isolation"
}

fun mechanicOf(raw: String?): Mechanic = when (raw?.lowercase()) {
    "isolation" -> Mechanic.ISOLATION
    else -> Mechanic.COMPOUND
}

fun ForceType.wire(): String = when (this) {
    ForceType.PUSH -> "push"
    ForceType.PULL -> "pull"
    ForceType.STATIC -> "static"
}

fun forceTypeOf(raw: String?): ForceType? = when (raw?.lowercase()) {
    "push" -> ForceType.PUSH
    "pull" -> ForceType.PULL
    "static" -> ForceType.STATIC
    else -> null
}

fun Laterality.wire(): String = when (this) {
    Laterality.BILATERAL -> "bilateral"
    Laterality.UNILATERAL -> "unilateral"
}

fun lateralityOf(raw: String?): Laterality = when (raw?.lowercase()) {
    "unilateral" -> Laterality.UNILATERAL
    else -> Laterality.BILATERAL
}

fun WeightUnit.wire(): String = when (this) {
    WeightUnit.KG -> "kg"
    WeightUnit.LB -> "lb"
}

fun weightUnitOf(raw: String?): WeightUnit = when (raw?.uppercase()) {
    "LB" -> WeightUnit.LB
    else -> WeightUnit.KG
}

fun SetType.wire(): String = when (this) {
    SetType.NORMAL -> "normal"
    SetType.WARMUP -> "warmup"
    SetType.WORK -> "work"
    SetType.DROP -> "drop"
    SetType.PYRAMID -> "pyramid"
    SetType.AMRAP -> "amrap"
    SetType.FAILURE -> "failure"
    SetType.REST_PAUSE -> "rest_pause"
    SetType.MYO_REPS -> "myo_reps"
    SetType.BACKOFF -> "backoff"
    SetType.TECHNIQUE -> "technique"
}

fun setTypeOf(raw: String?): SetType = when (raw?.lowercase()) {
    "warmup" -> SetType.WARMUP
    "work" -> SetType.WORK
    "drop" -> SetType.DROP
    "pyramid" -> SetType.PYRAMID
    "amrap" -> SetType.AMRAP
    "failure" -> SetType.FAILURE
    "rest_pause" -> SetType.REST_PAUSE
    "myo_reps" -> SetType.MYO_REPS
    "backoff" -> SetType.BACKOFF
    "technique" -> SetType.TECHNIQUE
    else -> SetType.NORMAL
}

fun ScheduledStatus.wire(): String = when (this) {
    ScheduledStatus.PLANNED -> "PLANNED"
    ScheduledStatus.SKIPPED -> "SKIPPED"
    ScheduledStatus.STARTED -> "STARTED"
    ScheduledStatus.COMPLETED -> "COMPLETED"
}

fun scheduledStatusOf(raw: String?): ScheduledStatus = when (raw?.uppercase()) {
    "SKIPPED" -> ScheduledStatus.SKIPPED
    "STARTED" -> ScheduledStatus.STARTED
    "COMPLETED" -> ScheduledStatus.COMPLETED
    else -> ScheduledStatus.PLANNED
}

fun SessionFinishKind.wire(): String = when (this) {
    SessionFinishKind.ACTIVE -> "ACTIVE"
    SessionFinishKind.FINISHED -> "FINISHED"
    SessionFinishKind.DISCARDED -> "DISCARDED"
}

fun finishKindOf(raw: String?): SessionFinishKind = when (raw?.uppercase()) {
    "FINISHED" -> SessionFinishKind.FINISHED
    "DISCARDED" -> SessionFinishKind.DISCARDED
    else -> SessionFinishKind.ACTIVE
}

fun MuscleRole.wire(): String = when (this) {
    MuscleRole.PRIME -> "prime"
    MuscleRole.SYNERGIST -> "synergist"
    MuscleRole.STABILIZER -> "stabilizer"
    MuscleRole.ANTAGONIST -> "antagonist"
}

fun muscleRoleOf(raw: String?): MuscleRole = when (raw?.lowercase()) {
    "prime" -> MuscleRole.PRIME
    "synergist" -> MuscleRole.SYNERGIST
    "stabilizer" -> MuscleRole.STABILIZER
    "antagonist" -> MuscleRole.ANTAGONIST
    else -> MuscleRole.SYNERGIST
}

fun Side.wire(): String = when (this) {
    Side.BOTH -> "BOTH"
    Side.LEFT -> "LEFT"
    Side.RIGHT -> "RIGHT"
}

fun sideOf(raw: String?): Side = when (raw?.uppercase()) {
    "LEFT" -> Side.LEFT
    "RIGHT" -> Side.RIGHT
    else -> Side.BOTH
}

fun PrKind.wire(): String = when (this) {
    PrKind.MAX_WEIGHT -> "MAX_WEIGHT"
    PrKind.MAX_REPS_AT_WEIGHT -> "MAX_REPS_AT_WEIGHT"
    PrKind.MAX_E1RM -> "MAX_E1RM"
    PrKind.MAX_SESSION_TONNAGE -> "MAX_SESSION_TONNAGE"
}

fun prKindOf(raw: String?): PrKind = when (raw?.uppercase()) {
    "MAX_REPS_AT_WEIGHT" -> PrKind.MAX_REPS_AT_WEIGHT
    "MAX_E1RM" -> PrKind.MAX_E1RM
    "MAX_SESSION_TONNAGE" -> PrKind.MAX_SESSION_TONNAGE
    else -> PrKind.MAX_WEIGHT
}
