package com.ben.inly.domain.selfhost.sync

import com.ben.inly.data.local.prefs.SettingsManager
import com.ben.inly.data.local.room.BlockDao
import com.ben.inly.data.local.room.CategoryDao
import com.ben.inly.data.local.room.CategoryEntity
import com.ben.inly.data.local.room.FolderDao
import com.ben.inly.data.local.room.FolderEntity
import com.ben.inly.data.local.room.NoteBlockEntity
import com.ben.inly.data.local.room.NoteDao
import com.ben.inly.data.local.room.NoteMetadataEntity
import com.ben.inly.data.local.room.SelfHostDeletedNoteDao
import com.ben.inly.data.local.room.TagDao
import com.ben.inly.data.local.room.TagEntity
import com.ben.inly.domain.model.DocumentBlock
import com.ben.inly.domain.model.ImageBlock
import com.ben.inly.domain.model.NoteBlock
import com.ben.inly.domain.model.NoteContent
import com.ben.inly.domain.model.VoiceBlock
import com.ben.inly.domain.repository.NoteRepository
import com.ben.inly.domain.selfhost.media.LocalMediaReader
import com.ben.inly.domain.selfhost.media.MediaReferenceScanner
import com.ben.inly.domain.selfhost.merge.NoteMergeHelper
import com.ben.inly.domain.selfhost.translation.NoteJsonCompiler
import com.ben.inly.domain.selfhost.translation.NoteJsonParser
import com.ben.inly.domain.selfhost.webdav.WebDavConfigurationException
import com.ben.inly.domain.selfhost.webdav.WebDavConflictException
import com.ben.inly.domain.selfhost.webdav.WebDavSyncClient
import com.ben.inly.domain.selfhost.webdav.WebDavSyncPaths
import com.ben.inly.domain.util.MediaStorageHelper
import com.ben.inly.domain.util.SyncCoordinator
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

sealed class SelfHostSyncResult {
    data class Success(val notesSynced: Int, val conflicts: Int) : SelfHostSyncResult()
    data class Failure(val cause: Throwable) : SelfHostSyncResult()
    data object AlreadyInProgress : SelfHostSyncResult()
    data object NotConfigured : SelfHostSyncResult()
}

