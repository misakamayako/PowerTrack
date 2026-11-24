package com.misakamayako.powertrack.feature.history

import androidx.compose.runtime.Immutable
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.misakamayako.powertrack.domain.SettingsRepository
import com.misakamayako.powertrack.domain.TrainingDate
import com.misakamayako.powertrack.domain.WorkoutRepository
import com.misakamayako.powertrack.model.WorkoutSession
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.time.Clock

/**
 * M4 历史列表 Store。
 */
interface HistoryStore : Store<HistoryStore.Intent, HistoryStore.State, HistoryStore.Label> {

    sealed interface Intent {
        data object Load : Intent
        data object LoadMore : Intent
        data class Open(val sessionId: String) : Intent
    }

    @Immutable
    data class State(
        val loading: Boolean = true,
        val sessions: PersistentList<WorkoutSession> = persistentListOf(),
        val today: LocalDate? = null,
        val limit: Int = 20,
        val openSessionId: String? = null,
        val error: String? = null,
    )

    sealed interface Message {
        data object Loading : Message
        data class Loaded(val sessions: PersistentList<WorkoutSession>, val today: LocalDate, val limit: Int) : Message
        data class Opened(val sessionId: String) : Message
        data class Failed(val text: String) : Message
    }

    sealed interface Label {
        data class OpenSession(val sessionId: String) : Label
    }
}

object HistoryReducer : Reducer<HistoryStore.State, HistoryStore.Message> {
    override fun HistoryStore.State.reduce(msg: HistoryStore.Message): HistoryStore.State = when (msg) {
        HistoryStore.Message.Loading -> copy(loading = true, error = null)
        is HistoryStore.Message.Loaded -> copy(
            loading = false,
            sessions = msg.sessions,
            today = msg.today,
            limit = msg.limit,
            error = null,
        )
        is HistoryStore.Message.Opened -> copy(openSessionId = msg.sessionId)
        is HistoryStore.Message.Failed -> copy(loading = false, error = msg.text)
    }
}

internal class HistoryExecutor(
    private val workoutRepository: WorkoutRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : CoroutineExecutor<HistoryStore.Intent, Unit, HistoryStore.State, HistoryStore.Message, HistoryStore.Label>() {

    override fun executeIntent(intent: HistoryStore.Intent) {
        when (intent) {
            HistoryStore.Intent.Load -> load(state().limit)
            HistoryStore.Intent.LoadMore -> load(state().limit + 20)
            is HistoryStore.Intent.Open -> {
                dispatch(HistoryStore.Message.Opened(intent.sessionId))
                publish(HistoryStore.Label.OpenSession(intent.sessionId))
            }
        }
    }

    private fun load(limit: Int) {
        dispatch(HistoryStore.Message.Loading)
        scope.launch {
            runCatching {
                val sessions = workoutRepository.finishedSessions(limit)
                val settings = settingsRepository.get()
                val today = TrainingDate.today(clock.now(), timeZone, settings.dayOffsetHours)
                sessions to today
            }
                .onSuccess { (sessions, today) ->
                    dispatch(HistoryStore.Message.Loaded(sessions, today, limit))
                }
                .onFailure { dispatch(HistoryStore.Message.Failed(it.message ?: "历史加载失败")) }
        }
    }
}

fun createHistoryStore(
    storeFactory: StoreFactory,
    workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository,
    clock: Clock = Clock.System,
): HistoryStore =
    object : HistoryStore,
        Store<HistoryStore.Intent, HistoryStore.State, HistoryStore.Label> by storeFactory.create(
            name = "HistoryStore",
            initialState = HistoryStore.State(),
            reducer = HistoryReducer,
            executorFactory = { HistoryExecutor(workoutRepository, settingsRepository, clock) },
        ) {}

/** 未来 N 天的日期范围，供排期页复用。 */
internal fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)
