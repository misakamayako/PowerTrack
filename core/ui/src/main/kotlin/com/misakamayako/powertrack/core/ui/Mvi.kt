package com.misakamayako.powertrack.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.extensions.coroutines.labels
import com.arkivanov.mvikotlin.extensions.coroutines.states
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * §4.7 Store 生命周期（修订版，见 Decision Log D25）。
 *
 * Store 的寿命必须等于**当前 NavEntry 在 back stack 上的寿命**，而不是"当前是否在组合中"。
 *
 * 早先的实现用 `remember(key) { factory() } + DisposableEffect { onDispose { store.dispose() } }`，
 * 看起来省事，但只要发生一次栈内跳转（列表 → 详情、练中 → 选动作、编辑器 → 选动作），
 * `NavDisplay` 就会把下层 entry 的 content 移出组合，`onDispose` 立即 dispose 掉 Store：
 *
 * - 返回后是**全新的 Store**，搜索词/筛选/草稿/训练里的动作全丢；
 * - picker 回调拿到的是**已 dispose 的 Store**，`accept()` 静默无效（"选完动作没加进去"）。
 *
 * Navigation 3 的 `rememberViewModelStoreNavEntryDecorator()` 已经为每个 entry 提供
 * 独立的 `ViewModelStoreOwner`，并只在 entry 真正被 pop 时清理：
 * 源码里 `ViewModelStoreNavEntryDecorator(onPop = { key -> viewModelStoreProvider.clearKey(key) })`
 * 以「entry 已离开 back stack 且离开组合」为条件调用 `onPop`。
 * 所以把 Store 挂在这个 entry 级 ViewModel 上，寿命就正好对了：
 * 栈内跳转不会 dispose，pop 才会 dispose（`onCleared`）。
 */
@Composable
fun <S : Store<*, *, *>> rememberManagedStore(
    key: Any?,
    factory: () -> S,
): S {
    val owner = LocalViewModelStoreOwner.current
    if (owner == null) {
        // 没有 ViewModelStoreOwner（Compose Preview、纯 Compose 测试）时的兜底：
        // 退回"随组合存亡"的最简实现，只为让预览能渲染，不是产品路径。
        val store = remember(key) { factory() }
        DisposableEffect(store) { onDispose { store.dispose() } }
        return store
    }
    val holder: StoreViewHolder = viewModel(
        viewModelStoreOwner = owner,
        key = key?.toString() ?: DEFAULT_STORE_HOLDER_KEY,
        factory = viewModelFactory {
            initializer { StoreViewHolder(factory()) }
        },
    )
    @Suppress("UNCHECKED_CAST")
    return holder.store as S
}

/**
 * Store 的宿主 ViewModel。**唯一**负责 `dispose()`，时机 = 所在 NavEntry 被 pop。
 *
 * 注意：不要再用 `DisposableEffect` 兜底 dispose —— 那等于把寿命退回"是否在组合中"，
 * 正是本次要修掉的 bug。
 */
class StoreViewHolder(val store: Store<*, *, *>) : ViewModel() {
    override fun onCleared() {
        store.dispose()
    }
}

private const val DEFAULT_STORE_HOLDER_KEY = "powertrack.managed-store"

/**
 * §4.8：`states` → `Flow` 的桥**显式加 `distinctUntilChanged()`**，
 * MVIKotlin 不替你保证去重。
 */
fun <St : Any> Store<*, St, *>.stateFlow(): Flow<St> = states.distinctUntilChanged()

/** Compose 侧读 Store 状态（首次组合即有初始值，避免闪空）。 */
@Composable
fun <St : Any> Store<*, St, *>.collectState(): State<St> {
    val flow = remember(this) { stateFlow() }
    return flow.collectAsState(initial = state)
}

/** §4.5：导航等一次性事件走 Label，UI 层收集后执行，不进 State。 */
@Composable
fun <L : Any> Store<*, *, L>.collectLabels(onLabel: (L) -> Unit) {
    LaunchedEffect(this) {
        labels.collect(onLabel)
    }
}

/** 读一次当前状态（用于初始值、调试）。 */
val <St : Any> Store<*, St, *>.currentState: St get() = state
