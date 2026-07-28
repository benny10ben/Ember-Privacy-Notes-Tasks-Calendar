package com.ben.inly.data.sync

import com.ben.inly.core.security.SyncEncryptionManager
import com.ben.inly.domain.repository.NoteRepository
import com.ben.inly.data.local.prefs.SettingsManager
import com.ben.inly.data.local.room.CategoryEntity
import com.ben.inly.data.local.room.FolderEntity
import com.ben.inly.data.local.room.NoteMetadataEntity
import com.ben.inly.data.local.room.TagEntity
import com.ben.inly.domain.model.*
import com.ben.inly.domain.sync.SyncEnvelope
import com.ben.inly.domain.sync.SyncRepository
import com.ben.inly.domain.sync.SyncType
import com.ben.inly.domain.util.MediaStorageHelper
import com.ben.inly.domain.util.SyncCoordinator
import com.ben.inly.sync.NoteMergeHelper
import com.ben.inly.sync.SyncClient
import kotlinx.coroutines.Dispatchers
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

    private suspend fun downloadMissingMedia(content: NoteContent) {
        extractMediaFileNames(content).forEach { fileName ->
            val file = File(mediaStorageHelper.getAbsoluteMediaPath(fileName))
            if (!file.exists()) syncClient.downloadMedia(fileName, file)
        }
    }

    private suspend fun uploadLocalMedia(content: NoteContent) {
        extractMediaFileNames(content).forEach { fileName ->
            val file = File(mediaStorageHelper.getAbsoluteMediaPath(fileName))
            if (file.exists()) syncClient.uploadMedia(fileName, file)
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
            // Same grace-period reasoning as self-host's media cleanup - "not referenced right now"
            // doesn't mean "not referenced anywhere," since this device might just not have applied
            // the note that still needs it yet. If any peer still genuinely needs a file, its own
            // next push re-uploads it (self-healing), even after this device has removed it here.
            val orphaned = remoteMedia.filter {
                it.fileName !in referencedFileNames && (nowMs - it.lastModified) > MEDIA_ORPHAN_GRACE_PERIOD_MS
            }
            orphaned.forEach { entry ->
                try {
                    syncClient.deleteRemoteMedia(entry.fileName)
                    val localFile = File(mediaStorageHelper.getAbsoluteMediaPath(entry.fileName))
                    if (localFile.exists()) localFile.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private companion object {
        const val MEDIA_ORPHAN_GRACE_PERIOD_MS = 24L * 60 * 60 * 1000
    }

    override suspend fun applyRemoteChanges(changes: List<SyncEnvelope>): Boolean = withContext(Dispatchers.IO) {
        // Every merge below reads a note then writes it back, racing any other local writer unless the
        // caller already holds SyncCoordinator.mutex - both current callers (SyncViewModel) do, but this
        // check makes that dependency loud instead of a silent, hard-to-reproduce data-loss bug if a
        // future caller ever forgets to acquire the lock first
        check(SyncCoordinator.mutex.isLocked) {
            "applyRemoteChanges must be called while holding SyncCoordinator.mutex"
        }

        val syncKey = settingsManager.getSyncEncryptionKey()
        // The caller only advances its own sync watermark past this batch if every envelope applied
        // cleanly - a per-envelope failure used to be swallowed silently here with no way for the
        // caller to know, so the watermark advanced anyway and that one change was lost forever
        // (the server would never resend something already "since"-filtered out). Re-applying an
        // already-succeeded envelope on the next retry is a harmless no-op merge, so failing the
        // whole batch on one bad envelope is the safe default.
        var allSucceeded = true

        changes.forEach { envelope ->
            try {
                val decryptedMetaJson = encryptionManager.decryptPayload(envelope.metadataJson, syncKey)
                val decryptedContentJson = if (envelope.contentJson.isNotEmpty()) {
                    encryptionManager.decryptPayload(envelope.contentJson, syncKey)
                } else null

                when (envelope.entityType) {

                    // notes
                    SyncType.NOTE -> {
                        val remoteMeta = json.decodeFromString<NoteMetadataEntity>(decryptedMetaJson)
                        val remoteContent = if (!decryptedContentJson.isNullOrEmpty()) {
                            json.decodeFromString<NoteContent>(decryptedContentJson)
                        } else NoteContent(blocks = emptyList())

                        val localMeta = repository.getNoteById(envelope.entityId)

                        if (localMeta == null) {
                            // A tombstone this device already knows about (received earlier, or its
                            // own past deletion) must block recreation here - otherwise a peer that
                            // hasn't caught up to the delete yet would keep resurrecting the note
                            // every time it pushes its own still-existing copy.
                            val tombstone = repository.getNoteTombstone(envelope.entityId)
                            if (!envelope.isDeleted && (tombstone == null || tombstone.deletedAt < envelope.updatedAt)) {
                                downloadMissingMedia(remoteContent)
                                if (remoteMeta.coverImagePath != null) {
                                    val file = File(mediaStorageHelper.getAbsoluteMediaPath(remoteMeta.coverImagePath))
                                    if (!file.exists()) syncClient.downloadMedia(remoteMeta.coverImagePath, file)
                                }
                                repository.saveNote(remoteMeta, remoteContent, stampUpdatedAt = false)

                                // EXPLICIT AI INDEXING CALL
                                repository.indexNote(remoteMeta, remoteContent)

                                com.ben.inly.domain.util.SyncEventBus.emitSyncCompleted(envelope.entityId)
                            }
                        } else if (envelope.isDeleted && envelope.updatedAt > localMeta.updatedAt) {
                            val trashedMeta = remoteMeta.copy(trashedAt = System.currentTimeMillis())
                            repository.saveNote(trashedMeta, remoteContent, stampUpdatedAt = false)

                            // EXPLICIT AI INDEXING CALL
                            repository.indexNote(trashedMeta, remoteContent)

                            com.ben.inly.domain.util.SyncEventBus.emitSyncCompleted(envelope.entityId)
                        } else if (!envelope.isDeleted) {
                            val localContent = repository.getNoteContent(envelope.entityId)
                            val mergedContent = NoteMergeHelper.mergeNoteContent(
                                localContent = localContent,
                                localUpdatedAt = localMeta.updatedAt,
                                remoteContent = remoteContent,
                                remoteUpdatedAt = envelope.updatedAt
                            )
                            downloadMissingMedia(mergedContent)
                            if (remoteMeta.coverImagePath != null) {
                                val file = File(mediaStorageHelper.getAbsoluteMediaPath(remoteMeta.coverImagePath))
                                if (!file.exists()) syncClient.downloadMedia(remoteMeta.coverImagePath, file)
                            }
                            val contentChanged = mergedContent != localContent
                            // Full-object comparison (normalizing the two fields that legitimately
                            // differ without being a real change: updatedAt itself, and filePath,
                            // which is vestigial/per-device) instead of a hand-picked field list -
                            // the old list of 4 fields silently dropped folder moves, reorders,
                            // template conversion, and Trash restores whenever the note body itself
                            // hadn't also changed.
                            val metadataChanged = localMeta.copy(updatedAt = remoteMeta.updatedAt, filePath = remoteMeta.filePath) != remoteMeta
                            if (contentChanged || metadataChanged) {
                                val resolvedUpdatedAt = maxOf(localMeta.updatedAt, envelope.updatedAt)
                                // Strictly greater, not >= - see NoteMergeHelper's identical reasoning.
                                val winningMeta = if (envelope.updatedAt > localMeta.updatedAt) {
                                    remoteMeta.copy(updatedAt = resolvedUpdatedAt)
                                } else {
                                    localMeta.copy(updatedAt = resolvedUpdatedAt)
                                }
                                repository.saveNote(winningMeta, mergedContent, stampUpdatedAt = false)

                                // EXPLICIT AI INDEXING CALL
                                repository.indexNote(winningMeta, mergedContent)

                                com.ben.inly.domain.util.SyncEventBus.emitSyncCompleted(envelope.entityId)
                            }
                        }
                    }

                    // Daily notes
                    SyncType.DAILY_NOTE -> {
                        val remoteMeta = json.decodeFromString<NoteMetadataEntity>(decryptedMetaJson)
                        val remoteContent = if (!decryptedContentJson.isNullOrEmpty()) {
                            json.decodeFromString<NoteContent>(decryptedContentJson)
                        } else NoteContent(blocks = emptyList())

                        val dateString = envelope.entityId
                        val localMeta = repository.getDailyNoteMetadata(dateString)

                        if (localMeta == null) {
                            val tombstone = repository.getNoteTombstone(dateString)
                            if (tombstone == null || tombstone.deletedAt < envelope.updatedAt) {
                                downloadMissingMedia(remoteContent)
                                repository.saveDailyNote(dateString, remoteContent, envelope.updatedAt, remoteMeta)

                                // EXPLICIT AI INDEXING CALL
                                val finalMeta = repository.getDailyNoteMetadata(dateString) ?: remoteMeta
                                repository.indexDailyNote(dateString, remoteContent, finalMeta)

                                com.ben.inly.domain.util.SyncEventBus.emitSyncCompleted(dateString)
                            }
                        } else {
                            val localContent = repository.getDailyNote(dateString)
                            val mergedContent = NoteMergeHelper.mergeNoteContent(
                                localContent = localContent,
                                localUpdatedAt = localMeta.updatedAt,
                                remoteContent = remoteContent,
                                remoteUpdatedAt = envelope.updatedAt
                            )
                            downloadMissingMedia(mergedContent)

                            val contentChanged  = mergedContent != localContent
                            val metadataChanged = localMeta.isFavorite != remoteMeta.isFavorite ||
                                    localMeta.coverImagePath != remoteMeta.coverImagePath

                            if (contentChanged || metadataChanged) {
                                val resolvedUpdatedAt = maxOf(localMeta.updatedAt, envelope.updatedAt)
                                val mergedMeta = localMeta.copy(
                                    isFavorite     = localMeta.isFavorite || remoteMeta.isFavorite,
                                    coverImagePath = if (envelope.updatedAt > localMeta.updatedAt) remoteMeta.coverImagePath else localMeta.coverImagePath
                                )
                                repository.saveDailyNote(dateString, mergedContent, resolvedUpdatedAt, mergedMeta)

                                // EXPLICIT AI INDEXING CALL
                                repository.indexDailyNote(dateString, mergedContent, mergedMeta)

                                com.ben.inly.domain.util.SyncEventBus.emitSyncCompleted(dateString)
                            }
                        }
                    }

                    // insertOrUpdateTag/insertFolder are for this device's OWN edits and always
                    // restamp updatedAt to now - applying a peer's tag/folder through them would
                    // both defeat LWW (the peer's real edit time is discarded) and never honor a
                    // deletion (isDeleted was never even read here before).
                    SyncType.TAG -> {
                        val remoteTag = json.decodeFromString<TagEntity>(decryptedMetaJson)
                        repository.applyRemoteTag(remoteTag)
                    }

                    SyncType.FOLDER -> {
                        val remoteFolder = json.decodeFromString<FolderEntity>(decryptedMetaJson)
                        repository.applyRemoteFolder(remoteFolder)
                    }

                    SyncType.CATEGORY -> {
                        val remoteCategory = json.decodeFromString<CategoryEntity>(decryptedMetaJson)
                        repository.applyRemoteCategory(remoteCategory)
                    }

                    SyncType.NOTE_TOMBSTONE -> {
                        val tombstone = json.decodeFromString<com.ben.inly.domain.sync.NoteTombstonePayload>(decryptedMetaJson)
                        repository.applyRemoteNoteTombstone(
                            noteId = tombstone.noteId,
                            isDaily = tombstone.isDaily,
                            dateString = tombstone.dateString,
                            deletedAt = tombstone.deletedAt
                        )
                        com.ben.inly.domain.util.SyncEventBus.emitSyncCompleted(
                            if (tombstone.isDaily) tombstone.dateString ?: tombstone.noteId else tombstone.noteId
                        )
                    }

                }
            } catch (e: Exception) {
                allSucceeded = false
                println("Failed to apply remote change for ${envelope.entityId}: ${e.message}")
            }
        }

        allSucceeded
    }

    override suspend fun collectLocalChanges(since: Long): List<SyncEnvelope> = withContext(Dispatchers.IO) {
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

            uploadLocalMedia(content)

            if (!meta.isDaily && meta.coverImagePath != null) {
                val file = File(mediaStorageHelper.getAbsoluteMediaPath(meta.coverImagePath))
                if (file.exists()) syncClient.uploadMedia(meta.coverImagePath, file)
            }

            val encryptedMeta    = encryptionManager.encryptPayload(json.encodeToString(meta), syncKey)
            val encryptedContent = encryptionManager.encryptPayload(json.encodeToString(content), syncKey)
            val type = if (meta.isDaily) SyncType.DAILY_NOTE else SyncType.NOTE
            val eId  = if (meta.isDaily && meta.dateString != null) meta.dateString else meta.noteId

            changes.add(
                SyncEnvelope(
                    entityId     = eId,
                    entityType   = type,
                    metadataJson = encryptedMeta,
                    contentJson  = encryptedContent,
                    updatedAt    = meta.updatedAt,
                    isDeleted    = meta.trashedAt != null
                )
            )
        }

        // Modified-since on updatedAt (not createdAt, and not filtered to isDeleted=0 like the
        // list-for-display queries) - a rename/reparent after creation, or a deletion, previously
        // had no way to ever be selected as a change to send.
        val modifiedTags = repository.getTagsModifiedSince(lastSyncTime)
        modifiedTags.forEach { tag ->
            val encryptedTag = encryptionManager.encryptPayload(json.encodeToString(tag), syncKey)
            changes.add(SyncEnvelope(
                entityId = tag.tagId, entityType = SyncType.TAG,
                metadataJson = encryptedTag, contentJson = "",
                updatedAt = tag.updatedAt, isDeleted = tag.isDeleted
            ))
        }

        val modifiedFolders = repository.getFoldersModifiedSince(lastSyncTime)
        modifiedFolders.forEach { folder ->
            val encryptedFolder = encryptionManager.encryptPayload(json.encodeToString(folder), syncKey)
            changes.add(SyncEnvelope(
                entityId = folder.folderId, entityType = SyncType.FOLDER,
                metadataJson = encryptedFolder, contentJson = "",
                updatedAt = folder.updatedAt, isDeleted = folder.isDeleted
            ))
        }

        // Permanent (Trash "delete forever", or folder delete) note deletions - collectLocalChanges
        // above can never surface these since deleteNote already removed the Room row before this
        // even runs, leaving nothing to select on updatedAt. This is the only place a hard delete is
        // ever announced to a peer.
        val modifiedTombstones = repository.getNoteTombstonesModifiedSince(lastSyncTime)
        modifiedTombstones.forEach { tombstone ->
            val payload = com.ben.inly.domain.sync.NoteTombstonePayload(
                noteId = tombstone.noteId,
                isDaily = tombstone.isDaily,
                dateString = tombstone.dateString,
                deletedAt = tombstone.deletedAt
            )
            val encryptedPayload = encryptionManager.encryptPayload(json.encodeToString(payload), syncKey)
            changes.add(SyncEnvelope(
                entityId = tombstone.noteId, entityType = SyncType.NOTE_TOMBSTONE,
                metadataJson = encryptedPayload, contentJson = "",
                updatedAt = tombstone.deletedAt, isDeleted = true
            ))
        }

        val modifiedCategories = repository.getCategoriesModifiedSince(lastSyncTime)
        modifiedCategories.forEach { category ->
            val encryptedCategory = encryptionManager.encryptPayload(json.encodeToString(category), syncKey)
            changes.add(SyncEnvelope(
                entityId = category.categoryId, entityType = SyncType.CATEGORY,
                metadataJson = encryptedCategory, contentJson = "",
                updatedAt = category.updatedAt, isDeleted = category.isDeleted
            ))
        }

        return@withContext changes
    }
}