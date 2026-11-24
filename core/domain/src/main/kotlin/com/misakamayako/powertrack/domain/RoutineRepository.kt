package com.misakamayako.powertrack.domain

import com.misakamayako.powertrack.model.Routine
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.flow.Flow

/**
 * 计划模板仓储（§2.5）。
 *
 * `save` 必须在**一个事务**里重写全部 slot / prescription（M2：拖动排序时 order_index 重写要在一个事务里）。
 */
interface RoutineRepository {

    fun observeRoutines(includeArchived: Boolean = false): Flow<PersistentList<Routine>>

    suspend fun routines(includeArchived: Boolean = false): PersistentList<Routine>

    suspend fun routine(id: String): Routine?

    suspend fun create(routine: Routine): String

    suspend fun save(routine: Routine)

    suspend fun rename(id: String, name: String)

    suspend fun setArchived(id: String, archived: Boolean)

    suspend fun delete(id: String)
}
