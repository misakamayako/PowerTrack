package com.misakamayako.powertrack.domain

import com.misakamayako.powertrack.model.Equipment
import com.misakamayako.powertrack.model.Exercise
import com.misakamayako.powertrack.model.ExerciseMuscle
import com.misakamayako.powertrack.model.ExerciseMuscleDetail
import com.misakamayako.powertrack.model.ExerciseWithMuscles
import com.misakamayako.powertrack.model.Muscle
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.flow.Flow

/**
 * 动作与肌肉的读取接口（§4.2：feature 之间只通过 core:domain 的接口通信）。
 */
interface ExerciseRepository {

    fun observeMuscles(): Flow<PersistentList<Muscle>>

    fun observeExercises(): Flow<PersistentList<Exercise>>

    suspend fun muscleTree(): PersistentList<Muscle>

    /** 动作库列表：按名称 / 肌肉 / 器械筛选。 */
    suspend fun search(
        query: String = "",
        muscleId: String? = null,
        equipment: Equipment? = null,
        limit: Int = 300,
    ): PersistentList<Exercise>

    suspend fun exercise(id: String): ExerciseWithMuscles?

    suspend fun exercises(ids: List<String>): Map<String, Exercise>

    suspend fun musclesForExercise(exerciseId: String): PersistentList<ExerciseMuscleDetail>

    /** 动作名 → id 的快速解析，seed 与导入用。 */
    suspend fun idBySlug(slug: String): String?

    suspend fun createCustom(exercise: Exercise, muscles: List<ExerciseMuscle>): String

    suspend fun updateCustom(exercise: Exercise, muscles: List<ExerciseMuscle>?)

    suspend fun deleteCustom(id: String)

    suspend fun count(): Long
}
