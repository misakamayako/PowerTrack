package com.misakamayako.powertrack.feature.settings

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.misakamayako.powertrack.core.ui.StoreFactoryProvider
import com.misakamayako.powertrack.core.ui.collectState
import com.misakamayako.powertrack.core.ui.rememberManagedStore
import com.misakamayako.powertrack.designsystem.component.CountStepper
import com.misakamayako.powertrack.designsystem.component.PtCard
import com.misakamayako.powertrack.designsystem.component.PtChip
import com.misakamayako.powertrack.designsystem.component.SecondaryButton
import com.misakamayako.powertrack.designsystem.component.StatusPill
import com.misakamayako.powertrack.designsystem.component.WeightStepper
import com.misakamayako.powertrack.designsystem.theme.PtSpacing
import com.misakamayako.powertrack.domain.ExportRepository
import com.misakamayako.powertrack.domain.SettingsRepository
import com.misakamayako.powertrack.model.AppSettings
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 设置：训练日偏移、默认休息、体重、JSON 导出（§3.5 / M5）。
 */
interface SettingsStore : Store<SettingsStore.Intent, SettingsStore.State, SettingsStore.Label> {

    sealed interface Intent {
        data object Load : Intent
        data class SetDayOffset(val hours: Int) : Intent
        data class SetRestSec(val seconds: Int) : Intent
        data class SetBodyweight(val kg: Double?) : Intent
        data object Export : Intent
        data object ClearMessage : Intent
    }

    @Immutable
    data class State(
        val loading: Boolean = true,
        val settings: AppSettings = AppSettings.DEFAULT,
        val exportedJson: String? = null,
        val message: String? = null,
        val error: String? = null,
    )

    sealed interface Message {
        data object Loading : Message
        data class Loaded(val settings: AppSettings) : Message
        data class Exported(val json: String) : Message
        data class Notify(val text: String) : Message
        data class Failed(val text: String) : Message
        data object MessageCleared : Message
    }

    sealed interface Label
}

object SettingsReducer : Reducer<SettingsStore.State, SettingsStore.Message> {
    override fun SettingsStore.State.reduce(msg: SettingsStore.Message): SettingsStore.State = when (msg) {
        SettingsStore.Message.Loading -> copy(loading = true, error = null)
        is SettingsStore.Message.Loaded -> copy(loading = false, settings = msg.settings, error = null)
        is SettingsStore.Message.Exported -> copy(exportedJson = msg.json, message = "已生成 ${msg.json.length} 字符的 JSON")
        is SettingsStore.Message.Notify -> copy(message = msg.text)
        is SettingsStore.Message.Failed -> copy(loading = false, error = msg.text)
        SettingsStore.Message.MessageCleared -> copy(message = null, error = null)
    }
}

internal class SettingsExecutor(
    private val settingsRepository: SettingsRepository,
    private val exportRepository: ExportRepository,
) : CoroutineExecutor<
    SettingsStore.Intent,
    Unit,
    SettingsStore.State,
    SettingsStore.Message,
    SettingsStore.Label,
    >() {

    override fun executeIntent(intent: SettingsStore.Intent) {
        when (intent) {
            SettingsStore.Intent.Load -> load()
            is SettingsStore.Intent.SetDayOffset -> update { it.copy(dayOffsetHours = intent.hours) }
            is SettingsStore.Intent.SetRestSec -> update { it.copy(defaultRestSec = intent.seconds) }
            is SettingsStore.Intent.SetBodyweight -> update { it.copy(bodyweightKg = intent.kg) }
            SettingsStore.Intent.Export -> export()
            SettingsStore.Intent.ClearMessage -> dispatch(SettingsStore.Message.MessageCleared)
        }
    }

    private fun load() {
        dispatch(SettingsStore.Message.Loading)
        scope.launch {
            runCatching { settingsRepository.get() }
                .onSuccess { dispatch(SettingsStore.Message.Loaded(it)) }
                .onFailure { dispatch(SettingsStore.Message.Failed(it.message ?: "设置加载失败")) }
        }
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(state().settings)
        dispatch(SettingsStore.Message.Loaded(next))
        scope.launch {
            runCatching { settingsRepository.update(next) }
                .onSuccess { dispatch(SettingsStore.Message.Notify("已保存")) }
                .onFailure { dispatch(SettingsStore.Message.Failed(it.message ?: "保存失败")) }
        }
    }

    private fun export() {
        scope.launch {
            runCatching { exportRepository.exportJson() }
                .onSuccess { dispatch(SettingsStore.Message.Exported(it)) }
                .onFailure { dispatch(SettingsStore.Message.Failed(it.message ?: "导出失败")) }
        }
    }
}

fun createSettingsStore(
    storeFactory: StoreFactory,
    settingsRepository: SettingsRepository,
    exportRepository: ExportRepository,
): SettingsStore =
    object : SettingsStore,
        Store<SettingsStore.Intent, SettingsStore.State, SettingsStore.Label> by storeFactory.create(
            name = "SettingsStore",
            initialState = SettingsStore.State(),
            reducer = SettingsReducer,
            executorFactory = { SettingsExecutor(settingsRepository, exportRepository) },
        ) {}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val storeFactory: StoreFactory = StoreFactoryProvider.default
    val settingsRepository: SettingsRepository = koinInject()
    val exportRepository: ExportRepository = koinInject()
    val store = rememberManagedStore("settings") {
        createSettingsStore(storeFactory, settingsRepository, exportRepository)
    }
    val state by store.collectState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { store.accept(SettingsStore.Intent.Load) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(PtSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(PtSpacing.md),
    ) {
        item {
            Text("设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        item {
            PtCard {
                Text("训练日偏移", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "熬夜训练时，凌晨的记录可以归到前一天。当前偏移 ${state.settings.dayOffsetHours} 小时。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(PtSpacing.xs)) {
                    (-2..4).forEach { hours ->
                        PtChip(
                            text = if (hours == 0) "不偏移" else "+$hours",
                            selected = state.settings.dayOffsetHours == hours,
                            onClick = { store.accept(SettingsStore.Intent.SetDayOffset(hours)) },
                        )
                    }
                }
            }
        }

        item {
            PtCard {
                Text("默认组间休息", style = MaterialTheme.typography.titleMedium)
                CountStepper(
                    value = state.settings.defaultRestSec,
                    onValueChange = { store.accept(SettingsStore.Intent.SetRestSec(it)) },
                    label = "秒",
                    min = 15,
                    max = 600,
                )
            }
        }

        item {
            PtCard {
                Text("体重", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "自重 / 助力动作的 resolvedLoad 需要它",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WeightStepper(
                    valueKg = state.settings.bodyweightKg ?: 70.0,
                    onValueChange = { store.accept(SettingsStore.Intent.SetBodyweight(it)) },
                )
            }
        }

        item {
            PtCard {
                Text("数据导出", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "纯本地 JSON，不含账号与网络。导出后复制到剪贴板。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(PtSpacing.sm)) {
                    SecondaryButton(
                        text = "生成 JSON",
                        onClick = { store.accept(SettingsStore.Intent.Export) },
                    )
                    SecondaryButton(
                        text = "复制",
                        onClick = {
                            val json = state.exportedJson ?: return@SecondaryButton
                            scope.launch {
                                clipboard.setClipEntry(ClipData.newPlainText("PowerTrack", json).toClipEntry())
                            }
                        },
                    )
                }
                state.exportedJson?.let { json ->
                    StatusPill(text = "${json.length} 字符")
                }
                state.message?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                state.error?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

