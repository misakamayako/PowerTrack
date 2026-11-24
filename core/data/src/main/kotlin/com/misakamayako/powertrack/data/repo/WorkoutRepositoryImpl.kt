package com.misakamayako.powertrack.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.misakamayako.powertrack.db.PowerTrackDatabase
import com.misakamayako.powertrack.data.db.loadRoutine
import com.misakamayako.powertrack.data.db.loadSession
import com.misakamayako.powertrack.data.db.loadSet
import com.misakamayako.powertrack.data.json.SnapshotJson
import com.misakamayako.powertrack.data.json.SpecJson
import com.misakamayako.powertrack.data.mapper.toModel
import com.misakamayako.powertrack.data.mapper.wire
import com.misakamayako.powertrack.domain.PrDetector
import com.misakamayako.powertrack.domain.SettingsRepository
import com.misakamayako.powertrack.domain.SetMetricsCalculator
import com.misakamayako.powertrack.domain.WorkoutRepository
import com.misakamayako.powertrack.model.Ids
import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.PersonalRecord
import com.misakamayako.powertrack.model.RepSpec
import com.misakamayako.powertrack.model.Routine
import com.misakamayako.powertrack.model.ScheduledStatus
import com.misakamayako.powertrack.model.SessionExercise
import com.misakamayako.powertrack.model.SessionFinishKind
import com.misakamayako.powertrack.model.SessionSet
import com.misakamayako.powertrack.model.SessionSetSegment
import com.misakamayako.powertrack.model.SetPrescription
import com.misakamayako.powertrack.model.SetType
import com.misakamayako.powertrack.model.Side
import com.misakamayako.powertrack.model.WorkoutSession
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * §4.9 练中 write-through 的实现：每个写方法都是"先落库，再返回写库后的真实模型"。
 */
