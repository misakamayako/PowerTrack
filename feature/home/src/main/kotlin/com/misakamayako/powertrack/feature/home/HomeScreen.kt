package com.misakamayako.powertrack.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.misakamayako.powertrack.core.navigation.AppKey
import com.misakamayako.powertrack.core.ui.StoreFactoryProvider
import com.misakamayako.powertrack.core.ui.collectLabels
import com.misakamayako.powertrack.core.ui.collectState
import com.misakamayako.powertrack.core.ui.format.Labels
import com.misakamayako.powertrack.core.ui.rememberManagedStore
import com.misakamayako.powertrack.designsystem.component.EmptyState
import com.misakamayako.powertrack.designsystem.component.LoadingBox
import com.misakamayako.powertrack.designsystem.component.PtCard
import com.misakamayako.powertrack.designsystem.component.SectionHeader
import com.misakamayako.powertrack.designsystem.component.SecondaryButton
import com.misakamayako.powertrack.designsystem.component.StatTile
import com.misakamayako.powertrack.designsystem.component.StatusPill
import com.misakamayako.powertrack.designsystem.component.ThumbZoneButton
import com.misakamayako.powertrack.designsystem.theme.PtSpacing
import com.misakamayako.powertrack.domain.ScheduleRepository
import com.misakamayako.powertrack.domain.SettingsRepository
import com.misakamayako.powertrack.domain.WorkoutRepository
import org.koin.compose.koinInject

/**
 * §8 首页：下一次计划卡 / 上次训练卡 / 本周进度卡 + 进行中的训练入口。
 */
@Composable
fun HomeScreen(
    onNavigate: (AppKey) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val storeFactory: StoreFactory = StoreFactoryProvider.default
    val scheduleRepository: ScheduleRepository = koinInject()
    val workoutRepository: WorkoutRepository = koinInject()
    val settingsRepository: SettingsRepository = koinInject()

    val store = rememberManagedStore("home") {
        createHomeStore(storeFactory, scheduleRepository, workoutRepository, settingsRepository)
    }
    val state by store.collectState()

    store.collectLabels { label ->
        when (label) {
            is HomeStore.Label.Navigate -> onNavigate(label.key)
            is HomeStore.Label.ShowMessage -> onMessage(label.text)
        }
    }

    LaunchedEffect(Unit) { store.accept(HomeStore.Intent.Refresh) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(PtSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(PtSpacing.md),
    ) {
        item {
            Column {
                Text(
                    text = state.today?.let { date ->
                        "${Labels.date(date)} ${Labels.weekday(date)}"
                    } ?: "今天",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "记录每一组，练得更稳",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val activeSessionId = state.activeSessionId
        if (activeSessionId != null) {
            item {
                PtCard {
                    SectionHeader(title = "有一场训练正在进行")
                    Text(
                        text = "上次记录还没结束，继续把它练完。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ThumbZoneButton(
                        text = "继续训练",
                        onClick = { store.accept(HomeStore.Intent.ResumeActive(activeSessionId)) },
                    )
                }
            }
        }

        if (state.loading && state.today == null) {
            item { LoadingBox() }
        }

        state.error?.let { error ->
            item {
                PtCard {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    SecondaryButton(text = "重试", onClick = { store.accept(HomeStore.Intent.Refresh) })
                }
            }
        }

        // 1. 下一次计划卡
        item {
            PtCard {
                val planned = state.nextPlanned
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader(
                        title = "下一次计划",
                        modifier = Modifier.weight(1f),
                        actionText = "全部",
                        onAction = { store.accept(HomeStore.Intent.OpenSchedule) },
                    )
                    if (state.hasPlannedToday) {
                        StatusPill(text = "今天")
                    }
                }

                if (planned == null) {
                    Text(
                        text = if (activeSessionId != null) {
                            "本周没有待开始的排期，先把上面那场练完。"
                        } else {
                            "本周无计划。可以先建一个计划排到某天，也可以直接开一场自由训练。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // 首页以前只有"直接开练"的入口，想先做计划的用户无处可去，全都掉进空白训练。
                    // 「制定计划」始终可用；「自由训练」在有进行中的训练时隐藏（否则会插第二条 ACTIVE）。
                    Row(horizontalArrangement = Arrangement.spacedBy(PtSpacing.sm)) {
                        SecondaryButton(
                            text = "制定计划",
                            onClick = { store.accept(HomeStore.Intent.OpenRoutines) },
                            modifier = Modifier.weight(1f),
                        )
                        if (activeSessionId == null) {
                            SecondaryButton(
                                text = "自由训练",
                                onClick = { store.accept(HomeStore.Intent.StartBlank) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                } else {
                    Text(
                        text = planned.routineName ?: planned.routineSnapshot?.name ?: "训练",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${Labels.date(planned.localDate, state.today)} ${Labels.weekday(planned.localDate)} · " +
                            "${planned.plannedSetCount} 组",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // 快照里一个动作都没有的计划，开始后必定是"空白训练"：先说清楚，别让用户白点
                    val planIsEmpty = planned.routineSnapshot?.slots.isNullOrEmpty()
                    if (planIsEmpty) {
                        Text(
                            text = "这个计划里还没有动作，先「查看计划」把动作和组数加进去。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        ThumbZoneButton(
                            text = "开始这次训练",
                            onClick = { store.accept(HomeStore.Intent.StartScheduled(planned.id)) },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(PtSpacing.sm)) {
                        SecondaryButton(
                            text = "跳过",
                            onClick = { store.accept(HomeStore.Intent.SkipScheduled(planned.id)) },
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            text = "查看计划",
                            onClick = { store.accept(HomeStore.Intent.OpenRoutines) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // 2. 上次训练卡
        item {
            PtCard {
                SectionHeader(
                    title = "上次训练",
                    actionText = "历史",
                    onAction = { store.accept(HomeStore.Intent.OpenHistory) },
                )
                val last = state.lastSession
                if (last == null) {
                    EmptyState(text = "还没有训练记录", hint = "练完第一次之后，这里会显示摘要")
                } else {
                    Text(
                        text = "${Labels.date(last.date, state.today)} ${Labels.weekday(last.date)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PtSpacing.xl),
                    ) {
                        StatTile(label = "动作", value = "${last.exerciseCount}")
                        StatTile(label = "组数", value = "${last.setCount}")
                        StatTile(label = "吨位", value = Labels.tonnage(last.tonnageKg))
                        StatTile(label = "时长", value = Labels.duration(last.duration))
                    }
                    Spacer(Modifier.height(PtSpacing.xs))
                    SecondaryButton(
                        text = "重复上次",
                        onClick = { store.accept(HomeStore.Intent.RepeatLast) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // 3. 本周进度卡
        item {
            PtCard {
                SectionHeader(title = "本周进度")
                val week = state.week
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PtSpacing.xl),
                ) {
                    StatTile(
                        label = "依从率",
                        value = week.rate?.let { "${(it * 100).toInt()}%" } ?: "—",
                        highlight = true,
                    )
                    StatTile(label = "到练/计划", value = "${week.attended}/${week.scheduled}")
                    StatTile(label = "完成组数", value = "${week.completedSets}")
                    StatTile(label = "总吨位", value = Labels.tonnage(week.tonnageKg))
                }
                if (week.scheduled == 0) {
                    Text(
                        text = "本周还没有排期。去计划页排一次，依从率才有意义。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (activeSessionId == null) {
            item {
                ThumbZoneButton(
                    text = "直接开始空白训练",
                    onClick = { store.accept(HomeStore.Intent.StartBlank) },
                    modifier = Modifier.padding(top = PtSpacing.sm),
                )
            }
        }
    }
}
