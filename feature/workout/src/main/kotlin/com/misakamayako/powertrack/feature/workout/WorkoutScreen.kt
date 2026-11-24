package com.misakamayako.powertrack.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.misakamayako.powertrack.core.navigation.WorkoutStep
import com.misakamayako.powertrack.core.ui.StoreFactoryProvider
import com.misakamayako.powertrack.core.ui.collectLabels
import com.misakamayako.powertrack.core.ui.collectState
import com.misakamayako.powertrack.core.ui.format.Labels
import com.misakamayako.powertrack.core.ui.rememberManagedStore
import com.misakamayako.powertrack.designsystem.component.CountStepper
import com.misakamayako.powertrack.designsystem.component.EmptyState
import com.misakamayako.powertrack.designsystem.component.LoadingBox
import com.misakamayako.powertrack.designsystem.component.PtCard
import com.misakamayako.powertrack.designsystem.component.PtChip
import com.misakamayako.powertrack.designsystem.component.SecondaryButton
import com.misakamayako.powertrack.designsystem.component.StatTile
import com.misakamayako.powertrack.designsystem.component.StatusPill
import com.misakamayako.powertrack.designsystem.component.ThumbZoneButton
import com.misakamayako.powertrack.designsystem.component.WeightStepper
import com.misakamayako.powertrack.designsystem.component.formatNumber
import com.misakamayako.powertrack.designsystem.theme.PtSpacing
import com.misakamayako.powertrack.domain.SetMetricsCalculator
import com.misakamayako.powertrack.domain.SettingsRepository
import com.misakamayako.powertrack.domain.WorkoutRepository
import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.RepSpec
import com.misakamayako.powertrack.model.SessionExercise
import com.misakamayako.powertrack.model.SessionSet
import com.misakamayako.powertrack.model.SetType
import com.misakamayako.powertrack.model.WorkoutSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.time.Clock

