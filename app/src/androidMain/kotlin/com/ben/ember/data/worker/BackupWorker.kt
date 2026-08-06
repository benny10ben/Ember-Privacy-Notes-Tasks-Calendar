package com.ben.ember.data.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ben.ember.data.local.prefs.SettingsManager
import com.ben.ember.domain.repository.BackupRepository
import com.ben.ember.domain.util.AndroidBackupExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val backupRepository: BackupRepository,
    private val settingsManager: SettingsManager,
    private val backupNotifier: BackupNotifier,
    private val backupExporter: AndroidBackupExporter,
    private val backupScheduler: BackupScheduler
) : CoroutineWorker(appContext, workerParams) {

    // Safe parser identical to your SettingsViewModel
    private val safeJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val isEnabled = settingsManager.autoBackupEnabledFlow.first()
            if (!isEnabled) return@withContext Result.success()

            val uriString = settingsManager.backupDirectoryUriFlow.first()
            if (uriString.isNullOrBlank()) {
                backupNotifier.showBackupFailedNotification(
                    "Backup Failed",
                    "Auto-backup is enabled, but no folder is selected. Please check your settings."
                )
                return@withContext Result.failure()
            }

            val treeUri = Uri.parse(uriString)
            val pickedDir = DocumentFile.fromTreeUri(applicationContext, treeUri)

            if (pickedDir == null || !pickedDir.canWrite()) {
                settingsManager.saveAutoBackupEnabled(false)
                backupNotifier.showBackupFailedNotification(
                    "Backup Folder Missing",
                    "Ember lost access to your backup folder. Auto-backups have been paused."
                )
                return@withContext Result.failure()
            }

            enforceRetentionPolicy(pickedDir, keepCount = 3)

            val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
            val fileName = "EmberBackup_$timeStamp.ember"

            val newBackupFile = pickedDir.createFile("application/zip", fileName)
                ?: throw java.io.IOException("Failed to create file. Storage might be full.")

            val backupData = backupRepository.createBackupData()
            val jsonContent = safeJson.encodeToString(backupData)
            val filesDir = applicationContext.filesDir

            backupExporter.exportToZip(newBackupFile.uri, jsonContent, filesDir)
            backupNotifier.showBackupSuccessNotification(fileName)

            Log.d("BackupWorker", "Background backup completed successfully: $fileName")

            scheduleNextRun()
            return@withContext Result.success()

        } catch (e: java.io.IOException) {
            e.printStackTrace()
            backupNotifier.showBackupFailedNotification(
                "Storage Full",
                "Your automated backup failed because the device is out of storage space."
            )
            return@withContext Result.failure() // No reschedule — combine flow handles recovery on next app launch

        } catch (e: SecurityException) {
            e.printStackTrace()
            backupNotifier.showBackupFailedNotification(
                "Permission Denied",
                "Ember doesn't have permission to write to your backup folder."
            )
            return@withContext Result.failure()

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.failure()
        }
    }

    private suspend fun scheduleNextRun() {
        val isEnabled = settingsManager.autoBackupEnabledFlow.first()
        if (isEnabled) {
            val freq = settingsManager.backupFrequencyFlow.first()
            val time = settingsManager.backupTimeFlow.first()
            val day = settingsManager.backupDayFlow.first()
            backupScheduler.scheduleBackup(freq, time, day, forceReplace = true)
        }
    }

    /**
     * Scans the target directory for old Ember backups and deletes the oldest ones,
     * ensuring we only keep a rolling window of [keepCount] backups.
     */
    private fun enforceRetentionPolicy(dir: DocumentFile, keepCount: Int) {
        try {
            val existingBackups = dir.listFiles()
                .filter { it.name?.contains("EmberBackup_") == true }
                .sortedByDescending { it.lastModified() } // Newest first

            // If we already have [keepCount] or more, delete the oldest to make room for the new one
            if (existingBackups.size >= keepCount) {
                // Keep one slot open for the backup we are about to create
                val backupsToDelete = existingBackups.drop(keepCount - 1)
                backupsToDelete.forEach { it.delete() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}