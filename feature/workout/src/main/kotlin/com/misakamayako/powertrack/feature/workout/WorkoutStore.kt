package com.misakamayako.powertrack.feature.workout

import androidx.compose.runtime.Immutable
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.misakamayako.powertrack.core.navigation.WorkoutStep
import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.RepSpec
import com.misakamayako.powertrack.model.SessionExercise
import com.misakamayako.powertrack.model.SetType
import com.misakamayako.powertrack.model.WorkoutSession
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

/**
 * M3 练中 Store（生死线）。
 *
 * §4.12：**倒计时数字绝对不进 State**。State 只存 `restStartedAt` + `restDurationMs`，
 * UI 每秒本地更新剩余时间。
 */
interface WorkoutStore : Store<WorkoutStore.Intent, WorkoutStore.State, WorkoutStore.Label> {

    sealed interface Intent {
        data class Load(val sessionId: String) : Intent
        data class SelectExercise(val sessionExerciseId: String) : Intent
        data class AddExercise(val exerciseId: String) : Intent
        data class RemoveExercise(val sessionExerciseId: String) : Intent
        data class AddSet(
            val sessionExerciseId: String,
            val setType: SetType,
            val load: LoadSpec,
            val reps: RepSpec,
            val restBeforeSec: Int?,
        ) : Intent
        data class UpdateSegment(val segmentId: String, val load: LoadSpec?, val reps: RepSpec?) : Intent
        data class AppendDropSegment(val setId: String, val load: LoadSpec, val reps: RepSpec) : Intent
        data class DeleteSet(val setId: String) : Intent
        data class StartRest(val seconds: Int) : Intent
        data object StopRest : Intent
        data object Undo : Intent
        data object Finish : Intent
        data object Discard : Intent
        data object ClearError : Intent
    }

    @Immutable
    data class State(
        val loading: Boolean = true,
        val session: WorkoutSession? = null,
        val activeExerciseId: String? = null,
        val rest: RestState? = null,
        val canUndo: Boolean = false,
        val finishing: Boolean = false,
        val summary: Summary? = null,
        val error: String? = null,
    ) {
        val activeExercise: SessionExercise?
            get() = session?.exercises?.firstOrNull { it.id == activeExerciseId }

        val totalSets: Int get() = session?.completedSetCount ?: 0
    }

    /** 只存开始时间与总时长，界面自己算剩余（§4.12）。 */
    @Immutable
    data class RestState(
        val startedAtMillis: Long,
        val durationMs: Long,
    )

    @Immutable
    data class Summary(
        val sessionId: String,
        val setCount: Int,
        val exerciseCount: Int,
        val tonnageKg: Double,
        val durationMs: Long,
        val prs: PersistentList<String> = persistentListOf(),
    )

    sealed interface Message {
        data object Loading : Message
        data class Loaded(val session: WorkoutSession) : Message
        data class ExerciseSelected(val sessionExerciseId: String?) : Message
        data class SessionUpdated(val session: WorkoutSession) : Message
        data class RestStarted(val rest: RestState?) : Message
        data class CanUndo(val canUndo: Boolean) : Message
        data class Finishing(val finishing: Boolean) : Message
        data class Finished(val summary: Summary) : Message
        data class Failed(val text: String) : Message
        data object ErrorCleared : Message
    }

    sealed interface Label {
        data class Navigate(val step: WorkoutStep) : Label
        data class ShowMessage(val text: String) : Label
        data class Finished(val sessionId: String) : Label

        /**
         * 丢弃已经**落库完成**，UI 可以安全退出。
         *
         * 不能在 `accept(Discard)` 之后立刻退出：退出会 pop 掉 entry，
         * Store 随之 dispose、executor 的协程被取消，删除可能根本没执行到库里
         * （表现为"丢弃了，首页还在显示这场训练"）。
         */
        data object Discarded : Label
    }
}

/** 纯函数 Reducer：只做状态搬运，不做 IO。 */
object WorkoutReducer : Reducer<WorkoutStore.State, WorkoutStore.Message> {
    override fun WorkoutStore.State.reduce(msg: WorkoutStore.Message): WorkoutStore.State = when (msg) {
        WorkoutStore.Message.Loading -> copy(loading = true, error = null)
        is WorkoutStore.Message.Loaded -> copy(
            loading = false,
            session = msg.session,
            activeExerciseId = msg.session.exercises.firstOrNull()?.id,
            error = null,
        )
        is WorkoutStore.Message.ExerciseSelected -> copy(activeExerciseId = msg.sessionExerciseId)
        is WorkoutStore.Message.SessionUpdated -> copy(session = msg.session, error = null)
        is WorkoutStore.Message.RestStarted -> copy(rest = msg.rest)
        is WorkoutStore.Message.CanUndo -> copy(canUndo = msg.canUndo)
        is WorkoutStore.Message.Finishing -> copy(finishing = msg.finishing)
        is WorkoutStore.Message.Finished -> copy(finishing = false, summary = msg.summary)
        is WorkoutStore.Message.Failed -> copy(loading = false, finishing = false, error = msg.text)
        WorkoutStore.Message.ErrorCleared -> copy(error = null)
    }
}

/** 递减组默认减重比例（§6「DROP →」）。 */
const val DROP_RATIO = 0.8

/**
 * 纯函数：算出「DROP →」应该用什么重量。
 */
fun dropLoadOf(current: LoadSpec?): LoadSpec? = when (current) {
    is LoadSpec.Weight -> LoadSpec.Weight((current.kg * DROP_RATIO * 2).toInt() / 2.0)
    is LoadSpec.Bodyweight -> current
    is LoadSpec.Assisted -> LoadSpec.Assisted(current.kg)
    is LoadSpec.MachineStack -> current
    else -> null
}

/** 把 session 里的 sets 汇总成吨位，供 summary 用。 */
fun tonnageOf(session: WorkoutSession): Double {
    var sum = 0.0
    session.exercises.forEach { exercise ->
        exercise.sets.forEach { set ->
            com.misakamayako.powertrack.domain.SetMetricsCalculator
                .compute(set.segments, session.bodyweightKg)
                .tonnage
                ?.let { sum += it }
        }
    }
    return sum
}

/** 当前输入状态（重量/次数）保存在 UI 本地，不放进 Store State。 */
@Immutable
data class RecordInput(
    val loadKg: Double = 60.0,
    val reps: Int = 8,
    val setType: SetType = SetType.NORMAL,
)

/** 便捷扩展：某次训练里所有的组（已排序）。 */
val WorkoutSession.allSets: PersistentList<com.misakamayako.powertrack.model.SessionSet>
    get() = exercises.flatMap { exercise -> exercise.sets }.toPersistentList()
