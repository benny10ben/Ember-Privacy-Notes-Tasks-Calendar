package com.ben.inly.presentation.mobile.daily

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.koin.compose.viewmodel.koinViewModel
import com.ben.inly.domain.model.CellData
import com.ben.inly.domain.model.ColumnType
import com.ben.inly.domain.model.FilterConfig
import com.ben.inly.domain.model.GalleryCardSize
import com.ben.inly.domain.model.NoteBlock
import com.ben.inly.domain.model.Stroke
import com.ben.inly.domain.model.ViewType
import com.ben.inly.domain.util.isDesktopPlatform
import com.ben.inly.presentation.shared.components.KmpBackHandler
import com.ben.inly.presentation.shared.components.NotePickerDialog
import com.ben.inly.presentation.shared.editor.BlockSelectionPill
import com.ben.inly.presentation.shared.editor.EditorActions
import com.ben.inly.presentation.shared.editor.EditorScreen
import com.ben.inly.presentation.shared.editor.SelectionModeObserver
import com.ben.inly.presentation.shared.editor.MobileMenuState
import com.ben.inly.presentation.shared.editor.blockViews.databaseBlockView.DatabaseTemplatePickerSheet
import com.ben.inly.presentation.shared.editor.blockViews.databaseBlockView.NoteLinkText
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.daysUntil
import com.ben.inly.presentation.shared.editor.EditorToolbar
import com.ben.inly.presentation.shared.editor.GlobalEditorState
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import com.ben.inly.data.local.room.CalendarTaskEntity
import com.ben.inly.presentation.shared.UserSettings
import com.ben.inly.presentation.shared.components.InlyBottomSheet
import com.ben.inly.presentation.shared.components.TopBarIconButtonGroup
import com.ben.inly.presentation.shared.components.TopBarIconButtonItem
import com.ben.inly.presentation.shared.rememberStableStatusBarsPadding
import com.ben.inly.presentation.shared.stableStatusBarsPadding
import com.ben.inly.presentation.sync.SyncViewModel
import com.ben.inly.presentation.sync.showSyncToast
import dev.chrisbanes.haze.hazeSource
import inly.app.generated.resources.Res
import inly.app.generated.resources.calendar
import inly.app.generated.resources.ellipsis
import inly.app.generated.resources.inbox
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource

private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