class WorkoutRepositoryImpl(
    private val db: PowerTrackDatabase,
    private val settingsRepository: SettingsRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = Clock.System,
) : WorkoutRepository {

    private val queries = db.workoutQueries
    private val scheduleQueries = db.scheduleQueries
    private val metricsQueries = db.metricsQueries

    override fun observeActive(): Flow<WorkoutSession?> =
        queries.selectActive().asFlow().mapToOneOrNull(io)
            .map { row -> row?.let { db.loadSession(it.id) } }

    override fun observeFinished(limit: Int): Flow<PersistentList<WorkoutSession>> =
        queries.selectFinished(limit.toLong()).asFlow().mapToList(io)
            .map { rows -> rows.mapNotNull { db.loadSession(it.id) }.toPersistentList() }

    override fun observePrs(): Flow<PersistentList<PersonalRecord>> =
        metricsQueries.selectAllPrs().asFlow().mapToList(io)
            .map { rows -> rows.map { it.toModel() }.toPersistentList() }

    override suspend fun activeSession(): WorkoutSession? = withContext(io) {
        queries.selectActive().executeAsOneOrNull()?.let { db.loadSession(it.id) }
    }

    override suspend fun session(id: String): WorkoutSession? = withContext(io) { db.loadSession(id) }

    override suspend fun finishedSessions(limit: Int): PersistentList<WorkoutSession> = withContext(io) {
        queries.selectFinished(limit.toLong()).executeAsList()
            .mapNotNull { db.loadSession(it.id) }
            .toPersistentList()
    }

    override suspend fun lastFinished(): WorkoutSession? = withContext(io) {
        queries.selectLastFinished().executeAsOneOrNull()?.let { db.loadSession(it.id) }
    }

    override suspend fun startBlank(): WorkoutSession = withContext(io) {
        val settings = settingsRepository.get()
        val now = clock.now()
        val id = Ids.new()
        queries.insert(
            id = id,
            started_at = now.toEpochMilliseconds(),
            ended_at = null,
            source_scheduled_id = null,
            routine_snapshot = null,
            bodyweight_kg = settings.bodyweightKg,
            session_rpe = null,
            notes = null,
            finish_kind = SessionFinishKind.ACTIVE.wire(),
        )
        db.loadSession(id) ?: error("刚插入的 session 读不回来：$id")
    }

    override suspend fun startFromScheduled(scheduledId: String): WorkoutSession? = withContext(io) {
        val scheduledRow = scheduleQueries.selectById(scheduledId).executeAsOneOrNull() ?: return@withContext null
        // 快照优先；老数据没有快照时回退到计划表
        val snapshot = SnapshotJson.decodeRoutine(scheduledRow.routineSnapshot)
            ?: scheduledRow.routineId?.let { db.loadRoutine(it) }
        val settings = settingsRepository.get()
        val now = clock.now()
        val id = Ids.new()
        db.transaction {
            queries.insert(
                id = id,
                started_at = now.toEpochMilliseconds(),
                ended_at = null,
                source_scheduled_id = scheduledId,
                routine_snapshot = snapshot?.let { SnapshotJson.encodeRoutine(it) },
                bodyweight_kg = settings.bodyweightKg,
                session_rpe = null,
                notes = null,
                finish_kind = SessionFinishKind.ACTIVE.wire(),
            )
            scheduleQueries.updateStatus(ScheduledStatus.STARTED.wire(), scheduledId)
            snapshot?.let { cloneRoutineIntoSession(id, it) }
        }
        db.loadSession(id)
    }

    override suspend fun repeatLast(): WorkoutSession? = withContext(io) {
        val lastRow = queries.selectLastFinished().executeAsOneOrNull() ?: return@withContext null
        val last = db.loadSession(lastRow.id) ?: return@withContext null
        val settings = settingsRepository.get()
        val now = clock.now()
        val id = Ids.new()
        db.transaction {
            queries.insert(
                id = id,
                started_at = now.toEpochMilliseconds(),
                ended_at = null,
                source_scheduled_id = null,
                routine_snapshot = last.routineSnapshot?.let { SnapshotJson.encodeRoutine(it) },
                bodyweight_kg = settings.bodyweightKg ?: last.bodyweightKg,
                session_rpe = null,
                notes = null,
                finish_kind = SessionFinishKind.ACTIVE.wire(),
            )
            last.exercises.sortedBy { it.orderIndex }.forEachIndexed { exerciseIndex, exercise ->
                val sessionExerciseId = Ids.new()
                queries.insertExercise(
                    id = sessionExerciseId,
                    session_id = id,
                    exercise_id = exercise.exerciseId,
                    order_index = exerciseIndex.toLong(),
                    superset_group = exercise.supersetGroup,
                    notes = exercise.notes,
                )
                exercise.sets.sortedBy { it.orderIndex }.forEachIndexed { setIndex, set ->
                    val setId = Ids.new()
                    queries.insertSet(
                        id = setId,
                        session_exercise_id = sessionExerciseId,
                        order_index = setIndex.toLong(),
                        set_type = set.setType.wire(),
                        is_warmup = if (set.isWarmup) 1L else 0L,
                        prescribed_json = set.prescribed?.let { SnapshotJson.encodePrescription(it) },
                        rest_before_sec = set.restBeforeSec?.toLong(),
                        rpe = set.rpe,
                        notes = set.notes,
                    )
                    set.segments.sortedBy { it.orderIndex }.forEachIndexed { segmentIndex, segment ->
                        insertSegment(
                            setId = setId,
                            orderIndex = segmentIndex,
                            load = segment.load,
                            reps = segment.reps,
                            isFailure = segment.isFailure,
                            partialReps = segment.partialReps,
                            side = segment.side,
                            tempo = segment.tempo,
                            holdMs = segment.holdMs,
                            offsetMs = segment.offsetMs,
                            bodyweightKg = last.bodyweightKg,
                        )
                    }
                }
            }
        }
        db.loadSession(id)
    }

    override suspend fun addExercise(sessionId: String, exerciseId: String): SessionExercise = withContext(io) {
        val nextOrder = queries.selectExercises(sessionId).executeAsList().size
        val id = Ids.new()
        queries.insertExercise(
            id = id,
            session_id = sessionId,
            exercise_id = exerciseId,
            order_index = nextOrder.toLong(),
            superset_group = null,
            notes = null,
        )
        db.loadSession(sessionId)?.exercises?.firstOrNull { it.id == id }
            ?: error("刚插入的 session_exercise 读不回来：$id")
    }

    override suspend fun addSet(
        sessionExerciseId: String,
        setType: SetType,
        prescribed: SetPrescription?,
        restBeforeSec: Int?,
        load: LoadSpec,
        reps: RepSpec,
        isWarmup: Boolean,
    ): SessionSet = withContext(io) {
        val nextOrder = queries.maxSetOrder(sessionExerciseId).executeAsOne() + 1
        val bodyweight = bodyweightOfSessionOf(sessionExerciseId)
        val id = Ids.new()
        db.transaction {
            queries.insertSet(
                id = id,
                session_exercise_id = sessionExerciseId,
                order_index = nextOrder,
                set_type = setType.wire(),
                is_warmup = if (isWarmup) 1L else 0L,
                prescribed_json = prescribed?.let { SnapshotJson.encodePrescription(it) },
                rest_before_sec = restBeforeSec?.toLong(),
                rpe = null,
                notes = null,
            )
            insertSegment(
                setId = id,
                orderIndex = 0,
                load = load,
                reps = reps,
                isFailure = false,
                partialReps = 0,
                side = Side.BOTH,
                tempo = null,
                holdMs = null,
                offsetMs = 0,
                bodyweightKg = bodyweight,
            )
        }
        db.loadSet(id) ?: error("刚插入的 session_set 读不回来：$id")
    }

    override suspend fun appendSegment(
        setId: String,
        load: LoadSpec,
        reps: RepSpec,
        isFailure: Boolean,
        partialReps: Int,
        side: Side,
        offsetMs: Long,
    ): SessionSetSegment = withContext(io) {
        val bodyweight = bodyweightOfSet(setId)
        val nextOrder = queries.maxSegmentOrder(setId).executeAsOne() + 1
        val segmentId = Ids.new()
        db.transaction {
            insertSegment(
                setId = setId,
                orderIndex = nextOrder.toInt(),
                load = load,
                reps = reps,
                isFailure = isFailure,
                partialReps = partialReps,
                side = side,
                tempo = null,
                holdMs = null,
                offsetMs = offsetMs,
                bodyweightKg = bodyweight,
                forcedId = segmentId,
            )
        }
        queries.selectSegmentById(segmentId).executeAsOne().toModel()
    }

    override suspend fun updateSegment(
        segmentId: String,
        load: LoadSpec?,
        reps: RepSpec?,
        isFailure: Boolean?,
    ): SessionSetSegment? = withContext(io) {
        val existing = queries.selectSegmentById(segmentId).executeAsOneOrNull() ?: return@withContext null
        val newLoad = load ?: SpecJson.decodeLoad(existing.load_json) ?: return@withContext null
        val newReps = reps ?: SpecJson.decodeReps(existing.rep_json) ?: return@withContext null
        val newFailure = isFailure ?: (existing.is_failure != 0L)
        val metrics = SetMetricsCalculator.compute(
            listOf(
                SessionSetSegment(
                    id = segmentId,
                    sessionSetId = existing.session_set_id,
                    orderIndex = existing.order_index.toInt(),
                    load = newLoad,
                    reps = newReps,
                    isFailure = newFailure,
                    partialReps = existing.partial_reps.toInt(),
                ),
            ),
            bodyweightOfSet(existing.session_set_id),
        )
        queries.updateSegment(
            load_json = SpecJson.encodeLoad(newLoad),
            rep_json = SpecJson.encodeReps(newReps),
            is_failure = if (newFailure) 1L else 0L,
            partial_reps = existing.partial_reps,
            resolved_load_kg = metrics.resolvedLoadKg,
            resolved_reps = metrics.resolvedReps?.toLong(),
            e1rm = metrics.e1rm,
            id = segmentId,
        )
        queries.selectSegmentById(segmentId).executeAsOneOrNull()?.toModel()
    }

    override suspend fun updateSet(setId: String, rpe: Double?, notes: String?, restBeforeSec: Int?): Unit =
        withContext(io) {
            val existing = queries.selectSetById(setId).executeAsOneOrNull() ?: return@withContext
            queries.updateSet(
                rpe = rpe ?: existing.rpe,
                notes = notes ?: existing.notes,
                rest_before_sec = restBeforeSec?.toLong() ?: existing.rest_before_sec,
                id = setId,
            )
        }

    override suspend fun deleteSet(setId: String): Unit = withContext(io) {
        queries.deleteSet(setId)
    }

    override suspend fun deleteExercise(sessionExerciseId: String): Unit = withContext(io) {
        queries.deleteExercise(sessionExerciseId)
    }

    override suspend fun insertSet(sessionExerciseId: String, set: SessionSet, index: Int): Unit = withContext(io) {
        db.transaction {
            queries.insertSet(
                id = set.id,
                session_exercise_id = sessionExerciseId,
                order_index = index.toLong(),
                set_type = set.setType.wire(),
                is_warmup = if (set.isWarmup) 1L else 0L,
                prescribed_json = set.prescribed?.let { SnapshotJson.encodePrescription(it) },
                rest_before_sec = set.restBeforeSec?.toLong(),
                rpe = set.rpe,
                notes = set.notes,
            )
            set.segments.sortedBy { it.orderIndex }.forEach { segment ->
                insertSegment(
                    setId = set.id,
                    orderIndex = segment.orderIndex,
                    load = segment.load,
                    reps = segment.reps,
                    isFailure = segment.isFailure,
                    partialReps = segment.partialReps,
                    side = segment.side,
                    tempo = segment.tempo,
                    holdMs = segment.holdMs,
                    offsetMs = segment.offsetMs,
                    bodyweightKg = bodyweightOfSet(set.id),
                    forcedId = segment.id,
                )
            }
        }
    }

    override suspend fun finish(sessionId: String): WorkoutSession? = withContext(io) {
        val before = db.loadSession(sessionId) ?: return@withContext null
        val now = clock.now()
        val finished = before.copy(endedAt = now, finishKind = SessionFinishKind.FINISHED)
        val existing = metricsQueries.selectAllPrs().executeAsList().map { it.toModel() }
        val improved = PrDetector.improved(PrDetector.candidates(finished), existing)

        db.transaction {
            queries.updateFinish(
                ended_at = now.toEpochMilliseconds(),
                finish_kind = SessionFinishKind.FINISHED.wire(),
                session_rpe = finished.sessionRpe,
                notes = finished.notes,
                id = sessionId,
            )
            // 与 session 结束在同一事务里回填排期状态（§2.1 铁律 3）
            before.sourceScheduledId?.let { scheduledId ->
                scheduleQueries.updateStatus(ScheduledStatus.COMPLETED.wire(), scheduledId)
            }
            improved.forEach { pr ->
                metricsQueries.upsertPr(
                    id = pr.id,
                    exercise_id = pr.exerciseId,
                    kind = pr.kind.wire(),
                    value_ = pr.value,
                    unit = pr.unit,
                    session_set_id = pr.sessionSetId,
                    achieved_at = pr.achievedAt.toEpochMilliseconds(),
                )
            }
        }
        db.loadSession(sessionId)
    }

    override suspend fun discard(sessionId: String): Unit = withContext(io) {
        val session = db.loadSession(sessionId)
        db.transaction {
            queries.deleteById(sessionId)
            session?.sourceScheduledId?.let { scheduledId ->
                scheduleQueries.updateStatus(ScheduledStatus.PLANNED.wire(), scheduledId)
            }
        }
    }

    override suspend fun lastSetOf(exerciseId: String, setType: SetType): SessionSet? = withContext(io) {
        val row = queries.selectLastSetOf(exerciseId, setType.wire()).executeAsOneOrNull()
            ?: return@withContext null
        db.loadSet(row.id)
    }

    override suspend fun bestE1rm(exerciseId: String): Double? = withContext(io) {
        queries.selectBestE1rm(exerciseId).executeAsOneOrNull()?.MAX
    }

    // ---------- 内部辅助（全部同步，只允许在事务里调用） ----------

    private fun cloneRoutineIntoSession(sessionId: String, routine: Routine) {
        routine.slots.sortedBy { it.orderIndex }.forEachIndexed { slotIndex, slot ->
            val sessionExerciseId = Ids.new()
            queries.insertExercise(
                id = sessionExerciseId,
                session_id = sessionId,
                exercise_id = slot.exerciseId,
                order_index = slotIndex.toLong(),
                superset_group = slot.supersetGroup,
                notes = slot.notes,
            )
            slot.prescriptions.sortedBy { it.orderIndex }.forEachIndexed { setIndex, prescription ->
                queries.insertSet(
                    id = Ids.new(),
                    session_exercise_id = sessionExerciseId,
                    order_index = setIndex.toLong(),
                    set_type = prescription.setType.wire(),
                    is_warmup = if (prescription.isWarmup) 1L else 0L,
                    prescribed_json = SnapshotJson.encodePrescription(prescription),
                    rest_before_sec = prescription.restSec?.toLong(),
                    rpe = prescription.rpe,
                    notes = null,
                )
            }
        }
    }

    /** 每段自己算自己的冗余值（§3.4：逐段配对，绝不跨段组合）。 */
    private fun insertSegment(
        setId: String,
        orderIndex: Int,
        load: LoadSpec,
        reps: RepSpec,
        isFailure: Boolean,
        partialReps: Int,
        side: Side,
        tempo: String?,
        holdMs: Long?,
        offsetMs: Long,
        bodyweightKg: Double?,
        forcedId: String? = null,
    ): String {
        val segment = SessionSetSegment(
            id = forcedId ?: Ids.new(),
            sessionSetId = setId,
            orderIndex = orderIndex,
            load = load,
            reps = reps,
            isFailure = isFailure,
            partialReps = partialReps,
            side = side,
            tempo = tempo,
            holdMs = holdMs,
            offsetMs = offsetMs,
        )
        val metrics = SetMetricsCalculator.compute(listOf(segment), bodyweightKg)
        queries.insertSegment(
            id = segment.id,
            session_set_id = setId,
            order_index = orderIndex.toLong(),
            load_json = SpecJson.encodeLoad(load),
            rep_json = SpecJson.encodeReps(reps),
            is_failure = if (isFailure) 1L else 0L,
            partial_reps = partialReps.toLong(),
            side = side.wire(),
            tempo = tempo,
            hold_ms = holdMs,
            offset_ms = offsetMs,
            resolved_load_kg = metrics.resolvedLoadKg,
            resolved_reps = metrics.resolvedReps?.toLong(),
            e1rm = metrics.e1rm,
        )
        return segment.id
    }

    private fun bodyweightOfSessionOf(sessionExerciseId: String): Double? {
        val sessionId = queries.selectSessionIdForExercise(sessionExerciseId).executeAsOneOrNull() ?: return null
        return queries.selectById(sessionId).executeAsOneOrNull()?.bodyweight_kg
    }

    private fun bodyweightOfSet(setId: String): Double? {
        val row = queries.selectSetById(setId).executeAsOneOrNull() ?: return null
        val sessionExerciseId = row.session_exercise_id
        return bodyweightOfSessionOf(sessionExerciseId)
    }
}
