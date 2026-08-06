package com.ben.ember.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ember.database.EmberDatabase
import java.io.File

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val appDir = File(System.getProperty("user.home"), ".ember")
        appDir.mkdirs()

        val dbFile = File(appDir, "ember.db")
        val isNewDatabase = !dbFile.exists()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

        if (isNewDatabase) {
            EmberDatabase.Schema.create(driver)
        }

        return driver
    }
}