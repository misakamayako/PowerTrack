package com.misakamayako.powertrack.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.misakamayako.powertrack.core.ui.StoreFactoryProvider
import com.misakamayako.powertrack.core.ui.collectState
import com.misakamayako.powertrack.core.ui.format.Labels
import com.misakamayako.powertrack.core.ui.rememberManagedStore
import com.misakamayako.powertrack.designsystem.component.EmptyState
import com.misakamayako.powertrack.designsystem.component.LoadingBox
import com.misakamayako.powertrack.designsystem.component.PtCard
import com.misakamayako.powertrack.designsystem.component.StatTile
import com.misakamayako.powertrack.designsystem.component.StatusPill
import com.misakamayako.powertrack.designsystem.theme.PtSpacing
import com.misakamayako.powertrack.domain.Adherence
import com.misakamayako.powertrack.domain.SetMetricsCalculator
import com.misakamayako.powertrack.domain.SettingsRepository
import com.misakamayako.powertrack.domain.TrainingDate
import com.misakamayako.powertrack.domain.WorkoutRepository
import com.misakamayako.powertrack.model.WorkoutSession
import org.koin.compose.koinInject

/**
 * M4 历史列表：最近完成的训练（不包含进行中的）。
 */
@Composable
fun HistoryScreen(
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val storeFactory: StoreFactory = StoreFactoryProvider.default
    val workoutRepository: WorkoutRepository = koinInject()
    val settingsRepository: SettingsRepository = koinInject()
    val store = rememberManagedStore("history") {
        createHistoryStore(storeFactory, workoutRepository, settingsRepository)
    }
    val state by store.collectState()

    LaunchedEffect(Unit) { store.accept(HistoryStore.Intent.Load) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(PtSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(PtSpacing.sm),
    ) {
        item {
            Text(
                text = "训练历史",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        if (state.loading && state.sessions.isEmpty()) item { LoadingBox() }
        if (!state.loading && state.sessions.isEmpty()) {
            item { EmptyState(text = "还没有训练记录", hint = "练完一次之后会出现在这里") }
        }

        items(state.sessions, key = { it.id }) { session ->
            PtCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Labels.date(
                                TrainingDate.of(session.endedAt ?: session.startedAt),
                                state.today,
                            ) + " " + Labels.weekday(TrainingDate.of(session.endedAt ?: session.startedAt)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = session.exercises.joinToString("、") { it.exerciseName ?: it.exerciseId }
                                .ifEmpty { "空白训练" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    StatusPill(text = "${session.completedSetCount} 组")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(PtSpacing.xl)) {
                    StatTile(label = "吨位", value = Labels.tonnage(tonnageOf(session)))
                    StatTile(label = "时长", value = Labels.duration(session.duration))
                    Adherence.sessionCompletion(session)?.let { ratio ->
                        StatTile(label = "完成度", value = "${(ratio * 100).toInt()}%")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    com.misakamayako.powertrack.designsystem.component.SecondaryButton(
                        text = "查看详情",
                        onClick = { store.accept(HistoryStore.Intent.Open(session.id)) },
                    )
                }
            }
        }
    }

    LaunchedEffect(state.openSessionId) {
        state.openSessionId?.let { onOpenSession(it) }
    }
}

/**
 * M4 历史详情：组的 segment 明细要能展开看（递减组必须看得见）。
 */
@Composable
fun HistoryDetailScreen(
    sessionId: String,
    modifier: Modifier = Modifier,
) {
    val repository: WorkoutRepository = koinInject()
    val session by produceState<WorkoutSession?>(initialValue = null, sessionId) {
        value = repository.session(sessionId)
    }

    val current = session
    if (current == null) {
        LoadingBox(modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(PtSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(PtSpacing.md),
    ) {
        item {
            PtCard {
                Text(
                    text = current.endedAt?.let {
                        "${Labels.date(TrainingDate.of(it))} ${Labels.weekday(TrainingDate.of(it))}"
                    } ?: "进行中",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(PtSpacing.xl)) {
                    StatTile(label = "组数", value = "${current.completedSetCount}")
                    StatTile(label = "吨位", value = Labels.tonnage(tonnageOf(current)))
                    StatTile(label = "时长", value = Labels.duration(current.duration))
                }
                current.routineSnapshot?.let { snapshot ->
                    Text(
                        text = "来自计划：${snapshot.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(current.exercises, key = { it.id }) { exercise ->
            PtCard {
                Text(
                    text = exercise.exerciseName ?: exercise.exerciseId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                exercise.sets.forEach { set ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = Labels.segments(set.segments),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                        )
                        Text(
                            text = Labels.setType(set.setType),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 与首页一致的吨位口径。 */
private fun tonnageOf(session: WorkoutSession): Double {
    var sum = 0.0
    session.exercises.forEach { exercise ->
        exercise.sets.forEach { set ->
            SetMetricsCalculator.compute(set.segments, session.bodyweightKg).tonnage?.let { sum += it }
        }
    }
    return sum
}

