package com.misakamayako.powertrack.data.seed

import kotlinx.serialization.Serializable

/** seed JSON 的 DTO（§3.6）。用固定 slug 作为 id，保证幂等。 */
@Serializable
data class SeedMusclesFile(
    val version: Int = 1,
    val muscles: List<SeedMuscle> = emptyList(),
)

@Serializable
data class SeedMuscle(
    val id: String,
    val parentId: String? = null,
    val name: String,
    val nameEn: String? = null,
    val region: String? = null,
    val depth: Int = 0,
    val orderIndex: Int = 0,
)

@Serializable
data class SeedExercisesFile(
    val version: Int = 1,
    val exercises: List<SeedExercise> = emptyList(),
)

@Serializable
data class SeedExercise(
    val id: String,
    val name: String,
    val nameEn: String? = null,
    val equipment: String = "other",
    val mechanic: String = "compound",
    val forceType: String? = null,
    val laterality: String = "bilateral",
    val instructions: String? = null,
    /** assets 里的演示视频路径 */
    val video: String? = null,
    /** assets 里的肌肉示意图路径 */
    val photo: String? = null,
    val muscles: List<SeedExerciseMuscle> = emptyList(),
)

@Serializable
data class SeedExerciseMuscle(
    val muscleId: String,
    val role: String = "synergist",
    val contribution: Double = 1.0,
)
