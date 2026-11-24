package com.misakamayako.powertrack.data.repo

import com.misakamayako.powertrack.db.PowerTrackDatabase
import com.misakamayako.powertrack.data.db.loadRoutine
import com.misakamayako.powertrack.data.db.loadSession
import com.misakamayako.powertrack.data.json.SnapshotJson
import com.misakamayako.powertrack.data.json.SpecJson
import com.misakamayako.powertrack.domain.ExportRepository
import com.misakamayako.powertrack.domain.ImportResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

/**
 * §0：不做云同步，只做本地 JSON 导出。
 */
class ExportRepositoryImpl(
    private val db: PowerTrackDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = Clock.System,
) : ExportRepository {

    override suspend fun exportJson(): String = withContext(io) {
        val routines = db.routineQueries.selectAll().executeAsList().mapNotNull { db.loadRoutine(it.id) }
        val sessions = db.workoutQueries.selectAllFinished().executeAsList().mapNotNull { db.loadSession(it.id) }

        buildJsonObject {
            put("v", 1)
            put("exportedAt", clock.now().toEpochMilliseconds())
            put(
                "routines",
                buildJsonArray {
                    routines.forEach { routine ->
                        add(Json.parseToJsonElement(SnapshotJson.encodeRoutine(routine)))
                    }
                },
            )
            put(
                "sessions",
                buildJsonArray {
                    sessions.forEach { session ->
                        add(
                            buildJsonObject {
                                put("id", session.id)
                                put("startedAt", session.startedAt.toEpochMilliseconds())
                                session.endedAt?.let { put("endedAt", it.toEpochMilliseconds()) }
                                session.sourceScheduledId?.let { put("sourceScheduledId", it) }
                                session.bodyweightKg?.let { put("bodyweightKg", it) }
                                put(
                                    "exercises",
                                    buildJsonArray {
                                        session.exercises.forEach { exercise ->
                                            add(
                                                buildJsonObject {
                                                    put("exerciseId", exercise.exerciseId)
                                                    put("orderIndex", exercise.orderIndex)
                                                    put(
                                                        "sets",
                                                        buildJsonArray {
                                                            exercise.sets.forEach { set ->
                                                                add(
                                                                    buildJsonObject {
                                                                        put("setType", set.setType.name)
                                                                        put("isWarmup", set.isWarmup)
                                                                        put(
                                                                            "segments",
                                                                            buildJsonArray {
                                                                                set.segments.forEach { segment ->
                                                                                    add(
                                                                                        buildJsonObject {
                                                                                            put(
                                                                                                "load",
                                                                                                Json.parseToJsonElement(
                                                                                                    SpecJson.encodeLoad(segment.load),
                                                                                                ),
                                                                                            )
                                                                                            put(
                                                                                                "reps",
                                                                                                Json.parseToJsonElement(
                                                                                                    SpecJson.encodeReps(segment.reps),
                                                                                                ),
                                                                                            )
                                                                                            segment.resolvedLoadKg?.let { put("resolvedLoadKg", it) }
                                                                                            segment.resolvedReps?.let { put("resolvedReps", it) }
                                                                                            segment.e1rm?.let { put("e1rm", it) }
                                                                                        },
                                                                                    )
                                                                                }
                                                                            },
                                                                        )
                                                                    },
                                                                )
                                                            }
                                                        },
                                                    )
                                                },
                                            )
                                        }
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }.toString()
    }

    override suspend fun importJson(json: String): ImportResult =
        ImportResult(0, 0, 0, listOf("导入功能计划在 M5 之后实现，当前只支持导出"))
}
