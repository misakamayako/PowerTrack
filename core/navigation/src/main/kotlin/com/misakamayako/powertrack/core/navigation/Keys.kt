package com.misakamayako.powertrack.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * §4.5：**NavKey 全部 `@Serializable`**，否则进程死亡恢复失效。
 * **禁止在 NavKey 上挂 lambda**（§4.11）。
 */
@Serializable
sealed interface AppKey : NavKey

@Serializable
data object HomeKey : AppKey

@Serializable
data object ExerciseLibraryKey : AppKey

@Serializable
data class ExerciseDetailKey(val exerciseId: String) : AppKey

@Serializable
data object RoutineListKey : AppKey

/** 新建时 `routineId = null`。 */
@Serializable
data class RoutineEditorKey(val routineId: String? = null) : AppKey

/** 排期列表（M4）。 */
@Serializable
data object ScheduleKey : AppKey

/** 练中流程只占一个 back stack entry（§4.6）。 */
@Serializable
data class WorkoutKey(val sessionId: String) : AppKey

@Serializable
data object HistoryKey : AppKey

@Serializable
data class HistoryDetailKey(val sessionId: String) : AppKey

@Serializable
data object StatsKey : AppKey

@Serializable
data object SettingsKey : AppKey

/**
 * 练中流程的**内部** back stack（§4.6）。
 */
@Serializable
sealed interface WorkoutStep : NavKey {
    @Serializable
    data object ExerciseList : WorkoutStep

    @Serializable
    data class Record(val sessionExerciseId: String) : WorkoutStep

    @Serializable
    data object Summary : WorkoutStep
}

/** 底部导航的五个顶层目的地（§8）。 */
enum class TopLevelDestination(val label: String) {
    HOME("首页"),
    LIBRARY("动作库"),
    HISTORY("历史"),
    STATS("统计"),
    SETTINGS("设置"),
}

fun NavKey.topLevelDestination(): TopLevelDestination? = when (this) {
    is HomeKey -> TopLevelDestination.HOME
    is ExerciseLibraryKey, is ExerciseDetailKey -> TopLevelDestination.LIBRARY
    is HistoryKey, is HistoryDetailKey -> TopLevelDestination.HISTORY
    is StatsKey -> TopLevelDestination.STATS
    is SettingsKey -> TopLevelDestination.SETTINGS
    else -> null
}

fun TopLevelDestination.key(): AppKey = when (this) {
    TopLevelDestination.HOME -> HomeKey
    TopLevelDestination.LIBRARY -> ExerciseLibraryKey
    TopLevelDestination.HISTORY -> HistoryKey
    TopLevelDestination.STATS -> StatsKey
    TopLevelDestination.SETTINGS -> SettingsKey
}
