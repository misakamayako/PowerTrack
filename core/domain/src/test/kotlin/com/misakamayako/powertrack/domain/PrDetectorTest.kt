package com.misakamayako.powertrack.domain

import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.PersonalRecord
import com.misakamayako.powertrack.model.PrKind
import com.misakamayako.powertrack.model.RepSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * §5.4：PR 检测幂等、带容差、同 (exerciseId, kind) 只有一条。
 */
class PrDetectorTest {

    private val now = Fixtures.T0

    private fun sessionWith(vararg sets: Pair<Double, Int>) = Fixtures.session(
        exercises = listOf(
            Fixtures.exercise(
                sets = sets.mapIndexed { index, (kg, reps) ->
                    Fixtures.set(
                        id = "set$index",
                        orderIndex = index,
                        segments = listOf(
                            Fixtures.segment(
                                id = "seg$index",
                                set = "set$index",
                                load = LoadSpec.Weight(kg),
                                reps = RepSpec.Fixed(reps),
                            ),
                        ),
                    )
                },
            ),
        ),
        endedAt = now,
    )

    @Test
    fun `检测四种 PR`() {
        val candidates = PrDetector.candidates(sessionWith(100.0 to 8, 105.0 to 5))
        val byKind = candidates.associateBy { it.kind }

        assertEquals(105.0, byKind[PrKind.MAX_WEIGHT]!!.value)
        // 100x8 -> 126.67 比 105x5 -> 122.5 高
        assertTrue(Tolerance.eq(126.6666, byKind[PrKind.MAX_E1RM]!!.value))
        // 同重量最大次数：100kg 做 8 次超过 105kg 做 5 次
        assertEquals(8.0, byKind[PrKind.MAX_REPS_AT_WEIGHT]!!.value)
    }

    @Test
    fun `同重量下的最大次数按重量分组`() {
        val candidates = PrDetector.candidates(sessionWith(100.0 to 8, 100.0 to 10))
        val pr = candidates.first { it.kind == PrKind.MAX_REPS_AT_WEIGHT }
        assertEquals(10.0, pr.value)
        assertTrue(pr.unit.contains("100"), "unit 要记住重量：${pr.unit}")
    }

    @Test
    fun `吨位 PR 会计入全部组`() {
        val candidates = PrDetector.candidates(sessionWith(100.0 to 8, 80.0 to 6))
        val pr = candidates.first { it.kind == PrKind.MAX_SESSION_TONNAGE }
        assertEquals(1280.0, pr.value)
    }

    @Test
    fun `幂等 id`() {
        val a = PrCandidate("bench", PrKind.MAX_WEIGHT, 100.0, "kg", "set1", now)
        val b = PrCandidate("bench", PrKind.MAX_WEIGHT, 120.0, "kg", "set2", now)
        assertEquals(a.id, b.id, "同一 (exerciseId, kind) 必须复用同一行 id，重复 finish 才不产生重复 PR")
    }

    @Test
    fun `只有真正超过历史才写入，且用容差比较`() {
        val candidates = PrDetector.candidates(sessionWith(100.0 to 8))
        val existing = listOf(
            PersonalRecord(
                id = PrCandidate.prId("barbell_bench_press", PrKind.MAX_WEIGHT),
                exerciseId = "barbell_bench_press",
                kind = PrKind.MAX_WEIGHT,
                value = 100.0001,
                unit = "kg",
                achievedAt = now,
            ),
        )
        val improved = PrDetector.improved(candidates, existing)
        assertTrue(
            improved.none { it.kind == PrKind.MAX_WEIGHT },
            "0.001 容差内的相同重量不算破纪录",
        )
    }

    @Test
    fun `刷新纪录时返回新值`() {
        val candidates = PrDetector.candidates(sessionWith(110.0 to 5))
        val existing = listOf(
            PersonalRecord(
                id = "old",
                exerciseId = "barbell_bench_press",
                kind = PrKind.MAX_WEIGHT,
                value = 100.0,
                unit = "kg",
                achievedAt = now,
            ),
        )
        val improved = PrDetector.improved(candidates, existing)
        assertEquals(110.0, improved.first { it.kind == PrKind.MAX_WEIGHT }.value)
    }
}
