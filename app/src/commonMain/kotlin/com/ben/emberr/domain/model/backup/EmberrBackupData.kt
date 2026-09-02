package com.ben.emberr.domain.model.backup

import com.ben.emberr.data.local.room.BookmarkBlockEntity
import com.ben.emberr.data.local.room.CalendarTaskEntity
import com.ben.emberr.data.local.room.DocumentBlockEntity
import com.ben.emberr.data.local.room.FolderEntity
import com.ben.emberr.data.local.room.ImageBlockEntity
import com.ben.emberr.data.local.room.NoteBlockEntity
import com.ben.emberr.data.local.room.NoteMetadataEntity
import com.ben.emberr.data.local.room.TagEntity
import kotlinx.serialization.Serializable

/**
 * The root wrapper for an Emberr backup file.
 * This represents the entire state of the local Room database at the time of export.
 * * --- THE 3 GOLDEN RULES OF BACKUP SCHEMA MIGRATION ---
 * To ensure old backups can be read by new app versions (Forward Compatibility)
 * and new backups don't crash old app versions (Backward Compatibility),
 * you MUST follow these rules when updating this class or ANY of its child entities:
 * * 1. NEVER RENAME OR DELETE: If a feature is removed, leave the variable here but
 * make it nullable (e.g., `val oldFeature: String? = null`).
 * 2. ALWAYS USE DEFAULTS: Any new variable added in a future update MUST have a
 * default value (e.g., `= emptyList()`, `= false`, `= ""`).
 * 3. NEVER CHANGE TYPES: Do not change an existing variable's type (e.g., Int to String).
 * Instead, deprecate the old one and create a new variable.
 * * Note: If a massive structural change is required in the future that breaks these
 * rules, increment the `version` integer and write a manual migration in SettingsViewModel.
 */

@Serializable
data class EmberrBackupData(
    val version: Int = 1,
    val exportTimestamp: Long,
    val notes: List<NoteMetadataEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val blocks: List<NoteBlockEntity> = emptyList(),
    val calendarTasks: List<CalendarTaskEntity> = emptyList(),
    val imageBlocks: List<ImageBlockEntity> = emptyList(),
    val documentBlocks: List<DocumentBlockEntity> = emptyList(),
    val bookmarkBlocks: List<BookmarkBlockEntity> = emptyList()
)