package com.misakamayako.powertrack.feature.home

import androidx.compose.runtime.Immutable
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.misakamayako.powertrack.core.navigation.AppKey
import com.misakamayako.powertrack.model.ScheduledSession
import com.misakamayako.powertrack.model.WorkoutSession
import kotlinx.datetime.LocalDate
import kotlin.time.Duration

/**
 * §8 首页 Store 契约。Reducer 是纯函数，可在 JVM 单测里跑（§4.4）。
 */
interface HomeStore : Store<HomeStore.Intent, HomeStore.State, HomeStore.Label> {

    sealed interface Intent {
        data object Refresh : Intent
        data class StartScheduled(val id: String) : Intent
        data class SkipScheduled(val id: String) : Intent
        data object StartBlank : Intent
        data object RepeatLast : Intent
        data class ResumeActive(val sessionId: String) : Intent
        data object OpenHistory : Intent
        data object OpenSchedule : Intent
        data object OpenRoutines : Intent
    }

    @Immutable
    data class State(
        val loading: Boolean = true,
        val today: LocalDate? = null,
        val nextPlanned: ScheduledSession? = null,
        val lastSession: LastSessionSummary? = null,
        val activeSessionId: String? = null,
        val week: WeekProgress = WeekProgress(),
        val error: String? = null,
    ) {
        val hasPlannedToday: Boolean
            get() = nextPlanned != null && nextPlanned.localDate == today
    }

    @Immutable
    data class LastSessionSummary(
        val sessionId: String,
        val date: LocalDate,
        val exerciseCount: Int,
        val setCount: Int,
        val tonnageKg: Double,
        val duration: Duration?,
    )

    @Immutable
    data class WeekProgress(
        val attended: Int = 0,
        val scheduled: Int = 0,
        val completedSets: Int = 0,
        val tonnageKg: Double = 0.0,
    ) {
        val rate: Double? get() = if (scheduled == 0) null else attended.toDouble() / scheduled
    }

    sealed interface Message {
        data object Loading : Message
        data class Loaded(
            val today: LocalDate,
            val nextPlanned: ScheduledSession?,
            val lastSession: LastSessionSummary?,
            val activeSessionId: String?,
            val week: WeekProgress,
        ) : Message
        data class Failed(val text: String) : Message
    }

    sealed interface Label {
        data class Navigate(val key: AppKey) : Label
        data class ShowMessage(val text: String) : Label
    }
}

/** 纯函数 Reducer（§4.8：集合用 PersistentList，这里没有集合）。 */
object HomeReducer : Reducer<HomeStore.State, HomeStore.Message> {
    override fun HomeStore.State.reduce(msg: HomeStore.Message): HomeStore.State = when (msg) {
        HomeStore.Message.Loading -> copy(loading = true, error = null)
        is HomeStore.Message.Loaded -> copy(
            loading = false,
            today = msg.today,
            nextPlanned = msg.nextPlanned,
            lastSession = msg.lastSession,
            activeSessionId = msg.activeSessionId,
            week = msg.week,
            error = null,
        )
        is HomeStore.Message.Failed -> copy(loading = false, error = msg.text)
    }
}

/** 首页摘要需要的输入，executor 算好后交给 reducer。 */
internal data class HomeSnapshot(
    val today: LocalDate,
    val nextPlanned: ScheduledSession?,
    val lastSession: WorkoutSession?,
    val activeSessionId: String?,
    val week: HomeStore.WeekProgress,
)
