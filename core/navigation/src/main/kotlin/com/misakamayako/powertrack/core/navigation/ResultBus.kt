package com.misakamayako.powertrack.core.navigation

import com.misakamayako.powertrack.model.Ids
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

/**
 * §4.11：N3 里不能把 lambda 挂在 `NavKey` 上（key 必须 `@Serializable`），
 * 所以 picker 的返回值走一条单向总线：
 *
 * ```
 * 调用方：newRequestId() → Push(ExerciseLibrary) → await(requestId)
 * picker：publish(requestId, exerciseId) 并 pop
 * ```
 */
class ResultBus {

    private val _results = MutableSharedFlow<NavResult>(extraBufferCapacity = 16)
    val results: SharedFlow<NavResult> = _results

    fun newRequestId(): String = Ids.new()

    fun publish(requestId: String, payload: String?) {
        _results.tryEmit(NavResult(requestId, payload))
    }

    suspend fun await(requestId: String): String? =
        results.filter { it.requestId == requestId }.first().payload
}

/**
 * `payload` 用 String（exercise id 等），避免为了可序列化把领域对象塞进导航结果。
 */
data class NavResult(
    val requestId: String,
    val payload: String? = null,
)
