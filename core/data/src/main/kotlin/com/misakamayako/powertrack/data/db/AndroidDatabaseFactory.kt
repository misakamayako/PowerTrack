package com.misakamayako.powertrack.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.misakamayako.powertrack.db.PowerTrackDatabase

/**
 * 数据库创建入口。
 *
 * §3.2：SQLDelight 的 `AndroidSqliteDriver` 默认不开 `PRAGMA foreign_keys = ON`，
 * 必须在 callback 里手动开，否则所有 `ON DELETE CASCADE` 全部失效。
 */
class AndroidDatabaseFactory(private val context: Context) {

    fun create(): PowerTrackDatabase = PowerTrackDatabase(createDriver())

    fun createDriver(): SqlDriver {
        val schema = PowerTrackDatabase.Schema
        return AndroidSqliteDriver(
            schema = schema,
            context = context,
            name = DATABASE_NAME,
            callback = object : AndroidSqliteDriver.Callback(schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.setForeignKeyConstraintsEnabled(true)
                }
            },
        )
    }

    companion object {
        const val DATABASE_NAME = "powertrack_v2.db"
    }
}
