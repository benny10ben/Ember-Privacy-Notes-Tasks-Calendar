package com.ben.inly.domain.sync

import com.ben.inly.core.security.SyncEncryptionManager
import com.ben.inly.data.local.prefs.SettingsManager
import com.ben.inly.data.local.room.CategoryEntity
import com.ben.inly.data.local.room.FolderEntity
import com.ben.inly.data.local.room.NoteMetadataEntity
import com.ben.inly.data.local.room.TagEntity
import com.ben.inly.domain.model.CellData
import com.ben.inly.domain.model.ColumnType
import com.ben.inly.domain.model.DatabaseBlock
import com.ben.inly.domain.model.DocumentBlock
import com.ben.inly.domain.model.ImageBlock
import com.ben.inly.domain.model.NoteBlock
import com.ben.inly.domain.model.NoteContent
import com.ben.inly.domain.model.VoiceBlock
import com.ben.inly.domain.repository.NoteRepository
import com.ben.inly.domain.util.MediaStorageHelper
import com.ben.inly.domain.util.SyncEventBus
import com.ben.inly.domain.util.withSyncCoordinatorOrSkip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class SyncRepositoryImpl(
    private val repository: NoteRepository,
    private val mediaStorageHelper: MediaStorageHelper,
    private val settingsManager: SettingsManager,
    private val encryptionManager: SyncEncryptionManager,
    private val syncClient: SyncClient
) : SyncRepository {

    private val json = Json { ignoreUnknownKeys = true }

    // Coroutine scope for background media transfers to prevent blocking text sync operations.
    private val mediaTransferScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightMediaTransfersMutex = Mutex()
    private val inFlightMediaTransfers = mutableSetOf<String>()

    // Executes a single file transfer on the background scope if it isn't already in flight.
    // Updates MediaTransferStatusBus so UI components stay in sync with real-time transfer states.
    private fun launchMediaTransfer(fileName: String, phase: MediaTransferPhase, block: suspend () -> MediaTransferOutcome) {
        mediaTransferScope.launch {
            val acquired = inFlightMediaTransfersMutex.withLock { inFlightMediaTransfers.add(fileName) }
            if (!acquired) {
                return@launch
            }
            MediaTransferStatusBus.markStarted(fileName, phase)
            var outcome = MediaTransferOutcome.FAILED
            try {
                outcome = block()
            } catch (e: Exception) {
                LanSyncLog.e("mediaSync: $phase of $fileName failed unexpectedly: ${e.message}", e)
            } finally {
                inFlightMediaTransfersMutex.withLock { inFlightMediaTransfers.remove(fileName) }
                when (outcome) {
                    MediaTransferOutcome.SUCCESS -> MediaTransferStatusBus.markFinished(fileName, succeeded = true)
                    MediaTransferOutcome.FAILED -> MediaTransferStatusBus.markFinished(fileName, succeeded = false)
                    MediaTransferOutcome.PEER_NOT_READY -> MediaTransferStatusBus.markDeferred(fileName)
                }
            }
        }
    }

    private fun extractMediaFileNames(content: NoteContent): List<String> {
        val mediaFiles = mutableListOf<String>()

        fun scan(blocks: List<NoteBlock>) {
            blocks.forEach { block ->
                when (block) {
                    is ImageBlock -> block.localFilePath?.substringAfterLast("/")?.let { mediaFiles.add(it) }
                    is DocumentBlock -> block.localFilePath?.substringAfterLast("/")?.let { mediaFiles.add(it) }
                    is VoiceBlock -> block.localFilePath?.substringAfterLast("/")?.let { mediaFiles.add(it) }
                    is DatabaseBlock -> {
                        val mediaColIds = block.columns
                            .filter { it.type == ColumnType.FILES || it.type == ColumnType.AUDIO }
                            .map { it.id }.toSet()
                        block.rows.forEach { row ->
                            mediaColIds.forEach { colId ->
                                val files = (row.cells[colId] as? CellData.MediaList)?.files ?: emptyList()
                                files.forEach { media ->
                                    val cleanLocalPath = media.fileName.substringAfterLast("/")
                                    if (cleanLocalPath.isNotBlank()) mediaFiles.add(cleanLocalPath)
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
        }

        scan(content.blocks)
        return mediaFiles.distinct()
    }

    private fun downloadMissingMedia(content: NoteContent) {
        extractMediaFileNames(content).forEach { fileName ->
            val file = File(mediaStorageHelper.getAbsoluteMediaPath(fileName))
            if (!file.exists()) {
                launchMediaTransfer(fileName, MediaTransferPhase.DOWNLOADING) {
                    val outcome = syncClient.downloadMedia(fileName, file)
                    if (outcome == MediaTransferOutcome.FAILED) {
                        LanSyncLog.e("downloadMissingMedia: $fileName failed, will retry on the next sync pass")
                    }
                    outcome
                }
            }
        }
    }

    private fun uploadLocalMedia(content: NoteContent) {
        extractMediaFileNames(content).forEach { fileName ->
            val file = File(mediaStorageHelper.getAbsoluteMediaPath(fileName))
            if (file.exists()) {
                launchMediaTransfer(fileName, MediaTransferPhase.UPLOADING) {
                    val outcome = syncClient.uploadMedia(fileName, file)
                    if (outcome == MediaTransferOutcome.FAILED) {
                        LanSyncLog.e(
                            "uploadLocalMedia: $fileName (${file.length()} bytes) failed to upload, " +
                                    "note metadata will still be pushed and reference a file the peer doesn't have yet - " +
                                    "it will self-heal once this upload succeeds on a later sync pass"
                        )
                    }
                    outcome
                }
            }
        }
    }

    private suspend fun collectAllReferencedMediaFileNames(): Set<String> {
        val fileNames = mutableSetOf<String>()
        repository.getNotesModifiedSince(0L).forEach { meta ->
            val content = if (meta.isDaily && meta.dateString != null) {
                repository.getDailyNote(meta.dateString)
            } else {
                repository.getNoteContent(meta.noteId)
            }
            if (content != null) fileNames += extractMediaFileNames(content)
            meta.coverImagePath?.substringAfterLast("/")?.let { fileNames.add(it) }
        }
        return fileNames
    }

    override suspend fun cleanupOrphanedMedia() = withContext(Dispatchers.IO) {
        try {
            val referencedFileNames = collectAllReferencedMediaFileNames()
            val remoteMedia = syncClient.listRemoteMedia()
            val nowMs = System.currentTimeMillis()
            // Filters and deletes remote media files that are no longer referenced locally and exceed the grace period.
            val orphaned = remoteMedia.filter {
                it.fileName !in referencedFileNames && (nowMs - it.lastModified) > MEDIA_ORPHAN_GRACE_PERIOD_MS
            }
            orphaned.forEach { entry ->
                try {
                    syncClient.deleteRemoteMedia(entry.fileName)
                    val localFile = File(mediaStorageHelper.getAbsoluteMediaPath(entry.fileName))
                    if (localFile.exists()) localFile.delete()
                } catch (e: Exception) {
                    LanSyncLog.e("cleanupOrphanedMedia: failed to delete orphaned ${entry.fileName}: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            LanSyncLog.e("cleanupOrphanedMedia: failed with ${e::class.simpleName}: ${e.message}", e)
        }
    }

    override suspend fun reconcileMedia() = withContext(Dispatchers.IO) {
        try {
            val referencedFileNames = collectAllReferencedMediaFileNames()
            val remoteFileNames = syncClient.listRemoteMedia().map { it.fileName }.toSet()
            val existingLocalFileNames = referencedFileNames.filterTo(mutableSetOf()) { fileExistsLocally(it) }

            // Uploads referenced local files missing on the peer; downloads referenced peer files missing locally.
            val toUpload = existingLocalFileNames - remoteFileNames
            val toDownload = referencedFileNames.filter { it in remoteFileNames && it !in existingLocalFileNames }

            toUpload.forEach { fileName ->
                val file = File(mediaStorageHelper.getAbsoluteMediaPath(fileName))
                launchMediaTransfer(fileName, MediaTransferPhase.UPLOADING) {
                    syncClient.uploadMedia(fileName, file)
                }
            }
            toDownload.forEach { fileName ->
                val file = File(mediaStorageHelper.getAbsoluteMediaPath(fileName))
                file.parentFile?.mkdirs()
                launchMediaTransfer(fileName, MediaTransferPhase.DOWNLOADING) {
                    syncClient.downloadMedia(fileName, file)
                }
            }
        } catch (e: Exception) {
            LanSyncLog.e("reconcileMedia: failed with ${e::class.simpleName}: ${e.message}", e)
        }
    }

    override fun retryMediaDownload(fileName: String) {
        val file = File(mediaStorageHelper.getAbsoluteMediaPath(fileName))
        if (file.exists()) return
        file.parentFile?.mkdirs()
        launchMediaTransfer(fileName, MediaTransferPhase.DOWNLOADING) {
            syncClient.downloadMedia(fileName, file)
        }
    }

    private fun fileExistsLocally(fileName: String): Boolean {
        return try {
            File(mediaStorageHelper.getAbsoluteMediaPath(fileName)).exists()
        } catch (e: Exception) {
            LanSyncLog.e("reconcileMedia: could not check local existence for $fileName", e)
            false
        }
    }

    private companion object {
        const val MEDIA_ORPHAN_GRACE_PERIOD_MS = 24L * 60 * 60 * 1000
    }

    override suspend fun applyRemoteChanges(changes: List<SyncEnvelope>): Boolean =
        withContext(Dispatchers.IO) {
            val syncKey = settingsManager.getSyncEncryptionKey()
            var allSucceeded = true

            changes.forEach { envelope ->
                // Media downloads are queued after releasing the lock so large file downloads do not block editor saves.
                var pendingMediaContent: NoteContent? = null
                var pendingCoverImagePath: String? = null

                val applied = withSyncCoordinatorOrSkip {
                    try {
                        val decryptedMetaJson =
                            encryptionManager.decryptPayload(envelope.metadataJson, syncKey)
                        val decryptedContentJson = if (envelope.contentJson.isNotEmpty()) {
                            encryptionManager.decryptPayload(envelope.contentJson, syncKey)
                        } else null

                        when (envelope.entityType) {

                            // notes
                            SyncType.NOTE -> {
                                val remoteMeta =
                                    json.decodeFromString<NoteMetadataEntity>(decryptedMetaJson)
                                val remoteContent = if (!decryptedContentJson.isNullOrEmpty()) {
                                    json.decodeFromString<NoteContent>(decryptedContentJson)
                                } else NoteContent(blocks = emptyList())

                                val localMeta = repository.getNoteById(envelope.entityId)

                                if (localMeta == null) {
                                    // Prevents re-creating a note if a tombstone already exists.
                                    val tombstone = repository.getNoteTombstone(envelope.entityId)
                                    if (!envelope.isDeleted && (tombstone == null || tombstone.deletedAt < envelope.updatedAt)) {
                                        repository.saveNote(
                                            remoteMeta,
                                            remoteContent,
                                            stampUpdatedAt = false
                                        )

                                        // EXPLICIT AI INDEXING CALL
                                        repository.indexNote(remoteMeta, remoteContent)

                                        SyncEventBus.emitSyncCompleted(envelope.entityId)
                                        pendingMediaContent = remoteContent
                                        pendingCoverImagePath = remoteMeta.coverImagePath
                                    }
                                } else if (envelope.isDeleted && envelope.updatedAt > localMeta.updatedAt) {
                                    val trashedMeta =
                                        remoteMeta.copy(trashedAt = System.currentTimeMillis())
                                    repository.saveNote(
                                        trashedMeta,
                                        remoteContent,
                                        stampUpdatedAt = false
                                    )

                                    // EXPLICIT AI INDEXING CALL
                                    repository.indexNote(trashedMeta, remoteContent)

                                    SyncEventBus.emitSyncCompleted(envelope.entityId)
                                } else if (!envelope.isDeleted) {
                                    val localContent = repository.getNoteContent(envelope.entityId)
                                    val mergedContent = NoteMergeHelper.mergeNoteContent(
                                        localContent = localContent,
                                        localUpdatedAt = localMeta.updatedAt,
                                        remoteContent = remoteContent,
                                        remoteUpdatedAt = envelope.updatedAt
                                    )
                                    pendingMediaContent = mergedContent
                                    pendingCoverImagePath = remoteMeta.coverImagePath
                                    val contentChanged = mergedContent != localContent
                                    // Checks for metadata differences ignoring non-sync fields like filePath.
                                    val metadataChanged = localMeta.copy(
                                        updatedAt = remoteMeta.updatedAt,
                                        filePath = remoteMeta.filePath
                                    ) != remoteMeta
                                    if (contentChanged || metadataChanged) {
                                        val resolvedUpdatedAt =
                                            maxOf(localMeta.updatedAt, envelope.updatedAt)
                                        val winningMeta =
                                            if (envelope.updatedAt > localMeta.updatedAt) {
                                                remoteMeta.copy(updatedAt = resolvedUpdatedAt)
                                            } else {
                                                localMeta.copy(updatedAt = resolvedUpdatedAt)
                                            }
                                        repository.saveNote(
                                            winningMeta,
                                            mergedContent,
                                            stampUpdatedAt = false
                                        )

                                        // EXPLICIT AI INDEXING CALL
                                        repository.indexNote(winningMeta, mergedContent)

                                        SyncEventBus.emitSyncCompleted(envelope.entityId)
                                    }
                                }
                            }

                            // Daily notes
                            SyncType.DAILY_NOTE -> {
                                val remoteMeta =
                                    json.decodeFromString<NoteMetadataEntity>(decryptedMetaJson)
                                val remoteContent = if (!decryptedContentJson.isNullOrEmpty()) {
                                    json.decodeFromString<NoteContent>(decryptedContentJson)
                                } else NoteContent(blocks = emptyList())

                                val dateString = envelope.entityId
                                val localMeta = repository.getDailyNoteMetadata(dateString)

                                if (localMeta == null) {
                                    val tombstone = repository.getNoteTombstone(dateString)
                                    if (tombstone == null || tombstone.deletedAt < envelope.updatedAt) {
                                        repository.saveDailyNote(
                                            dateString,
                                            remoteContent,
                                            envelope.updatedAt,
                                            remoteMeta
                                        )

                                        // EXPLICIT AI INDEXING CALL
                                        val finalMeta = repository.getDailyNoteMetadata(dateString)
                                            ?: remoteMeta
                                        repository.indexDailyNote(
                                            dateString,
                                            remoteContent,
                                            finalMeta
                                        )

                                        SyncEventBus.emitSyncCompleted(dateString)
                                        pendingMediaContent = remoteContent
                                    }
                                } else {
                                    val localContent = repository.getDailyNote(dateString)
                                    val mergedContent = NoteMergeHelper.mergeNoteContent(
                                        localContent = localContent,
                                        localUpdatedAt = localMeta.updatedAt,
                                        remoteContent = remoteContent,
                                        remoteUpdatedAt = envelope.updatedAt
                                    )
                                    pendingMediaContent = mergedContent

                                    val contentChanged = mergedContent != localContent
                                    val metadataChanged =
                                        localMeta.isFavorite != remoteMeta.isFavorite ||
                                                localMeta.coverImagePath != remoteMeta.coverImagePath

                                    if (contentChanged || metadataChanged) {
                                        val resolvedUpdatedAt =
                                            maxOf(localMeta.updatedAt, envelope.updatedAt)
                                        val mergedMeta = localMeta.copy(
                                            isFavorite = localMeta.isFavorite || remoteMeta.isFavorite,
                                            coverImagePath = if (envelope.updatedAt > localMeta.updatedAt) remoteMeta.coverImagePath else localMeta.coverImagePath
                                        )
                                        repository.saveDailyNote(
                                            dateString,
                                            mergedContent,
                                            resolvedUpdatedAt,
                                            mergedMeta
                                        )

                                        // EXPLICIT AI INDEXING CALL
                                        repository.indexDailyNote(
                                            dateString,
                                            mergedContent,
                                            mergedMeta
                                        )

                                        SyncEventBus.emitSyncCompleted(dateString)
                                    }
                                }
                            }

                            SyncType.TAG -> {
                                val remoteTag = json.decodeFromString<TagEntity>(decryptedMetaJson)
                                repository.applyRemoteTag(remoteTag)
                            }

                            SyncType.FOLDER -> {
                                val remoteFolder =
                                    json.decodeFromString<FolderEntity>(decryptedMetaJson)
                                repository.applyRemoteFolder(remoteFolder)
                            }

                            SyncType.CATEGORY -> {
                                val remoteCategory =
                                    json.decodeFromString<CategoryEntity>(decryptedMetaJson)
                                repository.applyRemoteCategory(remoteCategory)
                            }

                            SyncType.NOTE_TOMBSTONE -> {
                                val tombstone =
                                    json.decodeFromString<NoteTombstonePayload>(decryptedMetaJson)
                                repository.applyRemoteNoteTombstone(
                                    noteId = tombstone.noteId,
                                    isDaily = tombstone.isDaily,
                                    dateString = tombstone.dateString,
                                    deletedAt = tombstone.deletedAt
                                )
                                SyncEventBus.emitSyncCompleted(
                                    if (tombstone.isDaily) tombstone.dateString
                                        ?: tombstone.noteId else tombstone.noteId
                                )
                            }

                        }
                        true
                    } catch (e: Exception) {
                        LanSyncLog.e("applyRemoteChanges: failed to apply change for ${envelope.entityId}: ${e.message}", e)
                        false
                    }
                }

                // Triggers background downloads for media referenced by newly applied notes.
                try {
                    pendingMediaContent?.let { downloadMissingMedia(it) }
                    pendingCoverImagePath?.let { path ->
                        val file = File(mediaStorageHelper.getAbsoluteMediaPath(path))
                        if (!file.exists()) {
                            launchMediaTransfer(path, MediaTransferPhase.DOWNLOADING) {
                                val outcome = syncClient.downloadMedia(path, file)
                                if (outcome == MediaTransferOutcome.FAILED) {
                                    LanSyncLog.e("applyRemoteChanges: cover image $path failed to download for ${envelope.entityId}")
                                }
                                outcome
                            }
                        }
                    }
                } catch (e: Exception) {
                    LanSyncLog.e("applyRemoteChanges: failed to queue media download for ${envelope.entityId}: ${e.message}", e)
                }

                if (applied != true) {
                    allSucceeded = false
                }
            }

            allSucceeded
        }

    override suspend fun collectLocalChanges(since: Long, uploadMedia: Boolean): List<SyncEnvelope> =
        withContext(Dispatchers.IO) {
            val lastSyncTime = since
            val syncKey = settingsManager.getSyncEncryptionKey()
            val changes = mutableListOf<SyncEnvelope>()
            val modifiedNotes = repository.getNotesModifiedSince(lastSyncTime)

            modifiedNotes.forEach { meta ->
                val content = if (meta.isDaily && meta.dateString != null) {
                    repository.getDailyNote(meta.dateString)
                } else {
                    repository.getNoteContent(meta.noteId)
                } ?: NoteContent(blocks = emptyList())

                if (uploadMedia) {
                    uploadLocalMedia(content)

                    val coverPath = meta.coverImagePath
                    if (!meta.isDaily && coverPath != null) {
                        val file = File(mediaStorageHelper.getAbsoluteMediaPath(coverPath))
                        if (file.exists()) {
                            launchMediaTransfer(coverPath, MediaTransferPhase.UPLOADING) {
                                val outcome = syncClient.uploadMedia(coverPath, file)
                                if (outcome == MediaTransferOutcome.FAILED) {
                                    LanSyncLog.e("collectLocalChanges: cover image $coverPath failed to upload for ${meta.noteId}")
                                }
                                outcome
                            }
                        }
                    }
                }

                val encryptedMeta =
                    encryptionManager.encryptPayload(json.encodeToString(meta), syncKey)
                val encryptedContent =
                    encryptionManager.encryptPayload(json.encodeToString(content), syncKey)
                val type = if (meta.isDaily) SyncType.DAILY_NOTE else SyncType.NOTE
                val eId =
                    if (meta.isDaily && meta.dateString != null) meta.dateString else meta.noteId

                changes.add(
                    SyncEnvelope(
                        entityId = eId,
                        entityType = type,
                        metadataJson = encryptedMeta,
                        contentJson = encryptedContent,
                        updatedAt = meta.updatedAt,
                        isDeleted = meta.trashedAt != null
                    )
                )
            }

            // Collects tags modified since lastSyncTime.
            val modifiedTags = repository.getTagsModifiedSince(lastSyncTime)
            modifiedTags.forEach { tag ->
                val encryptedTag =
                    encryptionManager.encryptPayload(json.encodeToString(tag), syncKey)
                changes.add(
                    SyncEnvelope(
                        entityId = tag.tagId, entityType = SyncType.TAG,
                        metadataJson = encryptedTag, contentJson = "",
                        updatedAt = tag.updatedAt, isDeleted = tag.isDeleted
                    )
                )
            }

            // Collects folders modified since lastSyncTime.
            val modifiedFolders = repository.getFoldersModifiedSince(lastSyncTime)
            modifiedFolders.forEach { folder ->
                val encryptedFolder =
                    encryptionManager.encryptPayload(json.encodeToString(folder), syncKey)
                changes.add(
                    SyncEnvelope(
                        entityId = folder.folderId, entityType = SyncType.FOLDER,
                        metadataJson = encryptedFolder, contentJson = "",
                        updatedAt = folder.updatedAt, isDeleted = folder.isDeleted
                    )
                )
            }

            // Collects permanently deleted note tombstones.
            val modifiedTombstones = repository.getNoteTombstonesModifiedSince(lastSyncTime)
            modifiedTombstones.forEach { tombstone ->
                val payload = NoteTombstonePayload(
                    noteId = tombstone.noteId,
                    isDaily = tombstone.isDaily,
                    dateString = tombstone.dateString,
                    deletedAt = tombstone.deletedAt
                )
                val encryptedPayload =
                    encryptionManager.encryptPayload(json.encodeToString(payload), syncKey)
                changes.add(
                    SyncEnvelope(
                        entityId = tombstone.noteId, entityType = SyncType.NOTE_TOMBSTONE,
                        metadataJson = encryptedPayload, contentJson = "",
                        updatedAt = tombstone.deletedAt, isDeleted = true
                    )
                )
            }

            // Collects categories modified since lastSyncTime.
            val modifiedCategories = repository.getCategoriesModifiedSince(lastSyncTime)
            modifiedCategories.forEach { category ->
                val encryptedCategory =
                    encryptionManager.encryptPayload(json.encodeToString(category), syncKey)
                changes.add(
                    SyncEnvelope(
                        entityId = category.categoryId, entityType = SyncType.CATEGORY,
                        metadataJson = encryptedCategory, contentJson = "",
                        updatedAt = category.updatedAt, isDeleted = category.isDeleted
                    )
                )
            }

            return@withContext changes
        }
}