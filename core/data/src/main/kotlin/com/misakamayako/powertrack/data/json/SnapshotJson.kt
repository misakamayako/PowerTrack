package com.misakamayako.powertrack.data.json

import com.misakamayako.powertrack.model.AppSettings
import com.misakamayako.powertrack.model.Routine
import com.misakamayako.powertrack.model.RoutineSlot
import com.misakamayako.powertrack.model.SetPrescription
import com.misakamayako.powertrack.model.SetType
import com.misakamayako.powertrack.model.WeightUnit
import com.misakamayako.powertrack.data.mapper.setTypeOf
import com.misakamayako.powertrack.data.mapper.wire
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

/**
 * §3.3 快照 JSON。
 *
 * 实际训练要**快照计划**，不要引用计划（铁律 2）。快照格式同样带版本，
 * 老版本的解析分支必须保留。
 */
object SnapshotJson {

    const val VERSION = 1

    // ---------- 单条 prescription ----------

    fun encodePrescription(prescription: SetPrescription): String = buildJsonObject {
        put("v", VERSION)
        put("id", prescription.id)
        put("slotId", prescription.slotId)
        put("orderIndex", prescription.orderIndex)
        put("setType", prescription.setType.wire())
        put("isWarmup", prescription.isWarmup)
        put("load", Json.parseToJsonElement(SpecJson.encodeLoad(prescription.loadSpec)))
        put("reps", Json.parseToJsonElement(SpecJson.encodeReps(prescription.repSpec)))
        prescription.restSec?.let { put("restSec", it) }
        prescription.rpe?.let { put("rpe", it) }
        prescription.progression?.let { put("progression", it) }
    }.toString()

    fun decodePrescription(raw: String?): SetPrescription? {
        if (raw.isNullOrBlank()) return null
        val obj = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        return when (obj.int("v") ?: VERSION) {
            VERSION -> decodePrescriptionV1(obj)
            else -> decodePrescriptionV1(obj)
        }
    }

    private fun decodePrescriptionV1(obj: kotlinx.serialization.json.JsonObject): SetPrescription? {
        val id = obj.string("id") ?: return null
        return SetPrescription(
            id = id,
            slotId = obj.string("slotId") ?: "",
            orderIndex = obj.int("orderIndex") ?: 0,
            setType = setTypeOf(obj.string("setType") ?: SetType.NORMAL.wire()),
            isWarmup = obj.bool("isWarmup") ?: false,
            loadSpec = SpecJson.decodeLoad(obj.objOrNull("load")?.toString()) ?: return null,
            repSpec = SpecJson.decodeReps(obj.objOrNull("reps")?.toString()) ?: return null,
            restSec = obj.int("restSec"),
            rpe = obj.double("rpe"),
            progression = obj.string("progression"),
        )
    }

    // ---------- 整份计划快照 ----------