/**
 * M3 练中流程：单 Key + 嵌套 NavDisplay（§4.6）。
 *
 * 外层只占一个 back stack entry，内部步骤在 `WorkoutStep` 里维护。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    sessionId: String,
    innerBackStack: MutableList<WorkoutStep>,
    onExit: () -> Unit,
    onRequestExercisePick: (callback: (String) -> Unit) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val storeFactory: StoreFactory = StoreFactoryProvider.default
    val workoutRepository: WorkoutRepository = koinInject()
    val settingsRepository: SettingsRepository = koinInject()
    val store = rememberManagedStore(sessionId) {
        createWorkoutStore(storeFactory, workoutRepository, settingsRepository)
    }
    val state by store.collectState()

    // 只在这个 Store 的生命周期里 Load 一次：Store 挂在 NavEntry 的 ViewModel 上，
    // 去选动作再回来不会重建，也不该把刚加进去的动作用旧快照覆盖掉（D25）。
    LaunchedEffect(store) { store.accept(WorkoutStore.Intent.Load(sessionId)) }

    store.collectLabels { label ->
        when (label) {
            is WorkoutStore.Label.Navigate -> {
                if (innerBackStack.isEmpty()) innerBackStack.add(WorkoutStep.ExerciseList)
                if (label.step is WorkoutStep.Summary) {
                    innerBackStack.clear()
                    innerBackStack.add(WorkoutStep.Summary)
                } else if (innerBackStack.lastOrNull() != label.step) {
                    innerBackStack.add(label.step)
                }
            }
            is WorkoutStore.Label.ShowMessage -> onMessage(label.text)
            // 丢弃已落库，这时退出才是安全的（D26）
            WorkoutStore.Label.Discarded -> onExit()
            is WorkoutStore.Label.Finished -> Unit
        }
    }

    val step = innerBackStack.lastOrNull() ?: WorkoutStep.ExerciseList
    val session = state.session

    // 训练页以前只在 session == null 时显示错误，写操作失败（记录/丢弃/撤销）是静默的
    val error = state.error
    LaunchedEffect(error) {
        if (error != null && session != null) {
            onMessage(error)
            store.accept(WorkoutStore.Intent.ClearError)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (step) {
                            WorkoutStep.ExerciseList -> "训练中"
                            is WorkoutStep.Record -> session?.exercises
                                ?.firstOrNull { it.id == step.sessionExerciseId }
                                ?.exerciseName ?: "记录"
                            WorkoutStep.Summary -> "训练总结"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when {
                                step is WorkoutStep.Record -> innerBackStack.removeLastOrNull()
                                innerBackStack.size > 1 -> innerBackStack.removeLastOrNull()
                                else -> onExit()
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.canUndo) {
                        TextButton(onClick = { store.accept(WorkoutStore.Intent.Undo) }) { Text("撤销") }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading && session == null -> LoadingBox()
                session == null -> EmptyState(
                    text = state.error ?: "训练不存在",
                    hint = "回首页重新开一场",
                )
                step == WorkoutStep.Summary -> SummaryContent(
                    summary = state.summary,
                    onDone = onExit,
                )
                step is WorkoutStep.Record -> RecordContent(
                    store = store,
                    state = state,
                    sessionExerciseId = step.sessionExerciseId,
                    onBack = { innerBackStack.removeLastOrNull() },
                )
                else -> ExerciseListContent(
                    store = store,
                    state = state,
                    session = session,
                    onOpenExercise = { id -> innerBackStack.add(WorkoutStep.Record(id)) },
                    onRequestExercisePick = onRequestExercisePick,
                )
            }

            // 休息计时：顶部一条横幅，不压住记录页底部的重量/次数输入与「完成本组」
            state.rest?.let { rest ->
                RestOverlay(
                    rest = rest,
                    onSkip = { store.accept(WorkoutStore.Intent.StopRest) },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }        }
    }
}

@Composable
private fun ExerciseListContent(
    store: WorkoutStore,
    state: WorkoutStore.State,
    session: WorkoutSession,
    onOpenExercise: (String) -> Unit,
    onRequestExercisePick: (callback: (String) -> Unit) -> Unit,
) {
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(PtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PtSpacing.sm),
        ) {
            item {
                PtCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        StatTile(label = "动作", value = "${session.exercises.size}")
                        StatTile(label = "组数", value = "${session.completedSetCount}")
                        StatTile(label = "吨位", value = Labels.tonnage(tonnageOf(session)))
                        StatTile(label = "时长", value = Labels.duration(session.duration))
                    }
                }
            }

            if (session.exercises.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(PtSpacing.sm)) {
                        EmptyState(
                            text = "还没有动作",
                            hint = "先添加一个动作，再记录第一组",
                        )
                        SecondaryButton(
                            text = "添加动作",
                            onClick = {
                                onRequestExercisePick { exerciseId ->
                                    store.accept(WorkoutStore.Intent.AddExercise(exerciseId))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            items(session.exercises, key = { it.id }) { exercise ->
                PtCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = exercise.exerciseName ?: exercise.exerciseId,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${exercise.sets.size} 组",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onOpenExercise(exercise.id) }) { Text("记录") }
                    }
                }
            }

            // 空列表时只在 EmptyState 里给一个入口，避免出现两个「添加动作」
            if (session.exercises.isNotEmpty()) {
                item {
                    SecondaryButton(
                        text = "添加动作",
                        onClick = {
                            onRequestExercisePick { exerciseId ->
                                store.accept(WorkoutStore.Intent.AddExercise(exerciseId))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(PtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PtSpacing.sm),
        ) {
            ThumbZoneButton(
                text = "结束并保存这次训练",
                onClick = { confirmFinish = true },
            )
            TextButton(
                onClick = { confirmDiscard = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("丢弃这次训练") }
        }
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("结束训练？") },
            text = { Text("会写入结束时间、计算吨位并检测 PR。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmFinish = false
                        store.accept(WorkoutStore.Intent.Finish)
                    },
                ) { Text("结束") }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("再练会儿") } },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("丢弃这次训练？") },
            text = { Text("本次记录会被硬删除，无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        // 只发 Intent，等 Discarded Label 再退出（D26）
                        store.accept(WorkoutStore.Intent.Discard)
                    },
                ) { Text("丢弃") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun RecordContent(
    store: WorkoutStore,
    state: WorkoutStore.State,
    sessionExerciseId: String,
    onBack: () -> Unit,
) {
    val exercise = state.session?.exercises?.firstOrNull { it.id == sessionExerciseId }
    val haptics = LocalHapticFeedback.current
    var input by remember { mutableStateOf(RecordInput()) }
    var confirmDeleteSet by remember { mutableStateOf<SessionSet?>(null) }

    if (exercise == null) {
        EmptyState(text = "这个动作已被删除")
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(PtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PtSpacing.sm),
        ) {
            items(exercise.sets, key = { it.id }) { set ->
                PtCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Labels.segments(set.segments),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(PtSpacing.xs)) {
                                StatusPill(text = Labels.setType(set.setType))
                                if (set.isWarmup) StatusPill(text = "热身")
                                set.segments.count { it.isFailure }.let { failures ->
                                    if (failures > 0) StatusPill(text = "力竭")
                                }
                            }
                        }
                        IconButton(onClick = { confirmDeleteSet = set }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除这一组")
                        }
                    }
                    val dropLoad = dropLoadOf(set.segments.lastOrNull()?.load)
                    if (dropLoad != null) {
                        SecondaryButton(
                            text = "DROP → ${Labels.load(dropLoad)}",
                            onClick = {
                                store.accept(
                                    WorkoutStore.Intent.AppendDropSegment(
                                        setId = set.id,
                                        load = dropLoad,
                                        reps = RepSpec.Fixed(8),
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(PtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PtSpacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(PtSpacing.xs)) {
                listOf(SetType.NORMAL, SetType.DROP, SetType.AMRAP, SetType.FAILURE).forEach { type ->
                    PtChip(
                        text = Labels.setType(type),
                        selected = input.setType == type,
                        onClick = { input = input.copy(setType = type) },
                    )
                }
            }
            WeightStepper(
                valueKg = input.loadKg,
                onValueChange = { input = input.copy(loadKg = it) },
            )
            CountStepper(
                value = input.reps,
                onValueChange = { input = input.copy(reps = it) },
                label = "次",
                min = 0,
            )
            ThumbZoneButton(
                text = "完成本组",
                onClick = {
                    store.accept(
                        WorkoutStore.Intent.AddSet(
                            sessionExerciseId = sessionExerciseId,
                            setType = input.setType,
                            load = LoadSpec.Weight(input.loadKg),
                            reps = RepSpec.Fixed(input.reps),
                            restBeforeSec = null,
                        ),
                    )
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
            )
        }
    }

    confirmDeleteSet?.let { set ->
        AlertDialog(
            onDismissRequest = { confirmDeleteSet = null },
            title = { Text("删除这一组？") },
            text = { Text("删掉之后可以在顶部「撤销」。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteSet = null
                        store.accept(WorkoutStore.Intent.DeleteSet(set.id))
                    },
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteSet = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SummaryContent(summary: WorkoutStore.Summary?, onDone: () -> Unit) {
    if (summary == null) {
        EmptyState(text = "还没有总结", hint = "先结束训练")
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(PtSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(PtSpacing.md),
    ) {
        PtCard {
            Text("这次训练", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(PtSpacing.xl)) {
                StatTile(label = "动作", value = "${summary.exerciseCount}")
                StatTile(label = "组数", value = "${summary.setCount}")
                StatTile(label = "吨位", value = Labels.tonnage(summary.tonnageKg))
                StatTile(label = "时长", value = Labels.millisToClock(summary.durationMs))
            }
        }
        if (summary.prs.isNotEmpty()) {
            PtCard {
                Text("新纪录", style = MaterialTheme.typography.titleMedium)
                summary.prs.forEach { pr -> Text("🏆 $pr", style = MaterialTheme.typography.bodyLarge) }
            }
        }
        ThumbZoneButton(text = "完成", onClick = onDone)
    }
}

/** 休息计时横幅：每 100ms 只更新本地 State（§4.12）。 */
@Composable
private fun RestOverlay(
    rest: WorkoutStore.RestState,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var remaining by remember(rest) { mutableLongStateOf(rest.durationMs) }
    LaunchedEffect(rest) {
        while (true) {
            val elapsed = Clock.System.now().toEpochMilliseconds() - rest.startedAtMillis
            remaining = (rest.durationMs - elapsed).coerceAtLeast(0)
            if (remaining == 0L) break
            delay(100)
        }
        // 计时归零就自动收起，否则会一直停在 0:00 挡住页面
        onSkip()
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = PtSpacing.lg, vertical = PtSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PtSpacing.md),
            ) {
                Text("组间休息", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = "%d:%02d".format(remaining / 60000, (remaining % 60000) / 1000),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSkip) { Text("跳过") }
            }
            LinearProgressIndicator(
                progress = { if (rest.durationMs <= 0) 0f else remaining.toFloat() / rest.durationMs },
                modifier = Modifier.fillMaxWidth().height(4.dp),
            )
        }
    }
}

/** 供列表页复用的组数统计。 */
fun SessionExercise.setCount(): Int = sets.size

/** 计算某组的总吨位（展示用）。 */
fun SessionSet.tonnage(): Double =
    SetMetricsCalculator.compute(segments, bodyweightKg = null).tonnage ?: 0.0
