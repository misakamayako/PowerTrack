package com.misakamayako.powertrack

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.misakamayako.powertrack.core.navigation.AppKey
import com.misakamayako.powertrack.core.navigation.AppScaffold
import com.misakamayako.powertrack.core.navigation.ExerciseDetailKey
import com.misakamayako.powertrack.core.navigation.ExerciseLibraryKey
import com.misakamayako.powertrack.core.navigation.HistoryDetailKey
import com.misakamayako.powertrack.core.navigation.HistoryKey
import com.misakamayako.powertrack.core.navigation.HomeKey
import com.misakamayako.powertrack.core.navigation.PowerTrackNavHost
import com.misakamayako.powertrack.core.navigation.RoutineEditorKey
import com.misakamayako.powertrack.core.navigation.RoutineListKey
import com.misakamayako.powertrack.core.navigation.ScheduleKey
import com.misakamayako.powertrack.core.navigation.SettingsKey
import com.misakamayako.powertrack.core.navigation.StatsKey
import com.misakamayako.powertrack.core.navigation.WorkoutKey
import com.misakamayako.powertrack.core.navigation.WorkoutStep
import com.misakamayako.powertrack.core.navigation.rememberAppBackStack
import com.misakamayako.powertrack.core.navigation.screenPadding
import com.misakamayako.powertrack.feature.exerciselibrary.ExerciseDetailScreen
import com.misakamayako.powertrack.feature.exerciselibrary.ExerciseLibraryScreen
import com.misakamayako.powertrack.feature.history.HistoryDetailScreen
import com.misakamayako.powertrack.feature.history.HistoryScreen
import com.misakamayako.powertrack.feature.home.HomeScreen
import com.misakamayako.powertrack.feature.home.ScheduleScreen
import com.misakamayako.powertrack.feature.routineeditor.RoutineEditorScreen
import com.misakamayako.powertrack.feature.routineeditor.RoutineListScreen
import com.misakamayako.powertrack.feature.settings.SettingsScreen
import com.misakamayako.powertrack.feature.stats.StatsScreen
import com.misakamayako.powertrack.feature.workout.WorkoutScreen
import kotlinx.coroutines.launch

/** 顶层 tab 切换：清到根再推入，避免 back stack 越叠越深。 */
private fun NavBackStack<NavKey>.navigateTopLevel(key: AppKey) {
    if (lastOrNull() == key) return
    while (size > 1) removeLastOrNull()
    add(key)
}

/**
 * 应用装配点（§4.2：app 是唯一聚合点）。
 *
 * 导航请求走 Store 的 Label → 这里统一执行 back stack 操作（§4.5）。
 */