    fun encodeRoutine(routine: Routine): String = buildJsonObject {
        put("v", VERSION)
        put(
            "routine",
            buildJsonObject {
                put("id", routine.id)
                put("name", routine.name)
                routine.notes?.let { put("notes", it) }
                put(
                    "tags",
                    buildJsonArray { routine.tags.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } },
                )
                routine.archivedAt?.let { put("archivedAt", it.toEpochMilliseconds()) }
                put("createdAt", routine.createdAt.toEpochMilliseconds())
                put("updatedAt", routine.updatedAt.toEpochMilliseconds())
                put(
                    "slots",
                    buildJsonArray {
                        routine.slots.forEach { slot ->
                            add(
                                buildJsonObject {
                                    put("id", slot.id)
                                    put("routineId", slot.routineId)
                                    put("orderIndex", slot.orderIndex)
                                    put("exerciseId", slot.exerciseId)
                                    slot.supersetGroup?.let { put("supersetGroup", it) }
                                    slot.restSec?.let { put("restSec", it) }
                                    slot.notes?.let { put("notes", it) }
                                    slot.exerciseName?.let { put("exerciseName", it) }
                                    put(
                                        "prescriptions",
                                        buildJsonArray {
                                            slot.prescriptions.forEach { prescription ->
                                                add(
                                                    buildJsonObject {
                                                        put("id", prescription.id)
                                                        put("slotId", prescription.slotId)
                                                        put("orderIndex", prescription.orderIndex)
                                                        put("setType", prescription.setType.wire())
                                                        put("isWarmup", prescription.isWarmup)
                                                        put(
                                                            "load",
                                                            Json.parseToJsonElement(SpecJson.encodeLoad(prescription.loadSpec)),
                                                        )
                                                        put(
                                                            "reps",
                                                            Json.parseToJsonElement(SpecJson.encodeReps(prescription.repSpec)),
                                                        )
                                                        prescription.restSec?.let { put("restSec", it) }
                                                        prescription.rpe?.let { put("rpe", it) }
                                                        prescription.progression?.let { put("progression", it) }
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
    }.toString()

    fun decodeRoutine(raw: String?): Routine? {
        if (raw.isNullOrBlank()) return null
        val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        return when (root.int("v") ?: VERSION) {
            VERSION -> decodeRoutineV1(root.objOrNull("routine") ?: return null)
            else -> decodeRoutineV1(root.objOrNull("routine") ?: return null)
        }
    }

    private fun decodeRoutineV1(obj: kotlinx.serialization.json.JsonObject): Routine? {
        val id = obj.string("id") ?: return null
        val slots = obj.arrayOrEmpty("slots").mapNotNull { element ->
            val slot = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            RoutineSlot(
                id = slot.string("id") ?: return@mapNotNull null,
                routineId = slot.string("routineId") ?: id,
                orderIndex = slot.int("orderIndex") ?: 0,
                exerciseId = slot.string("exerciseId") ?: return@mapNotNull null,
                supersetGroup = slot.string("supersetGroup"),
                restSec = slot.int("restSec"),
                notes = slot.string("notes"),
                exerciseName = slot.string("exerciseName"),
                prescriptions = slot.arrayOrEmpty("prescriptions").mapNotNull { p ->
                    val prescriptionObject = p as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                    decodePrescriptionV1(prescriptionObject)
                }.toPersistentList(),
            )
        }.toPersistentList()

        return Routine(
            id = id,
            name = obj.string("name") ?: "",
            notes = obj.string("notes"),
            tags = obj.arrayOrEmpty("tags").mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                .toPersistentList(),
            archivedAt = obj.long("archivedAt")?.let { Instant.fromEpochMilliseconds(it) },
            createdAt = Instant.fromEpochMilliseconds(obj.long("createdAt") ?: 0L),
            updatedAt = Instant.fromEpochMilliseconds(obj.long("updatedAt") ?: 0L),
            slots = slots,
        )
    }

    // ---------- 设置 ----------

    fun encodeSettings(settings: AppSettings): String = buildJsonObject {
        put("v", VERSION)
        put("dayOffsetHours", settings.dayOffsetHours)
        put("defaultRestSec", settings.defaultRestSec)
        put("unit", settings.unit.wire())
        settings.bodyweightKg?.let { put("bodyweightKg", it) }
    }.toString()

    fun decodeSettings(raw: String?): AppSettings {
        if (raw.isNullOrBlank()) return AppSettings.DEFAULT
        val obj = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return AppSettings.DEFAULT
        return AppSettings(
            dayOffsetHours = obj.int("dayOffsetHours") ?: AppSettings.DEFAULT.dayOffsetHours,
            defaultRestSec = obj.int("defaultRestSec") ?: AppSettings.DEFAULT.defaultRestSec,
            unit = if (obj.string("unit") == "lb") WeightUnit.LB else WeightUnit.KG,
            bodyweightKg = obj.double("bodyweightKg"),
        )
    }

    /** 空计划快照的占位，避免调用方到处判空。 */
    val EMPTY_ROUTINE: Routine
        get() = Routine(
            id = "",
            name = "",
            slots = persistentListOf(),
            createdAt = Instant.fromEpochMilliseconds(0),
            updatedAt = Instant.fromEpochMilliseconds(0),
        )
}
