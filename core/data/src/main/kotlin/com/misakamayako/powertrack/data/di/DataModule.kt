package com.misakamayako.powertrack.data.di

import com.misakamayako.powertrack.data.db.AndroidDatabaseFactory
import com.misakamayako.powertrack.data.repo.ExerciseRepositoryImpl
import com.misakamayako.powertrack.data.repo.ExportRepositoryImpl
import com.misakamayako.powertrack.data.repo.RoutineRepositoryImpl
import com.misakamayako.powertrack.data.repo.ScheduleRepositoryImpl
import com.misakamayako.powertrack.data.repo.SettingsRepositoryImpl
import com.misakamayako.powertrack.data.repo.StatsRepositoryImpl
import com.misakamayako.powertrack.data.repo.WorkoutRepositoryImpl
import com.misakamayako.powertrack.data.seed.AndroidSeedAssetReader
import com.misakamayako.powertrack.data.seed.SeedAssetReader
import com.misakamayako.powertrack.data.seed.SeedLoader
import com.misakamayako.powertrack.domain.ExerciseRepository
import com.misakamayako.powertrack.domain.ExportRepository
import com.misakamayako.powertrack.domain.RoutineRepository
import com.misakamayako.powertrack.domain.ScheduleRepository
import com.misakamayako.powertrack.domain.SettingsRepository
import com.misakamayako.powertrack.domain.StatsRepository
import com.misakamayako.powertrack.domain.WorkoutRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** 数据层 Koin 装配。app 只依赖 domain 的接口，实现类只在这里出现。 */
val dataModule = module {

    single { AndroidDatabaseFactory(androidContext()) }

    single { get<AndroidDatabaseFactory>().create() }

    single<SeedAssetReader> { AndroidSeedAssetReader(androidContext().assets) }

    single { SeedLoader(db = get(), reader = get()) }

    single<SettingsRepository> { SettingsRepositoryImpl(db = get()) }

    single<ExerciseRepository> { ExerciseRepositoryImpl(db = get()) }

    single<RoutineRepository> { RoutineRepositoryImpl(db = get()) }

    single<ScheduleRepository> { ScheduleRepositoryImpl(db = get()) }

    single<WorkoutRepository> { WorkoutRepositoryImpl(db = get(), settingsRepository = get()) }

    single<StatsRepository> { StatsRepositoryImpl(db = get()) }

    single<ExportRepository> { ExportRepositoryImpl(db = get()) }
}
