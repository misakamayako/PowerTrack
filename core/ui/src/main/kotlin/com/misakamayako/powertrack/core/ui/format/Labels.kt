package com.misakamayako.powertrack.core.ui.format

import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.MuscleRole
import com.misakamayako.powertrack.model.PrKind
import com.misakamayako.powertrack.model.RepSpec
import com.misakamayako.powertrack.model.SessionSetSegment
import com.misakamayako.powertrack.model.SetType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 展示格式化。纯函数，可单测。
 */
object Labels {

    fun setType(type: SetType): String = when (type) {
        SetType.NORMAL -> "常规"
        SetType.WARMUP -> "热身"
        SetType.WORK -> "工作组"
        SetType.DROP -> "递减"
        SetType.PYRAMID -> "金字塔"
        SetType.AMRAP -> "AMRAP"
        SetType.FAILURE -> "力竭"
        SetType.REST_PAUSE -> "休息暂停"
        SetType.MYO_REPS -> "Myo-reps"
        SetType.BACKOFF -> "退让组"
        SetType.TECHNIQUE -> "技术组"
    }

    fun role(role: MuscleRole): String = when (role) {
        MuscleRole.PRIME -> "主动肌"
        MuscleRole.SYNERGIST -> "协同肌"
        MuscleRole.STABILIZER -> "稳定肌"
        MuscleRole.ANTAGONIST -> "拮抗肌"
    }

    fun prKind(kind: PrKind): String = when (kind) {
        PrKind.MAX_WEIGHT -> "最大重量"
        PrKind.MAX_REPS_AT_WEIGHT -> "同重量最多次数"
        PrKind.MAX_E1RM -> "最高 e1RM"
        PrKind.MAX_SESSION_TONNAGE -> "单次最高吨位"
    }

    fun load(load: LoadSpec, unit: String = "kg"): String = when (load) {
        is LoadSpec.Weight -> if (isWhole(load.kg)) "${load.kg.toInt()}$unit" else "${load.kg}$unit"
        is LoadSpec.PercentOf1RM -> "${trim(load.pct)}% 1RM"
        is LoadSpec.Bodyweight -> load.addedKg?.let { "自重+${trim(it)}$unit" } ?: "自重"
        is LoadSpec.Assisted -> "助力${trim(load.kg)}$unit"
        is LoadSpec.Band -> load.color?.let { "弹力带($it)" } ?: "弹力带 L${load.level}"
        is LoadSpec.MachineStack -> load.kgPerLevel?.let { "档位${load.level}(${trim(load.level * it)}$unit)" }
            ?: "档位${load.level}"
        is LoadSpec.RpeOnly -> "RPE ${trim(load.target)}"
        is LoadSpec.FreeText -> load.text
    }

    fun reps(reps: RepSpec): String = when (reps) {
        is RepSpec.Fixed -> "${reps.n}"
        is RepSpec.Range -> "${reps.min}-${reps.max}"
        RepSpec.Amrap -> "AMRAP"
        RepSpec.ToFailure -> "力竭"
        is RepSpec.Time -> "${reps.durationMs / 1000}s"
        is RepSpec.Distance -> "${trim(reps.meters)}m"
        is RepSpec.Calories -> "${reps.kcal}kcal"
    }

    /** `100kg × 8 → 80kg × 6 → 60kg × 5`（递减组一眼能看出来）。 */
    fun segments(segments: List<SessionSetSegment>, unit: String = "kg"): String =
        segments.sortedBy { it.orderIndex }
            .joinToString(" → ") { "${load(it.load, unit)}×${reps(it.reps)}" }

    /** 一组的总吨位，用于列表快速浏览。 */
    fun tonnage(tonnageKg: Double): String =
        if (tonnageKg >= 1000) "${trim(tonnageKg / 1000)}t" else "${trim(tonnageKg)}kg"

    fun duration(duration: Duration?): String {
        if (duration == null) return "—"
        val totalSeconds = duration.inWholeSeconds
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    fun millisToClock(ms: Long): String = duration(ms.milliseconds)

    /** `2026-11-18 -> 11月18日`，跨年时补年份。 */
    fun date(date: LocalDate, today: LocalDate? = null): String {
        val parts = date.toString().split("-") // ISO: yyyy-MM-dd
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        val day = parts[2].toInt()
        val withYear = today != null && today.toString().startsWith("${year}-").not()
        return if (withYear) "${year}年${month}月${day}日" else "${month}月${day}日"
    }

    fun weekday(date: LocalDate): String = when (date.dayOfWeek.isoDayNumber) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }

    fun rest(seconds: Int?): String = when {
        seconds == null -> "—"
        seconds >= 60 -> "${seconds / 60}分${if (seconds % 60 == 0) "" else "${seconds % 60}秒"}"
        else -> "${seconds}秒"
    }

    private fun trim(value: Double): String =
        if (isWhole(value)) value.toInt().toString() else value.toString()

    private fun isWhole(value: Double): Boolean = value == value.toInt().toDouble()


}
