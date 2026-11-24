package com.misakamayako.powertrack.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.misakamayako.powertrack.db.PowerTrackDatabase
import com.misakamayako.powertrack.data.json.SnapshotJson
import com.misakamayako.powertrack.data.mapper.toModel
import com.misakamayako.powertrack.domain.SettingsRepository
import com.misakamayako.powertrack.model.AppSettings
import com.misakamayako.powertrack.model.BodyMetric
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SettingsRepositoryImpl(
    private val db: PowerTrackDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : SettingsRepository {

    private val meta = db.metricsQueries

    private val updates = MutableSharedFlow<AppSettings>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun observe(): Flow<AppSettings> = flow {
        emit(read())
        emitAll(updates)
    }

    override suspend fun get(): AppSettings = withContext(io) { read() }

    private fun read(): AppSettings =
        SnapshotJson.decodeSettings(meta.getMeta(KEY_SETTINGS).executeAsOneOrNull())

    override suspend fun update(settings: AppSettings): Unit = withContext(io) {
        db.transaction {
            meta.setMeta(KEY_SETTINGS, SnapshotJson.encodeSettings(settings))
        }
        updates.emit(settings)
    }

    override suspend fun setDayOffsetHours(hours: Int): Unit =
        update(get().copy(dayOffsetHours = hours.coerceIn(AppSettings.DAY_OFFSET_RANGE)))

    override suspend fun setDefaultRestSec(seconds: Int): Unit =
        update(get().copy(defaultRestSec = seconds.coerceIn(AppSettings.REST_SEC_RANGE)))

    override suspend fun setBodyweight(kg: Double?): Unit = update(get().copy(bodyweightKg = kg))

    override fun observeBodyMetrics(): Flow<PersistentList<BodyMetric>> =
        meta.selectBodyMetrics(100L).asFlow().mapToList(io)
            .map { rows -> rows.map { it.toModel() }.toPersistentList() }

    override suspend fun addBodyMetric(metric: BodyMetric): Unit = withContext(io) {
        meta.insertBodyMetric(
            id = metric.id,
            local_date = metric.localDate.toString(),
            weight_kg = metric.weightKg,
            body_fat_pct = metric.bodyFatPct,
            note = metric.note,
        )
        metric.weightKg?.let { updates.emit(read().copy(bodyweightKg = it)) }
    }

    private companion object {
        const val KEY_SETTINGS = "settings"
    }
}
