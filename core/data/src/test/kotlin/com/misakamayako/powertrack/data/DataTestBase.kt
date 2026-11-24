package com.misakamayako.powertrack.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.misakamayako.powertrack.data.repo.ExerciseRepositoryImpl
import com.misakamayako.powertrack.data.repo.RoutineRepositoryImpl
import com.misakamayako.powertrack.data.repo.ScheduleRepositoryImpl
import com.misakamayako.powertrack.data.repo.SettingsRepositoryImpl
import com.misakamayako.powertrack.data.repo.WorkoutRepositoryImpl
import com.misakamayako.powertrack.data.seed.SeedAssetReader
import com.misakamayako.powertrack.data.seed.SeedLoader
import com.misakamayako.powertrack.db.PowerTrackDatabase
import java.io.File
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * JVM 单测用的内存数据库（§7.3：JVM 单测不许 Robolectric）。
 *
 * 外键 pragma 与真机一致地打开，这样 CASCADE 行为也能被测到。
 */
open class DataTestBase {

    protected val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { drv ->
        drv.execute(null, "PRAGMA foreign_keys = ON", 0)
    }
    protected val db: PowerTrackDatabase = PowerTrackDatabase(driver).also {
        PowerTrackDatabase.Schema.create(driver)
    }

    protected val fixedNow: Instant = Instant.fromEpochMilliseconds(1_760_000_000_000L)
    protected val clock: Clock = object : Clock {
        override fun now(): Instant = fixedNow
    }

    protected val settingsRepository by lazy { SettingsRepositoryImpl(db) }
    protected val exerciseRepository by lazy { ExerciseRepositoryImpl(db) }
    protected val routineRepository by lazy { RoutineRepositoryImpl(db, clock = clock) }
    protected val scheduleRepository by lazy { ScheduleRepositoryImpl(db, clock = clock) }
    protected val workoutRepository by lazy {
        WorkoutRepositoryImpl(db, settingsRepository = settingsRepository, clock = clock)
    }

    /** 读真实 assets 目录，保证 seed JSON 本身也被测到。 */
    protected val assetReader: SeedAssetReader = SeedAssetReader { name ->
        val candidates = listOf(
            File("src/main/assets/$name"),
            File("core/data/src/main/assets/$name"),
        )
        candidates.firstOrNull { it.exists() }?.readText(Charsets.UTF_8)
            ?: error("找不到 assets/$name")
    }

    protected fun seed() {
        SeedLoader(db, assetReader).seedIfNeeded(fixedNow)
    }

    protected fun teardown() {
        driver.close()
    }
}
