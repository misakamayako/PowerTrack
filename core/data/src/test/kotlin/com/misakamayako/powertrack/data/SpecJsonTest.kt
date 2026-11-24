package com.misakamayako.powertrack.data

import com.misakamayako.powertrack.data.json.SnapshotJson
import com.misakamayako.powertrack.data.json.SpecJson
import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.RepSpec
import com.misakamayako.powertrack.model.SetType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * §3.3 JSON 策略：带 version、discriminator 用稳定字符串、旧版本分支保留。
 */
class SpecJsonTest {

    @Test
    fun `LoadSpec 全变体往返`() {
        val cases = listOf(
            LoadSpec.Weight(100.0),
            LoadSpec.PercentOf1RM(80.0),
            LoadSpec.Bodyweight(10.0),
            LoadSpec.Bodyweight(null),
            LoadSpec.Assisted(15.0),
            LoadSpec.Band(level = 3, color = "red"),
            LoadSpec.MachineStack(level = 7, kgPerLevel = 5.0),
            LoadSpec.RpeOnly(8.5),
            LoadSpec.FreeText("挂片 2×20kg"),
        )
        cases.forEach { spec ->
            val json = SpecJson.encodeLoad(spec)
            assertTrue(json.contains("\"v\":1"), "必须带版本：$json")
            assertEquals(spec, SpecJson.decodeLoad(json))
        }
    }

    @Test
    fun `RepSpec 全变体往返`() {
        val cases = listOf(
            RepSpec.Fixed(8),
            RepSpec.Range(5, 8),
            RepSpec.Amrap,
            RepSpec.ToFailure,
            RepSpec.Time(45_000),
            RepSpec.Distance(100.0),
            RepSpec.Calories(20),
        )
        cases.forEach { spec ->
            assertEquals(spec, SpecJson.decodeReps(SpecJson.encodeReps(spec)))
        }
    }

    @Test
    fun `discriminator 是稳定字符串而不是类名`() {
        assertEquals("{\"v\":1,\"t\":\"weight\",\"kg\":100.0}", SpecJson.encodeLoad(LoadSpec.Weight(100.0)))
        assertEquals("{\"v\":1,\"t\":\"to_failure\"}", SpecJson.encodeReps(RepSpec.ToFailure))
    }

    @Test
    fun `未知版本尽力解析，读不出来退化为文本而不是丢数据`() {
        assertEquals(
            LoadSpec.Weight(100.0),
            SpecJson.decodeLoad("""{"v":9,"t":"weight","kg":100.0}"""),
        )
        val fallback = SpecJson.decodeLoad("""{"v":9,"t":"future_type","foo":1}""")
        assertNotNull(fallback)
        assertTrue(fallback is LoadSpec.FreeText, "未知类型必须保底成 FreeText")
        assertEquals("not json", (SpecJson.decodeLoad("not json") as LoadSpec.FreeText).text)
    }

    @Test
    fun `空字符串读出 null`() {
        assertEquals(null, SpecJson.decodeLoad(""))
        assertEquals(null, SpecJson.decodeLoad(null))
        assertEquals(null, SpecJson.decodeReps("  "))
    }

    @Test
    fun `计划快照往返（含嵌套 prescription 与 spec）`() {
        val routine = TestRoutines.pushDay()
        val encoded = SnapshotJson.encodeRoutine(routine)
        assertTrue(encoded.contains("\"v\":1"))
        val decoded = SnapshotJson.decodeRoutine(encoded)!!

        assertEquals(routine.id, decoded.id)
        assertEquals(routine.name, decoded.name)
        assertEquals(routine.tags, decoded.tags)
        assertEquals(routine.slots.size, decoded.slots.size)
        assertEquals(7, decoded.plannedSetCount)
        assertEquals(LoadSpec.PercentOf1RM(80.0), decoded.slots[0].prescriptions[0].loadSpec)
        assertEquals(SetType.NORMAL, decoded.slots[0].prescriptions[0].setType)
    }

    @Test
    fun `prescription 快照往返`() {
        val routine = TestRoutines.pushDay()
        val prescription = routine.slots[0].prescriptions[0]
        val decoded = SnapshotJson.decodePrescription(SnapshotJson.encodePrescription(prescription))!!
        assertEquals(prescription.id, decoded.id)
        assertEquals(prescription.slotId, decoded.slotId)
        assertEquals(prescription.restSec, decoded.restSec)
        assertEquals(prescription.rpe, decoded.rpe)
        assertEquals(prescription.loadSpec, decoded.loadSpec)
        assertEquals(prescription.repSpec, decoded.repSpec)
    }

    @Test
    fun `坏数据不崩溃`() {
        assertEquals(null, SnapshotJson.decodeRoutine("{bad}"))
        assertEquals(null, SnapshotJson.decodePrescription(""))
        assertEquals(com.misakamayako.powertrack.model.AppSettings.DEFAULT, SnapshotJson.decodeSettings("nope"))
    }
}