@Composable
fun DailyScreen(
    onSelectionModeChange: (Boolean) -> Unit = {},
    bottomContentPadding: Dp = 0.dp,
    onPickImage: (onPathSelected: (String) -> Unit) -> Unit = {},
    onTakePhoto: (onPathSelected: (String) -> Unit) -> Unit = {},
    onPickDocument: (onPathSelected: (String) -> Unit) -> Unit = {},
    onOpenFile: (filePath: String, mimeType: String) -> Unit = { _, _ -> },
    isSidebarVisible: Boolean = true,
    onToggleSidebar: () -> Unit = {},
    onNavigateToEditor: (String) -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToTrash: () -> Unit = {},
    showAddNoteDialog: Boolean = false,
    dateArg: String? = null,
    viewModel: DailyEditorViewModel = koinViewModel(),
    syncViewModel: SyncViewModel = koinViewModel()
) {
    val hazeState = remember { HazeState() }

    val allLinkableNotes by viewModel.allLinkableNotes.collectAsState()

    val clipboardManager = LocalClipboardManager.current
    val blocks by viewModel.visibleBlocks.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val selectedBlockIds by viewModel.selectedBlockIds.collectAsState()
    val focusRequest by viewModel.focusRequest.collectAsState()
    val loadedDateString by viewModel.loadedDateString.collectAsState()
    val previewCache by viewModel.previewCache.collectAsState()

    val initialDate = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val initialPage = remember { Int.MAX_VALUE / 2 }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { Int.MAX_VALUE })

    val isSelectionMode = selectedBlockIds.isNotEmpty()
    val selectedBlocksList = blocks.filter { it.id in selectedBlockIds }
    val isSelectionPinned = selectedBlocksList.isNotEmpty() && selectedBlocksList.all { it.isPinned }

    var showScheduledTasksSheet by remember { mutableStateOf(false) }
    var showCalendarSheet by remember { mutableStateOf(false) }

    // User Settings & Sync State
    var showSettingsMenu by remember { mutableStateOf(false) }
    val syncState by syncViewModel.syncStatus.collectAsState()

    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)

    var isKeyboardOpen by remember { mutableStateOf(false) }
    var previousImeBottom by remember { mutableIntStateOf(0) }

    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && imeBottom >= previousImeBottom) {
            isKeyboardOpen = true
        } else if (imeBottom < previousImeBottom) {
            isKeyboardOpen = false
        }

        if (imeBottom == 0) {
            isKeyboardOpen = false
        }
        previousImeBottom = imeBottom
    }

    // Lets a search result (or any other future deep link) jump straight to a specific day.
    // Mirrors what the in-screen calendar picker already does: calling viewModel.selectDate()
    // updates _selectedDate, which the LaunchedEffect(selectedDate) below picks up to scroll
    // the pager. Keyed on dateArg so re-tapping the same search result while already on this
    // screen re-triggers the jump even though the composable itself isn't recreated.
    LaunchedEffect(dateArg) {
        dateArg?.let { viewModel.selectDate(LocalDate.parse(it)) }
    }

    LaunchedEffect(syncState) {
        if (syncState != "Idle" && syncState != "Syncing...") {
            showSyncToast(syncState)
            syncViewModel.resetSyncStatus()
        }
    }

    val showToolbar = !isSelectionMode && !showAddNoteDialog && (isKeyboardOpen || isDesktopPlatform)

    val globalTags by viewModel.globalTags.collectAsState()
    val calendarTaskMap by viewModel.calendarTaskMap.collectAsState()
    val databaseTemplates by viewModel.databaseTemplates.collectAsState()
    var showDatabasePicker by remember { mutableStateOf(false) }
    var showNotePickerDialog by remember { mutableStateOf(false) }

    var subNotePanelId by remember { mutableStateOf<String?>(null) }

    SelectionModeObserver(isSelectionMode, onSelectionModeChange)

    KmpBackHandler(enabled = showSettingsMenu) {
        showSettingsMenu = false
    }
    KmpBackHandler(enabled = showCalendarSheet) {
        showCalendarSheet = false
    }
    KmpBackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                viewModel.selectDate(initialDate.plus((page - initialPage).toLong(), DateTimeUnit.DAY))
            }
    }

    LaunchedEffect(selectedDate) {
        val targetPage = initialPage + initialDate.daysUntil(selectedDate)
        if (pagerState.currentPage != targetPage && !pagerState.isScrollInProgress) {
            if (abs(pagerState.currentPage - targetPage) > 3) {
                pagerState.scrollToPage(targetPage)
            } else {
                pagerState.animateScrollToPage(targetPage)
            }
        }
        val keepDates = (-2..2).map { offset ->
            selectedDate.plus(offset.toLong(), DateTimeUnit.DAY).toString()
        }.toSet()
        viewModel.evictPreviewCache(keepDates)
    }

    var isListScrollEnabled by remember { mutableStateOf(true) }

    val sharedEditorActions = remember(viewModel, onOpenFile) {
        object : EditorActions {
            override fun onClearFocusRequest() = viewModel.clearFocusRequest()
            override fun onUpdateText(id: String, text: String) = viewModel.updateBlockText(id, text)
            override fun onToggleCheckbox(id: String, checked: Boolean) = viewModel.toggleCheckbox(id, checked)
            override fun onToggleExpand(id: String) = viewModel.toggleToggleBlock(id)
            override fun onFocusBlock(id: String) = viewModel.setFocusedBlock(id)
            override fun onChangeBlockType(type: String) = viewModel.changeFocusedBlockType(type)
            override fun onToggleFormat(format: String) = viewModel.toggleFormat(format)
            override fun onAdjustIndentation(increase: Boolean) = viewModel.adjustIndentation(increase)
            override fun onEnterPressed(id: String, before: String, after: String) = viewModel.handleEnter(id, before, after)
            override fun onBackspaceOnEmpty(id: String) = viewModel.handleBackspaceOnEmpty(id)
            override fun onToggleSelection(id: String) = viewModel.toggleSelection(id)
            override fun onUpdateReminder(id: String, timestamp: Long?) = viewModel.updateReminder(id, timestamp)
            override fun onUrlSubmit(id: String, url: String) = viewModel.handleUrlSubmit(id, url)
            override fun onImagePicked(id: String, uri: String) = viewModel.handleImagePicked(id, uri)
            override fun onDocumentPicked(id: String, uri: String) = viewModel.handleDocumentPicked(id, uri)
            override fun onAddBlankBlock() = viewModel.addBlankBlockBelowFocused()
            override fun onInsertMediaBlock(type: String) {
                when (type) {
                    "database" -> showDatabasePicker = true
                    "linked_note" -> showNotePickerDialog = true
                    else -> viewModel.insertNewMediaBlock(type)
                }
            }
            override fun onSaveDatabaseAsTemplate(blockId: String, templateName: String) =
                viewModel.saveDatabaseAsTemplate(blockId, templateName)
            override fun onOutsideTap() {}
            override fun onUpdateDbTitle(id: String, title: String) = viewModel.updateDbTitle(id, title)
            override fun onAddDbRow(id: String) = viewModel.addDbRow(id)
            override fun onAddDbColumn(id: String) = viewModel.addDbColumn(id)
            override fun onUpdateDbCell(blockId: String, rowId: String, colId: String, value: CellData) = viewModel.updateDbCell(blockId, rowId, colId, value)
            override fun onUpdateDbColumn(blockId: String, colId: String, name: String, type: ColumnType, isManualNameChange: Boolean) = viewModel.updateDbColumn(blockId, colId, name, type, isManualNameChange)
            override fun onUpdateDbSort(blockId: String, colId: String, isAscending: Boolean?) = viewModel.updateDbSort(blockId, colId, isAscending)
            override fun onUpdateDbGroupBy(blockId: String, colId: String?) = viewModel.updateDbGroupBy(blockId, colId)
            override fun onUpdateDbGalleryCardSize(blockId: String, size: GalleryCardSize) = viewModel.updateDbGalleryCardSize(blockId, size)
            override fun onToggleKanbanGroupVisibility(blockId: String, viewId: String, groupName: String, isHidden: Boolean) = viewModel.toggleKanbanGroupVisibility(blockId, viewId, groupName, isHidden)
            override fun onReorderKanbanGroups(blockId: String, viewId: String, orderedGroupKeys: List<String>) = viewModel.reorderKanbanGroups(blockId, viewId, orderedGroupKeys)
            override fun onAddDbFilter(blockId: String, colId: String, operator: String, value: String) = viewModel.addDbFilter(blockId, colId, operator, value)
            override fun onRemoveDbFilter(blockId: String, config: FilterConfig) = viewModel.removeDbFilter(blockId, config)
            override fun onReorderDbColumns(blockId: String, from: Int, to: Int) = viewModel.reorderDbColumns(blockId, from, to)
            override fun onReorderDatabaseViews(blockId: String, from: Int, to: Int) = viewModel.reorderDatabaseViews(blockId, from, to)
            override fun onUpdateDbFormula(blockId: String, colId: String, expression: String) = viewModel.updateDbFormula(blockId, colId, expression)
            override fun onDeleteDbColumn(blockId: String, colId: String) = viewModel.deleteDbColumn(blockId, colId)
            override fun onDeleteDbRow(blockId: String, rowId: String) = viewModel.deleteDbRow(blockId, rowId)
            override fun onAddDbRowAt(blockId: String, index: Int) = viewModel.addDbRowAt(blockId, index)
            override fun onAddDbColumnAt(blockId: String, index: Int) = viewModel.addDbColumnAt(blockId, index)
            override fun onUpdateDbColumnWidth(blockId: String, colId: String, width: Int) = viewModel.updateDbColumnWidth(blockId, colId, width)
            override fun onVoiceRecorded(id: String, filePath: String, duration: Int) = viewModel.handleVoiceRecorded(id, filePath, duration)
            override fun onRemoveVoice(id: String) = viewModel.handleRemoveVoice(id)
            override fun onDeleteImageBlock(id: String) = viewModel.deleteImageBlock(id)
            override fun onCreateGlobalTag(name: String, colorHex: String): String = viewModel.createGlobalTag(name, colorHex)
            override fun onRequestImagePicker(blockId: String) {
                onPickImage { path -> viewModel.handleImagePicked(blockId, path) }
            }
            override fun onRequestCamera(blockId: String) {
                onTakePhoto { path -> viewModel.handleImagePicked(blockId, path) }
            }
            override fun onRequestDocumentPicker(blockId: String) {
                onPickDocument { path -> viewModel.handleDocumentPicked(blockId, path) }
            }
            override fun onRequestDbFilePicker(blockId: String, rowId: String, colId: String, isAudio: Boolean) {
                onPickDocument { path ->
                    viewModel.handleDbFilePicked(blockId, rowId, colId, path)
                }
            }
            override fun onStopDbAudioRecording(blockId: String, rowId: String, colId: String, cancel: Boolean) {
                viewModel.stopDbHardwareRecording(blockId, rowId, colId, cancel)
            }
            override fun onOpenFile(filePath: String, mimeType: String) {
                onOpenFile(filePath, mimeType)
            }
            override fun onStartRecording() = viewModel.startHardwareRecording()
            override fun onStopRecording(blockId: String, cancel: Boolean) = viewModel.stopHardwareRecording(blockId, cancel)
            override fun onPlayAudio(filePath: String, onComplete: () -> Unit) = viewModel.playAudio(filePath, onComplete)
            override fun onStopAudio() = viewModel.stopAudio()
            override fun onTogglePin() = viewModel.togglePinSelectedBlocks()
            override fun setScrollEnabled(enabled: Boolean) {
                isListScrollEnabled = enabled
            }
            override fun onUpdateSketch(id: String, strokes: List<Stroke>) =
                viewModel.updateSketchStrokes(id, strokes)
            override fun onAddBlockAbove(id: String) = viewModel.addBlockAbove(id)
            override fun onAddBlockBelow(id: String) = viewModel.addBlockBelow(id)
            override fun onUpdateDbAggregation(blockId: String, colId: String, aggregationType: String?) =
                viewModel.updateDbAggregation(blockId, colId, aggregationType)
            override fun onUpdateDbCurrency(blockId: String, colId: String, symbol: String) =
                viewModel.updateDbCurrency(blockId, colId, symbol)
            override fun onUpdateDbFormulaCurrency(blockId: String, colId: String, enabled: Boolean) =
                viewModel.updateDbFormulaCurrency(blockId, colId, enabled)
            override fun onAddDatabaseView(blockId: String, type: ViewType) = viewModel.addDatabaseView(blockId, type)
            override fun onDeleteDatabaseView(blockId: String, viewId: String) = viewModel.deleteDatabaseView(blockId, viewId)
            override fun onSetActiveDatabaseView(blockId: String, viewId: String) = viewModel.setActiveDatabaseView(blockId, viewId)
            override fun onRenameDatabaseView(blockId: String, viewId: String, newName: String) = viewModel.renameDatabaseView(blockId, viewId, newName)
            override fun onNoteLinkClick(noteId: String) {
                if (isDesktopPlatform) {
                    subNotePanelId = noteId
                } else {
                    onNavigateToEditor(noteId)
                }
            }
            override fun onCreateLinkedNote(title: String): String {
                return viewModel.createLinkedNote(title)
            }
            override fun onOpenDatabaseNote(blockId: String, rowId: String, colId: String, existingNoteId: String?) {
                viewModel.openDatabaseNote(blockId, rowId, colId, existingNoteId) { resolvedNoteId ->
                    if (isDesktopPlatform) {
                        subNotePanelId = resolvedNoteId
                    } else {
                        onNavigateToEditor(resolvedNoteId)
                    }
                }
            }
            override suspend fun getNoteTitle(noteId: String): String {
                return viewModel.getNoteTitle(noteId)
            }
            override suspend fun getNoteMetadata(noteId: String) = viewModel.getNoteMetadata(noteId)
            override fun onUpdateLinkedNoteOptions(id: String, showIcon: Boolean, showCoverImage: Boolean) =
                viewModel.updateLinkedNoteOptions(id, showIcon, showCoverImage)
        }
    }

    val rightPanelContent = @Composable {
        var mobileMenuState by remember { mutableStateOf(MobileMenuState.MAIN) }
        var slashQuery by remember { mutableStateOf("") }

        LaunchedEffect(isKeyboardOpen) {
            if (!isKeyboardOpen && mobileMenuState != MobileMenuState.MAIN) {
                mobileMenuState = MobileMenuState.MAIN
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {

            if (isDesktopPlatform && !isSidebarVisible) {
                IconButton(
                    onClick = onToggleSidebar,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 16.dp)
                        .zIndex(10f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Open Sidebar",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState).background(MaterialTheme.colorScheme.background)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize(),
                userScrollEnabled = false,
                beyondViewportPageCount = 1,
                key = { page -> initialDate.plus((page - initialPage).toLong(), DateTimeUnit.DAY).toString() }
            ) { page ->
                val pageDate = initialDate.plus((page - initialPage).toLong(), DateTimeUnit.DAY)
                val pageDateString = pageDate.toString()

                LaunchedEffect(pageDateString) {
                    viewModel.prefetchDateIfNeeded(pageDateString)
                }

                val isCurrentActivePage =
                    pageDate == selectedDate && loadedDateString == pageDateString

                val displayBlocks: List<NoteBlock> = if (isCurrentActivePage) {
                    blocks
                } else {
                    previewCache[pageDateString] ?: emptyList()
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    EditorScreen(
                        blocks = displayBlocks,
                        allLinkableNotes = allLinkableNotes,
                        globalTags = globalTags,
                        actions = sharedEditorActions,
                        focusRequest = if (isCurrentActivePage) focusRequest else null,
                        selectedBlockIds = selectedBlockIds,
                        mobileMenuState = mobileMenuState,
                        onMobileMenuStateChange = { mobileMenuState = it },
                        slashQuery = slashQuery,
                        onSlashQueryChange = { slashQuery = it },
                        bottomContentPadding = bottomContentPadding,
                        isCurrentActivePage = isCurrentActivePage,
                        topContentPadding = if (isDesktopPlatform) {
                            if (!isSidebarVisible) 68.dp else 16.dp
                        } else {
                            rememberStableStatusBarsPadding().calculateTopPadding() + 68.dp
                        },
                        headerContent = if (isDesktopPlatform) null else {
                            {
                                CollapsedWeekStrip(
                                    selectedDate = selectedDate,
                                    onDateSelected = { viewModel.selectDate(it) },
                                    pagerState = pagerState,
                                    initialPage = initialPage,
                                    initialDate = initialDate
                                )
                            }
                        }
                    )
                }
            }
            }

            AnimatedVisibility(
                visible = showToolbar,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 250, delayMillis = 100, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(durationMillis = 250, delayMillis = 100)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(durationMillis = 200)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .then(if (isDesktopPlatform) Modifier else Modifier.navigationBarsPadding())
                    .padding(bottom = 8.dp, start = if (isDesktopPlatform) 16.dp else 6.dp, end = if (isDesktopPlatform) 16.dp else 6.dp)
            ) {
                EditorToolbar(
                    mobileMenuState = mobileMenuState,
                    onMenuStateChange = { mobileMenuState = it },
                    query = slashQuery,
                    hazeState = hazeState,
                    onChangeBlockType = { sharedEditorActions.onChangeBlockType(it) },
                    onToggleFormat = { sharedEditorActions.onToggleFormat(it) },
                    onAdjustIndentation = { sharedEditorActions.onAdjustIndentation(it) },
                    onInsertMediaBlock = { sharedEditorActions.onInsertMediaBlock(it) },
                    onSelectCurrentBlock = {
                        GlobalEditorState.currentlyFocusedBlockId?.let { id ->
                            sharedEditorActions.onToggleSelection(id)
                        }
                    }
                )
            }

            BlockSelectionPill(
                isVisible = isSelectionMode,
                selectedCount = selectedBlockIds.size,
                onClearSelection = { viewModel.clearSelection() },
                onSelectAll = { viewModel.selectAllBlocks() },
                onCopy = {
                    clipboardManager.setText(AnnotatedString(viewModel.getSelectedText()))
                    viewModel.clearSelection()
                },
                onCut = {
                    clipboardManager.setText(AnnotatedString(viewModel.cutSelectedBlocks()))
                },
                onAddBlockAbove = { viewModel.addBlockAboveSelection() },
                onAddBlockBelow = { viewModel.addBlockBelowSelection() },
                onDelete = { viewModel.deleteSelectedBlocks() },
                onTogglePin = { sharedEditorActions.onTogglePin() },
                isSelectionPinned = isSelectionPinned,
                selectedBlocks = selectedBlocksList,
                onUpdateLinkedNoteOptions = { id, showIcon, showCoverImage -> viewModel.updateLinkedNoteOptions(id, showIcon, showCoverImage) },
                hazeState = hazeState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .then(if (isDesktopPlatform) Modifier.padding(bottom = 16.dp) else Modifier.navigationBarsPadding())
            )

            DatabaseTemplatePickerSheet(
                expanded = showDatabasePicker,
                templates = databaseTemplates,
                onDismiss = { showDatabasePicker = false },
                onCreateBlank = { viewModel.insertNewMediaBlock("database") },
                onSelectTemplate = { viewModel.insertNewMediaBlock("database", it) }
            )

            NotePickerDialog(
                expanded = showNotePickerDialog,
                onDismiss = { showNotePickerDialog = false },
                allLinkableNotes = allLinkableNotes,
                onNoteSelected = { noteId ->
                    viewModel.insertNewMediaBlock("linked_note", linkedNoteId = noteId)
                    showNotePickerDialog = false
                },
                onCreateNote = { title ->
                    val newNoteId = viewModel.createLinkedNote(title)
                    viewModel.insertNewMediaBlock("linked_note", linkedNoteId = newNoteId)
                    showNotePickerDialog = false
                },
                onCreateBlankNote = {
                    val newNoteId = viewModel.createLinkedNote("Untitled")
                    showNotePickerDialog = false
                    if (isDesktopPlatform) {
                        subNotePanelId = newNoteId
                    } else {
                        onNavigateToEditor(newNoteId)
                    }
                }
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    rightPanelContent()
                }

                DailyTopBar(
                    selectedDate = selectedDate,
                    onCalendarIconClick = { showCalendarSheet = true },
                    onNotificationsClick = { showScheduledTasksSheet = true },
                    onOpenCalendarScreenClick = onNavigateToCalendar,
                    onToggleSidebar = onToggleSidebar,
                    hazeState = hazeState,
                    showSettingsMenu = showSettingsMenu,
                    onSettingsMenuOpen = { showSettingsMenu = true },
                    onSettingsMenuDismiss = { showSettingsMenu = false },
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToTrash = onNavigateToTrash,
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).zIndex(2f)
                )
            }

            if (showScheduledTasksSheet) {
                UpcomingTasksSheet(
                    initialDate = initialDate,
                    calendarTaskMap = calendarTaskMap,
                    viewModel = viewModel,
                    onDismiss = { showScheduledTasksSheet = false },
                    onTaskNoteLinkClick = { noteId ->
                        showScheduledTasksSheet = false
                        if (isDesktopPlatform) {
                            subNotePanelId = noteId
                        } else {
                            onNavigateToEditor(noteId)
                        }
                    }
                )
            }

            if (showCalendarSheet) {
                DailyCalendarSheet(
                    selectedDate = selectedDate,
                    initialDate = initialDate,
                    calendarTaskMap = calendarTaskMap,
                    onDismiss = { showCalendarSheet = false },
                    onDateSelected = { viewModel.selectDate(it) },
                    onGoToToday = { viewModel.selectDate(initialDate) }
                )
            }
        }
    }
}

