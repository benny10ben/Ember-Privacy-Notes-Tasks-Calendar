package com.ben.inly.presentation.shared.editor

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ben.inly.data.local.room.DatabaseTemplateEntity
import com.ben.inly.data.local.room.NoteMetadataEntity
import com.ben.inly.data.local.room.TagEntity
import com.ben.inly.domain.model.*
import com.ben.inly.domain.repository.NoteRepository
import com.ben.inly.domain.util.AiEventBus
import com.ben.inly.domain.util.AudioRecorder
import com.ben.inly.domain.util.FormulaEngine
import com.ben.inly.domain.util.HtmlMetadataFetcher
import com.ben.inly.domain.util.MediaStorageHelper
import com.ben.inly.domain.util.SyncCoordinator
import com.ben.inly.presentation.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@Stable
data class FocusRequest(
    val id: String,
    val placeCursorAtEnd: Boolean = false,
    val nonce: String = UUID.randomUUID().toString()
)

abstract class BaseEditorViewModel(
    protected val repository: NoteRepository,
    protected val mediaStorageHelper: MediaStorageHelper,
    protected val reminderScheduler: ReminderScheduler,
    protected val audioRecorder: AudioRecorder,
    private val appScope: CoroutineScope
) : ViewModel() {

    // AI event bus
    init {
        viewModelScope.launch {
            AiEventBus.indexRequest.collect {
                forceSyncAndIndexForAi()
            }
        }
        ActiveEditorRegistry.register(this)
    }

    fun forceSyncAndIndexForAi() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentHash = computeBlocksHash()
            if (currentHash != lastIndexedContentHash) {

                withContext(NonCancellable) {
                    performSave()
                    try {
                        performIndexing()
                        lastIndexedContentHash = currentHash
                        isAiIndexDirty = false
                        AiEventBus.notifyIndexComplete()
                    } catch (_: Exception) {
                        // Handle error
                    }
                }

            }
        }
    }
    // ----

    protected val _blocks = MutableStateFlow<List<NoteBlock>>(emptyList())
    var lastIndexedContentHash: Int = 0
    fun computeBlocksHash(): Int {
        return _blocks.value
            .filter { !it.isDeleted }
            .joinToString(separator = "|") { block ->
                when (block) {
                    is TextBlock -> "${block.id}:${block.text}"
                    is HeadingBlock -> "${block.id}:${block.text}"
                    is CheckboxBlock -> "${block.id}:${block.text}:${block.isChecked}"
                    is BulletedListBlock -> "${block.id}:${block.text}"
                    is NumberedListBlock -> "${block.id}:${block.text}"
                    is ToggleBlock -> "${block.id}:${block.text}"
                    is CodeBlock -> "${block.id}:${block.code}"
                    is QuoteBlock -> "${block.id}:${block.text}"
                    is DatabaseBlock -> "${block.id}:${block.rows.size}:${block.columns.size}"
                    else -> block.id
                }
            }
            .hashCode()
    }

    val visibleBlocks: StateFlow<List<NoteBlock>> = _blocks.map { allBlocks ->
        val visible = mutableListOf<NoteBlock>()
        var skipUntilLevel: Int? = null
        for (block in allBlocks) {
            if (block.isDeleted) continue

            if (skipUntilLevel != null) {
                if (block.indentationLevel > skipUntilLevel) continue
                else skipUntilLevel = null
            }
            visible.add(block)
            if (block is ToggleBlock && !block.isExpanded) skipUntilLevel = block.indentationLevel
        }

        // Last-resort guard against a stray top-level duplicate id (sync corruption, a stale copy
        // left behind by a non-recursive removal, ...) reaching EditorScreen's LazyColumn, which
        // crashes with "Key was already used" the moment two entries in this list share an id.
        val deduped = LinkedHashMap<String, NoteBlock>()
        visible.forEach { deduped[it.id] = it }
        deduped.values.toList()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    protected val _focusRequest = MutableStateFlow<FocusRequest?>(null)
    val focusRequest: StateFlow<FocusRequest?> = _focusRequest.asStateFlow()

    protected val _selectedBlockIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedBlockIds: StateFlow<Set<String>> = _selectedBlockIds.asStateFlow()

    protected var currentlyFocusedBlockId: String? = null
    protected var autosaveJob: Job? = null
    protected var indexingJob: Job? = null

    // Returns whether the write actually persisted - implementations catch their own IO failures
    // rather than throwing, so callers that need to react to (e.g. revert an optimistic UI change on)
    // a failed save must check this rather than relying on an exception
    protected abstract suspend fun performSave(): Boolean
    protected abstract suspend fun performIndexing()
    protected abstract fun getNoteTitleForReminder(): String

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    val allLinkableNotes: StateFlow<List<NoteMetadataEntity>> = repository.getAllLinkableNotes()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val databaseTemplates: StateFlow<List<DatabaseTemplateEntity>> = repository.getAllDatabaseTemplates()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Dedicated Json instance for DatabaseBlock schema templates (columns/views only).
    private val templateJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val undoStack = mutableListOf<List<NoteBlock>>()
    private val redoStack = mutableListOf<List<NoteBlock>>()
    private var lastHistorySaveTime = 0L
    private val MAX_HISTORY_SIZE = 50

    private val historyLock = Any()

    protected var lastLocalMutationTime: Long = 0L
    private val LOCAL_MUTATION_COOLDOWN_MS = 3000L

    protected fun isWithinLocalMutationCooldown(): Boolean =
        System.currentTimeMillis() - lastLocalMutationTime < LOCAL_MUTATION_COOLDOWN_MS

    protected var isAiIndexDirty = false

    open fun scheduleAutosave() {
        isAiIndexDirty = true

        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(1000L.milliseconds)
            performSave()
        }
    }

    suspend fun flushPendingSave() {
        if (autosaveJob?.isActive == true) {
            autosaveJob?.cancel()
            performSave()
        }
    }

    protected fun isNoteActuallyEmpty(blocks: List<NoteBlock>): Boolean {
        if (blocks.isEmpty()) return true
        if (blocks.size == 1) {
            val first = blocks.first()
            return first is TextBlock && first.text.isBlank()
        }
        return false
    }

    fun togglePinSelectedBlocks() {
        val toToggle = _selectedBlockIds.value
        if (toToggle.isEmpty()) return
        val now = System.currentTimeMillis()
        modifyBlocks(forceSave = true) { list ->
            list.map { b -> if (b.id in toToggle) b.withPin(!b.isPinned, now) else b }
        }
        clearSelection()
        scheduleAutosave()
    }

    private var lastHistoryFocusedBlockId: String? = null

    protected fun modifyBlocks(forceSave: Boolean = false, action: (List<NoteBlock>) -> List<NoteBlock>) {
        lateinit var newList: List<NoteBlock>
        val currentList = _blocks.getAndUpdate { list ->
            val rawList = action(list)

            val segregatedList = if (rawList.any { it.isPinned }) {
                val pinned = rawList.filter { it.isPinned }
                val unpinned = rawList.filter { !it.isPinned }
                pinned + unpinned
            } else {
                rawList
            }

            val renumbered = if (segregatedList.any { it is NumberedListBlock }) {
                recalculateNumberedLists(segregatedList)
            } else {
                segregatedList
            }

            val withTrailingBlock = renumbered.toMutableList()
            val lastVisible = withTrailingBlock.lastOrNull { !it.isDeleted }
            val needsTrailing = lastVisible == null ||
                    lastVisible !is TextBlock ||
                    lastVisible.text.isNotEmpty() ||
                    lastVisible.isPinned
            if (needsTrailing) {
                withTrailingBlock.add(
                    TextBlock(
                        id = UUID.randomUUID().toString(),
                        text = "",
                        indentationLevel = 0,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }

            newList = withTrailingBlock
            newList
        }

        if (currentList == newList) return

        lastLocalMutationTime = System.currentTimeMillis()

        synchronized(historyLock) {
            val now = System.currentTimeMillis()
            val isStructuralChange = currentList.size != newList.size ||
                    currentList.zip(newList).any { (old, new) ->
                        old.id != new.id || old::class != new::class
                    }
            val enoughTimePassed = now - lastHistorySaveTime > 2500
            val blockChanged = lastHistoryFocusedBlockId != currentlyFocusedBlockId

            val shouldSave = forceSave || isStructuralChange || enoughTimePassed || blockChanged

            if (shouldSave) {
                undoStack.add(currentList)
                if (undoStack.size > MAX_HISTORY_SIZE) undoStack.removeAt(0)
                redoStack.clear()
                lastHistorySaveTime = now
                lastHistoryFocusedBlockId = currentlyFocusedBlockId
            }

            lastWasStructural = isStructuralChange
            updateHistoryState()
        }
    }

    fun startHardwareRecording() {
        audioRecorder.startRecording()
    }

    fun stopHardwareRecording(blockId: String, cancel: Boolean = false) {
        val result = audioRecorder.stopRecording(cancel)
        if (result != null && !cancel) {
            handleVoiceRecorded(blockId, result.first, result.second)
        }
    }

    fun handleDbFilePicked(blockId: String, rowId: String, colId: String, uriString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaInfo = mediaStorageHelper.copyUriToInternalStorage(uriString)
            if (mediaInfo != null) {
                withContext(Dispatchers.Main) {
                    val cleanFileName = mediaInfo.localFileName.substringAfterLast("/")
                    val now = System.currentTimeMillis()
                    modifyBlocks { list ->
                        mapBlockById(list, blockId, now) { db ->
                            if (db is DatabaseBlock) {
                                val updatedRows = db.rows.map { row ->
                                    if (row.id == rowId) {
                                        val currentFiles = (row.cells[colId] as? CellData.MediaList)?.files ?: emptyList()
                                        val newFiles = currentFiles + MediaItem(cleanFileName, mediaInfo.originalName)

                                        val newMap = row.cells.toMutableMap()
                                        newMap[colId] = CellData.MediaList(newFiles)
                                        row.copy(cells = newMap, updatedAt = now)
                                    } else row
                                }
                                db.copy(rows = updatedRows, updatedAt = now)
                            } else db
                        }
                    }
                    scheduleAutosave()
                }
            }
        }
    }

    fun stopDbHardwareRecording(blockId: String, rowId: String, colId: String, cancel: Boolean = false) {
        val result = audioRecorder.stopRecording(cancel)
        if (result != null && !cancel) {
            val cleanFileName = result.first.substringAfterLast("/")
            val now = System.currentTimeMillis()
            modifyBlocks { list ->
                mapBlockById(list, blockId, now) { db ->
                    if (db is DatabaseBlock) {
                        val updatedRows = db.rows.map { row ->
                            if (row.id == rowId) {
                                val currentFiles = (row.cells[colId] as? CellData.MediaList)?.files ?: emptyList()
                                val newFiles = currentFiles + MediaItem(cleanFileName, "Audio Recording.m4a")

                                val newMap = row.cells.toMutableMap()
                                newMap[colId] = CellData.MediaList(newFiles)
                                row.copy(cells = newMap, updatedAt = now)
                            } else row
                        }
                        db.copy(rows = updatedRows, updatedAt = now)
                    } else db
                }
            }
            scheduleAutosave()
        }
    }

    fun playAudio(fileName: String, onComplete: () -> Unit) {
        audioRecorder.play(fileName, onComplete)
    }

    fun stopAudio() {
        audioRecorder.stopPlaying()
    }

    fun toggleCheckbox(blockId: String, isChecked: Boolean) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) {
                if (it is CheckboxBlock) {
                    if (isChecked) reminderScheduler.cancel(blockId)
                    it.copy(isChecked = isChecked, updatedAt = now)
                } else it
            }
        }
        scheduleAutosave()
    }

    fun toggleToggleBlock(blockId: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) {
                if (it is ToggleBlock) it.copy(isExpanded = !it.isExpanded, updatedAt = now) else it
            }
        }
        scheduleAutosave()
    }

    fun toggleFormat(format: String) {
        val id = currentlyFocusedBlockId ?: return
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, id, now) { b ->
                when (format) {
                    "bold" -> updateFormat(b, !b.isBold, b.isItalic, b.isStrikeThrough, b.isUnderlined, now)
                    "italic" -> updateFormat(b, b.isBold, !b.isItalic, b.isStrikeThrough, b.isUnderlined, now)
                    "strike" -> updateFormat(b, b.isBold, b.isItalic, !b.isStrikeThrough, b.isUnderlined, now)
                    "underline" -> updateFormat(b, b.isBold, b.isItalic, b.isStrikeThrough, !b.isUnderlined, now)
                    else -> b
                }
            }
        }
        scheduleAutosave()
    }

    private fun updateFormat(b: NoteBlock, bld: Boolean, itl: Boolean, stk: Boolean, und: Boolean, now: Long) = when (b) {
        is TextBlock -> b.copy(isBold = bld, isItalic = itl, isStrikeThrough = stk, isUnderlined = und, updatedAt = now)
        is HeadingBlock -> b.copy(isBold = bld, isItalic = itl, isStrikeThrough = stk, isUnderlined = und, updatedAt = now)
        is CheckboxBlock -> b.copy(isBold = bld, isItalic = itl, isStrikeThrough = stk, isUnderlined = und, updatedAt = now)
        is BulletedListBlock -> b.copy(isBold = bld, isItalic = itl, isStrikeThrough = stk, isUnderlined = und, updatedAt = now)
        is NumberedListBlock -> b.copy(isBold = bld, isItalic = itl, isStrikeThrough = stk, isUnderlined = und, updatedAt = now)
        is ToggleBlock -> b.copy(isBold = bld, isItalic = itl, isStrikeThrough = stk, isUnderlined = und, updatedAt = now)
        is CodeBlock -> b
        is QuoteBlock -> b.copy(isBold = bld, isItalic = itl, isStrikeThrough = stk, isUnderlined = und, updatedAt = now)
        else -> b
    }

    fun adjustIndentation(increment: Boolean) {
        val id = currentlyFocusedBlockId ?: return
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, id, now) { b ->
                val newLevel = if (increment) b.indentationLevel + 1 else maxOf(0, b.indentationLevel - 1)
                when (b) {
                    is TextBlock -> b.copy(indentationLevel = newLevel, updatedAt = now)
                    is HeadingBlock -> b.copy(indentationLevel = newLevel, updatedAt = now)
                    is CheckboxBlock -> b.copy(indentationLevel = newLevel, updatedAt = now)
                    is BulletedListBlock -> b.copy(indentationLevel = newLevel, updatedAt = now)
                    is NumberedListBlock -> b.copy(indentationLevel = newLevel, updatedAt = now)
                    is ToggleBlock -> b.copy(indentationLevel = newLevel, updatedAt = now)
                    is QuoteBlock -> b.copy(indentationLevel = newLevel, updatedAt = now)
                    else -> b
                }
            }
        }
        scheduleAutosave()
    }

    fun changeFocusedBlockType(type: String) {
        val id = currentlyFocusedBlockId ?: return
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            spliceAtBlock(list, id, now) { mutable, idx ->
                val b = mutable[idx]
                val rawText = getBlockText(b)

                val slashIndex = rawText.lastIndexOf('/')
                val isActivelySearching = slashIndex != -1 && !rawText.substring(slashIndex).contains(" ")
                val cleanedText = if (isActivelySearching) rawText.substring(0, slashIndex) else rawText

                val isNonTextBlock = type == "divider_solid" || type == "divider_dots" || type == "voice"

                if (isNonTextBlock && cleanedText.isNotEmpty()) {
                    val updatedText = when (b) {
                        is TextBlock -> b.copy(text = cleanedText, updatedAt = now)
                        is HeadingBlock -> b.copy(text = cleanedText, updatedAt = now)
                        is CheckboxBlock -> b.copy(text = cleanedText, updatedAt = now)
                        is BulletedListBlock -> b.copy(text = cleanedText, updatedAt = now)
                        is NumberedListBlock -> b.copy(text = cleanedText, updatedAt = now)
                        is ToggleBlock -> b.copy(text = cleanedText, updatedAt = now)
                        is CodeBlock -> b.copy(code = cleanedText, updatedAt = now)
                        is QuoteBlock -> b.copy(text = cleanedText, updatedAt = now)
                        else -> b
                    }
                    mutable[idx] = updatedText

                    val newId = UUID.randomUUID().toString()
                    val newBlock = when (type) {
                        "divider_solid" -> SolidDividerBlock(id = newId, indentationLevel = b.indentationLevel, updatedAt = now)
                        "divider_dots" -> ThreeDotDividerBlock(id = newId, indentationLevel = b.indentationLevel, updatedAt = now)
                        "voice" -> VoiceBlock(id = newId, indentationLevel = b.indentationLevel, updatedAt = now)
                        else -> b
                    }
                    mutable.add(idx + 1, newBlock.withPin(b.isPinned, now))
                } else {
                    val newBlock = when (type) {
                        "text" -> TextBlock(id, cleanedText, b.indentationLevel, updatedAt = now)
                        "h1" -> HeadingBlock(id, cleanedText, 1, b.indentationLevel, updatedAt = now)
                        "h2" -> HeadingBlock(id, cleanedText, 2, b.indentationLevel, updatedAt = now)
                        "checkbox" -> CheckboxBlock(id, cleanedText, false, b.indentationLevel, updatedAt = now)
                        "quote" -> QuoteBlock(id, cleanedText, b.indentationLevel, updatedAt = now)
                        "bullet" -> BulletedListBlock(id, cleanedText, b.indentationLevel, updatedAt = now)
                        "number" -> NumberedListBlock(id, cleanedText, 1, b.indentationLevel, updatedAt = now)
                        "toggle" -> ToggleBlock(id, cleanedText, true, b.indentationLevel, updatedAt = now)
                        "code" -> CodeBlock(id, cleanedText, updatedAt = now)
                        "voice" -> VoiceBlock(id, indentationLevel = b.indentationLevel, updatedAt = now)
                        "divider_solid" -> SolidDividerBlock(id = id, indentationLevel = b.indentationLevel, updatedAt = now)
                        "divider_dots" -> ThreeDotDividerBlock(id = id, indentationLevel = b.indentationLevel, updatedAt = now)
                        else -> b
                    }

                    mutable[idx] = newBlock.withPin(b.isPinned, now)

                    if (type == "toggle") {
                        val nextBlock = mutable.getOrNull(idx + 1)
                        if (nextBlock == null || nextBlock.indentationLevel <= b.indentationLevel) {
                            mutable.add(idx + 1, TextBlock(UUID.randomUUID().toString(), "", b.indentationLevel + 1, updatedAt = now))
                        }
                    }
                }
            }
        }
        scheduleAutosave()
    }

    private fun getBlockText(b: NoteBlock) = when (b) {
        is TextBlock -> b.text
        is HeadingBlock -> b.text
        is CheckboxBlock -> b.text
        is BulletedListBlock -> b.text
        is NumberedListBlock -> b.text
        is ToggleBlock -> b.text
        is CodeBlock -> b.code
        is QuoteBlock -> b.text
        else -> ""
    }

    fun handleEnter(id: String, textBefore: String, textAfter: String) {
        var blockToFocusId = ""
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            spliceAtBlock(list, id, now) { mutable, idx ->
                val cur = mutable[idx]
                val newId = UUID.randomUUID().toString()
                blockToFocusId = newId
                var insertIdx = idx + 1

                val updatedCurrent = when (cur) {
                    is TextBlock -> cur.copy(text = textBefore, updatedAt = now)
                    is HeadingBlock -> cur.copy(text = textBefore, updatedAt = now)
                    is CheckboxBlock -> cur.copy(text = textBefore, updatedAt = now)
                    is BulletedListBlock -> cur.copy(text = textBefore, updatedAt = now)
                    is NumberedListBlock -> cur.copy(text = textBefore, updatedAt = now)
                    is ToggleBlock -> cur.copy(text = textBefore, updatedAt = now)
                    is CodeBlock -> cur.copy(code = textBefore, updatedAt = now)
                    is QuoteBlock -> cur.copy(text = textBefore, updatedAt = now)
                    else -> cur
                }

                val newBlock = when (cur) {
                    is CheckboxBlock -> CheckboxBlock(newId, textAfter, false, cur.indentationLevel, isPinned = cur.isPinned, updatedAt = now)
                    is BulletedListBlock -> BulletedListBlock(newId, textAfter, cur.indentationLevel, isPinned = cur.isPinned, updatedAt = now)
                    is NumberedListBlock -> NumberedListBlock(newId, textAfter, cur.number + 1, cur.indentationLevel, isPinned = cur.isPinned, updatedAt = now)
                    is HeadingBlock -> TextBlock(newId, textAfter, 0, isPinned = cur.isPinned, updatedAt = now)
                    is QuoteBlock -> QuoteBlock(newId, textAfter, cur.indentationLevel, isPinned = cur.isPinned, updatedAt = now)
                    is ToggleBlock -> {
                        if (cur.isExpanded) {
                            TextBlock(newId, textAfter, cur.indentationLevel + 1, isPinned = cur.isPinned, updatedAt = now)
                        } else {
                            var i = idx + 1
                            while (i < mutable.size && mutable[i].indentationLevel > cur.indentationLevel) i++
                            insertIdx = i
                            ToggleBlock(newId, textAfter, false, cur.indentationLevel, isPinned = cur.isPinned, updatedAt = now)
                        }
                    }
                    else -> TextBlock(newId, textAfter, cur.indentationLevel, isPinned = cur.isPinned, updatedAt = now)
                }

                mutable[idx] = updatedCurrent
                mutable.add(insertIdx, newBlock)
            }
        }

        if (blockToFocusId.isNotEmpty()) {
            _focusRequest.value = FocusRequest(id = blockToFocusId)
            scheduleAutosave()
        }
    }

    fun handleBackspaceOnEmpty(id: String) {
        val currentBlocks = _blocks.value
        val idx = currentBlocks.indexOfFirst { it.id == id }
        if (idx == -1) return

        val cur = currentBlocks[idx]
        val now = System.currentTimeMillis()

        if (cur !is TextBlock) {
            modifyBlocks { list ->
                spliceAtBlock(list, id, now) { mutable, i ->
                    mutable[i] = TextBlock(cur.id, "", cur.indentationLevel, isPinned = cur.isPinned, updatedAt = now)
                }
            }
            scheduleAutosave()
            return
        }

        if (currentBlocks.size <= 1) return

        val prevBlock = currentBlocks.subList(0, idx).lastOrNull { !it.isDeleted }

        if (prevBlock is ToggleBlock && prevBlock.indentationLevel == cur.indentationLevel - 1) {
            val nextBlock = currentBlocks.subList(idx + 1, currentBlocks.size).firstOrNull { !it.isDeleted }
            val isOnlyChild = nextBlock == null || nextBlock.indentationLevel < cur.indentationLevel
            if (isOnlyChild) return
        }

        if (prevBlock != null) {
            val isMediaOrDivider = prevBlock is ImageBlock || prevBlock is DocumentBlock ||
                    prevBlock is DatabaseBlock || prevBlock is SolidDividerBlock ||
                    prevBlock is ThreeDotDividerBlock || prevBlock is BookmarkBlock ||
                    prevBlock is SketchBlock || prevBlock is VoiceBlock

            if (isMediaOrDivider) {
                modifyBlocks { list ->
                    deleteBlockEverywhereById(list, prevBlock.id, now)
                }
                scheduleAutosave()
            } else {
                _focusRequest.value = FocusRequest(id = prevBlock.id, placeCursorAtEnd = true)
                viewModelScope.launch {
                    delay(50.milliseconds)
                    modifyBlocks { list ->
                        deleteBlockEverywhereById(list, id, now)
                    }
                    scheduleAutosave()
                }
            }
        } else {
            val nextBlock = currentBlocks.subList(idx + 1, currentBlocks.size).firstOrNull { !it.isDeleted }
            if (nextBlock != null) {
                _focusRequest.value = FocusRequest(id = nextBlock.id, placeCursorAtEnd = false)
                viewModelScope.launch {
                    delay(50.milliseconds)
                    modifyBlocks { list ->
                        deleteBlockEverywhereById(list, id, now)
                    }
                    scheduleAutosave()
                }
            }
        }
    }

    private fun deleteBlockEverywhereById(list: List<NoteBlock>, id: String, now: Long): List<NoteBlock> {
        val spliced = spliceAtBlock(list, id, now) { mutable, i -> mutable[i] = mutable[i].markDeleted() }
        return mapBlockById(spliced, id, now) { it.markDeleted() }
    }

    fun addBlankBlockBelowFocused() {
        val targetId = currentlyFocusedBlockId ?: _blocks.value.lastOrNull()?.id ?: return
        val newId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            val idx = list.indexOfFirst { it.id == targetId }
            val indent = if (idx != -1) list[idx].indentationLevel else 0
            val isPinnedContext = if (idx != -1) list[idx].isPinned else false
            val new = TextBlock(id = newId, text = "", indentationLevel = indent, isPinned = isPinnedContext, updatedAt = now)
            list.toMutableList().apply {
                if (idx != -1) add(idx + 1, new) else add(new)
            }
        }
        _focusRequest.value = FocusRequest(id = newId)
        scheduleAutosave()
    }

    fun setFocusedBlock(id: String) { currentlyFocusedBlockId = id }
    fun clearFocusRequest() { _focusRequest.value = null }
    fun toggleSelection(id: String) { _selectedBlockIds.update { if (it.contains(id)) it - id else it + id } }
    fun clearSelection() { _selectedBlockIds.value = emptySet() }
    fun selectAllBlocks() { _selectedBlockIds.value = _blocks.value.map { it.id }.toSet() }

    fun getSelectedText(): String {
        val ids = _selectedBlockIds.value
        return _blocks.value.filter { it.id in ids }.joinToString("\n") { getBlockText(it) }
    }

    fun deleteSelectedBlocks() {
        val toDelete = _selectedBlockIds.value
        val now = System.currentTimeMillis()

        modifyBlocks { list ->
            val afterDelete = list.map { b -> if (b.id in toDelete) b.markDeleted() else b }
            val hasVisible = afterDelete.any { !it.isDeleted }
            if (!hasVisible) {
                afterDelete + listOf(TextBlock(id = UUID.randomUUID().toString(), text = "", updatedAt = now))
            } else {
                afterDelete
            }
        }

        clearSelection()
        scheduleAutosave()
    }

    fun cutSelectedBlocks(): String {
        val text = getSelectedText()
        deleteSelectedBlocks()
        return text
    }

    fun addBlockAboveSelection() {
        val selected = _selectedBlockIds.value
        if (selected.isEmpty()) return
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            spliceAtBlock(list, selected.first(), now) { mutable, idx ->
                val targetLevel = mutable[idx].indentationLevel
                mutable.add(idx, TextBlock(id = UUID.randomUUID().toString(), text = "", indentationLevel = targetLevel, updatedAt = now))
            }
        }
        clearSelection()
        scheduleAutosave()
    }

    fun addBlockBelowSelection() {
        val selected = _selectedBlockIds.value
        if (selected.isEmpty()) return
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            spliceAtBlock(list, selected.last(), now) { mutable, idx ->
                val targetLevel = mutable[idx].indentationLevel
                mutable.add(idx + 1, TextBlock(id = UUID.randomUUID().toString(), text = "", indentationLevel = targetLevel, updatedAt = now))
            }
        }
        clearSelection()
        scheduleAutosave()
    }

    private var lastWasStructural = false

    fun updateBlockText(blockId: String, newText: String) {
        val now = System.currentTimeMillis()

        modifyBlocks(forceSave = false) { list ->
            mapBlockById(list, blockId, now) { b ->
                when (b) {
                    is TextBlock -> b.copy(text = newText, updatedAt = now)
                    is HeadingBlock -> b.copy(text = newText, updatedAt = now)
                    is CheckboxBlock -> b.copy(text = newText, updatedAt = now)
                    is BulletedListBlock -> b.copy(text = newText, updatedAt = now)
                    is NumberedListBlock -> b.copy(text = newText, updatedAt = now)
                    is ToggleBlock -> b.copy(text = newText, updatedAt = now)
                    is CodeBlock -> b.copy(code = newText, updatedAt = now)
                    is QuoteBlock -> b.copy(text = newText, updatedAt = now)
                    else -> b
                }
            }
        }
        scheduleAutosave()
    }

    private fun updateHistoryState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun withTombstonesForRemoved(current: List<NoteBlock>, target: List<NoteBlock>): List<NoteBlock> {
        val targetIds = target.mapTo(HashSet()) { it.id }
        val tombstones = current.filter { it.id !in targetIds && !it.isDeleted }.map { it.markDeleted() }
        return if (tombstones.isEmpty()) target else target + tombstones
    }

    fun undo() {
        val previousList = synchronized(historyLock) {
            if (undoStack.isEmpty()) return
            undoStack.last()
        }

        val focusId = currentlyFocusedBlockId
        var targetFocusId: String? = null

        fun isFocusable(b: NoteBlock) = b is TextBlock || b is HeadingBlock || b is CheckboxBlock ||
                b is BulletedListBlock || b is NumberedListBlock || b is ToggleBlock ||
                b is QuoteBlock || b is CodeBlock

        if (focusId != null && previousList.any { !it.isDeleted && it.id == focusId && isFocusable(it) }) {
            targetFocusId = focusId
        } else if (focusId != null) {
            val currentList = _blocks.value
            val removedIndex = currentList.indexOfFirst { it.id == focusId }
            if (removedIndex > 0) {
                targetFocusId = currentList.subList(0, removedIndex).lastOrNull { !it.isDeleted && isFocusable(it) }?.id
            }
        }

        if (targetFocusId == null) {
            targetFocusId = previousList.lastOrNull { !it.isDeleted && isFocusable(it) }?.id
        }

        val needsFocusShift = targetFocusId != null && targetFocusId != focusId

        viewModelScope.launch {
            if (needsFocusShift) {
                _focusRequest.value = FocusRequest(id = targetFocusId!!, placeCursorAtEnd = true)
                currentlyFocusedBlockId = targetFocusId
                delay(50.milliseconds)
            }

            synchronized(historyLock) {
                val beforeRevert = _blocks.value
                redoStack.add(beforeRevert)
                if (undoStack.isNotEmpty()) undoStack.removeAt(undoStack.lastIndex)
                _blocks.value = withTombstonesForRemoved(beforeRevert, previousList)
                lastWasStructural = true
                lastHistorySaveTime = System.currentTimeMillis()
                lastLocalMutationTime = lastHistorySaveTime
                updateHistoryState()
            }
            scheduleAutosave()

            if (targetFocusId != null) {
                delay(50.milliseconds)
                _focusRequest.value = FocusRequest(id = targetFocusId, placeCursorAtEnd = true)
            }
        }
    }

    fun redo() {
        val nextList = synchronized(historyLock) {
            if (redoStack.isEmpty()) return
            redoStack.last()
        }

        val focusId = currentlyFocusedBlockId
        var targetFocusId: String? = null

        fun isFocusable(b: NoteBlock) = b is TextBlock || b is HeadingBlock || b is CheckboxBlock ||
                b is BulletedListBlock || b is NumberedListBlock || b is ToggleBlock ||
                b is QuoteBlock || b is CodeBlock

        if (focusId != null && nextList.any { !it.isDeleted && it.id == focusId && isFocusable(it) }) {
            targetFocusId = focusId
        } else if (focusId != null) {
            val currentList = _blocks.value
            val removedIndex = currentList.indexOfFirst { it.id == focusId }
            if (removedIndex > 0) {
                targetFocusId = currentList.subList(0, removedIndex).lastOrNull { !it.isDeleted && isFocusable(it) }?.id
            }
        }

        if (targetFocusId == null) {
            targetFocusId = nextList.lastOrNull { !it.isDeleted && isFocusable(it) }?.id
        }

        val needsFocusShift = targetFocusId != null && targetFocusId != focusId

        viewModelScope.launch {
            if (needsFocusShift) {
                _focusRequest.value = FocusRequest(id = targetFocusId!!, placeCursorAtEnd = true)
                currentlyFocusedBlockId = targetFocusId
                delay(50.milliseconds)
            }

            synchronized(historyLock) {
                val beforeAdvance = _blocks.value
                undoStack.add(beforeAdvance)
                if (redoStack.isNotEmpty()) redoStack.removeAt(redoStack.lastIndex)
                _blocks.value = withTombstonesForRemoved(beforeAdvance, nextList)
                lastWasStructural = true
                lastHistorySaveTime = System.currentTimeMillis()
                lastLocalMutationTime = lastHistorySaveTime
                updateHistoryState()
            }
            scheduleAutosave()

            if (targetFocusId != null) {
                delay(50.milliseconds)
                _focusRequest.value = FocusRequest(id = targetFocusId, placeCursorAtEnd = true)
            }
        }
    }

    protected fun findBlockById(blocks: List<NoteBlock>, id: String): NoteBlock? =
        blocks.find { it.id == id }

    protected fun mapBlockById(
        blocks: List<NoteBlock>,
        id: String,
        now: Long,
        transform: (NoteBlock) -> NoteBlock
    ): List<NoteBlock> = blocks.map { b -> if (b.id == id) transform(b) else b }

    protected fun spliceAtBlock(
        blocks: List<NoteBlock>,
        id: String,
        now: Long,
        onFound: (MutableList<NoteBlock>, Int) -> Unit
    ): List<NoteBlock> {
        val idx = blocks.indexOfFirst { it.id == id }
        if (idx == -1) return blocks
        val mutable = blocks.toMutableList()
        onFound(mutable, idx)
        return mutable
    }

    protected fun recalculateNumberedLists(blocks: List<NoteBlock>): List<NoteBlock> {
        val uniqueBlocks = blocks.distinctBy { it.id }
        val counters = mutableMapOf<Int, Int>()
        val now = System.currentTimeMillis()
        return uniqueBlocks.map { block ->
            if (block is NumberedListBlock) {
                val currentNum = counters.getOrDefault(block.indentationLevel, 1)
                counters[block.indentationLevel] = currentNum + 1
                if (block.number != currentNum) {
                    block.copy(number = currentNum, updatedAt = now)
                } else block
            } else {
                val keysToReset = counters.keys.filter { it >= block.indentationLevel }
                keysToReset.forEach { counters.remove(it) }
                block
            }
        }
    }

    open fun updateReminder(blockId: String, timestamp: Long?) {
        val now = System.currentTimeMillis()
        val blockText = (findBlockById(_blocks.value, blockId) as? CheckboxBlock)?.text ?: ""
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) {
                if (it is CheckboxBlock) it.copy(reminderTimestamp = timestamp, updatedAt = now) else it
            }
        }
        scheduleAutosave()

        if (timestamp != null) {
            reminderScheduler.schedule(
                blockId = blockId,
                noteTitle = getNoteTitleForReminder(),
                text = blockText.ifBlank { "Unfinished task" },
                timestamp = timestamp
            )
        } else {
            reminderScheduler.cancel(blockId)
        }
    }

    private fun buildDatabaseBlock(
        id: String,
        indent: Int,
        isPinned: Boolean,
        now: Long,
        template: DatabaseTemplateEntity?
    ): DatabaseBlock {
        if (template == null) {
            val defaultViewId = UUID.randomUUID().toString()
            return DatabaseBlock(
                id = id,
                columns = listOf(DatabaseColumn(id = UUID.randomUUID().toString(), databaseId = id, name = "Name", type = ColumnType.TEXT, updatedAt = now)),
                rows = emptyList(),
                views = listOf(DatabaseView(id = defaultViewId, name = "Table", type = ViewType.TABLE)),
                activeViewId = defaultViewId,
                indentationLevel = indent,
                isPinned = isPinned,
                updatedAt = now
            )
        }

        val templateColumns = try {
            templateJson.decodeFromString<List<DatabaseColumn>>(template.serializedColumns)
        } catch (_: Exception) {
            emptyList()
        }
        val templateViews = try {
            templateJson.decodeFromString<List<DatabaseView>>(template.serializedViews)
        } catch (_: Exception) {
            emptyList()
        }

        val oldToNewColumnId = templateColumns.associate { it.id to UUID.randomUUID().toString() }

        val newColumns = templateColumns.map { col ->
            col.copy(id = oldToNewColumnId.getValue(col.id), databaseId = id, updatedAt = now)
        }
        val newViews = templateViews.map { view ->
            view.copy(
                id = UUID.randomUUID().toString(),
                groupByColumnId = view.groupByColumnId?.let { oldToNewColumnId[it] }
            )
        }

        return DatabaseBlock(
            id = id,
            columns = newColumns,
            rows = emptyList(),
            views = newViews,
            activeViewId = newViews.firstOrNull()?.id,
            indentationLevel = indent,
            isPinned = isPinned,
            updatedAt = now
        )
    }

    /**
     * Saves a DatabaseBlock's schema (columns + views) as a reusable template. Rows are
     * intentionally never captured - a template is a blank-slate shape, not a data snapshot.
     */
    fun saveDatabaseAsTemplate(blockId: String, templateName: String) {
        val block = findBlockById(_blocks.value, blockId) as? DatabaseBlock ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertDatabaseTemplate(
                DatabaseTemplateEntity(
                    templateId = UUID.randomUUID().toString(),
                    name = templateName,
                    serializedColumns = templateJson.encodeToString(block.columns),
                    serializedViews = templateJson.encodeToString(block.views)
                )
            )
        }
    }

    fun insertNewMediaBlock(type: String, databaseTemplate: DatabaseTemplateEntity? = null, linkedNoteId: String? = null) {
        val activeBlockId = currentlyFocusedBlockId ?: _focusRequest.value?.id ?: _selectedBlockIds.value.firstOrNull()
        var newIdToFocus: String? = null
        val now = System.currentTimeMillis()

        modifyBlocks { list ->
            val mutableList = list.toMutableList()
            val newId = UUID.randomUUID().toString()
            newIdToFocus = newId

            val activeIndex = if (activeBlockId != null) mutableList.indexOfFirst { it.id == activeBlockId } else mutableList.size - 1
            val indent = if (activeIndex != -1) mutableList[activeIndex].indentationLevel else 0
            val isPinnedContext = if (activeIndex != -1) mutableList[activeIndex].isPinned else false

            val newBlock = when (type) {
                "image" -> ImageBlock(id = newId, indentationLevel = indent, isPinned = isPinnedContext, updatedAt = now)
                "document" -> DocumentBlock(id = newId, indentationLevel = indent, isPinned = isPinnedContext, updatedAt = now)
                "bookmark" -> BookmarkBlock(id = newId, indentationLevel = indent, isPinned = isPinnedContext, updatedAt = now)
                "linked_note" -> {
                    if (linkedNoteId == null) return@modifyBlocks list
                    LinkedNoteBlock(id = newId, linkedNoteId = linkedNoteId, indentationLevel = indent, isPinned = isPinnedContext, updatedAt = now)
                }
                "voice" -> VoiceBlock(id = newId, indentationLevel = indent, isPinned = isPinnedContext, updatedAt = now)
                "database" -> buildDatabaseBlock(newId, indent, isPinnedContext, now, databaseTemplate)
                "sketch" -> SketchBlock(id = newId, indentationLevel = indent, isPinned = isPinnedContext, updatedAt = now)
                else -> return@modifyBlocks list
            }

            if (activeIndex != -1) {
                val activeBlock = mutableList[activeIndex]
                if (activeBlock is TextBlock) {
                    val text = activeBlock.text
                    val slashIndex = text.lastIndexOf('/')
                    val isActivelySearching = slashIndex != -1 && !text.substring(slashIndex).contains(" ")

                    val cleanedText = if (isActivelySearching) text.substring(0, slashIndex) else text

                    if (cleanedText.isEmpty()) {
                        mutableList[activeIndex] = newBlock
                    } else {
                        mutableList[activeIndex] = activeBlock.copy(text = cleanedText, updatedAt = now)
                        mutableList.add(activeIndex + 1, newBlock)
                    }
                } else {
                    mutableList.add(activeIndex + 1, newBlock)
                }
            } else mutableList.add(newBlock)

            mutableList
        }
        newIdToFocus?.let { _focusRequest.value = FocusRequest(id = it) }
        scheduleAutosave()
    }

    fun updateSketchStrokes(blockId: String, strokes: List<Stroke>) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) {
                if (it is SketchBlock) it.copy(strokes = strokes, updatedAt = now) else it
            }
        }
        scheduleAutosave()
    }

    fun handleUrlSubmit(blockId: String, url: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list -> mapBlockById(list, blockId, now) { if (it is BookmarkBlock) it.copy(url = url, title = "Loading...", updatedAt = now) else it } }
        viewModelScope.launch(Dispatchers.IO) {
            val metadata = HtmlMetadataFetcher.fetchMetadata(url)
            val fetchedAt = System.currentTimeMillis()
            modifyBlocks { list ->
                mapBlockById(list, blockId, fetchedAt) {
                    if (it is BookmarkBlock)
                        it.copy(title = metadata.title, description = metadata.description, previewImageUrl = metadata.imageUrl, updatedAt = fetchedAt)
                    else it
                }
            }
            scheduleAutosave()
        }
    }

    fun handleImagePicked(blockId: String, uriString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaInfo = mediaStorageHelper.copyUriToInternalStorage(uriString)
            if (mediaInfo != null) {
                withContext(Dispatchers.Main) {
                    val now = System.currentTimeMillis()
                    modifyBlocks { list -> mapBlockById(list, blockId, now) { if (it is ImageBlock) it.copy(localFilePath = mediaInfo.localFileName, updatedAt = now) else it } }
                    scheduleAutosave()
                }
            }
        }
    }

    fun handleDocumentPicked(blockId: String, uriString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaInfo = mediaStorageHelper.copyUriToInternalStorage(uriString)
            if (mediaInfo != null) {
                withContext(Dispatchers.Main) {
                    val now = System.currentTimeMillis()
                    modifyBlocks { list ->
                        mapBlockById(list, blockId, now) {
                            if (it is DocumentBlock) {
                                it.copy(localFilePath = mediaInfo.localFileName, fileName = mediaInfo.originalName, mimeType = mediaInfo.mimeType, fileSizeString = mediaInfo.formattedSize, updatedAt = now)
                            } else it
                        }
                    }
                    scheduleAutosave()
                }
            }
        }
    }

    fun deleteImageBlock(blockId: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list -> mapBlockById(list, blockId, now) { it.markDeleted() } }
        scheduleAutosave()
    }

    fun handleVoiceRecorded(blockId: String, filePath: String, duration: Int) {
        val now = System.currentTimeMillis()
        modifyBlocks { list -> mapBlockById(list, blockId, now) { if (it is VoiceBlock) it.copy(localFilePath = filePath, durationSeconds = duration, updatedAt = now) else it } }
        scheduleAutosave()
    }

    fun handleRemoveVoice(blockId: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list -> mapBlockById(list, blockId, now) { if (it is VoiceBlock) it.copy(localFilePath = null, durationSeconds = 0, updatedAt = now) else it } }
        scheduleAutosave()
    }

    /**
     * Sorts/filters now live per-[DatabaseView] instead of on the block root, so every mutation
     * needs to find the currently active view before touching them. Falls back to the first view
     * when [DatabaseBlock.activeViewId] hasn't been set yet, and is a no-op if there are no views
     * at all (shouldn't happen for blocks created after this refactor).
     */
    private fun DatabaseBlock.withActiveViewUpdated(now: Long, transform: (DatabaseView) -> DatabaseView): DatabaseBlock {
        val targetViewId = activeViewId ?: views.firstOrNull()?.id ?: return this
        return copy(views = views.map { if (it.id == targetViewId) transform(it) else it }, updatedAt = now)
    }

    fun updateDbTitle(blockId: String, newTitle: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list -> mapBlockById(list, blockId, now) { if (it is DatabaseBlock) it.copy(title = newTitle, updatedAt = now) else it } }
        scheduleAutosave()
    }

    fun addDbRow(blockId: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list -> mapBlockById(list, blockId, now) { if (it is DatabaseBlock) it.copy(rows = it.rows + DatabaseRow(id = UUID.randomUUID().toString(), databaseId = blockId, cells = emptyMap(), updatedAt = now), updatedAt = now) else it } }
        scheduleAutosave()
    }

    fun addDbColumn(blockId: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list -> mapBlockById(list, blockId, now) { if (it is DatabaseBlock) it.copy(columns = it.columns + DatabaseColumn(id = UUID.randomUUID().toString(), databaseId = blockId, name = "New Column", type = ColumnType.TEXT, updatedAt = now), updatedAt = now) else it } }
        scheduleAutosave()
    }

    suspend fun getNoteTitle(noteId: String): String {
        return repository.getNoteById(noteId)?.title ?: ""
    }

    suspend fun getNoteMetadata(noteId: String): NoteMetadataEntity? {
        return repository.getNoteById(noteId)
    }

    fun updateLinkedNoteOptions(blockId: String, showIcon: Boolean, showCoverImage: Boolean) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) {
                if (it is LinkedNoteBlock) it.copy(showIcon = showIcon, showCoverImage = showCoverImage, updatedAt = now) else it
            }
        }
        scheduleAutosave()
    }

    fun updateDbCell(blockId: String, rowId: String, colId: String, newValue: CellData) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) { block ->
                if (block is DatabaseBlock) {
                    val updatedRows = block.rows.map { row ->
                        if (row.id == rowId) {
                            val newMap = row.cells.toMutableMap()
                            newMap[colId] = newValue
                            block.columns.filter { it.type == ColumnType.FORMULA }.forEach { formulaCol ->
                                formulaCol.formulaExpression?.let { expr ->
                                    newMap[formulaCol.id] = FormulaEngine.evaluate(expr, newMap, block.columns)
                                }
                            }
                            row.copy(cells = newMap, updatedAt = now)
                        } else row
                    }
                    block.copy(rows = updatedRows, updatedAt = now)
                } else block
            }
        }
        scheduleAutosave()
    }

    fun updateDbFormula(blockId: String, colId: String, expression: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) { db ->
                if (db is DatabaseBlock) {
                    val updatedCols = db.columns.map { col -> if (col.id == colId) col.copy(formulaExpression = expression, updatedAt = now) else col }
                    val updatedRows = db.rows.map { row ->
                        val newMap = row.cells.toMutableMap()
                        newMap[colId] = FormulaEngine.evaluate(expression, newMap, updatedCols)
                        row.copy(cells = newMap, updatedAt = now)
                    }
                    db.copy(columns = updatedCols, rows = updatedRows, updatedAt = now)
                } else db
            }
        }
        scheduleAutosave()
    }

    fun updateDbColumn(blockId: String, colId: String, newName: String, newType: ColumnType, isManualNameChange: Boolean = true) {
        val now = System.currentTimeMillis()
        modifyBlocks { list -> mapBlockById(list, blockId, now) { db -> if (db is DatabaseBlock) db.copy(columns = db.columns.map { col -> if (col.id == colId) col.copy(name = newName, type = newType, isNameManuallySet = col.isNameManuallySet || isManualNameChange, updatedAt = now) else col }, updatedAt = now) else db } }
        scheduleAutosave()
    }

    fun updateDbColumnWidth(blockId: String, colId: String, newWidth: Int) {
        val now = System.currentTimeMillis()
        modifyBlocks { list -> mapBlockById(list, blockId, now) { db -> if (db is DatabaseBlock) db.copy(columns = db.columns.map { col -> if (col.id == colId) col.copy(width = newWidth.coerceIn(40, 600), updatedAt = now) else col }, updatedAt = now) else db } }
        scheduleAutosave()
    }

    fun updateDbSort(blockId: String, colId: String, isAscending: Boolean?) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) { db ->
                if (db is DatabaseBlock) {
                    db.withActiveViewUpdated(now) { view ->
                        val modifiedSortList = view.activeSorts.toMutableList()
                        modifiedSortList.removeAll { it.columnId == colId }
                        if (isAscending != null) {
                            modifiedSortList.add(SortConfig(colId, isAscending))
                        }
                        view.copy(activeSorts = modifiedSortList)
                    }
                } else db
            }
        }
        scheduleAutosave()
    }

    fun updateDbGroupBy(blockId: String, colId: String?) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) { db ->
                if (db is DatabaseBlock) {
                    db.withActiveViewUpdated(now) { view -> view.copy(groupByColumnId = colId) }
                } else db
            }
        }
        scheduleAutosave()
    }

    fun updateDbGalleryCardSize(blockId: String, size: GalleryCardSize) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) { db ->
                if (db is DatabaseBlock) {
                    db.withActiveViewUpdated(now) { view -> view.copy(galleryCardSize = size) }
                } else db
            }
        }
        scheduleAutosave()
    }

    // Kanban bucket visibility is per-view.
    fun toggleKanbanGroupVisibility(blockId: String, viewId: String, groupName: String, isHidden: Boolean) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) { db ->
                if (db is DatabaseBlock) {
                    db.copy(
                        views = db.views.map { view ->
                            if (view.id != viewId) view else view.copy(
                                hiddenGroups = if (isHidden) (view.hiddenGroups + groupName).distinct()
                                else view.hiddenGroups - groupName
                            )
                        },
                        updatedAt = now
                    )
                } else db
            }
        }
        scheduleAutosave()
    }

    // Persists the drag-reordered board sequence chosen in the Group By sheet.
    fun reorderKanbanGroups(blockId: String, viewId: String, orderedGroupKeys: List<String>) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) { db ->
                if (db is DatabaseBlock) {
                    db.copy(
                        views = db.views.map { view ->
                            if (view.id != viewId) view else view.copy(groupOrder = orderedGroupKeys)
                        },
                        updatedAt = now
                    )
                } else db
            }
        }
        scheduleAutosave()
    }

    fun addDbFilter(blockId: String, colId: String, operator: String, value: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) { db ->
                if (db is DatabaseBlock) {
                    db.withActiveViewUpdated(now) { view -> view.copy(activeFilters = view.activeFilters + FilterConfig(colId, operator, value)) }
                } else db
            }
        }
        scheduleAutosave()
    }

    fun removeDbFilter(blockId: String, filter: FilterConfig) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) { db ->
                if (db is DatabaseBlock) {
                    db.withActiveViewUpdated(now) { view -> view.copy(activeFilters = view.activeFilters - filter) }
                } else db
            }
        }
        scheduleAutosave()
    }

    // Database views (Table/Kanban/Gallery)
    fun addDatabaseView(blockId: String, type: ViewType) {
        val now = System.currentTimeMillis()
        val newViewId = UUID.randomUUID().toString()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) { db ->
                if (db is DatabaseBlock) {
                    val baseName = when (type) {
                        ViewType.TABLE -> "Table"
                        ViewType.KANBAN -> "Board"
                        ViewType.GALLERY -> "Gallery"
                    }
                    val sameTypeCount = db.views.count { it.type == type }
                    val name = if (sameTypeCount == 0) baseName else "$baseName ${sameTypeCount + 1}"
                    val newView = DatabaseView(id = newViewId, name = name, type = type)
                    db.copy(views = db.views + newView, activeViewId = newViewId, updatedAt = now)
                } else db
            }
        }
        scheduleAutosave()
    }

    fun deleteDatabaseView(blockId: String, viewId: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) { db ->
                if (db is DatabaseBlock && db.views.size > 1) {
                    val remainingViews = db.views.filter { it.id != viewId }
                    val newActiveViewId = if (db.activeViewId == viewId) remainingViews.firstOrNull()?.id else db.activeViewId
                    db.copy(views = remainingViews, activeViewId = newActiveViewId, updatedAt = now)
                } else db
            }
        }
        scheduleAutosave()
    }

    fun setActiveDatabaseView(blockId: String, viewId: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list -> mapBlockById(list, blockId, now) { db -> if (db is DatabaseBlock) db.copy(activeViewId = viewId, updatedAt = now) else db } }
        scheduleAutosave()
    }

    fun renameDatabaseView(blockId: String, viewId: String, newName: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) { db ->
                if (db is DatabaseBlock) {
                    db.copy(views = db.views.map { if (it.id == viewId) it.copy(name = newName) else it }, updatedAt = now)
                } else db
            }
        }
        scheduleAutosave()
    }

    fun reorderDbColumns(blockId: String, fromIndex: Int, toIndex: Int) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) { db ->
                if (db is DatabaseBlock) {
                    val cols = db.columns.toMutableList()
                    val moved = cols.removeAt(fromIndex)
                    cols.add(toIndex, moved)
                    db.copy(columns = cols, updatedAt = now)
                } else db
            }
        }
        scheduleAutosave()
    }

    fun reorderDatabaseViews(blockId: String, fromIndex: Int, toIndex: Int) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) { db ->
                if (db is DatabaseBlock) {
                    val views = db.views.toMutableList()
                    val moved = views.removeAt(fromIndex)
                    views.add(toIndex, moved)
                    db.copy(views = views, updatedAt = now)
                } else db
            }
        }
        scheduleAutosave()
    }

    fun deleteDbColumn(blockId: String, colId: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) { db ->
                if (db is DatabaseBlock) {
                    val updatedCols = db.columns.map { col ->
                        if (col.id == colId) col.copy(isDeleted = true, updatedAt = now) else col
                    }
                    db.copy(columns = updatedCols, updatedAt = now)
                } else db
            }
        }
        scheduleAutosave()
    }

    fun deleteDbRow(blockId: String, rowId: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) { db ->
                if (db is DatabaseBlock) {
                    val updatedRows = db.rows.map { row ->
                        if (row.id == rowId) row.copy(isDeleted = true, updatedAt = now) else row
                    }
                    db.copy(rows = updatedRows, updatedAt = now)
                } else db
            }
        }
        scheduleAutosave()
    }

    fun addDbRowAt(blockId: String, index: Int) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) { db ->
                if (db is DatabaseBlock) {
                    val rows = db.rows.toMutableList()
                    rows.add(index.coerceIn(0, rows.size), DatabaseRow(id = UUID.randomUUID().toString(), databaseId = blockId, cells = emptyMap(), updatedAt = now))
                    db.copy(rows = rows, updatedAt = now)
                } else db
            }
        }
        scheduleAutosave()
    }

    fun addDbColumnAt(blockId: String, index: Int) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) { db ->
                if (db is DatabaseBlock) {
                    val cols = db.columns.toMutableList()
                    cols.add(index.coerceIn(0, cols.size), DatabaseColumn(id = UUID.randomUUID().toString(), databaseId = blockId, name = "New Column", type = ColumnType.TEXT, updatedAt = now))
                    db.copy(columns = cols, updatedAt = now)
                } else db
            }
        }
        scheduleAutosave()
    }

    override fun onCleared() {
        super.onCleared()
        ActiveEditorRegistry.unregister(this)
        autosaveJob?.cancel()
        appScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                performSave()
            }
        }
    }

    // Database tags
    val globalTags: StateFlow<List<TagEntity>> = repository.getAllTags()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun createGlobalTag(name: String, colorHex: String): String {
        val newId = UUID.randomUUID().toString()
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertOrUpdateTag(newId, name, colorHex)
        }
        return newId
    }

    fun addBlockAbove(id: String) {
        val now = System.currentTimeMillis()
        val newId = UUID.randomUUID().toString()
        modifyBlocks { list ->
            spliceAtBlock(list, id, now) { mutable, idx ->
                val indent = mutable[idx].indentationLevel
                mutable.add(idx, TextBlock(id = newId, text = "", indentationLevel = indent, updatedAt = now))
            }
        }
        _focusRequest.value = FocusRequest(id = newId)
        scheduleAutosave()
    }

    fun addBlockBelow(id: String) {
        val now = System.currentTimeMillis()
        val newId = UUID.randomUUID().toString()
        modifyBlocks { list ->
            spliceAtBlock(list, id, now) { mutable, idx ->
                val indent = mutable[idx].indentationLevel
                mutable.add(idx + 1, TextBlock(id = newId, text = "", indentationLevel = indent, updatedAt = now))
            }
        }
        _focusRequest.value = FocusRequest(id = newId)
        scheduleAutosave()
    }

    // Aggregators
    fun updateDbAggregation(blockId: String, colId: String, aggregationType: String?) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) { db ->
                if (db is DatabaseBlock)
                    db.copy(columns = db.columns.map { c ->
                        if (c.id == colId) c.copy(aggregationType = aggregationType, updatedAt = now) else c
                    }, updatedAt = now)
                else db
            }
        }
        scheduleAutosave()
    }

    fun updateDbCurrency(blockId: String, colId: String, symbol: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) { db ->
                if (db is DatabaseBlock)
                    db.copy(columns = db.columns.map { c ->
                        if (c.id == colId) c.copy(currencySymbol = symbol, updatedAt = now) else c
                    }, updatedAt = now)
                else db
            }
        }
        scheduleAutosave()
    }

    fun updateDbFormulaCurrency(blockId: String, colId: String, enabled: Boolean) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId, now) { db ->
                if (db is DatabaseBlock)
                    db.copy(columns = db.columns.map { c ->
                        if (c.id == colId) c.copy(isFormulaCurrency = enabled, updatedAt = now) else c
                    }, updatedAt = now)
                else db
            }
        }
        scheduleAutosave()
    }

    // Database table notes
    fun openDatabaseNote(
        blockId: String,
        rowId: String,
        colId: String,
        existingNoteId: String?,
        onNavigate: (String) -> Unit
    ) {
        if (!existingNoteId.isNullOrBlank()) {
            viewModelScope.launch {
                performSave()
                withContext(Dispatchers.Main) {
                    onNavigate(existingNoteId)
                }
            }
            return
        }

        val newNoteId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val subNoteMeta = NoteMetadataEntity(
                    noteId = newNoteId,
                    title = "",
                    folderId = null,
                    isDaily = false,
                    dateString = null,
                    createdAt = now,
                    updatedAt = now,
                    filePath = "note_$newNoteId.json",
                    isSubNote = true
                )

                // Create the sub-note first - if this fails, the parent's cell is never left pointing
                // at a note that doesn't exist. performSave() below takes SyncCoordinator.mutex itself
                // (it's not reentrant), so this write gets its own lock rather than one shared with it.
                SyncCoordinator.mutex.withLock {
                    repository.saveNote(subNoteMeta, NoteContent(blocks = emptyList()))
                }

                withContext(Dispatchers.Main) {
                    updateDbCell(blockId, rowId, colId, CellData.NoteRelation(listOf(newNoteId)))
                }

                performSave()

                withContext(Dispatchers.Main) {
                    onNavigate(newNoteId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createLinkedNote(title: String): String {
        val newNoteId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        viewModelScope.launch(Dispatchers.IO) {
            val newMeta = NoteMetadataEntity(
                noteId = newNoteId,
                title = title,
                folderId = null,
                isDaily = false,
                dateString = null,
                createdAt = now,
                updatedAt = now,
                filePath = "note_$newNoteId.json",
                isSubNote = true
            )
            repository.saveNote(newMeta, NoteContent(blocks = emptyList()))
        }
        return newNoteId
    }
}