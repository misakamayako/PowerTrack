package com.misakamayako.powertrack.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Instant

/**
 * 计划模板（§2.1）：可复用的训练日模板，如「推 A」「腿 B」。
 */
data class Routine(
    val id: String,
    val name: String,
    val notes: String? = null,
    val tags: PersistentList<String> = persistentListOf(),
    val archivedAt: Instant? = null,
    val createdAt: Instant = Instant.fromEpochMilliseconds(0),
    val updatedAt: Instant = Instant.fromEpochMilliseconds(0),
    val slots: PersistentList<RoutineSlot> = persistentListOf(),
) {
    val isArchived: Boolean get() = archivedAt != null

    /** 计划总组数，用于单次依从率的分母（§5.3）。 */
    val plannedSetCount: Int get() = slots.sumOf { it.prescriptions.size }
}

/**
 * 计划里的一个动作槽位（§2.5）。`orderIndex` 有序，同 `supersetGroup` 表示超级组。
 */
data class RoutineSlot(
    val id: String,
    val routineId: String,
    val orderIndex: Int,
    val exerciseId: String,
    val supersetGroup: String? = null,
    val restSec: Int? = null,
    val notes: String? = null,
    /** 由 data 层 JOIN 出来的展示字段，不单独存储。 */
    val exerciseName: String? = null,
    val prescriptions: PersistentList<SetPrescription> = persistentListOf(),
)

/**
 * 计划侧的一组目标（§2.5）。**不需要知道具体重量**，常用 `PercentOf1RM` / `RpeOnly`。
 */
data class SetPrescription(
    val id: String,
    val slotId: String,
    val orderIndex: Int,
    val setType: SetType = SetType.NORMAL,
    val isWarmup: Boolean = false,
    val loadSpec: LoadSpec = LoadSpec.RpeOnly(8.0),
    val repSpec: RepSpec = RepSpec.Fixed(8),
    val restSec: Int? = null,
    val rpe: Double? = null,
    val progression: String? = null,
)
