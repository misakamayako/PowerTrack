package com.misakamayako.powertrack.domain

import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.RepSpec
import com.misakamayako.powertrack.model.SessionExercise
import com.misakamayako.powertrack.model.SessionSet
import com.misakamayako.powertrack.model.SessionSetSegment
import com.misakamayako.powertrack.model.SetType
import com.misakamayako.powertrack.model.WorkoutSession
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Instant

object Fixtures {
    val T0: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)

    fun segment(
        id: String = "seg",
        load: LoadSpec = LoadSpec.Weight(100.0),
        reps: RepSpec = RepSpec.Fixed(8),
        order: Int = 0,
        set: String = "set",
        isFailure: Boolean = false,
        offsetMs: Long = 0,
    ): SessionSetSegment = SessionSetSegment(
        id = id,
        sessionSetId = set,
        orderIndex = order,
        load = load,
        reps = reps,
        isFailure = isFailure,
        offsetMs = offsetMs,
    )

    fun set(
        id: String = "set",
        orderIndex: Int = 0,
        segments: List<SessionSetSegment> = listOf(segment(set = id)),
        setType: SetType = SetType.NORMAL,
        isWarmup: Boolean = false,
        exerciseSetId: String = "se",
    ): SessionSet = SessionSet(
        id = id,
        sessionExerciseId = exerciseSetId,
        orderIndex = orderIndex,
        setType = setType,
        isWarmup = isWarmup,
        segments = persistentListOf(*segments.toTypedArray()),
    )

    fun exercise(
        id: String = "se",
        exerciseId: String = "barbell_bench_press",
        sets: List<SessionSet> = listOf(set(exerciseSetId = id)),
        orderIndex: Int = 0,
    ): SessionExercise = SessionExercise(
        id = id,
        sessionId = "session",
        exerciseId = exerciseId,
        orderIndex = orderIndex,
        sets = persistentListOf(*sets.toTypedArray()),
    )

    fun session(
        id: String = "session",
        exercises: List<SessionExercise> = listOf(exercise()),
        endedAt: Instant? = null,
        bodyweightKg: Double? = 80.0,
    ): WorkoutSession = WorkoutSession(
        id = id,
        startedAt = T0,
        endedAt = endedAt,
        bodyweightKg = bodyweightKg,
        exercises = persistentListOf(*exercises.toTypedArray()),
    )
}
