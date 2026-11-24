package com.misakamayako.powertrack.domain

import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.RepSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §3.4 的规则：冗余列必须由纯函数算出，且 e1RM 必须逐 segment 配对。
 */
class SetMetricsTest {

    @Test
    fun `常规组 100kg x 8`() {
        val metrics = SetMetricsCalculator.compute(
            listOf(Fixtures.segment(load = LoadSpec.Weight(100.0), reps = RepSpec.Fixed(8))),
            bodyweightKg = 80.0,
        )
        assertEquals(100.0, metrics.resolvedLoadKg)
        assertEquals(8, metrics.resolvedReps)
        assertEquals(800.0, metrics.tonnage)
        // Epley: 100 * (1 + 8/30)
        assertTrue(Tolerance.eq(126.6666, metrics.e1rm!!))
    }

    @Test
    fun `递减组 e1RM 必须逐段配对取最大，不能用最大重量配总次数`() {
        val segments = listOf(
            Fixtures.segment(id = "a", order = 0, load = LoadSpec.Weight(100.0), reps = RepSpec.Fixed(8)),
            Fixtures.segment(id = "b", order = 1, load = LoadSpec.Weight(80.0), reps = RepSpec.Fixed(6)),
            Fixtures.segment(id = "c", order = 2, load = LoadSpec.Weight(60.0), reps = RepSpec.Fixed(5)),
        )
        val metrics = SetMetricsCalculator.compute(segments, bodyweightKg = null)

        assertEquals(100.0, metrics.resolvedLoadKg)
        assertEquals(19, metrics.resolvedReps)
        assertEquals(1580.0, metrics.tonnage)
        // 不能是 100 * (1 + 19/30) = 163.33（离谱值），必须是 100 * (1 + 8/30)
        assertTrue(Tolerance.eq(126.6666, metrics.e1rm!!), "e1rm=${metrics.e1rm}")
    }

    @Test
    fun `自重动作用体重加附加重量`() {
        val metrics = SetMetricsCalculator.compute(
            listOf(Fixtures.segment(load = LoadSpec.Bodyweight(10.0), reps = RepSpec.Fixed(5))),
            bodyweightKg = 80.0,
        )
        assertEquals(90.0, metrics.resolvedLoadKg)
        assertTrue(Tolerance.eq(90.0 * (1 + 5.0 / 30.0), metrics.e1rm!!))
    }

    @Test
    fun `助力动作是减向`() {
        assertEquals(
            60.0,
            SetMetricsCalculator.loadOf(LoadSpec.Assisted(20.0), bodyweightKg = 80.0),
        )
    }

    @Test
    fun `器械档位没有每档重量时无法解析`() {
        assertNull(SetMetricsCalculator.loadOf(LoadSpec.MachineStack(level = 5, kgPerLevel = null), null))
        assertEquals(50.0, SetMetricsCalculator.loadOf(LoadSpec.MachineStack(level = 5, kgPerLevel = 10.0), null))
    }

    @Test
    fun `无法解析的 spec 跳过而不是当 0`() {
        val metrics = SetMetricsCalculator.compute(
            listOf(
                Fixtures.segment(id = "a", load = LoadSpec.PercentOf1RM(80.0), reps = RepSpec.Fixed(5)),
                Fixtures.segment(id = "b", order = 1, load = LoadSpec.Weight(100.0), reps = RepSpec.Fixed(5)),
                Fixtures.segment(id = "c", order = 2, load = LoadSpec.Weight(50.0), reps = RepSpec.ToFailure),
            ),
            bodyweightKg = null,
        )
        assertEquals(100.0, metrics.resolvedLoadKg)
        // %1RM 的重量解析不出来，但次数 5 是可解析的，要计入总次数
        assertEquals(10, metrics.resolvedReps, "ToFailure 不能当 0 次，但可解析的段要累加")
        assertTrue(Tolerance.eq(500.0, metrics.tonnage!!), "只有 load 和 reps 都可解析的段计入吨位")
    }

    @Test
    fun `次数超过 12 不参与 e1RM 但保留其他值`() {
        val metrics = SetMetricsCalculator.compute(
            listOf(Fixtures.segment(load = LoadSpec.Weight(60.0), reps = RepSpec.Fixed(20))),
            bodyweightKg = null,
        )
        assertEquals(60.0, metrics.resolvedLoadKg)
        assertEquals(20, metrics.resolvedReps)
        assertEquals(1200.0, metrics.tonnage)
        assertNull(metrics.e1rm, "reps > 12 的估算不可靠，不进 e1rm 冗余列")
    }

    @Test
    fun `空组返回全 null`() {
        val metrics = SetMetricsCalculator.compute(emptyList(), bodyweightKg = 80.0)
        assertNull(metrics.resolvedLoadKg)
        assertNull(metrics.resolvedReps)
        assertNull(metrics.e1rm)
        assertNull(metrics.tonnage)
    }
}
