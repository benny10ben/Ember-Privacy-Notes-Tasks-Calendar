package com.ben.ember.data.local.room

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The Android-specific implementation that provides the file path.
 */
fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val dbFile = context.getDatabasePath("ember_database.db")
    return Room.databaseBuilder(
        context = context.applicationContext,
        name = dbFile.absolutePath
    )
}

actual fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
    return builder
        .addMigrations(APP_DATABASE_MIGRATION_1_2)
        .build()
}