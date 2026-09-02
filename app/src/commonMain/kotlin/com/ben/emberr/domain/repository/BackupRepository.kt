package com.ben.emberr.domain.repository

import com.ben.emberr.domain.model.backup.EmberrBackupData

/**
 * Handles the extraction and restoration of the entire Emberr database.
 */
interface BackupRepository {

    /**
     * Gathers all data from all Room tables and packages it into a single EmberrBackupData object.
     */
    suspend fun createBackupData(): EmberrBackupData

    suspend fun restoreBackup(backupData: EmberrBackupData)
}