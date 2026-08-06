package com.ben.ember.domain.repository

import com.ben.ember.domain.model.backup.EmberBackupData

/**
 * Handles the extraction and restoration of the entire Ember database.
 */
interface BackupRepository {

    /**
     * Gathers all data from all Room tables and packages it into a single EmberBackupData object.
     */
    suspend fun createBackupData(): EmberBackupData

    suspend fun restoreBackup(backupData: EmberBackupData)
}