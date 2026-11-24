package com.misakamayako.powertrack.data.json

import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.RepSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * §3.3 JSON 策略：`load_json` / `rep_json` 的编解码。
 *
 * 格式固定带版本：`{"v":1,"t":"weight","kg":100.0}`
 * - `v`：schema 版本，读取时按 v 分支解析
 * - `t`：类型 discriminator，用**稳定字符串**，不依赖 Kotlin 类名
 *
 * 旧 version 的解析分支必须保留；遇到读不出来的怪数据退化成一个可显示的值，绝不丢数据、绝不崩。
 */
object SpecJson {

    const val VERSION = 1

    // ---------- LoadSpec ----------

    fun encodeLoad(spec: LoadSpec): String = buildJsonObject {
        put("v", VERSION)
        when (spec) {
            is LoadSpec.Weight -> {
                put("t", "weight")
                put("kg", spec.kg)
            }
            is LoadSpec.PercentOf1RM -> {
                put("t", "percent_of_1rm")
                put("pct", spec.pct)
            }
            is LoadSpec.Bodyweight -> {
                put("t", "bodyweight")
                spec.addedKg?.let { put("addedKg", it) }
            }
            is LoadSpec.Assisted -> {
                put("t", "assisted")
                put("kg", spec.kg)
            }
            is LoadSpec.Band -> {
                put("t", "band")
                put("level", spec.level)
                spec.color?.let { put("color", it) }
            }
            is LoadSpec.MachineStack -> {
                put("t", "machine_stack")
                put("level", spec.level)
                spec.kgPerLevel?.let { put("kgPerLevel", it) }
            }
            is LoadSpec.RpeOnly -> {
                put("t", "rpe_only")
                put("target", spec.target)
            }
            is LoadSpec.FreeText -> {
                put("t", "free_text")
                put("text", spec.text)
            }
        }
    }.toString()

    fun decodeLoad(raw: String?): LoadSpec? {
        if (raw.isNullOrBlank()) return null
        val obj = parseObject(raw) ?: return LoadSpec.FreeText(raw)
        return when (obj.int("v") ?: VERSION) {
            VERSION -> decodeLoadV1(obj)
            // 将来新增版本时在这里加分支；未知版本尽力按 v1 读，读不出就退化为文本
            else -> decodeLoadV1(obj) ?: LoadSpec.FreeText(raw)
        }
    }

    private fun decodeLoadV1(obj: JsonObject): LoadSpec? = when (obj.string("t")) {
        "weight" -> LoadSpec.Weight(obj.double("kg") ?: return null)
        "percent_of_1rm" -> LoadSpec.PercentOf1RM(obj.double("pct") ?: return null)
        "bodyweight" -> LoadSpec.Bodyweight(obj.double("addedKg"))
        "assisted" -> LoadSpec.Assisted(obj.double("kg") ?: return null)
        "band" -> LoadSpec.Band(obj.int("level") ?: return null, obj.string("color"))
        "machine_stack" -> LoadSpec.MachineStack(obj.int("level") ?: return null, obj.double("kgPerLevel"))
        "rpe_only" -> LoadSpec.RpeOnly(obj.double("target") ?: return null)
        "free_text" -> LoadSpec.FreeText(obj.string("text") ?: "")
        else -> null
    }

    // ---------- RepSpec ----------

    fun encodeReps(spec: RepSpec): String = buildJsonObject {
        put("v", VERSION)
        when (spec) {
            is RepSpec.Fixed -> {
                put("t", "fixed")
                put("n", spec.n)
            }
            is RepSpec.Range -> {
                put("t", "range")
                put("min", spec.min)
                put("max", spec.max)
            }
            RepSpec.Amrap -> put("t", "amrap")
            RepSpec.ToFailure -> put("t", "to_failure")
            is RepSpec.Time -> {
                put("t", "time")
                put("durationMs", spec.durationMs)
            }
            is RepSpec.Distance -> {
                put("t", "distance")
                put("meters", spec.meters)
            }
            is RepSpec.Calories -> {
                put("t", "calories")
                put("kcal", spec.kcal)
            }
        }
    }.toString()

    fun decodeReps(raw: String?): RepSpec? {
        if (raw.isNullOrBlank()) return null
        val obj = parseObject(raw) ?: return RepSpec.Fixed(0)
        return when (obj.int("v") ?: VERSION) {
            VERSION -> decodeRepsV1(obj)
            else -> decodeRepsV1(obj)
        }
    }

    private fun decodeRepsV1(obj: JsonObject): RepSpec? = when (obj.string("t")) {
        "fixed" -> RepSpec.Fixed(obj.int("n") ?: return null)
        "range" -> RepSpec.Range(obj.int("min") ?: return null, obj.int("max") ?: return null)
        "amrap" -> RepSpec.Amrap
        "to_failure" -> RepSpec.ToFailure
        "time" -> RepSpec.Time(obj.long("durationMs") ?: return null)
        "distance" -> RepSpec.Distance(obj.double("meters") ?: return null)
        "calories" -> RepSpec.Calories(obj.int("kcal") ?: return null)
        else -> null
    }

    // ---------- helpers ----------

    private fun parseObject(raw: String): JsonObject? =
        runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
}

/** JSON 读取小工具：类型不对时返回 null 而不是抛异常。 */
internal fun JsonObject.string(key: String): String? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.int(key: String): Int? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()

internal fun JsonObject.long(key: String): Long? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull()

internal fun JsonObject.double(key: String): Double? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()

internal fun JsonObject.bool(key: String): Boolean? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull()

internal fun JsonObject.objOrNull(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arrayOrEmpty(key: String): List<kotlinx.serialization.json.JsonElement> =
    (this[key] as? kotlinx.serialization.json.JsonArray) ?: emptyList()
