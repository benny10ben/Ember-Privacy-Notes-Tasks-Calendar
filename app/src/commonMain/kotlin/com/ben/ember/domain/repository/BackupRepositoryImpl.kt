package com.ben.ember.domain.repository

import com.ben.ember.data.local.room.BlockDao
import com.ben.ember.data.local.room.BookmarkBlockDao
import com.ben.ember.data.local.room.CalendarTaskDao
import com.ben.ember.data.local.room.DocumentBlockDao
import com.ben.ember.data.local.room.FolderDao
import com.ben.ember.data.local.room.ImageBlockDao
import com.ben.ember.data.local.room.NoteDao
import com.ben.ember.data.local.room.TagDao
import com.ben.ember.domain.model.backup.EmberBackupData
import kotlinx.coroutines.flow.first

class BackupRepositoryImpl(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val tagDao: TagDao,
    private val blockDao: BlockDao,
    private val calendarTaskDao: CalendarTaskDao,
    private val imageBlockDao: ImageBlockDao,
    private val documentBlockDao: DocumentBlockDao,
    private val bookmarkBlockDao: BookmarkBlockDao
) : BackupRepository {

    override suspend fun createBackupData(): EmberBackupData {
        // We must collect all notes, even trashed ones, to ensure a complete backup.
        val allNotes = noteDao.getAllNotesForBackup()

        // Fetch everything else. We use .first() to grab the current state from the Flow.
        val allFolders = folderDao.getAllFolders().first()
        val allTags = tagDao.getAllTags().first()
        val allTasks = calendarTaskDao.getAllTasksFlow().first()
        val allImages = imageBlockDao.getAllImagesFlow().first()
        val allDocuments = documentBlockDao.getAllDocumentsFlow().first()
        val allBookmarks = bookmarkBlockDao.getAllBookmarksFlow().first()

        // For blocks, we need to iterate through all notes and fetch their blocks.
        // We include deleted blocks (tombstones) because they are crucial for a healthy merge!
        val allBlocks = mutableListOf<com.ben.ember.data.local.room.NoteBlockEntity>()
        for (note in allNotes) {
            val blocksForNote = blockDao.getAllBlocksForNoteIncludingDeleted(note.noteId)
            allBlocks.addAll(blocksForNote)
        }

        return EmberBackupData(
            version = 1,
            exportTimestamp = System.currentTimeMillis(),
            notes = allNotes,
            folders = allFolders,
            tags = allTags,
            blocks = allBlocks,
            calendarTasks = allTasks,
            imageBlocks = allImages,
            documentBlocks = allDocuments,
            bookmarkBlocks = allBookmarks
        )
    }

    override suspend fun restoreBackup(backupData: EmberBackupData) {

        // Restore Folders and Tags
        backupData.folders.forEach { folderDao.insertFolder(it) }
        backupData.tags.forEach { tagDao.insertOrUpdateTag(it) }

        // Daily Note ID Mapper
        // Keeps track of backup Note IDs that need to be rerouted to local Note IDs
        val noteIdMapping = mutableMapOf<String, String>()

        /// Merge Note Metadata (Now with Trash Rescue!)
        for (backupNote in backupData.notes) {

            // Check for Daily Note Date Collisions
            if (backupNote.isDaily && backupNote.dateString != null) {
                val localDailyNote = noteDao.getDailyNoteMetadata(backupNote.dateString)
                if (localDailyNote != null && localDailyNote.noteId != backupNote.noteId) {
                    // Collision! Map the backup's blocks to the local note's ID.
                    noteIdMapping[backupNote.noteId] = localDailyNote.noteId
                    continue // Skip inserting the backup metadata, keep the local one
                }
            }

            val localNote = noteDao.getNoteById(backupNote.noteId)

            // Is the local note in the trash, but the backup note is healthy?
            val isNoteRescued = localNote != null && localNote.trashedAt != null && backupNote.trashedAt == null

            if (localNote == null || backupNote.updatedAt > localNote.updatedAt || isNoteRescued) {
                noteDao.insertOrUpdateMetadata(backupNote)
            }
        }

        // Merge Blocks (Now with Tombstone Rescue!)
        val backupBlocksByNote = backupData.blocks.groupBy { it.noteId }

        for ((originalNoteId, backupBlocks) in backupBlocksByNote) {

            val targetNoteId = noteIdMapping[originalNoteId] ?: originalNoteId
            val localBlocksMap = blockDao.getAllBlocksForNoteIncludingDeleted(targetNoteId).associateBy { it.blockId }
            var maxDisplayOrder = localBlocksMap.values.maxOfOrNull { it.displayOrder } ?: -1

            val blocksToSave = mutableListOf<com.ben.ember.data.local.room.NoteBlockEntity>()

            for (backupBlock in backupBlocks) {
                val localBlock = localBlocksMap[backupBlock.blockId]
                val mappedBackupBlock = backupBlock.copy(noteId = targetNoteId)

                if (localBlock != null) {

                    val isBlockRescued = localBlock.isDeleted && !mappedBackupBlock.isDeleted

                    if (mappedBackupBlock.updatedAt > localBlock.updatedAt || isBlockRescued) {
                        blocksToSave.add(
                            mappedBackupBlock.copy(
                                displayOrder = localBlock.displayOrder,
                                isDeleted = mappedBackupBlock.isDeleted // Restores it to life
                            )
                        )
                    }
                } else {
                    maxDisplayOrder++
                    blocksToSave.add(mappedBackupBlock.copy(displayOrder = maxDisplayOrder))
                }
            }

            if (blocksToSave.isNotEmpty()) {
                blockDao.insertOrUpdateBlocks(blocksToSave)
            }
        }

        // Restore Media/Task index tables (Also using the ID Mapper!)
        if (backupData.calendarTasks.isNotEmpty()) {
            val mappedTasks = backupData.calendarTasks.map {
                it.copy(noteId = noteIdMapping[it.noteId] ?: it.noteId)
            }
            calendarTaskDao.upsertTasks(mappedTasks)
        }
        if (backupData.imageBlocks.isNotEmpty()) {
            val mappedImages = backupData.imageBlocks.map {
                it.copy(noteId = noteIdMapping[it.noteId] ?: it.noteId)
            }
            imageBlockDao.upsertImages(mappedImages)
        }
        if (backupData.documentBlocks.isNotEmpty()) {
            val mappedDocs = backupData.documentBlocks.map {
                it.copy(noteId = noteIdMapping[it.noteId] ?: it.noteId)
            }
            documentBlockDao.upsertDocuments(mappedDocs)
        }
        if (backupData.bookmarkBlocks.isNotEmpty()) {
            val mappedBookmarks = backupData.bookmarkBlocks.map {
                it.copy(noteId = noteIdMapping[it.noteId] ?: it.noteId)
            }
            bookmarkBlockDao.upsertBookmarks(mappedBookmarks)
        }
    }
}