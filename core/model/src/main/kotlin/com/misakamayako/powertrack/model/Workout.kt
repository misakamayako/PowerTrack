package com.misakamayako.powertrack.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * 实际训练（§2.1）。**快照计划，不引用计划**（铁律 2）：
 * `routineSnapshot` 存的是开始训练那一刻的计划副本，模板后续改动不会改写历史。
 */
data class WorkoutSession(
    val id: String,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    /** 软引用：可空。没计划临时练时就是 null（铁律 1）。 */
    val sourceScheduledId: String? = null,
    val routineSnapshot: Routine? = null,
    val bodyweightKg: Double? = null,
    val sessionRpe: Double? = null,
    val notes: String? = null,
    val finishKind: SessionFinishKind = SessionFinishKind.ACTIVE,
    val exercises: PersistentList<SessionExercise> = persistentListOf(),
) {
    val isActive: Boolean get() = finishKind == SessionFinishKind.ACTIVE
    val isFinished: Boolean get() = finishKind == SessionFinishKind.FINISHED

    val duration: Duration?
        get() = endedAt?.let { it - startedAt }

    /** 本次完成的组数（含热身）。 */
    val completedSetCount: Int get() = exercises.sumOf { it.sets.size }

    /** 计划组数来自快照，单次依从率的分母（§5.3）。 */
    val plannedSetCount: Int get() = routineSnapshot?.plannedSetCount ?: 0
}

/** 训练中的动作实例。 */
data class SessionExercise(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val supersetGroup: String? = null,
    val notes: String? = null,
    /** data 层 JOIN 出来的展示字段。 */
    val exerciseName: String? = null,
    val sets: PersistentList<SessionSet> = persistentListOf(),
)

/**
 * 一组训练（§2.2）。**不是 (weight, reps)**，而是一小段时间轴：
 * segments 列表才是数据来源，`setType` 只是渲染标签。
 */
data class SessionSet(
    val id: String,
    val sessionExerciseId: String,
    val orderIndex: Int,
    val setType: SetType = SetType.NORMAL,
    val isWarmup: Boolean = false,
    /** 从计划克隆来的目标（快照），可为空。 */
    val prescribed: SetPrescription? = null,
    val restBeforeSec: Int? = null,
    val rpe: Double? = null,
    val notes: String? = null,
    val startedAt: Instant? = null,
    val segments: PersistentList<SessionSetSegment> = persistentListOf(),
) {
    /** 最大的那段负荷，仅用于展示；**不要**用它和总次数搭配算 e1RM。 */
    val topSegment: SessionSetSegment? get() = segments.maxByOrNull { it.orderIndex }
}

/**
 * 一组里的一个片段（§2.2）。递减组、中途减重、单侧不齐都靠多段表达。
 */
data class SessionSetSegment(
    val id: String,
    val sessionSetId: String,
    val orderIndex: Int,
    val load: LoadSpec,
    val reps: RepSpec,
    val isFailure: Boolean = false,
    val partialReps: Int = 0,
    val side: Side = Side.BOTH,
    val tempo: String? = null,
    val holdMs: Long? = null,
    /** 相对本组开始的毫秒偏移，用于还原节奏 / 算 TUT（§2.2）。 */
    val offsetMs: Long = 0,
    /** 冗余列（§3.4），由 core:domain 的纯函数算出，写库时一并写入。 */
    val resolvedLoadKg: Double? = null,
    val resolvedReps: Int? = null,
    val e1rm: Double? = null,
) {
    val offset: Duration get() = offsetMs.milliseconds
}
