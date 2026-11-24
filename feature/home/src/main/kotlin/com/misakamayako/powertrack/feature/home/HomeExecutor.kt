package com.misakamayako.powertrack.feature.home

import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.misakamayako.powertrack.core.navigation.HistoryKey
import com.misakamayako.powertrack.core.navigation.ScheduleKey
import com.misakamayako.powertrack.core.navigation.WorkoutKey
import com.misakamayako.powertrack.domain.Adherence
import com.misakamayako.powertrack.domain.ScheduleRepository
import com.misakamayako.powertrack.domain.SetMetricsCalculator
import com.misakamayako.powertrack.domain.SettingsRepository
import com.misakamayako.powertrack.domain.TrainingDate
import com.misakamayako.powertrack.domain.WorkoutRepository
import com.misakamayako.powertrack.model.ScheduledStatus
import com.misakamayako.powertrack.model.WorkoutSession
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.time.Clock

/**
 * 首页 Executor：所有 IO 在这里，Reducer 保持纯函数。
 */
internal class HomeExecutor(
    private val scheduleRepository: ScheduleRepository,
    private val workoutRepository: WorkoutRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : CoroutineExecutor<HomeStore.Intent, Unit, HomeStore.State, HomeStore.Message, HomeStore.Label>() {

    override fun executeIntent(intent: HomeStore.Intent) {
        when (intent) {
            HomeStore.Intent.Refresh -> refresh()
            is HomeStore.Intent.StartScheduled -> startScheduled(intent.id)
            is HomeStore.Intent.SkipScheduled -> skipScheduled(intent.id)
            HomeStore.Intent.StartBlank -> startBlank()
            HomeStore.Intent.RepeatLast -> repeatLast()
            is HomeStore.Intent.ResumeActive -> publish(HomeStore.Label.Navigate(WorkoutKey(intent.sessionId)))
            HomeStore.Intent.OpenHistory -> publish(HomeStore.Label.Navigate(HistoryKey))
            HomeStore.Intent.OpenSchedule -> publish(HomeStore.Label.Navigate(ScheduleKey))
            HomeStore.Intent.OpenRoutines -> publish(HomeStore.Label.Navigate(com.misakamayako.powertrack.core.navigation.RoutineListKey))
        }
    }

    private fun refresh() {
        dispatch(HomeStore.Message.Loading)
        scope.launch {
            runCatching { loadSnapshot() }
                .onSuccess { snapshot ->
                    dispatch(
                        HomeStore.Message.Loaded(
                            today = snapshot.today,
                            nextPlanned = snapshot.nextPlanned,
                            lastSession = snapshot.lastSession?.toSummary(),
                            activeSessionId = snapshot.activeSessionId,
                            week = snapshot.week,
                        ),
                    )
                }
                .onFailure { dispatch(HomeStore.Message.Failed(it.message ?: "加载失败")) }
        }
    }

    private suspend fun loadSnapshot(): HomeSnapshot {
        val settings = settingsRepository.get()
        val now = clock.now()
        val today = TrainingDate.today(now, timeZone, settings.dayOffsetHours)
        val weekStart = TrainingDate.weekStart(today)
        val weekEnd = TrainingDate.weekEnd(today)

        val weekScheduled = scheduleRepository.between(weekStart, weekEnd)
        val adherence = Adherence.weekly(weekScheduled, today, weeks = 1).singleOrNull()

        val upcoming = scheduleRepository.between(today, today.plus(45, DateTimeUnit.DAY))
        val nextPlanned = upcoming.firstOrNull { it.status == ScheduledStatus.PLANNED }

        val finished = workoutRepository.finishedSessions(limit = 60)
        val inWeek = finished.filter { session ->
            val date = TrainingDate.of(session.endedAt ?: session.startedAt, timeZone)
            date >= weekStart && date <= weekEnd
        }
        val weekSets = inWeek.sumOf { it.completedSetCount }
        val weekTonnage = inWeek.sumOf { tonnageOf(it) }

        return HomeSnapshot(
            today = today,
            nextPlanned = nextPlanned,
            lastSession = finished.firstOrNull(),
            activeSessionId = workoutRepository.activeSession()?.id,
            week = HomeStore.WeekProgress(
                attended = adherence?.attended ?: 0,
                scheduled = adherence?.scheduled ?: 0,
                completedSets = weekSets,
                tonnageKg = weekTonnage,
            ),
        )
    }

    private fun tonnageOf(session: WorkoutSession): Double {
        var sum = 0.0
        session.exercises.forEach { exercise ->
            exercise.sets.forEach { set ->
                SetMetricsCalculator.compute(set.segments, session.bodyweightKg).tonnage?.let { sum += it }
            }
        }
        return sum
    }

    private fun WorkoutSession.toSummary(): HomeStore.LastSessionSummary = HomeStore.LastSessionSummary(
        sessionId = id,
        date = TrainingDate.of(endedAt ?: startedAt, timeZone),
        exerciseCount = exercises.size,
        setCount = completedSetCount,
        tonnageKg = tonnageOf(this),
        duration = duration,
    )

    private fun startScheduled(id: String) {
        startSession(emptyMessage = "排期不存在") { workoutRepository.startFromScheduled(id) }
    }

    private fun skipScheduled(id: String) {
        scope.launch {
            runCatching { scheduleRepository.setStatus(id, ScheduledStatus.SKIPPED) }
                .onSuccess {
                    publish(HomeStore.Label.ShowMessage("已跳过这次计划"))
                    refresh()
                }
                .onFailure { dispatch(HomeStore.Message.Failed(it.message ?: "跳过失败")) }
        }
    }

    private fun startBlank() {
        startSession(emptyMessage = "无法开始训练") { workoutRepository.startBlank() }
    }

    private fun repeatLast() {
        startSession(emptyMessage = "还没有可重复的训练") { workoutRepository.repeatLast() }
    }

    /**
     * 所有"开一场新训练"的入口都走这里。
     *
     * 铁律：**同一时刻只允许一场 ACTIVE**。`selectActive()` 只取最新的一条，
     * 若允许并发新建，旧的那场就再也进不去（数据还在库里但 UI 里消失了）。
     * 所以先查有没有进行中的训练，有就直接回到它并提示，而不是再插一条空白记录。
     */
    private fun startSession(
        emptyMessage: String,
        create: suspend () -> WorkoutSession?,
    ) {
        scope.launch {
            runCatching {
                val active = workoutRepository.activeSession()
                if (active != null) {
                    publish(HomeStore.Label.ShowMessage("有一场训练正在进行，先练完或丢弃它"))
                    publish(HomeStore.Label.Navigate(WorkoutKey(active.id)))
                    return@runCatching
                }
                val session = create()
                if (session == null) {
                    publish(HomeStore.Label.ShowMessage(emptyMessage))
                } else {
                    publish(HomeStore.Label.Navigate(WorkoutKey(session.id)))
                }
            }.onFailure { dispatch(HomeStore.Message.Failed(it.message ?: "无法开始训练")) }
        }
    }
}

fun createHomeStore(
    storeFactory: StoreFactory,
    scheduleRepository: ScheduleRepository,
    workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository,
    clock: Clock = Clock.System,
): HomeStore = object : HomeStore, Store<HomeStore.Intent, HomeStore.State, HomeStore.Label> by storeFactory.create(
    name = "HomeStore",
    initialState = HomeStore.State(),
    reducer = HomeReducer,
    executorFactory = {
        HomeExecutor(
            scheduleRepository = scheduleRepository,
            workoutRepository = workoutRepository,
            settingsRepository = settingsRepository,
            clock = clock,
        )
    },
) {}
