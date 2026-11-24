package com.misakamayako.powertrack.feature.routineeditor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.misakamayako.powertrack.core.ui.StoreFactoryProvider
import com.misakamayako.powertrack.core.ui.collectLabels
import com.misakamayako.powertrack.core.ui.collectState
import com.misakamayako.powertrack.core.ui.format.Labels
import com.misakamayako.powertrack.core.ui.rememberManagedStore
import com.misakamayako.powertrack.designsystem.component.CountStepper
import com.misakamayako.powertrack.designsystem.component.EmptyState
import com.misakamayako.powertrack.designsystem.component.PtCard
import com.misakamayako.powertrack.designsystem.component.PtChip
import com.misakamayako.powertrack.designsystem.component.SecondaryButton
import com.misakamayako.powertrack.designsystem.component.StatusPill
import com.misakamayako.powertrack.designsystem.component.ThumbZoneButton
import com.misakamayako.powertrack.designsystem.component.WeightStepper
import com.misakamayako.powertrack.designsystem.theme.PtSpacing
import com.misakamayako.powertrack.domain.ExerciseRepository
import com.misakamayako.powertrack.domain.RoutineRepository
import com.misakamayako.powertrack.domain.ScheduleRepository
import com.misakamayako.powertrack.domain.TrainingDate
import com.misakamayako.powertrack.model.LoadSpec
import com.misakamayako.powertrack.model.RepSpec
import com.misakamayako.powertrack.model.Routine
import com.misakamayako.powertrack.model.SetPrescription
import com.misakamayako.powertrack.model.SetType
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.koin.compose.koinInject
import kotlin.time.Clock

@Composable
private fun rememberRoutineEditorStore(): RoutineEditorStore {
    val storeFactory: StoreFactory = StoreFactoryProvider.default
    val routineRepository: RoutineRepository = koinInject()
    val scheduleRepository: ScheduleRepository = koinInject()
    val exerciseRepository: ExerciseRepository = koinInject()
    return rememberManagedStore("routine-editor") {
        createRoutineEditorStore(
            storeFactory = storeFactory,
            routineRepository = routineRepository,
            scheduleRepository = scheduleRepository,
            exerciseRepository = exerciseRepository,
        )
    }
}

/**
 * M2 计划列表。
 */
