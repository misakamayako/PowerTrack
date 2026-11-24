package com.misakamayako.powertrack.data

import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.PrKind
import com.misakamayako.powertrack.model.RepSpec
import com.misakamayako.powertrack.model.SessionFinishKind
import com.misakamayako.powertrack.model.SetType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §4.9 练中 write-through：每次写完都能从库里读回同样的数据。
 * §5.4 finish 检测 PR 且重复 finish 幂等。
 */
class WorkoutRepositoryTest : DataTestBase() {

    @Test
    fun `空白开始到落库读回的数据一致`() = runTest {
        seed()
        val session = workoutRepository.startBlank()
        assertEquals(SessionFinishKind.ACTIVE, session.finishKind)

        val exercise = workoutRepository.addExercise(session.id, "barbell_bench_press")
        val set = workoutRepository.addSet(
            sessionExerciseId = exercise.id,
            setType = SetType.NORMAL,
            load = LoadSpec.Weight(100.0),
            reps = RepSpec.Fixed(8),
        )
        assertEquals(1, set.segments.size, "新建一组要立刻落一条 segment")

        workoutRepository.appendSegment(set.id, LoadSpec.Weight(80.0), RepSpec.Fixed(6))
        workoutRepository.appendSegment(set.id, LoadSpec.Weight(60.0), RepSpec.Fixed(5))

        val reloaded = workoutRepository.session(session.id)!!
        val reloadedSet = reloaded.exercises.single().sets.single()
        assertEquals(3, reloadedSet.segments.size)
        // 每段自己的冗余列（§3.4 逐段配对）
        assertEquals(100.0, reloadedSet.segments[0].resolvedLoadKg)
        assertEquals(8, reloadedSet.segments[0].resolvedReps)
        assertEquals(6, reloadedSet.segments[1].resolvedReps)
        assertTrue(reloadedSet.segments.all { it.e1rm != null })
    }

    @Test
    fun `appendSegment 的 offsetMs 被保存`() = runTest {
        seed()
        val session = workoutRepository.startBlank()
        val exercise = workoutRepository.addExercise(session.id, "barbell_bench_press")
        val set = workoutRepository.addSet(exercise.id, load = LoadSpec.Weight(100.0), reps = RepSpec.Fixed(8))
        workoutRepository.appendSegment(
            setId = set.id,
            load = LoadSpec.Weight(80.0),
            reps = RepSpec.Fixed(6),
            offsetMs = 42_000,
        )
        val reloaded = workoutRepository.session(session.id)!!.exercises.single().sets.single()
        assertEquals(42_000, reloaded.segments.last().offsetMs)
    }

    @Test
    fun `finish 写 endedAt 并生成 PR，重复 finish 不产生重复 PR`() = runTest {
        seed()
        val session = workoutRepository.startBlank()
        val exercise = workoutRepository.addExercise(session.id, "barbell_bench_press")
        val set = workoutRepository.addSet(exercise.id, load = LoadSpec.Weight(100.0), reps = RepSpec.Fixed(8))
        workoutRepository.addSet(exercise.id, load = LoadSpec.Weight(105.0), reps = RepSpec.Fixed(5))

        val finished = workoutRepository.finish(session.id)!!
        assertEquals(SessionFinishKind.FINISHED, finished.finishKind)
        assertNotNull(finished.endedAt)

        val prsAfterFirst = db.metricsQueries.selectAllPrs().executeAsList()
        assertTrue(prsAfterFirst.any { it.kind == "MAX_WEIGHT" && it.value_ == 105.0 })
        assertTrue(prsAfterFirst.any { it.kind == "MAX_E1RM" })
        val countAfterFirst = prsAfterFirst.size

        workoutRepository.finish(session.id)!!
        assertEquals(countAfterFirst, db.metricsQueries.selectAllPrs().executeAsList().size, "重复 finish 不能再写 PR")

        // PR id 与 (exerciseId, kind) 一一对应
        val byKey = db.metricsQueries.selectAllPrs().executeAsList()
            .groupBy { it.exercise_id to it.kind }
        assertTrue(byKey.values.all { it.size == 1 })
        assertTrue(byKey.containsKey("barbell_bench_press" to PrKind.MAX_WEIGHT.name))
    }

    @Test
    fun `更重的组会刷新 PR 而不是新增一行`() = runTest {
        seed()
        val first = workoutRepository.startBlank()
        val e1 = workoutRepository.addExercise(first.id, "barbell_bench_press")
        workoutRepository.addSet(e1.id, load = LoadSpec.Weight(100.0), reps = RepSpec.Fixed(5))
        workoutRepository.finish(first.id)

        val second = workoutRepository.startBlank()
        val e2 = workoutRepository.addExercise(second.id, "barbell_bench_press")
        workoutRepository.addSet(e2.id, load = LoadSpec.Weight(110.0), reps = RepSpec.Fixed(5))
        workoutRepository.finish(second.id)

        val weightPrs = db.metricsQueries.selectAllPrs().executeAsList()
            .filter { it.exercise_id == "barbell_bench_press" && it.kind == "MAX_WEIGHT" }
        assertEquals(1, weightPrs.size)
        assertEquals(110.0, weightPrs.single().value_)
    }

    @Test
    fun `discard 级联删除本次训练的所有子记录`() = runTest {
        seed()
        val session = workoutRepository.startBlank()
        val exercise = workoutRepository.addExercise(session.id, "barbell_bench_press")
        workoutRepository.addSet(exercise.id, load = LoadSpec.Weight(100.0), reps = RepSpec.Fixed(8))

        workoutRepository.discard(session.id)

        assertNull(workoutRepository.session(session.id))
        assertEquals(0, db.workoutQueries.selectExercises(session.id).executeAsList().size)
        assertEquals(0, db.workoutQueries.selectSetsForSession(session.id).executeAsList().size)
        assertEquals(0, db.workoutQueries.selectSegmentsForSession(session.id).executeAsList().size)
    }

    @Test
    fun `默认值预填能取到同动作同类型的上一次数据`() = runTest {
        seed()
        val session = workoutRepository.startBlank()
        val exercise = workoutRepository.addExercise(session.id, "barbell_bench_press")
        workoutRepository.addSet(exercise.id, load = LoadSpec.Weight(102.5), reps = RepSpec.Fixed(6))
        workoutRepository.finish(session.id)

        val last = workoutRepository.lastSetOf("barbell_bench_press", SetType.NORMAL)
        assertNotNull(last)
        assertEquals(LoadSpec.Weight(102.5), last.segments.single().load)
        assertNull(workoutRepository.lastSetOf("barbell_bench_press", SetType.DROP), "没练过的类型返回 null")
    }

    @Test
    fun `删除一组后其 segments 一起消失`() = runTest {
        seed()
        val session = workoutRepository.startBlank()
        val exercise = workoutRepository.addExercise(session.id, "barbell_bench_press")
        val set = workoutRepository.addSet(exercise.id, load = LoadSpec.Weight(100.0), reps = RepSpec.Fixed(8))
        workoutRepository.deleteSet(set.id)
        assertTrue(workoutRepository.session(session.id)!!.exercises.single().sets.isEmpty())
        assertEquals(0, db.workoutQueries.selectSegmentsForSet(set.id).executeAsList().size)
    }
}
