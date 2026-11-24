package com.misakamayako.powertrack.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.misakamayako.powertrack.core.ui.StoreFactoryProvider
import com.misakamayako.powertrack.core.ui.collectState
import com.misakamayako.powertrack.core.ui.format.Labels
import com.misakamayako.powertrack.core.ui.rememberManagedStore
import com.misakamayako.powertrack.designsystem.component.EmptyState
import com.misakamayako.powertrack.designsystem.component.LoadingBox
import com.misakamayako.powertrack.designsystem.component.PtCard
import com.misakamayako.powertrack.designsystem.component.PtChip
import com.misakamayako.powertrack.designsystem.component.StatTile
import com.misakamayako.powertrack.designsystem.component.StatusPill
import com.misakamayako.powertrack.designsystem.component.formatNumber
import com.misakamayako.powertrack.designsystem.theme.PtSpacing
import com.misakamayako.powertrack.domain.SettingsRepository
import com.misakamayako.powertrack.domain.StatsRepository
import org.koin.compose.koinInject

/**
 * M5 统计页：周吨位、e1RM 曲线、PR 列表。
 */
@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    val storeFactory: StoreFactory = StoreFactoryProvider.default
    val statsRepository: StatsRepository = koinInject()
    val settingsRepository: SettingsRepository = koinInject()
    val store = rememberManagedStore("stats") {
        createStatsStore(storeFactory, statsRepository, settingsRepository)
    }
    val state by store.collectState()

    LaunchedEffect(Unit) { store.accept(StatsStore.Intent.Load) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(PtSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(PtSpacing.md),
    ) {
        item {
            Column {
                Text("统计", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "共 ${state.totalSessions} 次训练",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.loading && state.weeklyTonnage.isEmpty()) item { LoadingBox() }

        item {
            PtCard {
                Text("周吨位", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "只统计 load 和 reps 都能解析的组，解析不出的跳过而不是当 0",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.weeklyTonnage.isEmpty()) {
                    EmptyState(text = "还没有足够的数据")
                } else {
                    val max = state.weeklyTonnage.maxOf { it.tonnage }.coerceAtLeast(1.0)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.weeklyTonnage.forEach { week ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(PtSpacing.sm),
                            ) {
                                Text(
                                    text = "${week.weekStart.toString().substring(5)}周",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(60.dp),
                                )
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { (week.tonnage / max).toFloat() },
                                    modifier = Modifier.weight(1f).height(12.dp),
                                )
                                Text(
                                    text = Labels.tonnage(week.tonnage),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            Text(
                                text = "${week.sessions} 次 · ${week.sets} 组",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 60.dp),
                            )
                        }
                    }
                }
            }
        }

        if (state.exercises.isNotEmpty()) {
            item {
                PtCard {
                    Text("e1RM 曲线", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(PtSpacing.xs)) {
                        items(state.exercises, key = { it.exerciseId }) { pr ->
                            PtChip(
                                text = pr.exerciseName ?: pr.exerciseId,
                                selected = state.selectedExerciseId == pr.exerciseId,
                                onClick = { store.accept(StatsStore.Intent.SelectExercise(pr.exerciseId)) },
                            )
                        }
                    }
                    if (state.e1rmPoints.isEmpty()) {
                        EmptyState(text = "这个动作还没有可估算的 e1RM")
                    } else {
                        E1rmChart(points = state.e1rmPoints.map { it.e1rm })
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "最低 ${formatNumber(state.e1rmPoints.minOf { it.e1rm })}kg",
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                text = "最高 ${formatNumber(state.e1rmPoints.maxOf { it.e1rm })}kg",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }

        item {
            PtCard {
                Text("个人纪录", style = MaterialTheme.typography.titleMedium)
                if (state.prs.isEmpty()) {
                    EmptyState(text = "还没有 PR", hint = "练几组能估算的之后会自动出现")
                } else {
                    state.prs.forEach { pr ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pr.exerciseName ?: pr.exerciseId,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = Labels.prKind(pr.kind),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            StatusPill(text = "${formatNumber(pr.value)}${pr.unit}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun E1rmChart(points: List<Double>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.fillMaxWidth().height(140.dp)) {
        if (points.size < 2) return@Canvas
        val min = points.min()
        val max = points.max()
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0
        val stepX = size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { index, value ->
            val x = stepX * index
            val y = size.height - ((value - min) / span * size.height * 0.8f).toFloat() - size.height * 0.1f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = 4f))
        points.forEachIndexed { index, value ->
            val x = stepX * index
            val y = size.height - ((value - min) / span * size.height * 0.8f).toFloat() - size.height * 0.1f
            drawCircle(color = lineColor, radius = 4f, center = Offset(x, y))
        }
    }
}
