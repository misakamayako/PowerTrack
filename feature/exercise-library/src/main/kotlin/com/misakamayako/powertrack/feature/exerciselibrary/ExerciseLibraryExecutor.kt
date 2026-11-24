package com.misakamayako.powertrack.feature.exerciselibrary

import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.misakamayako.powertrack.domain.ExerciseRepository
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch

internal class ExerciseLibraryExecutor(
    private val repository: ExerciseRepository,
) : CoroutineExecutor<
    ExerciseLibraryStore.Intent,
    Unit,
    ExerciseLibraryStore.State,
    ExerciseLibraryStore.Message,
    ExerciseLibraryStore.Label,
    >() {

    override fun executeIntent(intent: ExerciseLibraryStore.Intent) {
        when (intent) {
            ExerciseLibraryStore.Intent.Refresh -> {
                dispatch(ExerciseLibraryStore.Message.Loading)
                loadMuscles()
                reload()
            }
            is ExerciseLibraryStore.Intent.Query -> {
                dispatch(ExerciseLibraryStore.Message.QueryChanged(intent.text))
                dispatch(ExerciseLibraryStore.Message.Loading)
                reload()
            }
            is ExerciseLibraryStore.Intent.FilterMuscle -> {
                dispatch(ExerciseLibraryStore.Message.MuscleFilterChanged(intent.muscleId))
                dispatch(ExerciseLibraryStore.Message.Loading)
                reload()
            }
            is ExerciseLibraryStore.Intent.FilterEquipment -> {
                dispatch(ExerciseLibraryStore.Message.EquipmentFilterChanged(intent.equipment))
                dispatch(ExerciseLibraryStore.Message.Loading)
                reload()
            }
            is ExerciseLibraryStore.Intent.Pick -> {
                dispatch(ExerciseLibraryStore.Message.Picked(intent.exerciseId))
                publish(ExerciseLibraryStore.Label.Picked(intent.exerciseId))
            }
        }
    }

    private fun loadMuscles() {
        if (state().muscles.isNotEmpty()) return
        scope.launch {
            runCatching { repository.muscleTree() }
                .onSuccess { dispatch(ExerciseLibraryStore.Message.MusclesLoaded(it)) }
        }
    }

    /** 列表查询带上当前 state 的筛选条件。 */
    private fun reload() {
        val current = state()
        scope.launch {
            runCatching {
                repository.search(
                    query = current.query,
                    muscleId = current.muscleFilter,
                    equipment = current.equipmentFilter,
                ).toPersistentList()
            }
                .onSuccess { dispatch(ExerciseLibraryStore.Message.Loaded(it)) }
                .onFailure { dispatch(ExerciseLibraryStore.Message.Failed(it.message ?: "动作库加载失败")) }
        }
    }
}

fun createExerciseLibraryStore(
    storeFactory: StoreFactory,
    repository: ExerciseRepository,
): ExerciseLibraryStore =
    object : ExerciseLibraryStore,
        Store<ExerciseLibraryStore.Intent, ExerciseLibraryStore.State, ExerciseLibraryStore.Label> by storeFactory.create(
            name = "ExerciseLibraryStore",
            initialState = ExerciseLibraryStore.State(),
            reducer = ExerciseLibraryReducer,
            executorFactory = { ExerciseLibraryExecutor(repository) },
        ) {}
