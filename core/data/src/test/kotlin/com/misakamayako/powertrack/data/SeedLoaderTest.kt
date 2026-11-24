package com.misakamayako.powertrack.data

import com.misakamayako.powertrack.data.seed.SeedLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * §3.6 seed 数据：幂等、完整、外键合法。
 */
class SeedLoaderTest : DataTestBase() {

    @Test
    fun `首次导入写入肌肉树与动作库，第二次跳过`() {
        val first = SeedLoader(db, assetReader).seedIfNeeded(fixedNow)
        assertFalse(first.skipped)
        assertTrue(first.muscles in 26..30, "肌肉节点数异常：${first.muscles}")
        assertTrue(first.exercises in 130..150, "动作数异常：${first.exercises}")

        val second = SeedLoader(db, assetReader).seedIfNeeded(fixedNow)
        assertTrue(second.skipped, "第二次导入必须被 seed_version 跳过")

        assertEquals(first.muscles.toLong(), db.muscleQueries.count().executeAsOne())
        assertEquals(first.exercises.toLong(), db.exerciseQueries.count().executeAsOne())
    }

    @Test
    fun `每个动作至少一条 PRIME 关联，且肌肉都是二层节点`() {
        seed()
        val depthById = db.muscleQueries.selectAll().executeAsList().associate { it.id to it.depth.toInt() }

        val links = db.exerciseQueries.selectLinks().executeAsList()
        assertTrue(links.isNotEmpty())

        links.forEach { link ->
            assertEquals(1, depthById[link.muscle_id], "动作关联到了非二层节点：${link.muscle_id}")
            assertTrue(link.contribution in 0.0..1.0)
        }

        val exercisesMissingPrime = db.exerciseQueries.selectAll().executeAsList().filter { exercise ->
            links.none { it.exercise_id == exercise.id && it.role == "prime" }
        }
        assertTrue(exercisesMissingPrime.isEmpty(), "缺少 PRIME 的动作：${exercisesMissingPrime.map { it.id }}")
    }

    @Test
    fun `肌肉树父节点都存在`() {
        seed()
        val ids = db.muscleQueries.selectAll().executeAsList().map { it.id }.toSet()
        db.muscleQueries.selectAll().executeAsList().forEach { muscle ->
            muscle.parent_id?.let { parent -> assertTrue(parent in ids, "父节点不存在：$parent") }
        }
    }

    @Test
    fun `演示视频与肌肉示意图已打包并与动作关联`() {
        seed()
        val rows = db.exerciseQueries.selectAll().executeAsList()
        val withVideo = rows.filter { it.video_asset != null }
        val withPhoto = rows.filter { it.photo_asset != null }

        assertEquals(112, withVideo.size, "视频数量")
        assertEquals(112, withPhoto.size, "示意图数量")

        val assetsDir = listOf(java.io.File("src/main/assets"), java.io.File("core/data/src/main/assets"))
            .first { it.exists() }

        withVideo.forEach { row ->
            val file = java.io.File(assetsDir, row.video_asset!!)
            assertTrue(file.isFile && file.length() > 1_000, "视频文件缺失或为空：${row.video_asset}")
        }
        withPhoto.forEach { row ->
            val file = java.io.File(assetsDir, row.photo_asset!!)
            assertTrue(file.isFile && file.length() > 1_000, "示意图缺失或为空：${row.photo_asset}")
        }

        // 视频与图片成对出现：有视频的动作也应有示意图
        withVideo.forEach { row ->
            assertTrue(row.photo_asset != null, "动作 ${row.id} 有视频但没有示意图")
        }
    }

    @Test
    fun `动作模型读回 videoAsset 与 photoAsset`() = kotlinx.coroutines.test.runTest {
        seed()
        val detail = exerciseRepository.exercise("barbell_bench_press")!!
        assertEquals("exercises/barbell_bench_press.mp4", detail.exercise.videoAsset)
        assertEquals("exercises/barbell_bench_press.jpg", detail.exercise.photoAsset)

        val withoutMedia = db.exerciseQueries.selectAll().executeAsList().first { it.video_asset == null }
        assertTrue(exerciseRepository.exercise(withoutMedia.id)!!.exercise.videoAsset == null)
    }
}
