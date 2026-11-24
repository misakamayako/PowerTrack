package com.misakamayako.powertrack.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * 个人纪录（§5.4）。同一 `(exerciseId, kind)` 只保留最大值，`Finish` 时幂等写入。
 */
data class PersonalRecord(
    val id: String,
    val exerciseId: String,
    val kind: PrKind,
    val value: Double,
    val unit: String,
    val sessionSetId: String? = null,
    val achievedAt: Instant,
    /** 只读投影：动作名。 */
    val exerciseName: String? = null,
)

/** 体重 / 体测记录（schema 已留，UI 在 M5）。 */
data class BodyMetric(
    val id: String,
    val localDate: LocalDate,
    val weightKg: Double? = null,
    val bodyFatPct: Double? = null,
    val note: String? = null,
)
