package com.misakamayako.powertrack.feature.stats

import androidx.compose.runtime.Immutable
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.misakamayako.powertrack.domain.E1rmPoint
import com.misakamayako.powertrack.domain.SettingsRepository
import com.misakamayako.powertrack.domain.StatsRepository
import com.misakamayako.powertrack.domain.TrainingDate
import com.misakamayako.powertrack.domain.WeekTonnage
import com.misakamayako.powertrack.model.PersonalRecord
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * M5 统计：周吨位、PR 列表、单动作 e1RM 曲线。
 */
interface StatsStore : Store<StatsStore.Intent, StatsStore.State, StatsStore.Label> {

    sealed interface Intent {
        data object Load : Intent
        data class SelectExercise(val exerciseId: String) : Intent
    }

    @Immutable
    data class State(
        val loading: Boolean = true,
        val today: LocalDate? = null,
        val weeklyTonnage: PersistentList<WeekTonnage> = persistentListOf(),
        val prs: PersistentList<PersonalRecord> = persistentListOf(),
        val totalSessions: Long = 0,
        val selectedExerciseId: String? = null,
        val e1rmPoints: PersistentList<E1rmPoint> = persistentListOf(),
        val error: String? = null,
    ) {
        /** 有 PR 记录的动作，用作曲线选择器。 */
        val exercises: PersistentList<PersonalRecord>
            get() = prs.distinctBy { it.exerciseId }.toPersistentList()
    }

    sealed interface Message {
        data object Loading : Message
        data class Loaded(
            val today: LocalDate,
            val weeklyTonnage: PersistentList<WeekTonnage>,
            val prs: PersistentList<PersonalRecord>,
            val totalSessions: Long,
        ) : Message
        data class ExerciseSelected(val exerciseId: String) : Message
        data class E1rmLoaded(val points: PersistentList<E1rmPoint>) : Message
        data class Failed(val text: String) : Message
    }

    sealed interface Label
}

object StatsReducer : Reducer<StatsStore.State, StatsStore.Message> {
    override fun StatsStore.State.reduce(msg: StatsStore.Message): StatsStore.State = when (msg) {
        StatsStore.Message.Loading -> copy(loading = true, error = null)
        is StatsStore.Message.Loaded -> copy(
            loading = false,
            today = msg.today,
            weeklyTonnage = msg.weeklyTonnage,
            prs = msg.prs,
            totalSessions = msg.totalSessions,
            selectedExerciseId = selectedExerciseId ?: msg.prs.firstOrNull()?.exerciseId,
            error = null,
        )
        is StatsStore.Message.ExerciseSelected -> copy(selectedExerciseId = msg.exerciseId)
        is StatsStore.Message.E1rmLoaded -> copy(e1rmPoints = msg.points)
        is StatsStore.Message.Failed -> copy(loading = false, error = msg.text)
    }
}

internal class StatsExecutor(
    private val statsRepository: StatsRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : CoroutineExecutor<StatsStore.Intent, Unit, StatsStore.State, StatsStore.Message, StatsStore.Label>() {

    override fun executeIntent(intent: StatsStore.Intent) {
        when (intent) {
            StatsStore.Intent.Load -> load()
            is StatsStore.Intent.SelectExercise -> {
                dispatch(StatsStore.Message.ExerciseSelected(intent.exerciseId))
                loadE1rm(intent.exerciseId)
            }
        }
    }

    private fun load() {
        dispatch(StatsStore.Message.Loading)
        scope.launch {
            runCatching {
                val settings = settingsRepository.get()
                val today = TrainingDate.today(clock.now(), timeZone, settings.dayOffsetHours)
                val weekly = statsRepository.weeklyTonnage(weeks = 8, today = today)
                val prs = statsRepository.prs()
                val total = statsRepository.totalSessions()
                Triple(today, weekly, prs) to total
            }
                .onSuccess { (data, total) ->
                    val (today, weekly, prs) = data
                    dispatch(
                        StatsStore.Message.Loaded(
                            today = today,
                            weeklyTonnage = weekly.toPersistentList(),
                            prs = prs,
                            totalSessions = total,
                        ),
                    )
                    prs.firstOrNull()?.let { loadE1rm(it.exerciseId) }
                }
                .onFailure { dispatch(StatsStore.Message.Failed(it.message ?: "统计加载失败")) }
        }
    }

    private fun loadE1rm(exerciseId: String) {
        scope.launch {
            runCatching { statsRepository.e1rmSeries(exerciseId).toPersistentList() }
                .onSuccess { dispatch(StatsStore.Message.E1rmLoaded(it)) }
                .onFailure { dispatch(StatsStore.Message.Failed(it.message ?: "曲线加载失败")) }
        }
    }
}

fun createStatsStore(
    storeFactory: StoreFactory,
    statsRepository: StatsRepository,
    settingsRepository: SettingsRepository,
    clock: Clock = Clock.System,
): StatsStore =
    object : StatsStore,
        Store<StatsStore.Intent, StatsStore.State, StatsStore.Label> by storeFactory.create(
            name = "StatsStore",
            initialState = StatsStore.State(),
            reducer = StatsReducer,
            executorFactory = { StatsExecutor(statsRepository, settingsRepository, clock) },
        ) {}
