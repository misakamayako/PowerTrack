package com.misakamayako.powertrack.feature.exerciselibrary

import androidx.compose.runtime.Immutable
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.misakamayako.powertrack.model.Equipment
import com.misakamayako.powertrack.model.Exercise
import com.misakamayako.powertrack.model.ExerciseMuscleDetail
import com.misakamayako.powertrack.model.Muscle
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/**
 * M1 动作库：肌肉树浏览 + 搜索 + 器械筛选 + 选中回传。
 */
interface ExerciseLibraryStore :
    Store<ExerciseLibraryStore.Intent, ExerciseLibraryStore.State, ExerciseLibraryStore.Label> {

    sealed interface Intent {
        data object Refresh : Intent
        data class Query(val text: String) : Intent
        data class FilterMuscle(val muscleId: String?) : Intent
        data class FilterEquipment(val equipment: Equipment?) : Intent
        data class Pick(val exerciseId: String) : Intent
    }

    @Immutable
    data class State(
        val loading: Boolean = true,
        val query: String = "",
        val muscleFilter: String? = null,
        val equipmentFilter: Equipment? = null,
        val muscles: PersistentList<Muscle> = persistentListOf(),
        val exercises: PersistentList<Exercise> = persistentListOf(),
        val detail: ExerciseDetailState? = null,
        val pickedId: String? = null,
        val error: String? = null,
    )

    @Immutable
    data class ExerciseDetailState(
        val exercise: Exercise,
        val muscles: PersistentList<ExerciseMuscleDetail>,
    )

    sealed interface Message {
        data object Loading : Message
        data class QueryChanged(val text: String) : Message
        data class MuscleFilterChanged(val muscleId: String?) : Message
        data class EquipmentFilterChanged(val equipment: Equipment?) : Message
        data class MusclesLoaded(val muscles: PersistentList<Muscle>) : Message
        data class Loaded(val exercises: PersistentList<Exercise>) : Message
        data class DetailLoaded(val detail: ExerciseDetailState) : Message
        data class Picked(val exerciseId: String) : Message
        data class Failed(val text: String) : Message
    }

    sealed interface Label {
        data class Picked(val exerciseId: String) : Label
    }
}

/** 纯函数 Reducer。 */
object ExerciseLibraryReducer :
    Reducer<ExerciseLibraryStore.State, ExerciseLibraryStore.Message> {

    override fun ExerciseLibraryStore.State.reduce(
        msg: ExerciseLibraryStore.Message,
    ): ExerciseLibraryStore.State = when (msg) {
        ExerciseLibraryStore.Message.Loading -> copy(loading = true, error = null)
        is ExerciseLibraryStore.Message.QueryChanged -> copy(query = msg.text)
        is ExerciseLibraryStore.Message.MuscleFilterChanged -> copy(muscleFilter = msg.muscleId)
        is ExerciseLibraryStore.Message.EquipmentFilterChanged -> copy(equipmentFilter = msg.equipment)
        is ExerciseLibraryStore.Message.MusclesLoaded -> copy(muscles = msg.muscles)
        is ExerciseLibraryStore.Message.Loaded -> copy(loading = false, exercises = msg.exercises, error = null)
        is ExerciseLibraryStore.Message.DetailLoaded -> copy(detail = msg.detail)
        is ExerciseLibraryStore.Message.Picked -> copy(pickedId = msg.exerciseId)
        is ExerciseLibraryStore.Message.Failed -> copy(loading = false, error = msg.text)
    }
}
