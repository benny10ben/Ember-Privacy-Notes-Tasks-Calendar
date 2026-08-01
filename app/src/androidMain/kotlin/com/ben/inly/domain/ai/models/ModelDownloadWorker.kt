package com.ben.inly.domain.ai.models

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf

class ModelDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val modelDownloadManager: ModelDownloadManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure()
        ensureNotificationChannel()
        val initialFraction = resumeProgressFraction(fileName)
        setForeground(createForegroundInfo(fileName, initialFraction))
        setProgress(workDataOf(KEY_PROGRESS_FRACTION to initialFraction))

        val downloadFlow = when (fileName) {
            ModelFileNames.EMBEDDER -> modelDownloadManager.downloadEmbeddingModel()
            ModelFileNames.GENERATOR -> modelDownloadManager.downloadGeneratorModel()
            else -> return Result.failure()
        }

        var outcome: ModelDownloadProgress = ModelDownloadProgress.Failed("Download did not complete.")
        var lastNotifiedPercent = (initialFraction * 100).toInt()
        downloadFlow.collect { progress ->
            outcome = progress
            if (progress is ModelDownloadProgress.Downloading) {
                setProgress(workDataOf(KEY_PROGRESS_FRACTION to progress.fraction))
                val percent = (progress.fraction * 100).toInt()
                if (percent != lastNotifiedPercent) {
                    lastNotifiedPercent = percent
                    setForeground(createForegroundInfo(fileName, progress.fraction))
                }
            }
        }

        return when (val result = outcome) {
            ModelDownloadProgress.Completed -> Result.success()
            is ModelDownloadProgress.Failed -> Result.failure(workDataOf(KEY_ERROR_MESSAGE to result.message))
            is ModelDownloadProgress.Downloading -> Result.failure()
            ModelDownloadProgress.Paused -> Result.failure()
        }
    }

    private fun ensureNotificationChannel() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Model downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun createForegroundInfo(fileName: String, fraction: Float): ForegroundInfo {
        val displayName = if (fileName == ModelFileNames.EMBEDDER) "embedding model" else "AI model"
        val percent = (fraction * 100).toInt()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Downloading $displayName")
            .setContentText("$percent%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_FILE_NAME = "file_name"
        const val KEY_PROGRESS_FRACTION = "progress_fraction"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val CHANNEL_ID = "inly_model_downloads"
        private const val NOTIFICATION_ID = 4821
    }
}
