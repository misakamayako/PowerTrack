package com.misakamayako.powertrack.data.seed

import android.content.res.AssetManager
import com.misakamayako.powertrack.db.PowerTrackDatabase
import com.misakamayako.powertrack.data.mapper.equipmentOf
import com.misakamayako.powertrack.data.mapper.forceTypeOf
import com.misakamayako.powertrack.data.mapper.lateralityOf
import com.misakamayako.powertrack.data.mapper.mechanicOf
import com.misakamayako.powertrack.data.mapper.muscleRoleOf
import com.misakamayako.powertrack.data.mapper.wire
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/** 读 assets 的抽象，便于在 JVM 单测里替换成内存字符串。 */
fun interface SeedAssetReader {
    fun read(name: String): String
}

class AndroidSeedAssetReader(private val assets: AssetManager) : SeedAssetReader {
    override fun read(name: String): String =
        assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
}

/**
 * §3.6 seed 数据导入。
 *
 * 幂等策略：`INSERT OR IGNORE` + `app_meta.seed_version`，整批在**一个事务**里完成。
 */
class SeedLoader(
    private val db: PowerTrackDatabase,
    private val reader: SeedAssetReader,
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun seedIfNeeded(now: Instant): SeedResult {
        val current = db.metricsQueries.getMeta(KEY_SEED_VERSION).executeAsOneOrNull()?.toIntOrNull() ?: 0
        if (current >= SEED_VERSION) return SeedResult(skipped = true)

        val musclesFile = json.decodeFromString<SeedMusclesFile>(reader.read(ASSET_MUSCLES))
        val exercisesFile = json.decodeFromString<SeedExercisesFile>(reader.read(ASSET_EXERCISES))

        var insertedMuscles = 0
        var insertedExercises = 0
        val createdAt = now.toEpochMilliseconds()

        db.transaction {
            musclesFile.muscles.forEach { muscle ->
                db.muscleQueries.insert(
                    id = muscle.id,
                    parent_id = muscle.parentId,
                    name = muscle.name,
                    name_en = muscle.nameEn,
                    region = muscle.region,
                    depth = muscle.depth.toLong(),
                    order_index = muscle.orderIndex.toLong(),
                )
                insertedMuscles++
            }

            exercisesFile.exercises.forEach { exercise ->
                db.exerciseQueries.insert(
                    id = exercise.id,
                    name = exercise.name,
                    name_en = exercise.nameEn,
                    equipment = equipmentOf(exercise.equipment).wire(),
                    mechanic = mechanicOf(exercise.mechanic).wire(),
                    force_type = forceTypeOf(exercise.forceType)?.wire(),
                    laterality = lateralityOf(exercise.laterality).wire(),
                    default_unit = "KG",
                    instructions = exercise.instructions,
                    media_ref = null,
                    is_custom = 0L,
                    created_at = createdAt,
                    video_asset = exercise.video,
                    photo_asset = exercise.photo,
                )
                insertedExercises++
                exercise.muscles.forEach { link ->
                    db.exerciseQueries.insertLink(
                        exercise_id = exercise.id,
                        muscle_id = link.muscleId,
                        role = muscleRoleOf(link.role).wire(),
                        contribution = link.contribution.coerceIn(0.0, 1.0),
                    )
                }
            }

            db.metricsQueries.setMeta(KEY_SEED_VERSION, SEED_VERSION.toString())
        }

        return SeedResult(
            skipped = false,
            muscles = insertedMuscles,
            exercises = insertedExercises,
        )
    }

    companion object {
        const val SEED_VERSION = 2
        const val ASSET_MUSCLES = "seed_muscles.json"
        const val ASSET_EXERCISES = "seed_exercises.json"
        private const val KEY_SEED_VERSION = "seed_version"
    }
}

data class SeedResult(
    val skipped: Boolean,
    val muscles: Int = 0,
    val exercises: Int = 0,
)
