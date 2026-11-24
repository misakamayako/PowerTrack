package com.misakamayako.powertrack.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Instant

/**
 * 肌肉是**两层树**（§2.4），`parentId` 自引用。
 * 内置 id 用固定 slug 字符串，便于 seed 幂等和代码引用。
 */
data class Muscle(
    val id: String,
    val parentId: String? = null,
    val name: String,
    val nameEn: String? = null,
    val region: String? = null,
    val depth: Int = 0,
    val orderIndex: Int = 0,
) {
    val isGroup: Boolean get() = depth == 0
}

/**
 * 动作库条目。用户自建动作 `isCustom = true`，id 用 UUID；内置动作用 slug。
 */
data class Exercise(
    val id: String,
    val name: String,
    val nameEn: String? = null,
    val equipment: Equipment = Equipment.OTHER,
    val mechanic: Mechanic = Mechanic.COMPOUND,
    val forceType: ForceType? = null,
    val laterality: Laterality = Laterality.BILATERAL,
    val defaultUnit: WeightUnit = WeightUnit.KG,
    val instructions: String? = null,
    val mediaRef: String? = null,
    val isCustom: Boolean = false,
    val createdAt: Instant = Instant.fromEpochMilliseconds(0),
    /** 打包在 assets 里的动作演示视频路径（如 `exercises/barbell_bench_press.mp4`）。 */
    val videoAsset: String? = null,
    /** 打包在 assets 里的肌肉示意图路径（如 `exercises/barbell_bench_press.jpg`）。 */
    val photoAsset: String? = null,
    /** 动作与肌肉的关联，带权重和角色。列表查询时通常为空，详情查询才填充。 */
    val muscles: PersistentList<ExerciseMuscle> = persistentListOf(),
)

/**
 * 动作 ↔ 肌肉：**带权重和角色的多对多**（§2.4）。
 * `(exerciseId, muscleId)` 唯一，`contribution ∈ [0, 1]`。
 */
data class ExerciseMuscle(
    val exerciseId: String,
    val muscleId: String,
    val role: MuscleRole,
    val contribution: Double = 1.0,
) {
    init {
        require(contribution in 0.0..1.0) { "contribution 必须在 0..1 之间，实际 $contribution" }
    }
}

/**
 * 动作 + 参与肌肉的展示投影：`name` 是肌肉名，用于 UI 直接渲染。
 */
data class ExerciseMuscleDetail(
    val muscle: Muscle,
    val role: MuscleRole,
    val contribution: Double,
)

/** 动作详情：动作本体 + 按贡献度倒序的肌肉明细。 */
data class ExerciseWithMuscles(
    val exercise: Exercise,
    val muscles: PersistentList<ExerciseMuscleDetail> = persistentListOf(),
)