@Composable
fun RoutineListScreen(
    onCreate: () -> Unit,
    onOpenRoutine: (String) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val store = rememberRoutineEditorStore()
    val state by store.collectState()

    LaunchedEffect(Unit) { store.accept(RoutineEditorStore.Intent.Load) }
    store.collectLabels { label ->
        when (label) {
            is RoutineEditorStore.Label.ShowMessage -> onMessage(label.text)
            RoutineEditorStore.Label.NavigateBack -> Unit
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(PtSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(PtSpacing.md),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("训练计划", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onCreate) { Text("新建") }
            }
        }

        if (state.routines.isEmpty() && !state.loading) {
            item { EmptyState(text = "还没有计划", hint = "建一个「推 A」，把动作和组数填进去") }
        }

        items(state.routines, key = { it.id }) { routine ->
            PtCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(routine.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${routine.slots.size} 个动作 · ${routine.plannedSetCount} 组",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                routine.slots.take(3).forEach { slot ->
                    Text(
                        text = "${slot.exerciseName ?: slot.exerciseId} · ${slot.prescriptions.size} 组",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(PtSpacing.sm)) {
                    SecondaryButton(
                        text = "编辑",
                        onClick = { onOpenRoutine(routine.id) },
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "归档",
                        onClick = { store.accept(RoutineEditorStore.Intent.Archive(routine.id)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            ThumbZoneButton(
                text = "新建计划",
                onClick = onCreate,
            )
        }
    }
}

/**
 * M2 计划编辑器：槽位排序 + prescription 编辑 + 排期。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    routineId: String?,
    onBack: () -> Unit,
    onRequestExercisePick: (callback: (String) -> Unit) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val store = rememberRoutineEditorStore()
    val state by store.collectState()
    var addSetForSlot by remember { mutableStateOf<String?>(null) }
    var showSchedule by remember { mutableStateOf(false) }

    // 只在 Store 首次建立时按 routineId 打开：从动作库（picker）返回时此 entry 重新进入组合，
    // 若在这里再 OpenRoutine 一次，会把刚通过 picker 加进去的草稿槽位用库里的旧值覆盖掉（D25）。
    LaunchedEffect(store, routineId) {
        if (routineId == null) {
            store.accept(RoutineEditorStore.Intent.NewRoutine)
        } else {
            store.accept(RoutineEditorStore.Intent.OpenRoutine(routineId))
        }
    }
    store.collectLabels { label ->
        when (label) {
            is RoutineEditorStore.Label.ShowMessage -> onMessage(label.text)
            RoutineEditorStore.Label.NavigateBack -> onBack()
        }
    }

    val draft = state.draft
    if (draft == null) {
        EmptyState(text = "正在准备编辑器…", modifier = modifier)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (routineId == null) "新建计划" else "编辑计划") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 保存只留这一处（底部那个大按钮已删除，避免两个保存入口）
                    TextButton(
                        onClick = { store.accept(RoutineEditorStore.Intent.Save) },
                        enabled = !state.saving,
                    ) {
                        Text(if (state.saving) "保存中…" else "保存")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(PtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PtSpacing.md),
        ) {
            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { store.accept(RoutineEditorStore.Intent.Rename(it)) },
                    label = { Text("计划名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 排期结果必须落在页面上：只弹一个 Snackbar 的话，回到这一页什么都看不到
            state.scheduledDate?.let { scheduled ->
                item {
                    PtCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("已排期", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${Labels.date(scheduled)} ${Labels.weekday(scheduled)} · 首页会显示这次计划",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            StatusPill(text = "已排期")
                        }
                    }
                }
            }

            state.error?.let { error ->
                item {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PtSpacing.sm),
                ) {
                    SecondaryButton(
                        text = "添加动作",
                        onClick = {
                            onRequestExercisePick { exerciseId ->
                                store.accept(RoutineEditorStore.Intent.AddSlot(exerciseId))
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "排期",
                        onClick = { showSchedule = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            items(draft.slots, key = { it.id }) { slot ->
                PtCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${slot.orderIndex + 1}. ${slot.exerciseName ?: slot.exerciseId}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "${slot.prescriptions.size} 组",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { store.accept(RoutineEditorStore.Intent.MoveSlot(slot.id, -1)) }) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "上移")
                        }
                        IconButton(onClick = { store.accept(RoutineEditorStore.Intent.MoveSlot(slot.id, 1)) }) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "下移")
                        }
                        IconButton(onClick = { store.accept(RoutineEditorStore.Intent.RemoveSlot(slot.id)) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除动作")
                        }
                    }

                    slot.prescriptions.forEach { prescription ->
                        PrescriptionRow(
                            prescription = prescription,
                            onRemove = {
                                store.accept(
                                    RoutineEditorStore.Intent.RemoveSet(slot.id, prescription.id),
                                )
                            },
                        )
                    }

                    SecondaryButton(
                        text = "添加一组",
                        onClick = { addSetForSlot = slot.id },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (routineId != null) {
                item {
                    SecondaryButton(
                        text = "删除这个计划",
                        onClick = { store.accept(RoutineEditorStore.Intent.Delete(draft.id)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    addSetForSlot?.let { slotId ->
        AddSetDialog(
            onDismiss = { addSetForSlot = null },
            onConfirm = { setType, load, reps, rest ->
                store.accept(RoutineEditorStore.Intent.AddSet(slotId, setType, load, reps, rest))
                addSetForSlot = null
            },
        )
    }

    if (showSchedule) {
        val today = TrainingDate.today(Clock.System.now(), TimeZone.currentSystemDefault())
        val monday = TrainingDate.weekStart(today)
        val options = listOf(
            "今天" to today,
            "明天" to today.plus(1, DateTimeUnit.DAY),
            "本周一" to monday,
            "下周一" to monday.plus(7, DateTimeUnit.DAY),
        )
        AlertDialog(
            onDismissRequest = { showSchedule = false },
            title = { Text("排期到哪天") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(PtSpacing.xs)) {
                    options.forEach { (text, date) ->
                        SecondaryButton(
                            text = "$text（${Labels.date(date, today)}）",
                            onClick = {
                                store.accept(RoutineEditorStore.Intent.Schedule(date))
                                showSchedule = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSchedule = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun PrescriptionRow(prescription: SetPrescription, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${Labels.load(prescription.loadSpec)} × ${Labels.reps(prescription.repSpec)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "${Labels.setType(prescription.setType)} · 休息 ${Labels.rest(prescription.restSec)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "删除这一组")
        }
    }
}

/**
 * 添加一组。
 *
 * 之前用 `AlertDialog`：可用宽度只有 ~232dp，而 `WeightStepper` 两侧按钮各 64dp，
 * 中间只剩 ~88dp，34sp 的"60 kg / 75 %1RM"必然折行，两行又挤在固定 72dp 的高度里，
 * 于是出现"换行 + 字叠在一起"。现在改成**整宽 Dialog**，并给每个控件加显式标题。
 */
@Composable
private fun AddSetDialog(
    onDismiss: () -> Unit,
    onConfirm: (SetType, LoadSpec, RepSpec, Int) -> Unit,
) {
    var setType by remember { mutableStateOf(SetType.NORMAL) }
    var usePercent by remember { mutableStateOf(false) }
    var kg by remember { mutableStateOf(60.0) }
    var percent by remember { mutableStateOf(75.0) }
    var reps by remember { mutableStateOf(8) }
    var restSec by remember { mutableStateOf(120) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PtSpacing.lg),
            shape = RoundedCornerShape(PtSpacing.cardCorner),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(PtSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(PtSpacing.md),
            ) {
                Text(
                    text = "添加一组",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(PtSpacing.xs),
                ) {
                    listOf(SetType.NORMAL, SetType.WARMUP, SetType.DROP, SetType.AMRAP, SetType.FAILURE)
                        .forEach { type ->
                            PtChip(
                                text = Labels.setType(type),
                                selected = setType == type,
                                onClick = { setType = type },
                            )
                        }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(PtSpacing.xs)) {
                    PtChip(text = "按重量", selected = !usePercent, onClick = { usePercent = false })
                    PtChip(text = "按 %1RM", selected = usePercent, onClick = { usePercent = true })
                }

                Column(verticalArrangement = Arrangement.spacedBy(PtSpacing.xs)) {
                    Text(
                        text = if (usePercent) "强度（%1RM）" else "重量",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (usePercent) {
                        WeightStepper(
                            valueKg = percent,
                            onValueChange = { percent = it },
                            step = 2.5,
                            label = "%",
                        )
                    } else {
                        WeightStepper(valueKg = kg, onValueChange = { kg = it }, label = "kg")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(PtSpacing.xs)) {
                    Text(
                        text = "次数",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CountStepper(value = reps, onValueChange = { reps = it }, label = "次", min = 1)
                }

                Column(verticalArrangement = Arrangement.spacedBy(PtSpacing.xs)) {
                    Text(
                        text = "组间休息（秒）",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CountStepper(
                        value = restSec,
                        onValueChange = { restSec = it },
                        label = "秒",
                        min = 0,
                        max = 600,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PtSpacing.sm),
                ) {
                    SecondaryButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    ThumbZoneButton(
                        text = "添加",
                        onClick = {
                            val load: LoadSpec =
                                if (usePercent) LoadSpec.PercentOf1RM(percent) else LoadSpec.Weight(kg)
                            onConfirm(setType, load, RepSpec.Fixed(reps), restSec)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 供列表页展示用：计划里有几个动作、几组。 */
fun Routine.summary(): String = "${slots.size} 个动作 · ${plannedSetCount} 组"