@Composable
private fun DailyTopBar(
    selectedDate: LocalDate,
    onCalendarIconClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onOpenCalendarScreenClick: () -> Unit,
    onToggleSidebar: () -> Unit,
    hazeState: HazeState,
    showSettingsMenu: Boolean,
    onSettingsMenuOpen: () -> Unit,
    onSettingsMenuDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures {} }
            .then(if (isDesktopPlatform) Modifier else Modifier.stableStatusBarsPadding())
            .padding(top = if (isDesktopPlatform) 16.dp else 8.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 4.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isDesktopPlatform) {
                    IconButton(
                        onClick = onToggleSidebar,
                        modifier = Modifier.offset(x = (-8).dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Toggle Sidebar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                val isToday = selectedDate == Clock.System.todayIn(TimeZone.currentSystemDefault())
                val titleText = if (isToday) "Today" else {
                    val shortDay = selectedDate.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                    "$shortDay ${selectedDate.dayOfMonth}"
                }

                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .offset(x = if (isDesktopPlatform) (-6).dp else 0.dp)
                        .padding(top = 10.dp, bottom = 8.dp)
                        .noRippleClickable { onCalendarIconClick() }
                )
            }

            // Right Side: Icons
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically)
            {
                Box {
                    TopBarIconButtonGroup(
                        bgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                        tint = MaterialTheme.colorScheme.onBackground,
                        hazeState = hazeState,
                        items = listOf(
                            TopBarIconButtonItem(
                                icon = painterResource(Res.drawable.calendar),
                                contentDescription = "Open Calendar",
                                onClick = onOpenCalendarScreenClick
                            ),
                            TopBarIconButtonItem(
                                icon = painterResource(Res.drawable.inbox),
                                contentDescription = "Notifications",
                                onClick = onNotificationsClick
                            ),
                            TopBarIconButtonItem(
                                icon = painterResource(Res.drawable.ellipsis),
                                contentDescription = "Settings",
                                onClick = onSettingsMenuOpen
                            )
                        )
                    )

                    UserSettings(
                        expanded = showSettingsMenu,
                        onDismiss = onSettingsMenuDismiss,
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToTrash = onNavigateToTrash
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingTasksSheet(
    initialDate: LocalDate,
    calendarTaskMap: Map<LocalDate, List<CalendarTaskEntity>>,
    viewModel: DailyEditorViewModel,
    onDismiss: () -> Unit,
    onTaskNoteLinkClick: (String) -> Unit
) {
    val todayTasks = calendarTaskMap[initialDate] ?: emptyList()
    val tomorrowTasks = calendarTaskMap[initialDate.plus(1, DateTimeUnit.DAY)] ?: emptyList()

    InlyBottomSheet(
        expanded = true,
        onDismiss = onDismiss,
        title = "Upcoming Tasks",
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (todayTasks.isEmpty() && tomorrowTasks.isEmpty()) {
                Text(
                    "No tasks scheduled for today or tomorrow.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                if (todayTasks.isNotEmpty()) {
                    TaskDaySection("Today", todayTasks, viewModel, onTaskNoteLinkClick)
                }

                if (tomorrowTasks.isNotEmpty()) {
                    TaskDaySection("Tomorrow", tomorrowTasks, viewModel, onTaskNoteLinkClick)
                }
            }
        }
    }
}

@Composable
private fun DailyCalendarSheet(
    selectedDate: LocalDate,
    initialDate: LocalDate,
    calendarTaskMap: Map<LocalDate, List<CalendarTaskEntity>>,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onGoToToday: () -> Unit
) {
    InlyBottomSheet(
        expanded = true,
        onDismiss = onDismiss,
        title = null,
        subtitle = null
    ) { closeAnd ->
        BottomSheetMonthCalendar(
            selectedDate = selectedDate,
            today = initialDate,
            taskMap = calendarTaskMap,
            onDateSelected = {
                onDateSelected(it)
                closeAnd { onDismiss() }
            },
            onGoToToday = {
                onGoToToday()
                closeAnd { onDismiss() }
            }
        )
    }
}

@Composable
internal fun TaskDaySection(
    dayTitle: String,
    tasks: List<CalendarTaskEntity>,
    viewModel: DailyEditorViewModel,
    onNoteLinkClick: (String) -> Unit = {}
) {
    val sortedTasks = remember(tasks) {
        tasks.sortedBy { it.reminderTimestamp ?: 0L }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        // Day Header
        Text(
            text = dayTitle,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            sortedTasks.forEach { task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val timestamp = task.reminderTimestamp
                    val timeLabel = if (timestamp == null || timestamp == 0L) {
                        "All Day"
                    } else {
                        val dt = kotlinx.datetime.Instant.fromEpochMilliseconds(timestamp)
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                        val hour = dt.hour
                        val amPm = if (hour >= 12) "PM" else "AM"
                        val displayHour = if (hour % 12 == 0) 12 else hour % 12
                        "$displayHour:${dt.minute.toString().padStart(2, '0')} $amPm"
                    }

                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.width(72.dp)
                    )

                    Checkbox(
                        checked = task.isChecked,
                        onCheckedChange = { isChecked ->
                            viewModel.toggleCalendarTask(task, isChecked)
                        },
                        modifier = Modifier.size(24.dp),
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                            uncheckedColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    NoteLinkText(
                        text = task.text.ifBlank { "Empty task" },
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        fontWeight = null,
                        color = if (task.isChecked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onBackground,
                        maxLines = Int.MAX_VALUE,
                        onNoteLinkClick = onNoteLinkClick,
                        textDecoration = if (task.isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                    )
                }
            }
        }
    }
}