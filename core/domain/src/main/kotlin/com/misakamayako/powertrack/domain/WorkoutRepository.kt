package com.misakamayako.powertrack.domain

import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.PersonalRecord
import com.misakamayako.powertrack.model.RepSpec
import com.misakamayako.powertrack.model.Routine
import com.misakamayako.powertrack.model.SessionExercise
import com.misakamayako.powertrack.model.SessionSet
import com.misakamayako.powertrack.model.SessionSetSegment
import com.misakamayako.powertrack.model.SetPrescription
import com.misakamayako.powertrack.model.SetType
import com.misakamayako.powertrack.model.Side
import com.misakamayako.powertrack.model.WorkoutSession
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.flow.Flow

/**
 * 实际训练仓储（§4.9 练中 write-through）。
 *
 * 所有写操作**先落库再返回**，调用方拿到返回值后再更新 UI。
 * 返回的模型一定是写库后的真实状态（含重算过的冗余列）。
 */
interface WorkoutRepository {

    fun observeActive(): Flow<WorkoutSession?>

    fun observeFinished(limit: Int = 20): Flow<PersistentList<WorkoutSession>>

    fun observePrs(): Flow<PersistentList<PersonalRecord>>

    suspend fun activeSession(): WorkoutSession?

    suspend fun session(id: String): WorkoutSession?

    suspend fun finishedSessions(limit: Int = 20): PersistentList<WorkoutSession>

    suspend fun lastFinished(): WorkoutSession?

    /** 空白开始：`sourceScheduledId = null`，不需要占位计划（铁律 1）。 */
    suspend fun startBlank(): WorkoutSession

    /** 从排期开始：写 `routineSnapshot`，并把排期置为 STARTED。 */
    suspend fun startFromScheduled(scheduledId: String): WorkoutSession?

    /** 重复上次：快照上一次的 routine，但不关联任何排期。 */
    suspend fun repeatLast(): WorkoutSession?

    suspend fun addExercise(sessionId: String, exerciseId: String): SessionExercise

    suspend fun addSet(
        sessionExerciseId: String,
        setType: SetType = SetType.NORMAL,
        prescribed: SetPrescription? = null,
        restBeforeSec: Int? = null,
        load: LoadSpec,
        reps: RepSpec,
        isWarmup: Boolean = false,
    ): SessionSet

    /** 追加一段（递减 / 中途减重 / 单侧补齐），并重算整组冗余列。 */
    suspend fun appendSegment(
        setId: String,
        load: LoadSpec,
        reps: RepSpec,
        isFailure: Boolean = false,
        partialReps: Int = 0,
        side: Side = Side.BOTH,
        offsetMs: Long = 0,
    ): SessionSetSegment

    suspend fun updateSegment(
        segmentId: String,
        load: LoadSpec? = null,
        reps: RepSpec? = null,
        isFailure: Boolean? = null,
    ): SessionSetSegment?

    suspend fun updateSet(setId: String, rpe: Double? = null, notes: String? = null, restBeforeSec: Int? = null)

    suspend fun deleteSet(setId: String)

    suspend fun deleteExercise(sessionExerciseId: String)

    /** Undo 用：按原 id 重新插入一组（含 segments）。 */
    suspend fun insertSet(sessionExerciseId: String, set: SessionSet, index: Int)

    suspend fun finish(sessionId: String): WorkoutSession?

    /** Discard：硬删本次 session 及其所有子记录（§2.6）。 */
    suspend fun discard(sessionId: String)

    /** 同动作、同 setType 的上一次实际组，用于默认值预填（§6）。 */
    suspend fun lastSetOf(exerciseId: String, setType: SetType): SessionSet?

    /** 历史最高 e1RM，用于 %1RM 解析与"估算"标注。 */
    suspend fun bestE1rm(exerciseId: String): Double?
}
