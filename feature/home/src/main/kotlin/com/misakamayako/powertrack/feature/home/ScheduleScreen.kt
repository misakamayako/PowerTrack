package com.misakamayako.powertrack.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.misakamayako.powertrack.core.ui.format.Labels
import com.misakamayako.powertrack.designsystem.component.EmptyState
import com.misakamayako.powertrack.designsystem.component.PtCard
import com.misakamayako.powertrack.designsystem.component.SecondaryButton
import com.misakamayako.powertrack.designsystem.component.StatTile
import com.misakamayako.powertrack.designsystem.component.StatusPill
import com.misakamayako.powertrack.designsystem.theme.PtSpacing
import com.misakamayako.powertrack.domain.Adherence
import com.misakamayako.powertrack.domain.ScheduleRepository
import com.misakamayako.powertrack.domain.SettingsRepository
import com.misakamayako.powertrack.domain.TrainingDate
import com.misakamayako.powertrack.domain.WorkoutRepository
import com.misakamayako.powertrack.model.ScheduledSession
import com.misakamayako.powertrack.model.ScheduledStatus
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlinx.coroutines.launch

/**
 * M4 排期列表：依从率 + 跳过 + 直接开始。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onStart: (String) -> Unit,
    onBack: () -> Unit,
    onOpenPlans: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheduleRepository: ScheduleRepository = koinInject()
    val workoutRepository: WorkoutRepository = koinInject()
    val settingsRepository: SettingsRepository = koinInject()
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }

    val settings by produceState(SettingsRepositoryDefaults.settings, refresh) {
        value = settingsRepository.get()
    }
    val today: LocalDate = remember(settings) {
        TrainingDate.today(Clock.System.now(), TimeZone.currentSystemDefault(), settings.dayOffsetHours)
    }
    val sessions by produceState<PersistentList<ScheduledSession>>(persistentListOf(), refresh) {
        value = scheduleRepository.recent(80)
    }
    val week = remember(sessions, today) {
        Adherence.weekly(sessions, today, weeks = 1).singleOrNull()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("训练排期") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 排期页以前只能"开始/跳过"，没有回计划页的路，用户想排期却无处新建计划
                    TextButton(onClick = onOpenPlans) { Text("计划") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(PtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PtSpacing.sm),
        ) {
            item {
                PtCard {
                    Text("本周依从率", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(PtSpacing.xl)) {
                        StatTile(
                            label = "到练/计划",
                            value = "${week?.attended ?: 0}/${week?.scheduled ?: 0}",
                            highlight = true,
                        )
                        StatTile(
                            label = "依从率",
                            value = week?.rate?.let { "${(it * 100).toInt()}%" } ?: "—",
                        )
                    }
                    Text(
                        text = "没计划临时练的记录不会进依从率，只在历史里出现。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (sessions.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(PtSpacing.sm)) {
                        EmptyState(text = "还没有排期", hint = "先建一个计划（动作 + 组数），再排到某天")
                        SecondaryButton(
                            text = "去制定计划",
                            onClick = onOpenPlans,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            items(sessions, key = { it.id }) { scheduled ->
                PtCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${Labels.date(scheduled.localDate, today)} ${Labels.weekday(scheduled.localDate)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        StatusPill(text = scheduled.status.label())
                    }
                    Text(
                        text = "${scheduled.routineName ?: scheduled.routineSnapshot?.name ?: "训练"} · " +
                            "${scheduled.plannedSetCount} 组",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(PtSpacing.sm)) {
                        if (scheduled.status == ScheduledStatus.PLANNED) {
                            SecondaryButton(
                                text = "开始",
                                onClick = {
                                    scope.launch {
                                        // 同一时刻只允许一场 ACTIVE，否则 selectActive() 会把旧的那场藏起来
                                        val active = workoutRepository.activeSession()
                                        if (active != null) {
                                            onMessage("有一场训练正在进行，先练完或丢弃它")
                                            onStart(active.id)
                                        } else {
                                            val session = workoutRepository.startFromScheduled(scheduled.id)
                                            if (session == null) {
                                                onMessage("排期不存在")
                                            } else {
                                                refresh++
                                                onStart(session.id)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            SecondaryButton(
                                text = "跳过",
                                onClick = {
                                    scope.launch {
                                        scheduleRepository.setStatus(scheduled.id, ScheduledStatus.SKIPPED)
                                        refresh++
                                        onMessage("已跳过")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (scheduled.status == ScheduledStatus.STARTED) {
                            // 已开过的场次：回到那场进行中的训练，不再克隆一份新的
                            SecondaryButton(
                                text = "继续",
                                onClick = {
                                    scope.launch {
                                        val active = workoutRepository.activeSession()
                                        if (active == null) {
                                            onMessage("没有进行中的训练记录")
                                        } else {
                                            onStart(active.id)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        scheduled.completedSessionId?.let { sessionId ->
                            SecondaryButton(
                                text = "查看",
                                onClick = { onStart(sessionId) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun ScheduledStatus.label(): String = when (this) {
    ScheduledStatus.PLANNED -> "计划中"
    ScheduledStatus.SKIPPED -> "已跳过"
    ScheduledStatus.STARTED -> "进行中"
    ScheduledStatus.COMPLETED -> "已完成"
}

private object SettingsRepositoryDefaults {
    val settings = com.misakamayako.powertrack.model.AppSettings.DEFAULT
}
