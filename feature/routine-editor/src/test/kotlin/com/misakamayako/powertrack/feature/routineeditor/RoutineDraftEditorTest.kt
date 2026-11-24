package com.misakamayako.powertrack.feature.routineeditor

import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.RepSpec
import com.misakamayako.powertrack.model.SetType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * M2 的草稿编辑全部是纯函数，这里直接测（§4.4：Reducer 可 JVM 单测）。
 */
class RoutineDraftEditorTest {

    private val now = Instant.fromEpochMilliseconds(1_760_000_000_000L)

    private fun draftWithSlots(): com.misakamayako.powertrack.model.Routine {
        var routine = RoutineDraftEditor.newRoutine(now)
        routine = RoutineDraftEditor.addSlot(routine, "barbell_bench_press")
        routine = RoutineDraftEditor.addSlot(routine, "dumbbell_lateral_raise")
        return routine
    }

    @Test
    fun `新增计划带时间戳和空槽位`() {
        val routine = RoutineDraftEditor.newRoutine(now)
        assertTrue(routine.id.isNotBlank())
        assertEquals(0, routine.slots.size)
        assertEquals(now, routine.createdAt)
    }

    @Test
    fun `添加槽位时 orderIndex 递增`() {
        val routine = draftWithSlots()
        assertEquals(2, routine.slots.size)
        assertEquals(listOf(0, 1), routine.slots.map { it.orderIndex })
        assertEquals("dumbbell_lateral_raise", routine.slots[1].exerciseId)
    }

    @Test
    fun `添加槽位时带上动作名，列表才不会显示 raw id`() {
        val routine = RoutineDraftEditor.addSlot(
            RoutineDraftEditor.newRoutine(now),
            "barbell_bench_press",
            "杠铃卧推",
        )
        assertEquals("杠铃卧推", routine.slots.single().exerciseName)
    }

    @Test
    fun `上移下移重排序并重写 orderIndex`() {
        val routine = draftWithSlots()
        val second = routine.slots[1]
        val moved = RoutineDraftEditor.moveSlot(routine, second.id, -1)
        assertEquals("dumbbell_lateral_raise", moved.slots[0].exerciseId)
        assertEquals(listOf(0, 1), moved.slots.map { it.orderIndex })
    }

    @Test
    fun `越界移动不改变顺序`() {
        val routine = draftWithSlots()
        val first = routine.slots[0]
        val moved = RoutineDraftEditor.moveSlot(routine, first.id, -5)
        assertEquals(routine.slots.map { it.id }, moved.slots.map { it.id })
    }

    @Test
    fun `删除槽位后 orderIndex 连续`() {
        val routine = draftWithSlots()
        val removed = RoutineDraftEditor.removeSlot(routine, routine.slots[0].id)
        assertEquals(1, removed.slots.size)
        assertEquals(0, removed.slots.single().orderIndex)
    }

    @Test
    fun `添加一组时 prescription 的 orderIndex 递增且类型正确`() {
        var routine = draftWithSlots()
        val slotId = routine.slots[0].id
        routine = RoutineDraftEditor.addSet(
            routine, slotId, SetType.DROP, LoadSpec.Weight(60.0), RepSpec.Fixed(10), restSec = 90,
        )
        routine = RoutineDraftEditor.addSet(
            routine, slotId, SetType.NORMAL, LoadSpec.PercentOf1RM(80.0), RepSpec.Range(5, 8), restSec = 120,
        )
        val prescriptions = routine.slots[0].prescriptions
        assertEquals(2, prescriptions.size)
        assertEquals(listOf(0, 1), prescriptions.map { it.orderIndex })
        assertEquals(SetType.DROP, prescriptions[0].setType)
        assertEquals(LoadSpec.Weight(60.0), prescriptions[0].loadSpec)
        assertEquals(RepSpec.Range(5, 8), prescriptions[1].repSpec)
        assertTrue(prescriptions.all { it.slotId == slotId })
    }

    @Test
    fun `热身组的 isWarmup 跟随 setType`() {
        var routine = draftWithSlots()
        val slotId = routine.slots[0].id
        routine = RoutineDraftEditor.addSet(
            routine, slotId, SetType.WARMUP, LoadSpec.Weight(40.0), RepSpec.Fixed(12), restSec = 60,
        )
        assertTrue(routine.slots[0].prescriptions.single().isWarmup)
    }

    @Test
    fun `删除一组后剩余的 orderIndex 连续`() {
        var routine = draftWithSlots()
        val slotId = routine.slots[0].id
        repeat(3) { index ->
            routine = RoutineDraftEditor.addSet(
                routine, slotId, SetType.NORMAL, LoadSpec.Weight(50.0), RepSpec.Fixed(index + 5), null,
            )
        }
        val middle = routine.slots[0].prescriptions[1]
        routine = RoutineDraftEditor.removeSet(routine, slotId, middle.id)
        val remaining = routine.slots[0].prescriptions
        assertEquals(2, remaining.size)
        assertEquals(listOf(0, 1), remaining.map { it.orderIndex })
        assertEquals(7, routine.plannedSetCount + 5)
    }

    @Test
    fun `plannedSetCount 汇总所有槽位`() {
        var routine = draftWithSlots()
        routine.slots.forEach { slot ->
            routine = RoutineDraftEditor.addSet(
                routine, slot.id, SetType.NORMAL, LoadSpec.Weight(20.0), RepSpec.Fixed(10), null,
            )
        }
        assertEquals(2, routine.plannedSetCount)
    }

    @Test
    fun `改名不影响槽位`() {
        val routine = draftWithSlots()
        val renamed = RoutineDraftEditor.rename(routine, "腿 B")
        assertEquals("腿 B", renamed.name)
        assertEquals(routine.slots, renamed.slots)
    }

    @Test
    fun `Reducer 只在 Loaded 时清空 loading`() {
        val initial = RoutineEditorStore.State(loading = true)
        val loaded = RoutineEditorReducer.run {
            initial.reduce(RoutineEditorStore.Message.Loaded(com.misakamayako.powertrack.model.Routine(
                id = "r", name = "推 A", createdAt = now, updatedAt = now,
            ).let { kotlinx.collections.immutable.persistentListOf(it) }))
        }
        assertTrue(!loaded.loading)
        assertEquals(1, loaded.routines.size)
    }

    @Test
    fun `排期消息写入日期提示`() {
        val state = RoutineEditorReducer.run {
            RoutineEditorStore.State().reduce(RoutineEditorStore.Message.Scheduled(LocalDate(2026, 11, 23)))
        }
        assertEquals(LocalDate(2026, 11, 23), state.scheduledDate)
        assertTrue(state.message!!.contains("2026-11-23"))
    }
}
