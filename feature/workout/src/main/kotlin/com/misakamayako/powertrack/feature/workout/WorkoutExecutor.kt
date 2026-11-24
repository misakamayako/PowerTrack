package com.misakamayako.powertrack.feature.workout

import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.misakamayako.powertrack.core.navigation.WorkoutStep
import com.misakamayako.powertrack.domain.SettingsRepository
import com.misakamayako.powertrack.domain.WorkoutRepository
import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.RepSpec
import com.misakamayako.powertrack.model.SessionSet
import com.misakamayako.powertrack.model.SetType
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * 练中 Executor。**所有写操作都是先落库再更新 UI**（§4.9 write-through）。
 */
internal class WorkoutExecutor(
    private val workoutRepository: WorkoutRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock = Clock.System,
) : CoroutineExecutor<
    WorkoutStore.Intent,
    Unit,
    WorkoutStore.State,
    WorkoutStore.Message,
    WorkoutStore.Label,
    >() {

    private var sessionId: String? = null

    /** Undo 是内存命令栈，最多 20 条；不持久化（§4.10）。 */
    private val undoStack = ArrayDeque<UndoCommand>()

    private sealed interface UndoCommand {
        data class RestoreSet(val sessionExerciseId: String, val set: SessionSet, val index: Int) : UndoCommand
        data class DeleteCreatedSet(val setId: String) : UndoCommand
    }

    override fun executeIntent(intent: WorkoutStore.Intent) {
        when (intent) {
            is WorkoutStore.Intent.Load -> load(intent.sessionId)
            is WorkoutStore.Intent.SelectExercise -> {
                dispatch(WorkoutStore.Message.ExerciseSelected(intent.sessionExerciseId))
                publish(WorkoutStore.Label.Navigate(WorkoutStep.Record(intent.sessionExerciseId)))
            }
            is WorkoutStore.Intent.AddExercise -> addExercise(intent.exerciseId)
            is WorkoutStore.Intent.RemoveExercise -> removeExercise(intent.sessionExerciseId)
            is WorkoutStore.Intent.AddSet -> addSet(intent)
            is WorkoutStore.Intent.UpdateSegment -> updateSegment(intent)
            is WorkoutStore.Intent.AppendDropSegment -> appendDrop(intent)
            is WorkoutStore.Intent.DeleteSet -> deleteSet(intent.setId)
            is WorkoutStore.Intent.StartRest -> startRest(intent.seconds)
            WorkoutStore.Intent.StopRest -> dispatch(WorkoutStore.Message.RestStarted(null))
            WorkoutStore.Intent.Undo -> undo()
            WorkoutStore.Intent.Finish -> finish()
            WorkoutStore.Intent.Discard -> discard()
            WorkoutStore.Intent.ClearError -> dispatch(WorkoutStore.Message.ErrorCleared)
        }
    }

    private fun load(id: String) {
        sessionId = id
        dispatch(WorkoutStore.Message.Loading)
        scope.launch {
            runCatching { workoutRepository.session(id) }
                .onSuccess { session ->
                    if (session == null) {
                        dispatch(WorkoutStore.Message.Failed("训练不存在"))
                    } else {
                        dispatch(WorkoutStore.Message.Loaded(session))
                    }
                }
                .onFailure { dispatch(WorkoutStore.Message.Failed(it.message ?: "训练加载失败")) }
        }
    }

    private fun addExercise(exerciseId: String) {
        val id = sessionId ?: return
        scope.launch {
            runCatching {
                workoutRepository.addExercise(id, exerciseId)
                workoutRepository.session(id)
            }
                .onSuccess { session ->
                    session?.let {
                        dispatch(WorkoutStore.Message.SessionUpdated(it))
                        dispatch(WorkoutStore.Message.ExerciseSelected(it.exercises.last().id))
                        publish(WorkoutStore.Label.Navigate(WorkoutStep.Record(it.exercises.last().id)))
                    }
                }
                .onFailure { dispatch(WorkoutStore.Message.Failed(it.message ?: "添加动作失败")) }
        }
    }

    private fun removeExercise(sessionExerciseId: String) {
        val id = sessionId ?: return
        scope.launch {
            runCatching {
                workoutRepository.deleteExercise(sessionExerciseId)
                workoutRepository.session(id)
            }
                .onSuccess { session -> session?.let { dispatch(WorkoutStore.Message.SessionUpdated(it)) } }
                .onFailure { dispatch(WorkoutStore.Message.Failed(it.message ?: "删除动作失败")) }
        }
    }

    private fun addSet(intent: WorkoutStore.Intent.AddSet) {
        val id = sessionId ?: return
        scope.launch {
            runCatching {
                workoutRepository.addSet(
                    sessionExerciseId = intent.sessionExerciseId,
                    setType = intent.setType,
                    prescribed = null,
                    restBeforeSec = intent.restBeforeSec,
                    load = intent.load,
                    reps = intent.reps,
                )
                workoutRepository.session(id)
            }
                .onSuccess { session ->
                    session?.let {
                        dispatch(WorkoutStore.Message.SessionUpdated(it))
                        // 只有落库成功后才提示休息计时（§4.9）
                        val restSec = intent.restBeforeSec ?: defaultRest()
                        if (restSec > 0) startRest(restSec)
                        val added = it.exercises.firstOrNull { e -> e.id == intent.sessionExerciseId }?.sets?.lastOrNull()
                        if (added != null) {
                            pushUndo(UndoCommand.DeleteCreatedSet(added.id))
                        }
                    }
                }
                .onFailure { dispatch(WorkoutStore.Message.Failed(it.message ?: "记录失败")) }
        }
    }

    private fun updateSegment(intent: WorkoutStore.Intent.UpdateSegment) {
        val id = sessionId ?: return
        scope.launch {
            runCatching {
                workoutRepository.updateSegment(intent.segmentId, intent.load, intent.reps)
                workoutRepository.session(id)
            }
                .onSuccess { session -> session?.let { dispatch(WorkoutStore.Message.SessionUpdated(it)) } }
                .onFailure { dispatch(WorkoutStore.Message.Failed(it.message ?: "修改失败")) }
        }
    }

    private fun appendDrop(intent: WorkoutStore.Intent.AppendDropSegment) {
        val id = sessionId ?: return
        scope.launch {
            runCatching {
                workoutRepository.appendSegment(
                    setId = intent.setId,
                    load = intent.load,
                    reps = intent.reps,
                    offsetMs = 0,
                )
                workoutRepository.session(id)
            }
                .onSuccess { session ->
                    session?.let {
                        dispatch(WorkoutStore.Message.SessionUpdated(it))
                        publish(WorkoutStore.Label.ShowMessage("已追加一段递减"))
                    }
                }
                .onFailure { dispatch(WorkoutStore.Message.Failed(it.message ?: "追加失败")) }
        }
    }

    private fun deleteSet(setId: String) {
        val id = sessionId ?: return
        val set = state().session?.exercises?.flatMap { it.sets }?.firstOrNull { it.id == setId }
        val owner = state().session?.exercises?.firstOrNull { exercise -> exercise.sets.any { it.id == setId } }
        scope.launch {
            runCatching {
                workoutRepository.deleteSet(setId)
                workoutRepository.session(id)
            }
                .onSuccess { session ->
                    session?.let {
                        dispatch(WorkoutStore.Message.SessionUpdated(it))
                        if (set != null && owner != null) {
                            pushUndo(UndoCommand.RestoreSet(owner.id, set, set.orderIndex))
                            publish(WorkoutStore.Label.ShowMessage("已删除一组，可撤销"))
                        }
                    }
                }
                .onFailure { dispatch(WorkoutStore.Message.Failed(it.message ?: "删除失败")) }
        }
    }

    private fun startRest(seconds: Int) {
        if (seconds <= 0) return
        val rest = WorkoutStore.RestState(
            startedAtMillis = clock.now().toEpochMilliseconds(),
            durationMs = seconds * 1000L,
        )
        dispatch(WorkoutStore.Message.RestStarted(rest))
    }

    private fun undo() {
        val command = undoStack.removeLastOrNull() ?: return
        val id = sessionId ?: return
        scope.launch {
            runCatching {
                when (command) {
                    is UndoCommand.RestoreSet ->
                        workoutRepository.insertSet(command.sessionExerciseId, command.set, command.index)
                    is UndoCommand.DeleteCreatedSet -> workoutRepository.deleteSet(command.setId)
                }
                workoutRepository.session(id)
            }
                .onSuccess { session ->
                    session?.let {
                        dispatch(WorkoutStore.Message.SessionUpdated(it))
                        publish(WorkoutStore.Label.ShowMessage("已撤销"))
                    }
                    dispatch(WorkoutStore.Message.CanUndo(undoStack.isNotEmpty()))
                }
                .onFailure { dispatch(WorkoutStore.Message.Failed(it.message ?: "撤销失败")) }
        }
    }

    private fun finish() {
        val id = sessionId ?: return
        dispatch(WorkoutStore.Message.Finishing(true))
        scope.launch {
            runCatching { workoutRepository.finish(id) }
                .onSuccess { session ->
                    if (session == null) {
                        dispatch(WorkoutStore.Message.Failed("训练不存在"))
                    } else {
                        val summary = WorkoutStore.Summary(
                            sessionId = session.id,
                            setCount = session.completedSetCount,
                            exerciseCount = session.exercises.size,
                            tonnageKg = tonnageOf(session),
                            durationMs = session.duration?.inWholeMilliseconds ?: 0L,
                            prs = justAchievedPrs(session).toPersistentList(),
                        )
                        dispatch(WorkoutStore.Message.Finished(summary))
                        publish(WorkoutStore.Label.Navigate(WorkoutStep.Summary))
                    }
                }
                .onFailure { dispatch(WorkoutStore.Message.Failed(it.message ?: "结束训练失败")) }
        }
    }

    /** finish 已经把 PR 写进库了，这里读回来做展示。 */
    private suspend fun justAchievedPrs(session: com.misakamayako.powertrack.model.WorkoutSession): List<String> {
        val endedAt = session.endedAt ?: return emptyList()
        return workoutRepository.observePrs().first()
            .filter { it.achievedAt == endedAt }
            .map { pr ->
                val name = pr.exerciseName ?: pr.exerciseId
                "${name} · ${com.misakamayako.powertrack.core.ui.format.Labels.prKind(pr.kind)} " +
                    "${com.misakamayako.powertrack.designsystem.component.formatNumber(pr.value)}${pr.unit}"
            }
    }

    private fun discard() {
        val id = sessionId ?: return
        // 先落库、再发 Label：UI 只有在收到 Discarded 之后才 pop entry（D26）。
        scope.launch {
            runCatching { workoutRepository.discard(id) }
                .onSuccess {
                    publish(WorkoutStore.Label.ShowMessage("已丢弃这次训练"))
                    publish(WorkoutStore.Label.Discarded)
                }
                .onFailure { dispatch(WorkoutStore.Message.Failed(it.message ?: "丢弃失败")) }
        }
    }

    private fun pushUndo(command: UndoCommand) {
        undoStack.addLast(command)
        while (undoStack.size > 20) undoStack.removeFirst()
        dispatch(WorkoutStore.Message.CanUndo(true))
    }

    private suspend fun defaultRest(): Int = settingsRepository.get().defaultRestSec
}

fun createWorkoutStore(
    storeFactory: StoreFactory,
    workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository,
    clock: Clock = Clock.System,
): WorkoutStore =
    object : WorkoutStore,
        Store<WorkoutStore.Intent, WorkoutStore.State, WorkoutStore.Label> by storeFactory.create(
            name = "WorkoutStore",
            initialState = WorkoutStore.State(),
            reducer = WorkoutReducer,
            executorFactory = { WorkoutExecutor(workoutRepository, settingsRepository, clock) },
        ) {}
