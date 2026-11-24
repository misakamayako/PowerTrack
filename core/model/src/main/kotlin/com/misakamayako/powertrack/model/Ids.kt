package com.misakamayako.powertrack.model

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 全工程唯一的 ID 生成入口（§3.8）。
 *
 * 用 `kotlin.uuid.Uuid` 而不是 `java.util.UUID`，后者在 KMP 迁移时不可用。
 * `@OptIn(ExperimentalUuidApi::class)` 只允许出现在本文件。
 */
object Ids {
    @OptIn(ExperimentalUuidApi::class)
    fun new(): String = Uuid.random().toString()

    /** 内置 seed 用的固定 slug（§3.6），不做任何随机化。 */
    fun slug(value: String): String = value
}
