package com.misakamayako.powertrack.data

import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.RepSpec
import com.misakamayako.powertrack.model.Routine
import com.misakamayako.powertrack.model.RoutineSlot
import com.misakamayako.powertrack.model.SetPrescription
import com.misakamayako.powertrack.model.SetType
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Instant

/** 计划侧的测试夹具：卧推 4×5 @80% + 侧平举 3×15。 */
object TestRoutines {

    fun pushDay(
        id: String = "routine-push",
        now: Instant = Instant.fromEpochMilliseconds(1_760_000_000_000L),
    ): Routine {
        val benchSlotId = "slot-bench"
        val lateralSlotId = "slot-lateral"
        return Routine(
            id = id,
            name = "推 A",
            notes = "胸肩三头",
            tags = persistentListOf("推", "上肢"),
            createdAt = now,
            updatedAt = now,
            slots = persistentListOf(
                RoutineSlot(
                    id = benchSlotId,
                    routineId = id,
                    orderIndex = 0,
                    exerciseId = "barbell_bench_press",
                    restSec = 150,
                    prescriptions = persistentListOf(
                        prescription("p-bench-1", benchSlotId, 0, LoadSpec.PercentOf1RM(80.0), RepSpec.Fixed(5)),
                        prescription("p-bench-2", benchSlotId, 1, LoadSpec.PercentOf1RM(80.0), RepSpec.Fixed(5)),
                        prescription("p-bench-3", benchSlotId, 2, LoadSpec.PercentOf1RM(80.0), RepSpec.Fixed(5)),
                        prescription("p-bench-4", benchSlotId, 3, LoadSpec.PercentOf1RM(80.0), RepSpec.Fixed(5)),
                    ),
                ),
                RoutineSlot(
                    id = lateralSlotId,
                    routineId = id,
                    orderIndex = 1,
                    exerciseId = "dumbbell_lateral_raise",
                    restSec = 60,
                    prescriptions = persistentListOf(
                        prescription("p-lat-1", lateralSlotId, 0, LoadSpec.Weight(10.0), RepSpec.Fixed(15)),
                        prescription("p-lat-2", lateralSlotId, 1, LoadSpec.Weight(10.0), RepSpec.Fixed(15)),
                        prescription("p-lat-3", lateralSlotId, 2, LoadSpec.Weight(10.0), RepSpec.Fixed(15)),
                    ),
                ),
            ),
        )
    }

    fun prescription(
        id: String,
        slotId: String,
        orderIndex: Int,
        load: LoadSpec,
        reps: RepSpec,
        setType: SetType = SetType.NORMAL,
    ): SetPrescription = SetPrescription(
        id = id,
        slotId = slotId,
        orderIndex = orderIndex,
        setType = setType,
        loadSpec = load,
        repSpec = reps,
        restSec = 120,
        rpe = 8.0,
    )
}
