package com.misakamayako.powertrack.feature.routineeditor

import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.misakamayako.powertrack.domain.ExerciseRepository
import com.misakamayako.powertrack.domain.RoutineRepository
import com.misakamayako.powertrack.domain.ScheduleRepository
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

internal class RoutineEditorExecutor(
    private val routineRepository: RoutineRepository,
    private val scheduleRepository: ScheduleRepository,
    private val exerciseRepository: ExerciseRepository,
    private val clock: Clock = Clock.System,
) : CoroutineExecutor<
    RoutineEditorStore.Intent,
    Unit,
    RoutineEditorStore.State,
    RoutineEditorStore.Message,
    RoutineEditorStore.Label,
    >() {

    override fun executeIntent(intent: RoutineEditorStore.Intent) {
        when (intent) {
            RoutineEditorStore.Intent.Load -> loadList()
            RoutineEditorStore.Intent.NewRoutine -> {
                val draft = RoutineDraftEditor.newRoutine(clock.now())
                dispatch(RoutineEditorStore.Message.DraftChanged(draft, dirty = true))
            }
            is RoutineEditorStore.Intent.OpenRoutine -> openRoutine(intent.routineId)
            RoutineEditorStore.Intent.CloseEditor -> dispatch(RoutineEditorStore.Message.EditorClosed)
            is RoutineEditorStore.Intent.Rename -> updateDraft { RoutineDraftEditor.rename(it, intent.name) }
            is RoutineEditorStore.Intent.AddSlot -> addSlot(intent.exerciseId)
            is RoutineEditorStore.Intent.RemoveSlot -> updateDraft {
                RoutineDraftEditor.removeSlot(it, intent.slotId)
            }
            is RoutineEditorStore.Intent.MoveSlot -> updateDraft {
                RoutineDraftEditor.moveSlot(it, intent.slotId, intent.delta)
            }
            is RoutineEditorStore.Intent.AddSet -> updateDraft {
                RoutineDraftEditor.addSet(it, intent.slotId, intent.setType, intent.load, intent.reps, intent.restSec)
            }
            is RoutineEditorStore.Intent.RemoveSet -> updateDraft {
                RoutineDraftEditor.removeSet(it, intent.slotId, intent.prescriptionId)
            }
            RoutineEditorStore.Intent.Save -> save()
            is RoutineEditorStore.Intent.Archive -> archive(intent.routineId)
            is RoutineEditorStore.Intent.Delete -> delete(intent.routineId)
            is RoutineEditorStore.Intent.Schedule -> schedule(intent.date)
            RoutineEditorStore.Intent.DismissError -> dispatch(RoutineEditorStore.Message.ErrorDismissed)
        }
    }

    private fun updateDraft(transform: (com.misakamayako.powertrack.model.Routine) -> com.misakamayako.powertrack.model.Routine) {
        val draft = state().draft ?: return
        dispatch(RoutineEditorStore.Message.DraftChanged(transform(draft)))
    }

    /**
     * 加动作槽位：先查名字再写进草稿。
     *
     * 不查名字的话，编辑中的槽位只能显示 `exercise_id`（带下划线的英文 slug）。
     */
    private fun addSlot(exerciseId: String) {
        scope.launch {
            runCatching { exerciseRepository.exercise(exerciseId) }
                .onSuccess { found ->
                    val draft = state().draft ?: return@onSuccess
                    if (found == null) {
                        dispatch(RoutineEditorStore.Message.Failed("动作不存在：$exerciseId"))
                    } else {
                        dispatch(
                            RoutineEditorStore.Message.DraftChanged(
                                RoutineDraftEditor.addSlot(draft, found.exercise.id, found.exercise.name),
                            ),
                        )
                    }
                }
                .onFailure { dispatch(RoutineEditorStore.Message.Failed(it.message ?: "添加动作失败")) }
        }
    }

    private fun loadList() {
        dispatch(RoutineEditorStore.Message.Loading)
        scope.launch {
            runCatching { routineRepository.routines(includeArchived = false).toPersistentList() }
                .onSuccess { dispatch(RoutineEditorStore.Message.Loaded(it)) }
                .onFailure { dispatch(RoutineEditorStore.Message.Failed(it.message ?: "计划加载失败")) }
        }
    }

    private fun openRoutine(id: String) {
        scope.launch {
            runCatching { routineRepository.routine(id) }
                .onSuccess { routine ->
                    if (routine == null) {
                        dispatch(RoutineEditorStore.Message.Failed("计划不存在"))
                    } else {
                        dispatch(RoutineEditorStore.Message.DraftChanged(routine, dirty = false))
                    }
                }
                .onFailure { dispatch(RoutineEditorStore.Message.Failed(it.message ?: "计划加载失败")) }
        }
    }

    private fun save() {
        val draft = state().draft ?: return
        dispatch(RoutineEditorStore.Message.Saving(true))
        scope.launch {
            runCatching {
                val exists = routineRepository.routine(draft.id) != null
                val toSave = draft.copy(updatedAt = clock.now())
                if (exists) routineRepository.save(toSave) else routineRepository.create(toSave)
                toSave
            }
                .onSuccess { saved ->
                    dispatch(RoutineEditorStore.Message.Saving(false))
                    dispatch(RoutineEditorStore.Message.DraftChanged(saved, dirty = false))
                    dispatch(RoutineEditorStore.Message.Notify("已保存「${saved.name}」"))
                    publish(RoutineEditorStore.Label.ShowMessage("已保存「${saved.name}」"))
                    loadList()
                    // 保存成功就回列表：留在编辑页会让用户不知道到底存没存（D27）
                    publish(RoutineEditorStore.Label.NavigateBack)
                }
                .onFailure { dispatch(RoutineEditorStore.Message.Failed(it.message ?: "保存失败")) }
        }
    }

    private fun archive(routineId: String) {
        scope.launch {
            runCatching { routineRepository.setArchived(routineId, true) }
                .onSuccess {
                    dispatch(RoutineEditorStore.Message.Notify("已归档"))
                    publish(RoutineEditorStore.Label.ShowMessage("已归档"))
                    loadList()
                }
                .onFailure { dispatch(RoutineEditorStore.Message.Failed(it.message ?: "归档失败")) }
        }
    }

    private fun delete(routineId: String) {
        scope.launch {
            runCatching { routineRepository.delete(routineId) }
                .onSuccess {
                    // 不要再 dispatch(EditorClosed)：那会让页面先闪一下"正在准备编辑器…"再退出
                    dispatch(RoutineEditorStore.Message.Notify("已删除"))
                    publish(RoutineEditorStore.Label.ShowMessage("已删除这个计划"))
                    loadList()
                    publish(RoutineEditorStore.Label.NavigateBack)
                }
                .onFailure { dispatch(RoutineEditorStore.Message.Failed(it.message ?: "删除失败")) }
        }
    }

    /**
     * 排期。
     *
     * 允许直接对"还没保存过的新计划"排期：先落库再排，省掉"保存 → 返回列表 → 再进来 → 再排期"的往返。
     */
    private fun schedule(date: LocalDate) {
        val draft = state().draft
        scope.launch {
            runCatching {
                val current = draft ?: error("没有可排期的计划")
                val exists = routineRepository.routine(current.id) != null
                val toSave = current.copy(updatedAt = clock.now())
                if (exists) routineRepository.save(toSave) else routineRepository.create(toSave)
                scheduleRepository.schedule(toSave, date)
                toSave
            }
                .onSuccess { saved ->
                    dispatch(RoutineEditorStore.Message.DraftChanged(saved, dirty = false))
                    dispatch(RoutineEditorStore.Message.Scheduled(date))
                    dispatch(RoutineEditorStore.Message.Notify("已排期到 $date"))
                    publish(RoutineEditorStore.Label.ShowMessage("已排期到 $date"))
                    loadList()
                }
                .onFailure { dispatch(RoutineEditorStore.Message.Failed(it.message ?: "排期失败")) }
        }
    }
}

fun createRoutineEditorStore(
    storeFactory: StoreFactory,
    routineRepository: RoutineRepository,
    scheduleRepository: ScheduleRepository,
    exerciseRepository: ExerciseRepository,
    clock: Clock = Clock.System,
): RoutineEditorStore =
    object : RoutineEditorStore,
        Store<RoutineEditorStore.Intent, RoutineEditorStore.State, RoutineEditorStore.Label> by storeFactory.create(
            name = "RoutineEditorStore",
            initialState = RoutineEditorStore.State(),
            reducer = RoutineEditorReducer,
            executorFactory = {
                RoutineEditorExecutor(
                    routineRepository = routineRepository,
                    scheduleRepository = scheduleRepository,
                    exerciseRepository = exerciseRepository,
                    clock = clock,
                )
            },
        ) {}
