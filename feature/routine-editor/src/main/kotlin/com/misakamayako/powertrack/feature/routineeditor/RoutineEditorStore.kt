package com.misakamayako.powertrack.feature.routineeditor

import androidx.compose.runtime.Immutable
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.misakamayako.powertrack.model.Ids
import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.RepSpec
import com.misakamayako.powertrack.model.Routine
import com.misakamayako.powertrack.model.RoutineSlot
import com.misakamayako.powertrack.model.SetPrescription
import com.misakamayako.powertrack.model.SetType
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * M2 计划编辑。
 *
 * §4.9 唯一例外：routine 编辑页可以 Store 持有 draft + 显式保存。
 */
interface RoutineEditorStore :
    Store<RoutineEditorStore.Intent, RoutineEditorStore.State, RoutineEditorStore.Label> {

    sealed interface Intent {
        data object Load : Intent
        data object NewRoutine : Intent
        data class OpenRoutine(val routineId: String) : Intent
        data object CloseEditor : Intent
        data class Rename(val name: String) : Intent
        data class AddSlot(val exerciseId: String) : Intent
        data class RemoveSlot(val slotId: String) : Intent
        data class MoveSlot(val slotId: String, val delta: Int) : Intent
        data class AddSet(
            val slotId: String,
            val setType: SetType,
            val load: LoadSpec,
            val reps: RepSpec,
            val restSec: Int?,
        ) : Intent
        data class RemoveSet(val slotId: String, val prescriptionId: String) : Intent
        data object Save : Intent
        data class Archive(val routineId: String) : Intent
        data class Delete(val routineId: String) : Intent
        data class Schedule(val date: LocalDate) : Intent
        data object DismissError : Intent
    }

    @Immutable
    data class State(
        val loading: Boolean = true,
        val routines: PersistentList<Routine> = persistentListOf(),
        val draft: Routine? = null,
        val dirty: Boolean = false,
        val saving: Boolean = false,
        val scheduledDate: LocalDate? = null,
        val message: String? = null,
        val error: String? = null,
    ) {
        val editing: Boolean get() = draft != null
        val totalSets: Int get() = draft?.plannedSetCount ?: 0
    }

    sealed interface Message {
        data object Loading : Message
        data class Loaded(val routines: PersistentList<Routine>) : Message
        data class DraftChanged(val draft: Routine, val dirty: Boolean = true) : Message
        data object EditorClosed : Message
        data class Saving(val saving: Boolean) : Message
        data class Scheduled(val date: LocalDate) : Message
        data class Notify(val text: String) : Message
        data class Failed(val text: String) : Message
        data object ErrorDismissed : Message
    }

    sealed interface Label {
        data class ShowMessage(val text: String) : Label
        data object NavigateBack : Label
    }
}

/**
 * 纯函数 Reducer：所有 draft 编辑都在这里完成，可 JVM 单测。
 */
object RoutineEditorReducer :
    Reducer<RoutineEditorStore.State, RoutineEditorStore.Message> {

    override fun RoutineEditorStore.State.reduce(
        msg: RoutineEditorStore.Message,
    ): RoutineEditorStore.State = when (msg) {
        RoutineEditorStore.Message.Loading -> copy(loading = true, error = null)
        is RoutineEditorStore.Message.Loaded -> copy(loading = false, routines = msg.routines)
        is RoutineEditorStore.Message.DraftChanged -> copy(
            draft = msg.draft,
            dirty = msg.dirty,
            saving = false,
            // 编辑动作发生了说明用户在重试，旧的操作错误不再展示
            error = null,
        )
        RoutineEditorStore.Message.EditorClosed -> copy(draft = null, dirty = false, scheduledDate = null)
        is RoutineEditorStore.Message.Saving -> copy(saving = msg.saving)
        is RoutineEditorStore.Message.Scheduled -> copy(scheduledDate = msg.date, message = "已排期到 ${msg.date}")
        is RoutineEditorStore.Message.Notify -> copy(message = msg.text)
        is RoutineEditorStore.Message.Failed -> copy(loading = false, saving = false, error = msg.text)
        RoutineEditorStore.Message.ErrorDismissed -> copy(error = null, message = null)
    }
}

/** draft 的纯函数编辑器，Reducer 和单测都直接用。 */
object RoutineDraftEditor {

    fun newRoutine(now: Instant): Routine = Routine(
        id = Ids.new(),
        name = "新计划",
        createdAt = now,
        updatedAt = now,
    )

    fun rename(routine: Routine, name: String): Routine = routine.copy(name = name)

    /**
     * 加一个动作槽位。
     *
     * [exerciseName] 必须由调用方从动作库查出来带上：routine 里只有 `exercise_id`，
     * 但编辑中的草稿还没落库，`exerciseName = null` 时列表只能回退显示 raw id
     * （`barbell_bench_press` 这种带下划线的名字），看起来就像"没加进去"。
     */
    fun addSlot(routine: Routine, exerciseId: String, exerciseName: String? = null): Routine {
        val slot = RoutineSlot(
            id = Ids.new(),
            routineId = routine.id,
            orderIndex = routine.slots.size,
            exerciseId = exerciseId,
            exerciseName = exerciseName ?: routine.slots.firstOrNull { it.exerciseId == exerciseId }?.exerciseName,
        )
        return routine.copy(slots = (routine.slots + slot).toPersistentList())
    }

    fun removeSlot(routine: Routine, slotId: String): Routine =
        routine.copy(slots = routine.slots.filterNot { it.id == slotId }.toPersistentList().reindex())

    fun moveSlot(routine: Routine, slotId: String, delta: Int): Routine {
        val index = routine.slots.indexOfFirst { it.id == slotId }
        if (index < 0) return routine
        val target = (index + delta).coerceIn(0, routine.slots.lastIndex)
        if (target == index) return routine
        val list = routine.slots.toMutableList()
        val item = list.removeAt(index)
        list.add(target, item)
        return routine.copy(slots = list.toPersistentList().reindex())
    }

    fun addSet(
        routine: Routine,
        slotId: String,
        setType: SetType,
        load: LoadSpec,
        reps: RepSpec,
        restSec: Int?,
    ): Routine {
        val slot = routine.slots.firstOrNull { it.id == slotId } ?: return routine
        val prescription = SetPrescription(
            id = Ids.new(),
            slotId = slotId,
            orderIndex = slot.prescriptions.size,
            setType = setType,
            isWarmup = setType == SetType.WARMUP,
            loadSpec = load,
            repSpec = reps,
            restSec = restSec,
        )
        val updated = slot.copy(prescriptions = (slot.prescriptions + prescription).toPersistentList())
        return routine.copy(slots = routine.slots.map { if (it.id == slotId) updated else it }.toPersistentList())
    }

    fun removeSet(routine: Routine, slotId: String, prescriptionId: String): Routine {
        val slot = routine.slots.firstOrNull { it.id == slotId } ?: return routine
        val updated = slot.copy(
            prescriptions = slot.prescriptions
                .filterNot { it.id == prescriptionId }
                .toPersistentList()
                .reindexSets(),
        )
        return routine.copy(slots = routine.slots.map { if (it.id == slotId) updated else it }.toPersistentList())
    }

    private fun PersistentList<RoutineSlot>.reindex(): PersistentList<RoutineSlot> =
        mapIndexed { index, slot -> slot.copy(orderIndex = index) }.toPersistentList()

    private fun PersistentList<SetPrescription>.reindexSets(): PersistentList<SetPrescription> =
        mapIndexed { index, prescription -> prescription.copy(orderIndex = index) }.toPersistentList()
}
