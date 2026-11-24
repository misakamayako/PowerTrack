package com.misakamayako.powertrack.feature.exerciselibrary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.misakamayako.powertrack.core.ui.StoreFactoryProvider
import com.misakamayako.powertrack.core.ui.collectState
import com.misakamayako.powertrack.core.ui.format.Labels
import com.misakamayako.powertrack.core.ui.rememberManagedStore
import com.misakamayako.powertrack.designsystem.component.AssetImage
import com.misakamayako.powertrack.designsystem.component.AssetVideo
import com.misakamayako.powertrack.designsystem.component.EmptyState
import com.misakamayako.powertrack.designsystem.component.LoadingBox
import com.misakamayako.powertrack.designsystem.component.PtCard
import com.misakamayako.powertrack.designsystem.component.PtChip
import com.misakamayako.powertrack.designsystem.component.StatTile
import com.misakamayako.powertrack.designsystem.component.StatusPill
import com.misakamayako.powertrack.designsystem.theme.PtSpacing
import com.misakamayako.powertrack.domain.ExerciseRepository
import com.misakamayako.powertrack.model.Equipment
import com.misakamayako.powertrack.model.ExerciseWithMuscles
import org.koin.compose.koinInject

/**
 * M1 动作库列表：搜索 + 肌肉筛选 + 器械筛选。
 *
 * `picking = true` 时点击动作直接回传（picker 模式），否则走 `onOpenExercise` 进详情。
 * `showHeader = false` 用于被 `:app` 的 picker 盖层复用（标题由外面的 TopAppBar 提供）。
 */