@Composable
fun PowerTrackRoot() {
    val backStack = rememberAppBackStack()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // 选动作 picker 以**盖层**形式压在 NavDisplay 上，不再压进 back stack（D27）。
    // 压进 back stack 会让下层 entry 离开组合，Store（计划草稿 / 练中状态）随之销毁，
    // 于是"选完动作回来数据丢了"。盖层方案下页面一直活着，回调也不会有 pop 时序问题。
    var pickCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val requestPick: ((String) -> Unit) -> Unit = { callback -> pickCallback = callback }
    val navigateTopLevel: (AppKey) -> Unit = { key -> backStack.navigateTopLevel(key) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 92.dp),
                )
            },
            // 关键：根层不能再消费系统栏 inset，否则会和各页面自己的 Scaffold 叠加，
            // 表现为底部导航栏与三键导航之间多出一条空白（以及顶部重复留白）。
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { _ ->
            PowerTrackNavHost(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
                entryProvider = entryProvider {
                    entry<HomeKey> {
                        AppScaffold(currentKey = HomeKey, onNavigate = navigateTopLevel) { padding ->
                            HomeScreen(
                                onNavigate = { key -> backStack.add(key) },
                                onMessage = onMessage,
                                modifier = Modifier.screenPadding(padding),
                            )
                        }
                    }

                    entry<ExerciseLibraryKey> {
                        AppScaffold(currentKey = ExerciseLibraryKey, onNavigate = navigateTopLevel) { padding ->
                            ExerciseLibraryScreen(
                                modifier = Modifier.screenPadding(padding),
                                onOpenExercise = { id -> backStack.add(ExerciseDetailKey(id)) },
                            )
                        }
                    }

                    entry<ExerciseDetailKey> { key ->
                        DetailScaffold(title = "动作详情", onBack = { backStack.removeLastOrNull() }) { padding ->
                            ExerciseDetailScreen(
                                exerciseId = key.exerciseId,
                                modifier = Modifier.padding(padding),
                            )
                        }
                    }

                    entry<RoutineListKey> {
                        AppScaffold(currentKey = RoutineListKey, onNavigate = navigateTopLevel) { padding ->
                            RoutineListScreen(
                                onCreate = { backStack.add(RoutineEditorKey(null)) },
                                onOpenRoutine = { id -> backStack.add(RoutineEditorKey(id)) },
                                onMessage = onMessage,
                                modifier = Modifier.screenPadding(padding),
                            )
                        }
                    }

                    entry<RoutineEditorKey> { key ->
                        RoutineEditorScreen(
                            routineId = key.routineId,
                            onBack = { backStack.removeLastOrNull() },
                            onRequestExercisePick = requestPick,
                            onMessage = onMessage,
                        )
                    }

                    entry<ScheduleKey> {
                        ScheduleScreen(
                            onStart = { sessionId -> backStack.add(WorkoutKey(sessionId)) },
                            onBack = { backStack.removeLastOrNull() },
                            onOpenPlans = { backStack.add(RoutineListKey) },
                            onMessage = onMessage,
                        )
                    }

                    entry<WorkoutKey> { key ->
                        val innerStack = remember(key.sessionId) {
                            mutableStateListOf<WorkoutStep>(WorkoutStep.ExerciseList)
                        }
                        WorkoutScreen(
                            sessionId = key.sessionId,
                            innerBackStack = innerStack,
                            onExit = { backStack.removeLastOrNull() },
                            onRequestExercisePick = requestPick,
                            onMessage = onMessage,
                        )
                    }

                    entry<HistoryKey> {
                        AppScaffold(currentKey = HistoryKey, onNavigate = navigateTopLevel) { padding ->
                            HistoryScreen(
                                onOpenSession = { id -> backStack.add(HistoryDetailKey(id)) },
                                modifier = Modifier.screenPadding(padding),
                            )
                        }
                    }

                    entry<HistoryDetailKey> { key ->
                        DetailScaffold(title = "训练详情", onBack = { backStack.removeLastOrNull() }) { padding ->
                            HistoryDetailScreen(
                                sessionId = key.sessionId,
                                modifier = Modifier.padding(padding),
                            )
                        }
                    }

                    entry<StatsKey> {
                        AppScaffold(currentKey = StatsKey, onNavigate = navigateTopLevel) { padding ->
                            StatsScreen(modifier = Modifier.screenPadding(padding))
                        }
                    }

                    entry<SettingsKey> {
                        AppScaffold(currentKey = SettingsKey, onNavigate = navigateTopLevel) { padding ->
                            SettingsScreen(modifier = Modifier.screenPadding(padding))
                        }
                    }
                },
            )
        }

        if (pickCallback != null) {
            ExercisePickerOverlay(
                onCancel = { pickCallback = null },
                // 二次点击同一帧里出现两次回调也不怕：只有第一次能拿到非空回调
                onPicked = { exerciseId ->
                    pickCallback?.let { callback ->
                        pickCallback = null
                        callback(exerciseId)
                    }
                },
            )
        }
    }
}

/**
 * 选动作盖层（D27）。
 *
 * 自带一个独立的 `ViewModelStoreOwner`：关闭时 `clear()` → 里面的 `ExerciseLibraryStore` 随之 dispose，
 * 所以每次打开都是全新的搜索状态，也不会在 Activity 级留下常驻 Store。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePickerOverlay(
    onCancel: () -> Unit,
    onPicked: (String) -> Unit,
) {
    BackHandler(onBack = onCancel)
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) {
        onDispose { owner.viewModelStore.clear() }
    }

    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("选择动作") },
                    navigationIcon = {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                )
            },
        ) { padding ->
            ExerciseLibraryScreen(
                modifier = Modifier.padding(padding).fillMaxSize(),
                picking = true,
                showHeader = false,
                onPicked = onPicked,
            )
        }
    }
}

/** 详情类页面的壳：顶部只有一个返回。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize()) {
            content(padding)
        }
    }
}
