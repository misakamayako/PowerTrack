package com.misakamayako.powertrack.domain

import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.RepSpec
import com.misakamayako.powertrack.model.SetPrescription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * §2.5 / §5.5：%1RM 用 bestE1rm 推算，且必须标注估算。
 */
class PrescriptionResolverTest {

    private fun prescription(load: LoadSpec, reps: RepSpec = RepSpec.Fixed(5)) = SetPrescription(
        id = "p1",
        slotId = "slot1",
        orderIndex = 0,
        loadSpec = load,
        repSpec = reps,
    )

    @Test
    fun `Weight 直接给值，不标估算`() {
        val suggested = PrescriptionResolver.resolve(
            prescription(LoadSpec.Weight(100.0)),
            PrescriptionContext(),
        )
        assertEquals(LoadSpec.Weight(100.0), suggested.load)
        assertFalse(suggested.estimated)
    }

    @Test
    fun `PercentOf1RM 用 bestE1rm 推算并四舍五入到 0_5kg`() {
        val suggested = PrescriptionResolver.resolve(
            prescription(LoadSpec.PercentOf1RM(80.0)),
            PrescriptionContext(bestE1rm = 123.4),
        )
        // 123.4 * 0.8 = 98.72 -> 98.5
        assertEquals(LoadSpec.Weight(98.5), suggested.load)
        assertTrue(suggested.estimated, "估算值必须标注")
    }

    @Test
    fun `PercentOf1RM 没有历史时保留原始 spec 并标注估算`() {
        val suggested = PrescriptionResolver.resolve(
            prescription(LoadSpec.PercentOf1RM(80.0)),
            PrescriptionContext(bestE1rm = null),
        )
        assertEquals(LoadSpec.PercentOf1RM(80.0), suggested.load)
        assertTrue(suggested.estimated)
    }

    @Test
    fun `RpeOnly 用上次同类型组的重量`() {
        val last = Fixtures.segment(load = LoadSpec.Weight(72.5), reps = RepSpec.Fixed(8))
        val suggested = PrescriptionResolver.resolve(
            prescription(LoadSpec.RpeOnly(8.0)),
            PrescriptionContext(lastSameExerciseSegment = last),
        )
        assertEquals(LoadSpec.Weight(72.5), suggested.load)
        assertFalse(suggested.estimated)
    }

    @Test
    fun `RpeOnly 没有历史时保留原 spec`() {
        val suggested = PrescriptionResolver.resolve(
            prescription(LoadSpec.RpeOnly(7.5)),
            PrescriptionContext(),
        )
        assertEquals(LoadSpec.RpeOnly(7.5), suggested.load)
    }
}
