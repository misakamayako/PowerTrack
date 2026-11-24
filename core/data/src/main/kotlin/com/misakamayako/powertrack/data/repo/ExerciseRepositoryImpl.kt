package com.misakamayako.powertrack.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.misakamayako.powertrack.db.PowerTrackDatabase
import com.misakamayako.powertrack.data.mapper.muscleRoleOf
import com.misakamayako.powertrack.data.mapper.toModel
import com.misakamayako.powertrack.data.mapper.wire
import com.misakamayako.powertrack.domain.ExerciseRepository
import com.misakamayako.powertrack.model.Equipment
import com.misakamayako.powertrack.model.Exercise
import com.misakamayako.powertrack.model.ExerciseMuscle
import com.misakamayako.powertrack.model.ExerciseMuscleDetail
import com.misakamayako.powertrack.model.ExerciseWithMuscles
import com.misakamayako.powertrack.model.Muscle
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ExerciseRepositoryImpl(
    private val db: PowerTrackDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ExerciseRepository {

    private val exerciseQueries = db.exerciseQueries
    private val muscleQueries = db.muscleQueries

    override fun observeMuscles(): Flow<PersistentList<Muscle>> =
        muscleQueries.selectAll().asFlow().mapToList(io)
            .map { rows -> rows.map { it.toModel() }.toPersistentList() }

    override fun observeExercises(): Flow<PersistentList<Exercise>> =
        exerciseQueries.selectAll().asFlow().mapToList(io)
            .map { rows -> rows.map { it.toModel() }.toPersistentList() }

    override suspend fun muscleTree(): PersistentList<Muscle> = withContext(io) {
        muscleQueries.selectAll().executeAsList().map { it.toModel() }.toPersistentList()
    }

    override suspend fun search(
        query: String,
        muscleId: String?,
        equipment: Equipment?,
        limit: Int,
    ): PersistentList<Exercise> = withContext(io) {
        exerciseQueries
            .search(
                query = query.trim(),
                muscleId = muscleId.orEmpty(),
                equipment = equipment?.wire().orEmpty(),
                limitCount = limit.toLong(),
            )
            .executeAsList()
            .map { it.toModel() }
            .toPersistentList()
    }

    override suspend fun exercise(id: String): ExerciseWithMuscles? = withContext(io) {
        val row = exerciseQueries.selectById(id).executeAsOneOrNull() ?: return@withContext null
        ExerciseWithMuscles(
            exercise = row.toModel(),
            muscles = musclesForExerciseInternal(id),
        )
    }

    override suspend fun exercises(ids: List<String>): Map<String, Exercise> = withContext(io) {
        if (ids.isEmpty()) return@withContext emptyMap()
        val wanted = ids.toSet()
        exerciseQueries.selectAll().executeAsList()
            .filter { it.id in wanted }
            .associate { it.id to it.toModel() }
    }

    override suspend fun musclesForExercise(exerciseId: String): PersistentList<ExerciseMuscleDetail> =
        withContext(io) { musclesForExerciseInternal(exerciseId) }

    private fun musclesForExerciseInternal(exerciseId: String): PersistentList<ExerciseMuscleDetail> =
        exerciseQueries.selectMuscleDetails(exerciseId).executeAsList().map { row ->
            ExerciseMuscleDetail(
                muscle = Muscle(
                    id = row.muscleId,
                    name = row.muscleName,
                    nameEn = row.muscleNameEn,
                    depth = 1,
                ),
                role = muscleRoleOf(row.role),
                contribution = row.contribution,
            )
        }.toPersistentList()

    override suspend fun idBySlug(slug: String): String? = withContext(io) {
        exerciseQueries.selectBySlug(slug).executeAsOneOrNull()
    }

    override suspend fun createCustom(exercise: Exercise, muscles: List<ExerciseMuscle>): String =
        withContext(io) {
            db.transaction {
                exerciseQueries.insert(
                    id = exercise.id,
                    name = exercise.name,
                    name_en = exercise.nameEn,
                    equipment = exercise.equipment.wire(),
                    mechanic = exercise.mechanic.wire(),
                    force_type = exercise.forceType?.wire(),
                    laterality = exercise.laterality.wire(),
                    default_unit = exercise.defaultUnit.wire(),
                    instructions = exercise.instructions,
                    media_ref = exercise.mediaRef,
                    is_custom = if (exercise.isCustom) 1L else 0L,
                    created_at = exercise.createdAt.toEpochMilliseconds(),
                    video_asset = exercise.videoAsset,
                    photo_asset = exercise.photoAsset,
                )
                muscles.forEach { link ->
                    exerciseQueries.upsertLink(
                        exercise_id = link.exerciseId,
                        muscle_id = link.muscleId,
                        role = link.role.wire(),
                        contribution = link.contribution,
                    )
                }
            }
            exercise.id
        }

    override suspend fun updateCustom(exercise: Exercise, muscles: List<ExerciseMuscle>?): Unit = withContext(io) {
        db.transaction {
            exerciseQueries.update(
                name = exercise.name,
                name_en = exercise.nameEn,
                equipment = exercise.equipment.wire(),
                mechanic = exercise.mechanic.wire(),
                force_type = exercise.forceType?.wire(),
                laterality = exercise.laterality.wire(),
                default_unit = exercise.defaultUnit.wire(),
                instructions = exercise.instructions,
                media_ref = exercise.mediaRef,
                is_custom = if (exercise.isCustom) 1L else 0L,
                video_asset = exercise.videoAsset,
                photo_asset = exercise.photoAsset,
                id = exercise.id,
            )
            if (muscles != null) {
                exerciseQueries.deleteLinksForExercise(exercise.id)
                muscles.forEach { link ->
                    exerciseQueries.upsertLink(
                        exercise_id = link.exerciseId,
                        muscle_id = link.muscleId,
                        role = link.role.wire(),
                        contribution = link.contribution,
                    )
                }
            }
        }
    }

    override suspend fun deleteCustom(id: String): Unit = withContext(io) {
        exerciseQueries.deleteCustomById(id)
    }

    override suspend fun count(): Long = withContext(io) {
        exerciseQueries.count().executeAsOne()
    }
}
