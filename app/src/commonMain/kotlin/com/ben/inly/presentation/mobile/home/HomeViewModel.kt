package com.ben.inly.presentation.mobile.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ben.inly.data.local.prefs.SettingsManager
import com.ben.inly.data.local.room.FolderEntity
import com.ben.inly.data.local.room.NoteMetadataEntity
import com.ben.inly.domain.media.LocalMediaGarbageCollector
import com.ben.inly.domain.model.*
import com.ben.inly.domain.repository.NoteRepository
import com.ben.inly.domain.template.DefaultTemplateSeeder
import com.ben.inly.domain.util.VoiceTaskEventBus
import com.ben.inly.domain.util.VoiceRecognizer
import com.ben.inly.domain.util.SyncCoordinator
import com.ben.inly.domain.util.TaskExtractor
import com.ben.inly.presentation.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

enum class SortType { LAST_EDITED, DATE_CREATED, NAME, MANUAL }
enum class SortOrder { ASCENDING, DESCENDING }

class HomeViewModel(
    private val repository: NoteRepository,
    private val settingsManager: SettingsManager,
    private val reminderScheduler: ReminderScheduler,
    private val taskExtractor: TaskExtractor,
    private val voiceRecognizer: VoiceRecognizer,
    private val templateSeeder: DefaultTemplateSeeder,
    private val localMediaGarbageCollector: LocalMediaGarbageCollector
) : ViewModel() {

    val sortType: StateFlow<SortType> = settingsManager.sortTypeFlow
        .map { SortType.valueOf(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, SortType.LAST_EDITED)

    val sortOrder: StateFlow<SortOrder> = settingsManager.sortOrderFlow
        .map { SortOrder.valueOf(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, SortOrder.DESCENDING)

    fun updateSort(type: SortType, order: SortOrder) {
        settingsManager.saveSortSettings(type.name, order.name)
    }

    private fun applyNoteSort(
        list: List<NoteMetadataEntity>,
        type: SortType,
        order: SortOrder
    ): List<NoteMetadataEntity> {
        val descending = order == SortOrder.DESCENDING
        return when (type) {
            SortType.MANUAL -> list.sortedBy { it.sortOrder }
            SortType.LAST_EDITED -> if (descending) list.sortedByDescending { it.updatedAt } else list.sortedBy { it.updatedAt }
            SortType.DATE_CREATED -> if (descending) list.sortedByDescending { it.createdAt } else list.sortedBy { it.createdAt }
            SortType.NAME -> if (descending) list.sortedByDescending { it.title.lowercase() } else list.sortedBy { it.title.lowercase() }
        }
    }

    fun toggleFolderExpansion(folderId: String) {
        _expandedFolderIds.update { if (it.contains(folderId)) it - folderId else it + folderId }
    }

    // orderedKeys: the flat visual order of sidebar rows as the user saw them,
    // passed in from HomeScreen so the VM doesn't re-derive a potentially different order.
    fun reorderItems(draggedKey: String, targetKey: String, insertBefore: Boolean, orderedKeys: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {

            // Filter orderedKeys to only sb_note_ and sb_folder_ items (skip headers etc).
            val scopeKeys = orderedKeys.filter {
                it.startsWith("sb_note_") || it.startsWith("sb_folder_")
            }.toMutableList()

            if (draggedKey !in scopeKeys) return@launch
            if (targetKey !in scopeKeys) return@launch

            // Move dragged item to new position.
            scopeKeys.remove(draggedKey)
            val targetIndex = scopeKeys.indexOf(targetKey)
            if (targetIndex == -1) return@launch
            val insertAt = if (insertBefore) targetIndex else targetIndex + 1
            scopeKeys.add(insertAt.coerceIn(0, scopeKeys.size), draggedKey)

            // Write new sortOrder values based on the new visual order.
            scopeKeys.forEachIndexed { index, key ->
                val order = index + 1
                when {
                    key.startsWith("sb_folder_") ->
                        repository.updateFolderSortOrder(key.removePrefix("sb_folder_"), order)
                    key.startsWith("sb_note_") ->
                        repository.updateNoteSortOrder(key.removePrefix("sb_note_"), order)
                }
            }

            // Auto-switch to MANUAL sort.
            withContext(Dispatchers.Main) {
                settingsManager.saveSortSettings(SortType.MANUAL.name, SortOrder.ASCENDING.name)
            }
        }
    }

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedFolderId = MutableStateFlow<String?>(null)
    val selectedFolderId: StateFlow<String?> = _selectedFolderId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    private val _selectedNoteIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedNoteIds: StateFlow<Set<String>> = _selectedNoteIds.asStateFlow()

    private val _selectedFolderIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFolderIds: StateFlow<Set<String>> = _selectedFolderIds.asStateFlow()

    private val _expandedFolderIds = MutableStateFlow<Set<String>>(emptySet())
    val expandedFolderIds: StateFlow<Set<String>> = _expandedFolderIds.asStateFlow()

    private val _remindersCount = MutableStateFlow(0)
    val remindersCount: StateFlow<Int> = _remindersCount.asStateFlow()

    private val _bookmarksCount = MutableStateFlow(0)
    val bookmarksCount: StateFlow<Int> = _bookmarksCount.asStateFlow()

    private val _imagesCount = MutableStateFlow(0)
    val imagesCount: StateFlow<Int> = _imagesCount.asStateFlow()

    private val _documentsCount = MutableStateFlow(0)
    val documentsCount: StateFlow<Int> = _documentsCount.asStateFlow()

    private val _allFolders = repository.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recentNotes = repository.getAllLinkableNotes()
        .map { notes ->
            notes.filter { !it.title.equals("Inbox", ignoreCase = true) }
                .sortedByDescending { it.updatedAt }.take(4)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val favoriteNotes = repository.getFavoriteNotes()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val currentSubFolders = combine(_allFolders, _selectedFolderId) { all, currentParent ->
        all.filter { !it.isDeleted && it.parentFolderId == currentParent }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val breadcrumbs = combine(_allFolders, _selectedFolderId) { all, currentId ->
        val path = mutableListOf<FolderEntity>()
        var curr = currentId
        while (curr != null) {
            val folder = all.find { it.folderId == curr }
            if (folder != null) {
                path.add(0, folder)
                curr = folder.parentFolderId
            } else {
                break
            }
        }
        path
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val foldersByParent: StateFlow<Map<String?, List<FolderEntity>>> =
        combine(
            _allFolders,
            sortType
        ) { all: List<FolderEntity>, type: SortType ->
            val sorted = if (type == SortType.MANUAL)
                all.filter { !it.isDeleted }.sortedBy { it.sortOrder }
            else
                all.filter { !it.isDeleted }
            sorted.groupBy { it.parentFolderId }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val notes: StateFlow<List<NoteMetadataEntity>> = combine(
        _selectedFolderId.flatMapLatest { folderId ->
            if (folderId == null) repository.getAllNotes()
            else repository.getNotesInFolder(folderId)
        },
        _searchQuery,
        sortType,
        sortOrder
    ) { noteList, query, activeSortType, activeSortOrder ->
        val visibleNotes = noteList.filter { !it.title.equals("Inbox", ignoreCase = true) }
        val folderFiltered = if (query.isNotBlank()) visibleNotes else visibleNotes.filter { it.folderId == _selectedFolderId.value }

        val finalFilteredList = if (query.isBlank()) {
            folderFiltered
        } else {
            val q = query.lowercase()
            val filteredList = mutableListOf<NoteMetadataEntity>()

            for (note in folderFiltered) {
                if (note.title.lowercase().contains(q) || note.snippet.lowercase().contains(q)) {
                    filteredList.add(note)
                    continue
                }

                val content = repository.getNoteContent(note.noteId)
                if (content != null) {
                    val matches = content.blocks.any { block ->
                        when (block) {
                            is TextBlock -> block.text.lowercase().contains(q)
                            is HeadingBlock -> block.text.lowercase().contains(q)
                            is CheckboxBlock -> block.text.lowercase().contains(q)
                            is BulletedListBlock -> block.text.lowercase().contains(q)
                            is NumberedListBlock -> block.text.lowercase().contains(q)
                            is ToggleBlock -> block.text.lowercase().contains(q)
                            is CodeBlock -> block.code.lowercase().contains(q)
                            is BookmarkBlock -> {
                                block.url.lowercase().contains(q) ||
                                        block.title?.lowercase()?.contains(q) == true ||
                                        block.description?.lowercase()?.contains(q) == true
                            }
                            is DocumentBlock -> block.fileName.lowercase().contains(q)
                            is ImageBlock -> block.localFilePath?.lowercase()?.contains(q) == true
                            else -> false
                        }
                    }
                    if (matches) {
                        filteredList.add(note)
                    }
                }
            }
            filteredList
        }

        when (activeSortType) {
            SortType.MANUAL -> finalFilteredList.sortedBy { it.sortOrder }
            SortType.LAST_EDITED -> if (activeSortOrder == SortOrder.DESCENDING) finalFilteredList.sortedByDescending { it.updatedAt } else finalFilteredList.sortedBy { it.updatedAt }
            SortType.DATE_CREATED -> if (activeSortOrder == SortOrder.DESCENDING) finalFilteredList.sortedByDescending { it.createdAt } else finalFilteredList.sortedBy { it.createdAt }
            SortType.NAME -> if (activeSortOrder == SortOrder.DESCENDING) finalFilteredList.sortedByDescending { it.title.lowercase() } else finalFilteredList.sortedBy { it.title.lowercase() }
        }
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // All non-trashed notes grouped by folderId, sorted per the active sort setting.
    // Root notes live under the null key. Drives the desktop sidebar tree.
    val notesByFolder: StateFlow<Map<String?, List<NoteMetadataEntity>>> =
        combine(repository.getAllNotes(), sortType, sortOrder) { allNotes, type, order ->
            applyNoteSort(
                allNotes.filter { !it.title.equals("Inbox", ignoreCase = true) },
                type,
                order
            ).groupBy { it.folderId }
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    // Every saved template (predefined + user-created), alphabetical per NoteDao.getAllTemplates.
    val templates: StateFlow<List<NoteMetadataEntity>> = repository.getAllTemplates()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _templateSearchQuery = MutableStateFlow("")
    val templateSearchQuery: StateFlow<String> = _templateSearchQuery.asStateFlow()

    // Case-insensitive name filter over `templates`, recombined whenever either the query or the
    // underlying template list changes - mirrors the `notes` search pattern above.
    val filteredTemplates: StateFlow<List<NoteMetadataEntity>> = combine(
        templates,
        _templateSearchQuery
    ) { allTemplates, query ->
        if (query.isBlank()) allTemplates
        else allTemplates.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Updates the templates-sheet search box; filteredTemplates recomputes automatically.
    fun updateTemplateSearchQuery(query: String) {
        _templateSearchQuery.value = query
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.cleanupOldTrashedNotes()
            templateSeeder.seedIfMissing()
            _isLoading.value = false
        }
        viewModelScope.launch(Dispatchers.IO) {
            delay(2_000.milliseconds)
            localMediaGarbageCollector.collectAndDeleteOrphanedMedia()
            com.ben.inly.domain.ai.models.cleanupPendingModelDeletions()
        }
        viewModelScope.launch {
            repository.getIncompleteTasksCount().collect { count ->
                _remindersCount.value = count
            }
        }
        viewModelScope.launch {
            repository.getImagesCount().collect { _imagesCount.value = it }
        }
        viewModelScope.launch {
            repository.getDocumentsCount().collect { _documentsCount.value = it }
        }
        viewModelScope.launch {
            repository.getBookmarksCount().collect { _bookmarksCount.value = it }
        }
    }

    fun selectFolder(folderId: String?) {
        _selectedFolderId.value = folderId
        clearSelection()
    }

    fun navigateUp() {
        val currentId = _selectedFolderId.value ?: return
        val currentFolder = _allFolders.value.find { it.folderId == currentId }
        _selectedFolderId.value = currentFolder?.parentFolderId
        clearSelection()
    }

    fun createNewFolder(name: String) {
        createFolderInParent(parentFolderId = _selectedFolderId.value, name = name, autoExpand = false)
    }

    // Used by the sidebar tree's per-folder "+" action. autoExpand opens the parent so the new child is visible.
    fun createFolderInParent(parentFolderId: String?, name: String, autoExpand: Boolean = true) {
        if (autoExpand) parentFolderId?.let { fid -> _expandedFolderIds.update { it + fid } }
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertFolder(
                FolderEntity(
                    folderId = UUID.randomUUID().toString(),
                    name = name,
                    parentFolderId = parentFolderId,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun renameNote(noteId: String, newTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SyncCoordinator.mutex.withLock {
                    val meta = repository.getNoteById(noteId) ?: return@withLock
                    val content = repository.getNoteContent(noteId) ?: NoteContent(blocks = emptyList())
                    repository.saveNote(meta.copy(title = newTitle, updatedAt = System.currentTimeMillis()), content)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun renameFolder(folderId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val folder = _allFolders.value.find { it.folderId == folderId } ?: return@launch
            repository.insertFolder(folder.copy(name = newName))
        }
    }

    fun trashNote(noteId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SyncCoordinator.mutex.withLock {
                    val meta = repository.getNoteById(noteId) ?: return@withLock
                    val content = repository.getNoteContent(noteId) ?: NoteContent(blocks = emptyList())
                    repository.saveNote(meta.copy(trashedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()), content)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun trashFolder(folderId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SyncCoordinator.mutex.withLock {
                    trashFolderContentsRecursively(folderId, System.currentTimeMillis())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleNoteSelection(noteId: String) {
        _selectedNoteIds.update { if (it.contains(noteId)) it - noteId else it + noteId }
    }

    fun toggleFolderSelection(folderId: String) {
        _selectedFolderIds.update { if (it.contains(folderId)) it - folderId else it + folderId }
    }

    fun clearSelection() {
        _selectedNoteIds.value = emptySet()
        _selectedFolderIds.value = emptySet()
    }

    private suspend fun deleteFolderRecursively(folderId: String) {
        val notesInFolder = repository.getNotesInFolder(folderId).first()
        notesInFolder.forEach { note ->
            repository.deleteNote(note.noteId, note.filePath)
        }

        val subFolders = _allFolders.value.filter { it.parentFolderId == folderId }
        subFolders.forEach { subFolder ->
            deleteFolderRecursively(subFolder.folderId)
        }

        repository.deleteFolder(folderId)
    }

    fun deleteSelectedItems() {
        val toDeleteNotes = _selectedNoteIds.value
        val toDeleteFolders = _selectedFolderIds.value
        val now = System.currentTimeMillis()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                SyncCoordinator.mutex.withLock {
                    toDeleteNotes.forEach { noteId ->
                        val meta = repository.getNoteById(noteId)
                        if (meta != null) {
                            val content = repository.getNoteContent(noteId) ?: NoteContent(blocks = emptyList())
                            repository.saveNote(meta.copy(trashedAt = now, updatedAt = now), content)
                        }
                    }

                    toDeleteFolders.forEach { folderId ->
                        trashFolderContentsRecursively(folderId, now)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            clearSelection()
        }
    }

    private suspend fun trashFolderContentsRecursively(folderId: String, trashTime: Long) {
        val notesInFolder = repository.getNotesInFolder(folderId).first()
        notesInFolder.forEach { note ->
            val content = repository.getNoteContent(note.noteId) ?: NoteContent(blocks = emptyList())
            repository.saveNote(note.copy(trashedAt = trashTime, updatedAt = trashTime), content)
        }

        val subFolders = _allFolders.value.filter { it.parentFolderId == folderId }
        subFolders.forEach { subFolder ->
            trashFolderContentsRecursively(subFolder.folderId, trashTime)
        }

        val folder = _allFolders.value.find { it.folderId == folderId }
        if (folder != null) {
            repository.insertFolder(folder.copy(isDeleted = true, createdAt = trashTime))
        }
    }

    fun createNewNote(title: String = "", forceHomeFolder: Boolean = false, onNoteCreated: (String) -> Unit) {
        val target = if (forceHomeFolder) null else _selectedFolderId.value
        createNoteInParent(parentFolderId = target, title = title, autoExpand = false, onNoteCreated = onNoteCreated)
    }

    // Used by the sidebar tree's per-folder "+" action.
    fun createNoteInParent(
        parentFolderId: String?,
        title: String = "",
        autoExpand: Boolean = true,
        onNoteCreated: (String) -> Unit
    ) {
        if (autoExpand) parentFolderId?.let { fid -> _expandedFolderIds.update { it + fid } }
        viewModelScope.launch(Dispatchers.IO) {
            val newNoteId = UUID.randomUUID().toString()
            val fileName = "note_$newNoteId.json"

            val metadata = NoteMetadataEntity(
                noteId = newNoteId,
                title = title,
                icon = null,
                folderId = parentFolderId,
                isDaily = false,
                dateString = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                filePath = fileName,
                snippet = ""
            )

            repository.saveNote(metadata, NoteContent(blocks = emptyList()))

            withContext(Dispatchers.Main) {
                onNoteCreated(newNoteId)
            }
        }
    }

    // Re-seeds any missing predefined template. Idempotent and cheap (a single Flow read plus,
    // at most, two inserts), so it's safe to call every time the templates sheet is opened -
    // this is what brings back a predefined template the user deleted, per spec.
    fun onTemplatesMenuOpened() {
        viewModelScope.launch(Dispatchers.IO) {
            templateSeeder.seedIfMissing()
        }
    }

    // Clones a template's content with fresh block/schema ids (see NoteBlock.deepCopyWithNewIds)
    // and saves it as a brand-new, regular note in the currently open folder.
    fun createNoteFromTemplate(templateId: String, onNoteCreated: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val templateMeta = repository.getNoteById(templateId) ?: return@launch
            val templateContent = repository.getNoteContent(templateId) ?: NoteContent(blocks = emptyList())
            val newNoteId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            val metadata = NoteMetadataEntity(
                noteId = newNoteId,
                title = templateMeta.title,
                icon = templateMeta.icon,
                coverImagePath = templateMeta.coverImagePath,
                showWordCount = templateMeta.showWordCount,
                folderId = _selectedFolderId.value,
                isDaily = false,
                dateString = null,
                createdAt = now,
                updatedAt = now,
                filePath = "",
                isTemplate = false
            )

            repository.saveNote(metadata, templateContent.deepCopyWithNewIds())

            withContext(Dispatchers.Main) {
                onNoteCreated(newNoteId)
            }
        }
    }

    // Persists the given content as a brand-new, reusable template (e.g. "Save as template"
    // from an open note, or the Templates sheet's "Create New Template" button, which passes
    // empty content - same blank-title-allowed convention as createNewNote). Templates never
    // carry a folderId - the templates sheet is flat.
    fun saveAsTemplate(title: String, content: NoteContent, onTemplateCreated: (String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val newTemplateId = UUID.randomUUID().toString()
            val metadata = NoteMetadataEntity(
                noteId = newTemplateId,
                title = title,
                icon = null,
                folderId = null,
                isDaily = false,
                dateString = null,
                createdAt = now,
                updatedAt = now,
                filePath = "",
                isTemplate = true
            )
            repository.saveNote(metadata, content)

            withContext(Dispatchers.Main) {
                onTemplateCreated(newTemplateId)
            }
        }
    }

    // Permanently removes a template (predefined or user-created). Templates are hard-deleted
    // rather than trashed - they don't participate in the Trash flow.
    fun deleteTemplate(templateId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTemplate(templateId)
        }
    }

    private val _isVoiceTaskListening = MutableStateFlow(false)
    val isVoiceTaskListening: StateFlow<Boolean> = _isVoiceTaskListening.asStateFlow()

    private val _voiceTaskPartialText = MutableStateFlow("")
    val voiceTaskPartialText: StateFlow<String> = _voiceTaskPartialText.asStateFlow()

    private fun processVoiceTask(transcript: String) {
        if (transcript.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val parsedTasks = taskExtractor.extractTasks(transcript)
            if (parsedTasks.isEmpty()) return@launch

            val systemTZ = TimeZone.currentSystemDefault()

            val tasksByDate = parsedTasks.groupBy { task ->
                if (task.timestamp != null) {
                    Instant.fromEpochMilliseconds(task.timestamp)
                        .toLocalDateTime(systemTZ)
                        .date
                        .toString()
                } else {
                    Clock.System.todayIn(systemTZ).toString()
                }
            }

            try {
                SyncCoordinator.mutex.withLock {
                    for ((targetDateString, tasks) in tasksByDate) {
                        val content = repository.getDailyNote(targetDateString)
                        val currentBlocks = mutableListOf<NoteBlock>()

                        if (content != null && content.blocks.isNotEmpty()) {
                            currentBlocks.addAll(content.blocks)
                        } else {
                            currentBlocks.add(TextBlock(id = "root_$targetDateString", text = ""))
                        }

                        for (task in tasks) {
                            val newVoiceTaskBlock = CheckboxBlock(
                                id = UUID.randomUUID().toString(),
                                text = task.taskText,
                                isChecked = false,
                                reminderTimestamp = task.timestamp,
                                indentationLevel = 0
                            )

                            currentBlocks.add(newVoiceTaskBlock)

                            VoiceTaskEventBus.emitTaskAdded(targetDateString, newVoiceTaskBlock)

                            task.timestamp?.let { timeInMillis ->
                                reminderScheduler.schedule(
                                    blockId = newVoiceTaskBlock.id,
                                    noteTitle = "Daily: $targetDateString",
                                    text = task.taskText,
                                    timestamp = timeInMillis
                                )
                            }
                        }

                        repository.saveDailyNote(targetDateString, NoteContent(blocks = currentBlocks))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startVoiceTaskListening(onPermissionNeeded: () -> Unit = {}) {
        _isVoiceTaskListening.value = true
        _voiceTaskPartialText.value = "Listening..."

        voiceRecognizer.startListening(
            onPartial = { _voiceTaskPartialText.value = it },
            onResult = { result ->
                _isVoiceTaskListening.value = false
                processVoiceTask(result)
                _voiceTaskPartialText.value = ""
            },
            onError = { error ->
                _isVoiceTaskListening.value = false
                if (error == "No match") {
                    _voiceTaskPartialText.value = ""
                } else {
                    _voiceTaskPartialText.value = error
                }
            },
            onPermissionNeeded = {
                _isVoiceTaskListening.value = false
                _voiceTaskPartialText.value = ""
                onPermissionNeeded()
            }
        )
    }

    fun stopVoiceTaskListening() {
        voiceRecognizer.stopListening()
        _isVoiceTaskListening.value = false
        _voiceTaskPartialText.value = ""
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecognizer.destroy()
    }

    fun moveNote(noteId: String, targetFolderId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SyncCoordinator.mutex.withLock {
                    val meta = repository.getNoteById(noteId) ?: return@withLock
                    val content = repository.getNoteContent(noteId) ?: NoteContent(blocks = emptyList())
                    repository.saveNote(meta.copy(folderId = targetFolderId, updatedAt = System.currentTimeMillis()), content)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun moveFolder(folderId: String, targetParentId: String?) {
        // Prevent dropping a folder into itself or its own descendants.
        if (folderId == targetParentId) return
        val allFolders = _allFolders.value
        fun isDescendant(candidateId: String): Boolean {
            var curr: String? = candidateId
            while (curr != null) {
                if (curr == folderId) return true
                curr = allFolders.find { it.folderId == curr }?.parentFolderId
            }
            return false
        }
        if (targetParentId != null && isDescendant(targetParentId)) return
        viewModelScope.launch(Dispatchers.IO) {
            val folder = allFolders.find { it.folderId == folderId } ?: return@launch
            repository.insertFolder(folder.copy(parentFolderId = targetParentId))
        }
    }
}