class SelfHostSyncEngine(
    private val webDavSyncClient: WebDavSyncClient,
    private val noteDao: NoteDao,
    private val blockDao: BlockDao,
    private val folderDao: FolderDao,
    private val tagDao: TagDao,
    private val categoryDao: CategoryDao,
    private val settingsManager: SettingsManager,
    private val mediaStorageHelper: MediaStorageHelper,
    private val localMediaReader: LocalMediaReader,
    private val noteRepository: NoteRepository,
    private val selfHostDeletedNoteDao: SelfHostDeletedNoteDao
) {

    private enum class ReconcileOutcome { SYNCED, CONFLICT_SKIPPED, UNCHANGED }

    private val mutex = Mutex()
    private val manifestJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val blockJson = Json { ignoreUnknownKeys = true }
    private val collectionJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private companion object {
        const val MAX_MANIFEST_UPLOAD_RETRIES = 3

        // A media file with no local block referencing it is only a *candidate* for deletion, not a
        // certainty - this device might simply not have pulled the note that still needs it yet (a
        // slow-to-sync device, or a media-only sync running without a fresh text sync just before it).
        // Requiring the file to have sat unreferenced for a full day gives every other device many
        // sync cycles to prove it's still needed (by re-referencing and re-uploading it) before this
        // device ever deletes it from the server.
        const val MEDIA_ORPHAN_GRACE_PERIOD_MS = 24L * 60 * 60 * 1000
    }

    suspend fun hasPendingLocalChanges(): Boolean =
        noteDao.getNotesModifiedSince(settingsManager.getSelfHostLastSyncTimestamp()).isNotEmpty()

    // The private `mutex` above only keeps overlapping sync triggers (poller, worker, manual button)
    // from running this engine's own work twice at once. It says nothing to the editor's own saves -
    // NoteEditorViewModel/DailyEditorViewModel and SyncRepositoryImpl (LAN sync) all coordinate through
    // SyncCoordinator.mutex, so without also taking it here, a local save's read-modify-write over
    // note_blocks can interleave with this engine's own Room writes for the same note at any point,
    // mid-cycle - e.g. a save's currentEntities read landing between two of this engine's writes and
    // seeing a transiently inconsistent (even momentarily empty) result for that note.
    suspend fun runSync(): SelfHostSyncResult {
        SelfHostSyncLog.d("runSync() called")
        if (!mutex.tryLock()) {
            SelfHostSyncLog.d("runSync() skipped, a sync is already in progress")
            return SelfHostSyncResult.AlreadyInProgress
        }
        return try {
            SyncCoordinator.mutex.withLock { runSyncLocked() }
        } finally {
            mutex.unlock()
        }
    }

    suspend fun syncMedia(): SelfHostSyncResult {
        SelfHostSyncLog.d("syncMedia() called")
        if (!mutex.tryLock()) {
            SelfHostSyncLog.d("syncMedia() skipped, a sync is already in progress")
            return SelfHostSyncResult.AlreadyInProgress
        }
        return try {
            SyncCoordinator.mutex.withLock { syncMediaLocked() }
        } finally {
            mutex.unlock()
        }
    }

    suspend fun runBaselineSync(): SelfHostSyncResult {
        SelfHostSyncLog.d("runBaselineSync() called")
        if (!mutex.tryLock()) {
            SelfHostSyncLog.d("runBaselineSync() skipped, a sync is already in progress")
            return SelfHostSyncResult.AlreadyInProgress
        }
        return try {
            SyncCoordinator.mutex.withLock {
                val textResult = runSyncLocked()

                if (textResult is SelfHostSyncResult.Success) {
                    try {
                        when (val mediaResult = syncMediaLocked()) {
                            is SelfHostSyncResult.Failure -> SelfHostSyncLog.e(
                                "runBaselineSync(): baseline media sync failed, will retry via background worker: ${mediaResult.cause.message}",
                                mediaResult.cause
                            )
                            else -> SelfHostSyncLog.d("runBaselineSync(): baseline media sync finished with $mediaResult")
                        }
                    } catch (cause: Exception) {
                        SelfHostSyncLog.e(
                            "runBaselineSync(): baseline media sync threw unexpectedly, will retry via background worker",
                            cause
                        )
                    }
                } else {
                    SelfHostSyncLog.d("runBaselineSync(): skipping baseline media sync, text sync did not succeed ($textResult)")
                }

                textResult
            }
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun syncMediaLocked(): SelfHostSyncResult {
        return try {
            webDavSyncClient.ensureRemoteLayoutExists()

            val (manifest, manifestEtag) = downloadManifestWithEtag()
            val manifestMediaEntries = manifest.entries.filter { it.entryType == SelfHostEntryType.MEDIA }
            val remoteMediaFileNames = manifestMediaEntries.map { it.entryId }.toSet()

            val referencedFileNames = collectReferencedMediaFileNames()
            // Must check disk presence for every file the manifest tracks, not just the ones this
            // device's own surviving blocks currently reference - otherwise a file downloaded here
            // (present on disk, but orphaned from any local block, or only referenced by another
            // device's note) never gets recognized as "already have it" on the next cycle and gets
            // redownloaded every single sync, forever.
            val existingLocalFileNames = (referencedFileNames + remoteMediaFileNames)
                .filterTo(mutableSetOf()) { fileExistsLocally(it) }

            SelfHostSyncLog.d(
                "MediaSync: ${referencedFileNames.size} media file(s) referenced locally, " +
                    "${existingLocalFileNames.size} actually present on disk, " +
                    "${remoteMediaFileNames.size} tracked on server"
            )

            val toUpload = existingLocalFileNames - remoteMediaFileNames
            val toDownload = remoteMediaFileNames - existingLocalFileNames
            SelfHostSyncLog.d("MediaSync: ${toUpload.size} to upload, ${toDownload.size} to download")

            var uploadedCount = 0
            var failedCount = 0
            val successfullyUploaded = mutableSetOf<String>()

            for (fileName in toUpload) {
                try {
                    val path = mediaStorageHelper.getAbsoluteMediaPath(fileName)
                    val bytes = localMediaReader.readBytes(path)
                    if (bytes == null) {
                        failedCount++
                        SelfHostSyncLog.e("MediaSync Error: could not read local bytes for $fileName from path=$path")
                        continue
                    }
                    webDavSyncClient.uploadMedia(fileName, bytes)
                    successfullyUploaded.add(fileName)
                    uploadedCount++
                    SelfHostSyncLog.d("MediaSync: uploaded $fileName (${bytes.size} bytes)")
                } catch (cause: Exception) {
                    failedCount++
                    SelfHostSyncLog.e("MediaSync Error: failed to upload $fileName: ${cause.message}", cause)
                }
            }

            var downloadedCount = 0
            for (fileName in toDownload) {
                SelfHostSyncLog.d("MediaSync: Downloading missing local file $fileName")
                try {
                    val bytes = webDavSyncClient.downloadMedia(fileName)
                    if (bytes == null) {
                        SelfHostSyncLog.d("MediaSync: $fileName is listed in the manifest but missing on the server, skipping")
                        continue
                    }
                    val file = File(mediaStorageHelper.getAbsoluteMediaPath(fileName))
                    file.parentFile?.mkdirs()
                    file.writeBytes(bytes)
                    downloadedCount++
                    SelfHostSyncLog.d("MediaSync: downloaded $fileName (${bytes.size} bytes)")
                } catch (cause: Exception) {
                    failedCount++
                    SelfHostSyncLog.e("MediaSync Error: failed to download $fileName: ${cause.message}", cause)
                }
            }

            // A file the manifest tracks but no local block references anymore is only a *candidate*
            // for cleanup, not a certainty - see MEDIA_ORPHAN_GRACE_PERIOD_MS. If any device still
            // genuinely needs it, that device's own referencedFileNames will include it and it gets
            // re-uploaded (self-healing) even after this device removes it here.
            val nowMs = Clock.System.now().toEpochMilliseconds()
            val orphanedFileNames = manifestMediaEntries
                .filter { it.entryId !in referencedFileNames && (nowMs - it.updatedAt) > MEDIA_ORPHAN_GRACE_PERIOD_MS }
                .map { it.entryId }
                .toSet()

            var deletedCount = 0
            for (fileName in orphanedFileNames) {
                try {
                    webDavSyncClient.deleteFile(WebDavSyncPaths.mediaPath(fileName))
                    val localFile = File(mediaStorageHelper.getAbsoluteMediaPath(fileName))
                    if (localFile.exists()) localFile.delete()
                    deletedCount++
                    SelfHostSyncLog.d("MediaSync: deleted orphaned $fileName (unreferenced for over ${MEDIA_ORPHAN_GRACE_PERIOD_MS / 3_600_000}h)")
                } catch (cause: Exception) {
                    SelfHostSyncLog.e("MediaSync Error: failed to delete orphaned $fileName: ${cause.message}", cause)
                }
            }

            uploadMediaManifestEntries(
                manifest,
                (remoteMediaFileNames + successfullyUploaded) - orphanedFileNames,
                manifestEtag
            )

            SelfHostSyncLog.d(
                "MediaSync: complete, uploaded=$uploadedCount downloaded=$downloadedCount " +
                    "deleted=$deletedCount failed=$failedCount"
            )
            SelfHostSyncResult.Success(notesSynced = uploadedCount + downloadedCount, conflicts = failedCount)
        } catch (cause: WebDavConfigurationException) {
            SelfHostSyncLog.d("MediaSync: not configured (${cause.message})")
            SelfHostSyncResult.NotConfigured
        } catch (cause: Exception) {
            SelfHostSyncLog.e("MediaSync: sync failed with ${cause::class.simpleName}", cause)
            SelfHostSyncResult.Failure(cause)
        }
    }

    private fun fileExistsLocally(fileName: String): Boolean {
        return try {
            File(mediaStorageHelper.getAbsoluteMediaPath(fileName)).exists()
        } catch (cause: Exception) {
            SelfHostSyncLog.e("MediaSync: could not check local existence for $fileName", cause)
            false
        }
    }

    private suspend fun collectReferencedMediaFileNames(): Set<String> {
        val fileNames = mutableSetOf<String>()
        var mediaBlockCount = 0

        for (note in noteDao.getAllNotesForBackup()) {
            note.coverImagePath?.substringAfterLast("/")?.let { fileNames.add(it) }

            val blocks: List<NoteBlock> = blockDao.getAllBlocksForNoteIncludingDeleted(note.noteId)
                .filter { !it.isDeleted }
                .mapNotNull { entity ->
                    try {
                        blockJson.decodeFromString(NoteBlock.serializer(), entity.blockDataJson)
                    } catch (cause: Exception) {
                        SelfHostSyncLog.e("MediaSync: could not decode block ${entity.blockId} for note ${note.noteId}", cause)
                        null
                    }
                }

            mediaBlockCount += blocks.count { it is ImageBlock || it is DocumentBlock || it is VoiceBlock }
            fileNames += MediaReferenceScanner.extractMediaFileNames(blocks)
        }

        SelfHostSyncLog.d(
            "MediaSync: Found $mediaBlockCount local media block(s) to process, " +
                "${fileNames.size} distinct media file(s) referenced"
        )
        return fileNames
    }

    private suspend fun uploadMediaManifestEntries(
        previousManifest: SelfHostManifest,
        mediaFileNames: Set<String>,
        previousManifestEtag: String? = null,
        attempt: Int = 0
    ) {
        val nonMediaEntries = previousManifest.entries.filter { it.entryType != SelfHostEntryType.MEDIA }
        val previousMediaEntriesById = previousManifest.entries
            .filter { it.entryType == SelfHostEntryType.MEDIA }
            .associateBy { it.entryId }
        // Preserve the original upload timestamp for a file that was already tracked - stamping every
        // still-referenced file with "now" on every cycle would mean the orphan-cleanup grace period
        // (measured from this same updatedAt) never actually elapses for anything.
        val mediaEntries = mediaFileNames.map { fileName ->
            SelfHostManifestEntry(
                entryId = fileName,
                entryType = SelfHostEntryType.MEDIA,
                updatedAt = previousMediaEntriesById[fileName]?.updatedAt ?: Clock.System.now().toEpochMilliseconds()
            )
        }
        val newManifest = SelfHostManifest(entries = nonMediaEntries + mediaEntries)

        try {
            webDavSyncClient.uploadEncryptedJson(
                WebDavSyncPaths.MANIFEST_FILE,
                manifestJson.encodeToString(SelfHostManifest.serializer(), newManifest),
                previousManifestEtag
            )
        } catch (cause: WebDavConflictException) {
            if (attempt >= MAX_MANIFEST_UPLOAD_RETRIES) {
                SelfHostSyncLog.e(
                    "MediaSync: manifest upload conflict-skipped after $attempt retries, deferring to next cycle",
                    cause
                )
                return
            }
            val (freshManifest, freshEtag) = downloadManifestWithEtag()
            uploadMediaManifestEntries(freshManifest, mediaFileNames, freshEtag, attempt + 1)
        }
    }

    private suspend fun runSyncLocked(): SelfHostSyncResult {
        return try {
            webDavSyncClient.ensureRemoteLayoutExists()

            try {
                val dedupedCount = noteRepository.dedupeDuplicateDailyNotes()
                if (dedupedCount > 0) {
                    SelfHostSyncLog.d("TextSync: deduped $dedupedCount duplicate daily note row(s) left over from earlier syncs")
                }
            } catch (cause: Exception) {
                SelfHostSyncLog.e("TextSync: dedupeDuplicateDailyNotes failed, continuing sync anyway", cause)
            }

            val syncStartTimestamp = Clock.System.now().toEpochMilliseconds()
            val lastSyncTimestamp = settingsManager.getSelfHostLastSyncTimestamp()
            val (manifest, manifestEtag) = downloadManifestWithEtag()
            val isRemoteManifestEmpty = manifest.entries.isEmpty()
            val allLocalNotes = noteDao.getAllNotesForBackup()

            // lastSyncTimestamp lives in OS-level Preferences/keyring, entirely separate from the
            // Room database file - wiping just the app's local data directory (or reinstalling) clears
            // every local note but leaves that old watermark intact. Without this check, the engine
            // would trust the stale watermark, see nothing "changed" since it (nothing on the server
            // actually has changed), do nothing, and then uploadManifest() moments later would rebuild
            // the note-entries list from the now-empty local database - silently wiping every note out
            // of the shared manifest while leaving the actual files orphaned on the server. Treat a
            // non-empty remote manifest paired with an empty local database as "I need a full resync,"
            // exactly like a brand-new device pairing for the first time.
            //
            // "Empty" here deliberately ignores templates - DefaultTemplateSeeder writes the
            // predefined templates back in on every single launch before this ever runs, so the
            // local database is never literally empty at first run and this check would otherwise
            // never trigger for the exact wipe/reinstall scenario it exists for.
            val isLocalDatabaseEmpty = allLocalNotes.all { it.isTemplate }
            val effectiveLastSyncTimestamp = if (isLocalDatabaseEmpty && !isRemoteManifestEmpty) 0L else lastSyncTimestamp

            val remoteChangedEntries = manifest.entries
                .filter { it.entryType != SelfHostEntryType.MEDIA && it.updatedAt > effectiveLastSyncTimestamp }
                .associateBy { it.entryId }

            val localChangedNotes = if (isRemoteManifestEmpty) {
                allLocalNotes.associateBy { it.noteId }
            } else {
                noteDao.getNotesModifiedSince(effectiveLastSyncTimestamp).associateBy { it.noteId }
            }
            val candidateIds = remoteChangedEntries.keys + localChangedNotes.keys

            SelfHostSyncLog.d(
                "TextSync: lastSyncTimestamp=$lastSyncTimestamp, manifestEmpty=$isRemoteManifestEmpty, " +
                    "localDatabaseEmpty=$isLocalDatabaseEmpty, effectiveLastSyncTimestamp=$effectiveLastSyncTimestamp, " +
                    "remoteChanged=${remoteChangedEntries.size}, localChanged=${localChangedNotes.size}, " +
                    "candidates=${candidateIds.size}"
            )

            var syncedCount = 0
            var conflictCount = 0
            val conflictedNoteIds = mutableSetOf<String>()

            for (noteId in candidateIds) {
                val outcome = reconcileNote(noteId, remoteEntry = remoteChangedEntries[noteId])
                SelfHostSyncLog.d("TextSync: note=$noteId outcome=$outcome")
                when (outcome) {
                    ReconcileOutcome.SYNCED -> syncedCount++
                    ReconcileOutcome.CONFLICT_SKIPPED -> { conflictCount++; conflictedNoteIds += noteId }
                    ReconcileOutcome.UNCHANGED -> Unit
                }
            }

            reconcileFolders()
            reconcileTags()
            reconcileCategories()

            // Publish the manifest every cycle, conflicts or not - it used to be withheld entirely
            // whenever ANY single note conflicted, which meant one note fighting over a stale ETag
            // (exactly what happens when two devices are typing within the same few seconds) stalled
            // propagation for every OTHER note that synced just fine in the same cycle. uploadManifest
            // itself keeps a still-conflicted note's entry exactly as downloaded rather than rebuilding
            // it from local state, since local hasn't actually landed on the server yet.
            uploadManifest(manifest, conflictedNoteIds, manifestEtag)

            // A conflict-skipped note has already been merged and persisted locally - only the
            // remote PUT was rejected (another device's write got there first). Its local updatedAt
            // is therefore always older than syncStartTimestamp, so advancing the watermark here would
            // drop it out of every future candidate list forever, with no other trigger to push it
            // again. Leave the watermark untouched so the same note gets a fresh chance to converge
            // next cycle; everything else already synced this cycle regardless.
            if (conflictCount == 0) {
                settingsManager.saveSelfHostLastSyncTimestamp(syncStartTimestamp)
            } else {
                SelfHostSyncLog.d("TextSync: $conflictCount note(s) still conflict-skipped, deferring watermark advance to next cycle")
            }

            SelfHostSyncLog.d("TextSync: complete, synced=$syncedCount conflicts=$conflictCount")
            SelfHostSyncResult.Success(notesSynced = syncedCount, conflicts = conflictCount)
        } catch (cause: WebDavConfigurationException) {
            SelfHostSyncLog.d("TextSync: not configured (${cause.message})")
            SelfHostSyncResult.NotConfigured
        } catch (cause: Exception) {
            SelfHostSyncLog.e("TextSync: sync failed with ${cause::class.simpleName}", cause)
            SelfHostSyncResult.Failure(cause)
        }
    }

    // Folders/tags/categories are small, infrequently-changed collections, so unlike notes
    // (per-note files with block-level diffing) each is synced as a single encrypted JSON file
    // holding the full list, merged entity-by-entity via last-write-wins on updatedAt - the same
    // ETag-conditional-PUT conflict handling as pushMergedNote, just at collection granularity.
    private suspend fun reconcileFolders() {
        try {
            val remoteJsonWithEtag = webDavSyncClient.downloadAndDecryptJsonWithEtag(WebDavSyncPaths.FOLDERS_FILE)
            val remoteJson = remoteJsonWithEtag?.first
            val remoteFolders = remoteJson
                ?.let { collectionJson.decodeFromString(ListSerializer(FolderEntity.serializer()), it) }
                .orEmpty()
            val localFolders = folderDao.getFoldersModifiedSince(0L)

            val merged = LinkedHashMap<String, FolderEntity>()
            localFolders.forEach { merged[it.folderId] = it }
            remoteFolders.forEach { remote ->
                val local = merged[remote.folderId]
                if (local == null || remote.updatedAt >= local.updatedAt) {
                    merged[remote.folderId] = remote
                }
            }
            val mergedList = merged.values.toList()
            mergedList.forEach { folderDao.insertFolder(it) }

            if (mergedList.toSet() != remoteFolders.toSet()) {
                webDavSyncClient.uploadEncryptedJson(
                    WebDavSyncPaths.FOLDERS_FILE,
                    collectionJson.encodeToString(ListSerializer(FolderEntity.serializer()), mergedList),
                    remoteJsonWithEtag?.second
                )
            }
            SelfHostSyncLog.d("FolderSync: complete, ${mergedList.size} folder(s) reconciled")
        } catch (cause: WebDavConflictException) {
            SelfHostSyncLog.d("FolderSync: remote folders.json changed concurrently, will retry next cycle")
        } catch (cause: Exception) {
            SelfHostSyncLog.e("FolderSync: failed to sync folders: ${cause.message}", cause)
        }
    }

    private suspend fun reconcileTags() {
        try {
            val remoteJsonWithEtag = webDavSyncClient.downloadAndDecryptJsonWithEtag(WebDavSyncPaths.TAGS_FILE)
            val remoteJson = remoteJsonWithEtag?.first
            val remoteTags = remoteJson
                ?.let { collectionJson.decodeFromString(ListSerializer(TagEntity.serializer()), it) }
                .orEmpty()
            val localTags = tagDao.getTagsModifiedSince(0L)

            val merged = LinkedHashMap<String, TagEntity>()
            localTags.forEach { merged[it.tagId] = it }
            remoteTags.forEach { remote ->
                val local = merged[remote.tagId]
                if (local == null || remote.updatedAt >= local.updatedAt) {
                    merged[remote.tagId] = remote
                }
            }
            val mergedList = merged.values.toList()
            mergedList.forEach { tagDao.insertOrUpdateTag(it) }

            if (mergedList.toSet() != remoteTags.toSet()) {
                webDavSyncClient.uploadEncryptedJson(
                    WebDavSyncPaths.TAGS_FILE,
                    collectionJson.encodeToString(ListSerializer(TagEntity.serializer()), mergedList),
                    remoteJsonWithEtag?.second
                )
            }
            SelfHostSyncLog.d("TagSync: complete, ${mergedList.size} tag(s) reconciled")
        } catch (cause: WebDavConflictException) {
            SelfHostSyncLog.d("TagSync: remote tags.json changed concurrently, will retry next cycle")
        } catch (cause: Exception) {
            SelfHostSyncLog.e("TagSync: failed to sync tags: ${cause.message}", cause)
        }
    }

    private suspend fun reconcileCategories() {
        try {
            val remoteJsonWithEtag = webDavSyncClient.downloadAndDecryptJsonWithEtag(WebDavSyncPaths.CATEGORIES_FILE)
            val remoteJson = remoteJsonWithEtag?.first
            val remoteCategories = remoteJson
                ?.let { collectionJson.decodeFromString(ListSerializer(CategoryEntity.serializer()), it) }
                .orEmpty()
            val localCategories = categoryDao.getCategoriesModifiedSince(0L)

            val merged = LinkedHashMap<String, CategoryEntity>()
            localCategories.forEach { merged[it.categoryId] = it }
            remoteCategories.forEach { remote ->
                val local = merged[remote.categoryId]
                if (local == null || remote.updatedAt >= local.updatedAt) {
                    merged[remote.categoryId] = remote
                }
            }
            val mergedList = merged.values.toList()
            mergedList.forEach { categoryDao.insertOrUpdateCategory(it) }

            if (mergedList.toSet() != remoteCategories.toSet()) {
                webDavSyncClient.uploadEncryptedJson(
                    WebDavSyncPaths.CATEGORIES_FILE,
                    collectionJson.encodeToString(ListSerializer(CategoryEntity.serializer()), mergedList),
                    remoteJsonWithEtag?.second
                )
            }
            SelfHostSyncLog.d("CategorySync: complete, ${mergedList.size} categor(y/ies) reconciled")
        } catch (cause: WebDavConflictException) {
            SelfHostSyncLog.d("CategorySync: remote categories.json changed concurrently, will retry next cycle")
        } catch (cause: Exception) {
            SelfHostSyncLog.e("CategorySync: failed to sync categories: ${cause.message}", cause)
        }
    }

    private suspend fun reconcileNote(candidateId: String, remoteEntry: SelfHostManifestEntry?): ReconcileOutcome {
        if (remoteEntry?.isDeleted == true) {
            return applyRemoteTombstone(candidateId, remoteEntry)
        }

        return try {
            val isDaily = remoteEntry?.entryType == SelfHostEntryType.DAILY
            val remoteDateString = remoteEntry?.dateString

            val localMetadata = if (isDaily && remoteDateString != null) {
                noteDao.getDailyNoteMetadata(remoteDateString) ?: noteDao.getNoteById(candidateId)
            } else {
                noteDao.getNoteById(candidateId)
            }
            val noteId = localMetadata?.noteId ?: candidateId

            val remoteJsonWithEtag = when {
                remoteEntry == null -> null
                isDaily -> {
                    val dateString = remoteDateString ?: localMetadata?.dateString
                    if (dateString != null) webDavSyncClient.downloadDailyWithEtag(dateString) else null
                }
                else -> webDavSyncClient.downloadNoteWithEtag(noteId)
            }
            val remoteJson = remoteJsonWithEtag?.first
            // Captured from the exact same GET as remoteJson, so it reflects what the merge below was
            // actually based on - the push at the end of this function must condition on THIS etag,
            // not a freshly re-read one, or a write from another device landing in between would be
            // invisible to the If-Match check and get silently overwritten with no conflict detected.
            val downloadTimeEtag = remoteJsonWithEtag?.second
            val remoteOps = remoteJson?.let { NoteJsonParser.parseJsonToDatabaseOperations(it) }

            val mergedMetadata = pickNewerMetadata(localMetadata, remoteOps?.metadataUpsert)
                ?.copy(noteId = noteId)
                ?: return ReconcileOutcome.UNCHANGED

            val localBlocks = blockDao.getAllBlocksForNoteIncludingDeleted(noteId).filter { block ->
                val belongsToNote = block.noteId == noteId
                if (!belongsToNote) {
                    SelfHostSyncLog.e(
                        "SelfHostSyncEngine: query for note $noteId returned block ${block.blockId} " +
                            "belonging to note ${block.noteId}, discarding it"
                    )
                }
                belongsToNote
            }
            val remoteUpserts = remoteOps?.blockUpserts.orEmpty().map { it.copy(noteId = noteId) }
            val remoteDeletions = remoteOps?.blockDeletions.orEmpty()
            val mergedBlocks = NoteMergeHelper.mergeBlocks(
                noteId = noteId,
                localBlocks = localBlocks,
                remoteUpserts = remoteUpserts,
                remoteDeletions = remoteDeletions
            )

            noteDao.insertOrUpdateMetadata(mergedMetadata.copy(filePath = ""))
            blockDao.insertOrUpdateBlocks(mergedBlocks)

            // mergeBlocks preserves local's pre-merge LinkedHashMap insertion order, not each entity's
            // possibly-updated displayOrder - Room's own read query re-sorts on the next cold read, but
            // this in-memory refresh feeds the live UI cache directly, so it must sort explicitly or a
            // pure reorder never shows up until the app restarts and re-reads Room fresh.
            val refreshedContent = NoteContent(
                blocks = mergedBlocks.sortedBy { it.displayOrder }.mapNotNull { entity ->
                    try {
                        blockJson.decodeFromString(NoteBlock.serializer(), entity.blockDataJson)
                    } catch (cause: Exception) {
                        SelfHostSyncLog.e(
                            "SelfHostSyncEngine: could not decode block ${entity.blockId} while refreshing note cache",
                            cause
                        )
                        null
                    }
                }
            )

            if (mergedMetadata.isDaily) {
                mergedMetadata.dateString?.let { dateString ->
                    noteRepository.refreshDailyNoteCache(dateString, refreshedContent)
                }
            } else {
                noteRepository.refreshNoteContentCache(noteId, refreshedContent)
            }
            noteRepository.refreshProjectionsForNote(mergedMetadata, refreshedContent.blocks)

            // The LAN sync path (SyncRepositoryImpl) already emits this after applying a remote
            // change so an open editor screen reloads title/icon/favorite/cover and, for daily notes,
            // picks up "global_pinned" edits - WebDAV sync never did, leaving those fields (and pinned
            // blocks) stale in whatever note happened to be open when a pull landed. Emit unconditionally
            // here, before the push attempt below, since the merge is already committed to Room/cache
            // at this point regardless of whether the subsequent push succeeds.
            com.ben.inly.domain.util.SyncEventBus.emitSyncCompleted(
                if (mergedMetadata.isDaily) mergedMetadata.dateString ?: noteId else noteId
            )

            pushMergedNote(mergedMetadata, mergedBlocks, downloadTimeEtag)
            ReconcileOutcome.SYNCED
        } catch (cause: WebDavConflictException) {
            ReconcileOutcome.CONFLICT_SKIPPED
        }
    }

    // A manifest entry with isDeleted=true means some device permanently deleted this note (Trash's
    // "delete forever", or a folder delete) and recorded a tombstone instead of just letting the
    // entry vanish - vanishing entries are indistinguishable from "never existed" to a device that
    // still has a local copy, which is exactly what let hard-deleted notes silently resurrect before
    // this tombstone existed. No content download is needed here, unlike the normal merge path -
    // deletion carries no content to merge.
    private suspend fun applyRemoteTombstone(candidateId: String, remoteEntry: SelfHostManifestEntry): ReconcileOutcome {
        val isDaily = remoteEntry.entryType == SelfHostEntryType.DAILY
        val localMetadata = if (isDaily && remoteEntry.dateString != null) {
            noteDao.getDailyNoteMetadata(remoteEntry.dateString) ?: noteDao.getNoteById(candidateId)
        } else {
            noteDao.getNoteById(candidateId)
        }

        if (localMetadata == null) {
            return ReconcileOutcome.UNCHANGED
        }

        if (localMetadata.updatedAt > remoteEntry.updatedAt) {
            // Local was genuinely edited after this tombstone was recorded - someone is actively
            // using this note in good faith elsewhere. Don't destroy a live edit; the normal merge
            // path will push it back out and the stale tombstone will lose on the next reconcile.
            return ReconcileOutcome.UNCHANGED
        }

        val noteId = localMetadata.noteId
        noteRepository.hardDeleteLocalNote(noteId)
        SelfHostSyncLog.d("TextSync: applied remote tombstone for $noteId, hard-deleted local copy")
        com.ben.inly.domain.util.SyncEventBus.emitSyncCompleted(
            if (isDaily) remoteEntry.dateString ?: noteId else noteId
        )
        return ReconcileOutcome.SYNCED
    }

    private suspend fun pushMergedNote(
        metadata: NoteMetadataEntity,
        blocks: List<NoteBlockEntity>,
        ifMatchEtag: String?
    ) {
        val json = NoteJsonCompiler.compileNoteToJson(metadata, blocks)

        if (metadata.isDaily) {
            webDavSyncClient.uploadDaily(metadata.dateString ?: metadata.noteId, json, ifMatchEtag)
        } else {
            webDavSyncClient.uploadNote(metadata.noteId, json, ifMatchEtag)
        }
    }

    private fun pickNewerMetadata(local: NoteMetadataEntity?, remote: NoteMetadataEntity?): NoteMetadataEntity? {
        return when {
            remote == null -> local
            local == null -> remote
            remote.updatedAt > local.updatedAt -> remote
            else -> local
        }
    }

    // A missing manifest (raw == null - nothing has ever been uploaded yet) is genuinely empty and
    // safe to treat as such. A manifest that exists but fails to decode is NOT the same thing - it's
    // real remote state we just couldn't read this cycle (a transient corruption, a race with another
    // device's write). Silently downgrading that to "empty" used to let the caller advance its sync
    // watermark past changes it never actually saw, permanently orphaning them. Let the decode
    // failure propagate instead, so this cycle fails and retries with a fresh download next time.
    private suspend fun downloadManifest(): SelfHostManifest = downloadManifestWithEtag().first

    private suspend fun downloadManifestWithEtag(): Pair<SelfHostManifest, String?> {
        val result = webDavSyncClient.downloadAndDecryptJsonWithEtag(WebDavSyncPaths.MANIFEST_FILE)
            ?: return SelfHostManifest() to null
        val (raw, etag) = result
        return manifestJson.decodeFromString(SelfHostManifest.serializer(), raw) to etag
    }

    private suspend fun uploadManifest(
        previousManifest: SelfHostManifest,
        conflictedNoteIds: Set<String> = emptySet(),
        previousManifestEtag: String? = null,
        attempt: Int = 0
    ) {
        // A tombstone must never be dropped by whichever device happens to upload the manifest next -
        // if it were, a device that hasn't purged its own local copy yet (still catching up, or was
        // offline when the deletion happened) would have nothing telling it to delete, and its own
        // later manifest rebuild would resurrect the note for everyone. So every previously-known
        // tombstone is carried forward here forever, same as how media entries are preserved below,
        // regardless of which device authored it.
        val previousTombstones = previousManifest.entries.filter { it.isDeleted }
        val localTombstones = selfHostDeletedNoteDao.getAllTombstones()

        // The manifest tombstone is what tells other devices to delete their copy - it must live
        // forever regardless of this. This is the separate, one-time cleanup of the actual remote
        // note/daily file for a note THIS device permanently deleted, so it doesn't sit on the server
        // as a storage-leaking orphan forever. remoteFileDeleted is tracked per tombstone so a
        // successful deletion is never repeated every cycle, but a failed one (offline, server error)
        // is retried on the next.
        for (tombstone in localTombstones) {
            if (tombstone.remoteFileDeleted) continue
            try {
                val remotePath = if (tombstone.isDaily) {
                    WebDavSyncPaths.dailyPath(tombstone.dateString ?: tombstone.noteId)
                } else {
                    WebDavSyncPaths.notePath(tombstone.noteId)
                }
                webDavSyncClient.deleteFile(remotePath)
                selfHostDeletedNoteDao.markRemoteFileDeleted(tombstone.noteId)
            } catch (cause: Exception) {
                SelfHostSyncLog.e(
                    "TextSync: failed to delete remote file for permanently-deleted note ${tombstone.noteId}, will retry next cycle",
                    cause
                )
            }
        }

        val localTombstoneEntries = localTombstones.map { tombstone ->
            SelfHostManifestEntry(
                entryId = tombstone.noteId,
                entryType = if (tombstone.isDaily) SelfHostEntryType.DAILY else SelfHostEntryType.NOTE,
                updatedAt = tombstone.deletedAt,
                dateString = tombstone.dateString,
                isDeleted = true
            )
        }
        val mergedTombstonesById = LinkedHashMap<String, SelfHostManifestEntry>()
        previousTombstones.forEach { mergedTombstonesById[it.entryId] = it }
        localTombstoneEntries.forEach { entry ->
            val existing = mergedTombstonesById[entry.entryId]
            if (existing == null || entry.updatedAt > existing.updatedAt) {
                mergedTombstonesById[entry.entryId] = entry
            }
        }
        val tombstoneIds = mergedTombstonesById.keys
        val previousEntriesById = previousManifest.entries.associateBy { it.entryId }

        val noteEntries = noteDao.getAllNotesForBackup()
            .filter { it.noteId !in tombstoneIds }
            .map { note ->
                // A conflict means this device's push was rejected - the server's actual content is
                // whatever the OTHER device successfully wrote, which is exactly what produced this
                // entry in the manifest we just downloaded. Rebuilding it from local state here would
                // advertise content that was never actually accepted by the server, potentially with
                // a timestamp that causes other devices to wrongly skip re-checking this note.
                if (note.noteId in conflictedNoteIds) {
                    previousEntriesById[note.noteId] ?: SelfHostManifestEntry(
                        entryId = note.noteId,
                        entryType = if (note.isDaily) SelfHostEntryType.DAILY else SelfHostEntryType.NOTE,
                        updatedAt = note.updatedAt,
                        dateString = note.dateString.takeIf { note.isDaily }
                    )
                } else {
                    SelfHostManifestEntry(
                        entryId = note.noteId,
                        entryType = if (note.isDaily) SelfHostEntryType.DAILY else SelfHostEntryType.NOTE,
                        updatedAt = note.updatedAt,
                        dateString = note.dateString.takeIf { note.isDaily }
                    )
                }
            }
        val preservedMediaEntries = previousManifest.entries.filter { it.entryType == SelfHostEntryType.MEDIA }
        val newManifest = SelfHostManifest(
            entries = noteEntries + mergedTombstonesById.values + preservedMediaEntries
        )

        // The manifest is rewritten wholesale by every device on every cycle - without this If-Match,
        // two devices syncing within seconds of each other (exactly the concurrent-edit scenario this
        // sync engine is built for) would blindly stomp whichever one uploaded second, silently
        // dropping the other's tombstones/media entries with no conflict ever detected. On a 412,
        // the whole computation above is redone against a fresh download rather than blindly retried,
        // since it depends entirely on previousManifest's tombstones/media entries.
        try {
            webDavSyncClient.uploadEncryptedJson(
                WebDavSyncPaths.MANIFEST_FILE,
                manifestJson.encodeToString(SelfHostManifest.serializer(), newManifest),
                previousManifestEtag
            )
        } catch (cause: WebDavConflictException) {
            if (attempt >= MAX_MANIFEST_UPLOAD_RETRIES) {
                SelfHostSyncLog.e(
                    "TextSync: manifest upload conflict-skipped after $attempt retries, deferring to next cycle",
                    cause
                )
                return
            }
            val (freshManifest, freshEtag) = downloadManifestWithEtag()
            uploadManifest(freshManifest, conflictedNoteIds, freshEtag, attempt + 1)
        }
    }
}
