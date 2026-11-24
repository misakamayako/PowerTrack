package com.misakamayako.powertrack.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 器械类型（`exercise.equipment`）。存的是稳定字符串，不用 ordinal。
 */
@Serializable
enum class Equipment {
    @SerialName("barbell") BARBELL,
    @SerialName("dumbbell") DUMBBELL,
    @SerialName("machine") MACHINE,
    @SerialName("cable") CABLE,
    @SerialName("bodyweight") BODYWEIGHT,
    @SerialName("band") BAND,
    @SerialName("kettlebell") KETTLEBELL,
    @SerialName("other") OTHER,
}

@Serializable
enum class Mechanic {
    @SerialName("compound") COMPOUND,
    @SerialName("isolation") ISOLATION,
}

@Serializable
enum class ForceType {
    @SerialName("push") PUSH,
    @SerialName("pull") PULL,
    @SerialName("static") STATIC,
}

@Serializable
enum class Laterality {
    @SerialName("bilateral") BILATERAL,
    @SerialName("unilateral") UNILATERAL,
}

@Serializable
enum class WeightUnit {
    @SerialName("kg") KG,
    @SerialName("lb") LB,
}

/** 单侧动作的侧别（§2.2），M3 只实现 BOTH，但字段先留。 */
@Serializable
enum class Side {
    @SerialName("both") BOTH,
    @SerialName("left") LEFT,
    @SerialName("right") RIGHT,
}

/** 动作与肌肉关联的角色（§2.4），只用于 UI 分组展示。 */
@Serializable
enum class MuscleRole {
    @SerialName("prime") PRIME,
    @SerialName("synergist") SYNERGIST,
    @SerialName("stabilizer") STABILIZER,
    @SerialName("antagonist") ANTAGONIST,
}

/**
 * 组的类型（§2.2）。
 *
 * **禁令：只做渲染提示和默认可选项，绝不参与数据推断。** 所有业务数据都来自 segments。
 */
@Serializable
enum class SetType {
    @SerialName("normal") NORMAL,
    @SerialName("warmup") WARMUP,
    @SerialName("work") WORK,
    @SerialName("drop") DROP,
    @SerialName("pyramid") PYRAMID,
    @SerialName("amrap") AMRAP,
    @SerialName("failure") FAILURE,
    @SerialName("rest_pause") REST_PAUSE,
    @SerialName("myo_reps") MYO_REPS,
    @SerialName("backoff") BACKOFF,
    @SerialName("technique") TECHNIQUE,
}

/** 排期状态（§2.6）。不做 PARTIAL，完成度用比值表示。 */
@Serializable
enum class ScheduledStatus {
    @SerialName("planned") PLANNED,
    @SerialName("skipped") SKIPPED,
    @SerialName("started") STARTED,
    @SerialName("completed") COMPLETED,
}

/** 实际训练状态（§2.6）。不做 PAUSED。 */
@Serializable
enum class SessionFinishKind {
    @SerialName("active") ACTIVE,
    @SerialName("finished") FINISHED,
    @SerialName("discarded") DISCARDED,
}

/** PR 类型（§5.4）。 */
@Serializable
enum class PrKind {
    @SerialName("max_weight") MAX_WEIGHT,
    @SerialName("max_reps_at_weight") MAX_REPS_AT_WEIGHT,
    @SerialName("max_e1rm") MAX_E1RM,
    @SerialName("max_session_tonnage") MAX_SESSION_TONNAGE,
}
