package com.misakamayako.powertrack.feature.workout

import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.RepSpec
import com.misakamayako.powertrack.model.SessionExercise
import com.misakamayako.powertrack.model.SessionSet
import com.misakamayako.powertrack.model.SessionSetSegment
import com.misakamayako.powertrack.model.SetType
import com.misakamayako.powertrack.model.WorkoutSession
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * M3 的纯函数部分：Reducer 状态搬运、DROP 计算、吨位汇总、休息计时状态。
 */
class WorkoutReducerTest {

    private val t0 = Instant.fromEpochMilliseconds(1_760_000_000_000L)

    private fun segment(load: LoadSpec, reps: Int, id: String = "seg") = SessionSetSegment(
        id = id,
        sessionSetId = "set",
        orderIndex = 0,
        load = load,
        reps = RepSpec.Fixed(reps),
    )

    private fun set(id: String = "set", segments: List<SessionSetSegment> = listOf(segment(LoadSpec.Weight(100.0), 8))) =
        SessionSet(
            id = id,
            sessionExerciseId = "se",
            orderIndex = 0,
            setType = SetType.NORMAL,
            segments = persistentListOf(*segments.toTypedArray()),
        )

    private fun exercise(id: String = "se", sets: List<SessionSet> = listOf(set())) = SessionExercise(
        id = id,
        sessionId = "s",
        exerciseId = "barbell_bench_press",
        orderIndex = 0,
        sets = persistentListOf(*sets.toTypedArray()),
    )

    private fun session(exercises: List<SessionExercise> = listOf(exercise()), endedAt: Instant? = null) =
        WorkoutSession(
            id = "s",
            startedAt = t0,
            endedAt = endedAt,
            bodyweightKg = 80.0,
            exercises = persistentListOf(*exercises.toTypedArray()),
        )

    @Test
    fun `Loaded 选中第一个动作`() {
        val state = WorkoutReducer.run {
            WorkoutStore.State().reduce(WorkoutStore.Message.Loaded(session()))
        }
        assertEquals("se", state.activeExerciseId)
        assertTrue(!state.loading)
        assertEquals(1, state.totalSets)
    }

    @Test
    fun `SessionUpdated 不清掉选中的动作`() {
        val loading = WorkoutReducer.run { WorkoutStore.State().reduce(WorkoutStore.Message.Loaded(session())) }
        val selected = WorkoutReducer.run { loading.reduce(WorkoutStore.Message.ExerciseSelected("se")) }
        val updated = WorkoutReducer.run { selected.reduce(WorkoutStore.Message.SessionUpdated(session())) }
        assertEquals("se", updated.activeExerciseId)
    }

    @Test
    fun `休息状态写入与清除`() {
        val rest = WorkoutStore.RestState(startedAtMillis = 1000L, durationMs = 120_000L)
        val withRest = WorkoutReducer.run { WorkoutStore.State().reduce(WorkoutStore.Message.RestStarted(rest)) }
        assertEquals(120_000L, withRest.rest?.durationMs)

        val cleared = WorkoutReducer.run { withRest.reduce(WorkoutStore.Message.RestStarted(null)) }
        assertNull(cleared.rest)
    }

    @Test
    fun `Failed 会清掉 finishing 并保留 session`() {
        val state = WorkoutReducer.run { WorkoutStore.State().reduce(WorkoutStore.Message.Loaded(session())) }
        val finishing = WorkoutReducer.run { state.reduce(WorkoutStore.Message.Finishing(true)) }
        val failed = WorkoutReducer.run { finishing.reduce(WorkoutStore.Message.Failed("写库失败")) }
        assertEquals("写库失败", failed.error)
        assertTrue(!failed.finishing)
        assertEquals("s", failed.session?.id)
    }

    @Test
    fun `Finished 写入 summary`() {
        val summary = WorkoutStore.Summary(
            sessionId = "s",
            setCount = 3,
            exerciseCount = 1,
            tonnageKg = 2400.0,
            durationMs = 3_600_000,
        )
        val state = WorkoutReducer.run { WorkoutStore.State().reduce(WorkoutStore.Message.Finished(summary)) }
        assertEquals(3, state.summary?.setCount)
        assertTrue(!state.finishing)
    }

    @Test
    fun `DROP 按 80 百分比减重并对齐到 0_5kg`() {
        assertEquals(LoadSpec.Weight(80.0), dropLoadOf(LoadSpec.Weight(100.0)))
        assertEquals(LoadSpec.Weight(60.0), dropLoadOf(LoadSpec.Weight(75.0)))
    }

    @Test
    fun `无法推算重量的 load 不给 DROP 建议`() {
        assertNull(dropLoadOf(LoadSpec.PercentOf1RM(80.0)))
        assertNull(dropLoadOf(null))
        assertEquals(LoadSpec.Bodyweight(10.0), dropLoadOf(LoadSpec.Bodyweight(10.0)))
    }

    @Test
    fun `吨位按每组 segment 累加`() {
        val multi = session(
            exercises = listOf(
                exercise(
                    sets = listOf(
                        set("a", listOf(segment(LoadSpec.Weight(100.0), 8, "s1"))),
                        set(
                            "b",
                            listOf(
                                segment(LoadSpec.Weight(80.0), 6, "s2"),
                                segment(LoadSpec.Weight(60.0), 5, "s3"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        // 100*8 + 80*6 + 60*5 = 800 + 480 + 300
        assertEquals(1580.0, tonnageOf(multi))
    }

    @Test
    fun `热身组也计入吨位（展示口径）`() {
        val warmup = session(
            exercises = listOf(
                exercise(
                    sets = listOf(set("w", listOf(segment(LoadSpec.Weight(40.0), 10))).copy(isWarmup = true)),
                ),
            ),
        )
        assertEquals(400.0, tonnageOf(warmup))
    }
}
