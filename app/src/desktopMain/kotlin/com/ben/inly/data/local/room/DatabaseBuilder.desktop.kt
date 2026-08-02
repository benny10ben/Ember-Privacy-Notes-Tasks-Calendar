package com.ben.inly.data.local.room

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

/**
 * Desktop-specific implementation that provides the OS file path
 * and binds the bundled KMP SQLite driver.
 */
fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFile = File(System.getProperty("user.home"), ".inly/inly_database.db")
    dbFile.parentFile?.mkdirs()

    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.absolutePath
    )
}

actual fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
    return builder
        .setDriver(BundledSQLiteDriver())
        .addMigrations(APP_DATABASE_MIGRATION_1_2)
        .build()
}