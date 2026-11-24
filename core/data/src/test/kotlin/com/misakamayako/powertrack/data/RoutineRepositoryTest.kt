package com.misakamayako.powertrack.data

import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.RepSpec
import com.misakamayako.powertrack.model.SetPrescription
import com.misakamayako.powertrack.model.SetType
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §2.5 计划模板：整份保存要在一个事务里重写 slot / prescription，
 * 且带子表的行不许用 REPLACE（会级联删除子表）。
 */
class RoutineRepositoryTest : DataTestBase() {

    @Test
    fun `创建计划后能完整读回 slots 与 prescriptions`() = runTest {
        seed()
        val routine = TestRoutines.pushDay()
        routineRepository.create(routine)

        val loaded = routineRepository.routine(routine.id)
        assertNotNull(loaded)
        assertEquals("推 A", loaded.name)
        assertEquals(listOf("推", "上肢"), loaded.tags)
        assertEquals(2, loaded.slots.size)
        assertEquals(4, loaded.slots[0].prescriptions.size)
        assertEquals(3, loaded.slots[1].prescriptions.size)
        assertEquals(7, loaded.plannedSetCount)
        assertEquals("杠铃卧推", loaded.slots[0].exerciseName)
        assertEquals(LoadSpec.PercentOf1RM(80.0), loaded.slots[0].prescriptions[0].loadSpec)
        assertEquals(SetType.NORMAL, loaded.slots[0].prescriptions[0].setType)
    }

    @Test
    fun `保存时重排槽位不会触发级联删除`() = runTest {
        seed()
        val routine = TestRoutines.pushDay()
        routineRepository.create(routine)

        val reordered = routineRepository.routine(routine.id)!!.let { loaded ->
            val slots = loaded.slots.reversed().mapIndexed { index, slot -> slot.copy(orderIndex = index) }
            loaded.copy(slots = persistentListOf(*slots.toTypedArray()))
        }
        routineRepository.save(reordered)

        val after = routineRepository.routine(routine.id)!!
        assertEquals("dumbbell_lateral_raise", after.slots[0].exerciseId)
        assertEquals("barbell_bench_press", after.slots[1].exerciseId)
        assertEquals(3, after.slots[0].prescriptions.size, "重排后 prescription 不能丢")
        assertEquals(4, after.slots[1].prescriptions.size)
    }

    @Test
    fun `归档的计划默认不出现在列表里`() = runTest {
        seed()
        routineRepository.create(TestRoutines.pushDay())
        assertTrue(routineRepository.routines().isNotEmpty())

        routineRepository.setArchived("routine-push", true)
        assertTrue(routineRepository.routines().isEmpty())
        assertEquals(1, routineRepository.routines(includeArchived = true).size)
    }

    @Test
    fun `observeRoutines 会推完整聚合`() = runTest {
        seed()
        routineRepository.create(TestRoutines.pushDay())
        val list = routineRepository.observeRoutines().first()
        assertEquals(1, list.size)
        assertEquals(7, list.single().plannedSetCount)
    }

    @Test
    fun `改名与删除`() = runTest {
        seed()
        routineRepository.create(TestRoutines.pushDay())
        routineRepository.rename("routine-push", "推 B")
        assertEquals("推 B", routineRepository.routine("routine-push")!!.name)

        routineRepository.delete("routine-push")
        assertNull(routineRepository.routine("routine-push"))
        assertEquals(0, db.routineQueries.selectSlots("routine-push").executeAsList().size)
        assertEquals(0, db.routineQueries.selectPrescriptionsForRoutine("routine-push").executeAsList().size)
    }

    @Test
    fun `prescription 的 JSON spec 能往返`() = runTest {
        seed()
        val routine = TestRoutines.pushDay()
        routineRepository.create(routine)
        val loaded = routineRepository.routine(routine.id)!!
        val prescription: SetPrescription = loaded.slots[1].prescriptions[0]
        assertEquals(LoadSpec.Weight(10.0), prescription.loadSpec)
        assertEquals(RepSpec.Fixed(15), prescription.repSpec)
    }
}
