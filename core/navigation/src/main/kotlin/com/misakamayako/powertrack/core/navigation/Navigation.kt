package com.misakamayako.powertrack.core.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay

/**
 * §4.5 根 NavDisplay 装配。
 *
 * 默认 `entryDecorators` 必须包含 saveable state holder 与 ViewModel store，
 * 否则进程死亡恢复 / 跨 entry 的 ViewModel 都会出问题。
 */
@Composable
fun rememberAppBackStack(): NavBackStack<NavKey> = rememberNavBackStack(HomeKey)

@Composable
fun PowerTrackNavHost(
    backStack: NavBackStack<NavKey>,
    modifier: Modifier = Modifier,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = {
            if (backStack.size > 1) backStack.removeLastOrNull()
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider,
    )
}

/**
 * 顶部界面的统一 Scaffold：底部五个 tab（§8）。
 */
@Composable
fun AppScaffold(
    currentKey: NavKey,
    onNavigate: (AppKey) -> Unit,
    showBottomBar: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val current = currentKey.topLevelDestination()
    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = current == destination,
                            onClick = { if (current != destination) onNavigate(destination.key()) },
                            icon = {
                                Icon(
                                    imageVector = destination.icon(filled = current == destination),
                                    contentDescription = destination.label,
                                )
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        content(padding)
    }
}

private fun TopLevelDestination.icon(filled: Boolean): ImageVector = when (this) {
    TopLevelDestination.HOME -> if (filled) Icons.Filled.Home else Icons.Outlined.Home
    TopLevelDestination.LIBRARY -> if (filled) Icons.Filled.FitnessCenter else Icons.Outlined.FitnessCenter
    TopLevelDestination.HISTORY -> if (filled) Icons.Filled.History else Icons.Outlined.History
    TopLevelDestination.STATS -> if (filled) Icons.Filled.Insights else Icons.Outlined.Insights
    TopLevelDestination.SETTINGS -> if (filled) Icons.Filled.Settings else Icons.Outlined.Settings
}

/** 内容区统一的 padding 处理。 */
fun Modifier.screenPadding(padding: PaddingValues): Modifier = this.padding(padding).consumeWindowInsets(padding)