@Composable
fun ExerciseLibraryScreen(
    modifier: Modifier = Modifier,
    picking: Boolean = false,
    showHeader: Boolean = true,
    onOpenExercise: (String) -> Unit = {},
    onPicked: (String) -> Unit = {},
) {
    val storeFactory: StoreFactory = StoreFactoryProvider.default
    val repository: ExerciseRepository = koinInject()
    val store = rememberManagedStore("exercise-library") { createExerciseLibraryStore(storeFactory, repository) }
    val state by store.collectState()

    // Store 的寿命跟 NavEntry 走（D25），从详情页返回时搜索词/筛选/结果都还在，
    // 不需要也不应该重新加载一遍。
    LaunchedEffect(store) { store.accept(ExerciseLibraryStore.Intent.Refresh) }

    val muscleNameById = state.muscles.associate { it.id to it.name }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(PtSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(PtSpacing.sm),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(PtSpacing.sm)) {
                if (showHeader) {
                    Text(
                        text = if (picking) "选择动作" else "动作库",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { store.accept(ExerciseLibraryStore.Intent.Query(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索动作名 / 器械") },
                    singleLine = true,
                )
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(PtSpacing.xs)) {
                item {
                    PtChip(
                        text = "全部肌肉",
                        selected = state.muscleFilter == null,
                        onClick = { store.accept(ExerciseLibraryStore.Intent.FilterMuscle(null)) },
                    )
                }
                items(state.muscles.filter { it.depth == 1 }, key = { it.id }) { muscle ->
                    PtChip(
                        text = muscle.name,
                        selected = state.muscleFilter == muscle.id,
                        onClick = {
                            store.accept(
                                ExerciseLibraryStore.Intent.FilterMuscle(
                                    if (state.muscleFilter == muscle.id) null else muscle.id,
                                ),
                            )
                        },
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(PtSpacing.xs)) {
                item {
                    PtChip(
                        text = "全部器械",
                        selected = state.equipmentFilter == null,
                        onClick = { store.accept(ExerciseLibraryStore.Intent.FilterEquipment(null)) },
                    )
                }
                items(Equipment.entries, key = { it.name }) { equipment ->
                    PtChip(
                        text = equipment.label(),
                        selected = state.equipmentFilter == equipment,
                        onClick = {
                            store.accept(
                                ExerciseLibraryStore.Intent.FilterEquipment(
                                    if (state.equipmentFilter == equipment) null else equipment,
                                ),
                            )
                        },
                    )
                }
            }
        }

        if (state.error != null) {
            item {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            }
        }

        if (state.loading && state.exercises.isEmpty()) {
            item { LoadingBox() }
        }

        if (!state.loading && state.exercises.isEmpty()) {
            item { EmptyState(text = "没有匹配的动作", hint = "换个关键词或清除筛选试试") }
        }

        items(state.exercises, key = { it.id }) { exercise ->
            PtCard(
                modifier = Modifier.clickable {
                    if (picking) {
                        store.accept(ExerciseLibraryStore.Intent.Pick(exercise.id))
                        onPicked(exercise.id)
                    } else {
                        onOpenExercise(exercise.id)
                    }
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = listOfNotNull(
                                exercise.nameEn,
                                exercise.equipment.label(),
                                if (exercise.mechanic == com.misakamayako.powertrack.model.Mechanic.COMPOUND) "复合" else "孤立",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (exercise.isCustom) {
                        StatusPill(text = "自定义")
                    } else if (exercise.videoAsset != null) {
                        StatusPill(text = "演示")
                    }
                }
                state.muscleFilter?.let { muscleId ->
                    val name = muscleNameById[muscleId]
                    if (name != null) {
                        Text(
                            text = "参与：$name",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * M1 动作详情：参与的肌肉 + 角色 + 贡献度（按贡献度倒序）。
 */
@Composable
fun ExerciseDetailScreen(
    exerciseId: String,
    modifier: Modifier = Modifier,
) {
    val repository: ExerciseRepository = koinInject()
    val detail by produceState<ExerciseWithMuscles?>(initialValue = null, exerciseId) {
        value = repository.exercise(exerciseId)
    }

    val current = detail
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
            Column(verticalArrangement = Arrangement.spacedBy(PtSpacing.xs)) {
                Text(
                    text = current.exercise.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = current.exercise.nameEn.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(PtSpacing.xs)) {
                    StatusPill(text = current.exercise.equipment.label())
                    StatusPill(
                        text = if (current.exercise.mechanic == com.misakamayako.powertrack.model.Mechanic.COMPOUND) "复合动作" else "孤立动作",
                    )
                    StatusPill(
                        text = if (current.exercise.laterality == com.misakamayako.powertrack.model.Laterality.UNILATERAL) "单侧" else "双侧",
                    )
                }
            }
        }

        current.exercise.instructions?.let { instructions ->
            item {
                PtCard {
                    Text("要领", style = MaterialTheme.typography.titleSmall)
                    Text(instructions, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // 动作演示视频（打包在 assets 里，离线可播）
        current.exercise.videoAsset?.let { videoAsset ->
            item {
                PtCard {
                    Text("动作演示", style = MaterialTheme.typography.titleSmall)
                    AssetVideo(assetPath = videoAsset)
                }
            }
        }

        // 肌肉示意图（gloox 的 male/photo 图，打包在 assets 里）
        current.exercise.photoAsset?.let { photoAsset ->
            item {
                PtCard {
                    Text("发力部位", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "高亮处为主要参与的肌肉",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AssetImage(
                        assetPath = photoAsset,
                        contentDescription = "${current.exercise.name} 的肌肉示意图",
                    )
                }
            }
        }

        item {
            PtCard {
                Text("参与的肌肉", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "按贡献度排序，贡献度用于计算有效容量",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                current.muscles.forEach { detailItem ->
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = PtSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(detailItem.muscle.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = Labels.role(detailItem.role),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatTile(
                            label = "贡献度",
                            value = "${(detailItem.contribution * 100).toInt()}%",
                            highlight = detailItem.contribution >= 0.7,
                        )
                    }
                }
            }
        }
    }
}

fun Equipment.label(): String = when (this) {
    Equipment.BARBELL -> "杠铃"
    Equipment.DUMBBELL -> "哑铃"
    Equipment.MACHINE -> "器械"
    Equipment.CABLE -> "绳索"
    Equipment.BODYWEIGHT -> "自重"
    Equipment.BAND -> "弹力带"
    Equipment.KETTLEBELL -> "壶铃"
    Equipment.OTHER -> "其他"
}
