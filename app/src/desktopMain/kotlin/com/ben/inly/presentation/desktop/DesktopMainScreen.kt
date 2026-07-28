package com.ben.inly.presentation.desktop

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ben.inly.data.local.prefs.SettingsManager
import com.ben.inly.domain.model.NoteBlock
import com.ben.inly.domain.model.NoteContent
import com.ben.inly.presentation.settings.SettingsScreen
import com.ben.inly.presentation.settings.selfhost.SelfHostSetupScreen
import com.ben.inly.presentation.shared.UserSettings
import com.ben.inly.presentation.shared.components.InlyBottomSheet
import com.ben.inly.presentation.shared.components.InlyButtonPrimary
import com.ben.inly.presentation.shared.components.InlyButtonSecondary
import com.ben.inly.presentation.shared.components.InlyDesktopMenu
import com.ben.inly.presentation.shared.components.InlyTextField
import com.ben.inly.presentation.sync.SyncViewModel
import com.ben.inly.presentation.calendar.CalendarScreen
import com.ben.inly.presentation.mobile.daily.CollapsedWeekStrip
import com.ben.inly.presentation.mobile.daily.DailyEditorPane
import com.ben.inly.presentation.mobile.daily.DailyEditorViewModel
import com.ben.inly.presentation.mobile.daily.TaskDaySection
import com.ben.inly.presentation.mobile.home.DROP_KEY_ROOT
import com.ben.inly.presentation.mobile.home.DRAG_PREFIX_FOLDER
import com.ben.inly.presentation.mobile.home.DRAG_PREFIX_NOTE
import com.ben.inly.presentation.mobile.home.DesktopSortMenu
import com.ben.inly.presentation.mobile.home.DropInsertPosition
import com.ben.inly.presentation.mobile.home.HomeViewModel
import com.ben.inly.presentation.mobile.home.SidebarDragChip
import com.ben.inly.presentation.mobile.home.SidebarFolderRow
import com.ben.inly.presentation.mobile.home.SidebarNoteRow
import com.ben.inly.presentation.mobile.home.SidebarRootDropZone
import com.ben.inly.presentation.mobile.home.SidebarSectionHeader
import com.ben.inly.presentation.mobile.home.SidebarTreeRow
import com.ben.inly.presentation.mobile.home.SortType
import com.ben.inly.presentation.mobile.home.TemplatesDesktopMenu
import com.ben.inly.presentation.mobile.home.flattenFolderTree
import com.ben.inly.presentation.mobile.home.note.NoteScreen
import com.ben.inly.presentation.mobile.home.overview.bookmarks.BookmarksScreen
import com.ben.inly.presentation.mobile.home.overview.documents.DocumentsScreen
import com.ben.inly.presentation.mobile.home.overview.images.ImagesScreen
import com.ben.inly.presentation.mobile.home.overview.reminders.RemindersScreen
import com.ben.inly.presentation.mobile.home.rememberSidebarDragState
import com.ben.inly.presentation.mobile.home.sidebarDragTracker
import com.ben.inly.presentation.customInlyShadow
import com.ben.inly.presentation.search.SearchScreen
import com.ben.inly.presentation.trash.TrashScreen
import dev.chrisbanes.haze.HazeState
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import com.ben.inly.presentation.shared.components.TopBarIconButton
import com.ben.inly.presentation.shared.components.TopBarIconButtonGroup
import com.ben.inly.presentation.shared.components.TopBarIconButtonItem
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import inly.app.generated.resources.Res
import inly.app.generated.resources.arrow_up_down
import inly.app.generated.resources.astroid
import inly.app.generated.resources.bookmark
import inly.app.generated.resources.calendar
import inly.app.generated.resources.sidebar
import inly.app.generated.resources.check_square
import inly.app.generated.resources.ellipsis
import inly.app.generated.resources.pen_square
import inly.app.generated.resources.files
import inly.app.generated.resources.folder_plus
import inly.app.generated.resources.images
import inly.app.generated.resources.inbox
import inly.app.generated.resources.search
import inly.app.generated.resources.template
import org.jetbrains.compose.resources.painterResource
import java.awt.Cursor

private val PANEL_PADDING = 16.dp
private val DesktopPanelShape = RoundedCornerShape(12.dp)

// Right-panel state. Replaces Home's boolean flags.
sealed interface DetailPane {
    data class Daily(val date: LocalDate) : DetailPane
    data class Note(val noteId: String) : DetailPane
    data object Settings : DetailPane
    data object SelfHostSetup : DetailPane
    data object Trash : DetailPane
    data object Reminders : DetailPane
    data object Bookmarks : DetailPane
    data object Images : DetailPane
    data object Documents : DetailPane
    data object Calendar : DetailPane
}

private fun DetailPane.encode(): String = when (this) {
    is DetailPane.Daily -> "DAILY:${date}"
    is DetailPane.Note -> "NOTE:${noteId}"
    DetailPane.Settings -> "PANEL:SETTINGS"
    DetailPane.SelfHostSetup -> "PANEL:SELFHOST"
    DetailPane.Trash -> "PANEL:TRASH"
    DetailPane.Reminders -> "PANEL:REMINDERS"
    DetailPane.Bookmarks -> "PANEL:BOOKMARKS"
    DetailPane.Images -> "PANEL:IMAGES"
    DetailPane.Documents -> "PANEL:DOCUMENTS"
    DetailPane.Calendar -> "PANEL:CALENDAR"
}

private fun decodeDetailPane(raw: String, today: LocalDate): DetailPane = when {
    raw.startsWith("DAILY:") -> runCatching { DetailPane.Daily(LocalDate.parse(raw.removePrefix("DAILY:"))) }
        .getOrElse { DetailPane.Daily(today) }
    raw.startsWith("NOTE:") -> DetailPane.Note(raw.removePrefix("NOTE:"))
    raw == "PANEL:SETTINGS" -> DetailPane.Settings
    raw == "PANEL:SELFHOST" -> DetailPane.SelfHostSetup
    raw == "PANEL:TRASH" -> DetailPane.Trash
    raw == "PANEL:REMINDERS" -> DetailPane.Reminders
    raw == "PANEL:BOOKMARKS" -> DetailPane.Bookmarks
    raw == "PANEL:IMAGES" -> DetailPane.Images
    raw == "PANEL:DOCUMENTS" -> DetailPane.Documents
    raw == "PANEL:CALENDAR" -> DetailPane.Calendar
    else -> DetailPane.Daily(today)
}

@Composable
private fun Modifier.noRippleClickable(interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }, onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )

@Composable
private fun OverviewRow(
    icon: Painter,
    title: String,
    subtitle: String,
    isSelected: Boolean = false,
    iconSize: Dp = 22.dp,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected || isHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f) else Color.Transparent)
            .noRippleClickable(interactionSource, onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f), modifier = Modifier.size(iconSize))
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f))
    }
}

private val MIN_PANEL_WIDTH = 240.dp
private val MAX_PANEL_WIDTH = 520.dp
private val MIN_RAG_PANEL_WIDTH = 320.dp
private val MAX_RAG_PANEL_WIDTH = 640.dp
private val DEFAULT_RAG_PANEL_WIDTH = 400.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun DesktopMainScreen(
    homeViewModel: HomeViewModel = koinViewModel(),
    dailyViewModel: DailyEditorViewModel = koinViewModel(),
    syncViewModel: SyncViewModel = koinViewModel(),
    settingsManager: SettingsManager = koinInject(),
    isSidebarVisible: Boolean = true,
    sidebarWidth: Dp = 340.dp,
    onToggleSidebar: () -> Unit = {},
    onSelectionModeChange: (Boolean) -> Unit = {},
    onPickImage: (onPathSelected: (String) -> Unit) -> Unit = {},
    onTakePhoto: (onPathSelected: (String) -> Unit) -> Unit = {},
    onPickDocument: (onPathSelected: (String) -> Unit) -> Unit = {},
    onOpenFile: (filePath: String, mimeType: String) -> Unit = { _, _ -> },
    onExportMarkdown: (fileName: String, content: String) -> Unit = { _, _ -> },
    onExportPdf: (fileName: String, title: String, blocks: List<NoteBlock>) -> Unit = { _, _, _ -> },
    onExportBackup: (String) -> Unit = {},
    onImportBackupClick: () -> Unit = {},
    onAiIconTap: () -> Unit = {},
    isRagChatVisible: Boolean = false,
    ragViewModel: com.ben.inly.presentation.rag.RagViewModel? = null,
    onDismissRagChat: () -> Unit = {},
) {
    val sidebarHazeState = remember { HazeState() }
    val rightPanelHazeState = remember { HazeState() }
    val savedWidth by settingsManager.desktopSidebarWidthFlow.collectAsState(initial = sidebarWidth.value)
    var panelWidth by remember { mutableStateOf(sidebarWidth) }
    var hasLoadedWidth by remember { mutableStateOf(false) }
    var ragPanelWidth by remember { mutableStateOf(DEFAULT_RAG_PANEL_WIDTH) }

    LaunchedEffect(savedWidth) {
        if (!hasLoadedWidth) {
            panelWidth = savedWidth.dp
            hasLoadedWidth = true
        }
    }
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    // Home data
    val isLoading by homeViewModel.isLoading.collectAsState()
    val favoriteNotes by homeViewModel.favoriteNotes.collectAsState()
    val recentNotes by homeViewModel.recentNotes.collectAsState()
    val selectedNoteIds by homeViewModel.selectedNoteIds.collectAsState()
    val selectedFolderIds by homeViewModel.selectedFolderIds.collectAsState()
    val foldersByParent by homeViewModel.foldersByParent.collectAsState()
    val notesByFolder by homeViewModel.notesByFolder.collectAsState()
    val expandedFolderIds by homeViewModel.expandedFolderIds.collectAsState()
    val currentSortType by homeViewModel.sortType.collectAsState()
    val currentSortOrder by homeViewModel.sortOrder.collectAsState()
    val remindersCount by homeViewModel.remindersCount.collectAsState()
    val bookmarksCount by homeViewModel.bookmarksCount.collectAsState()
    val imagesCount by homeViewModel.imagesCount.collectAsState()
    val documentsCount by homeViewModel.documentsCount.collectAsState()
    val templates by homeViewModel.filteredTemplates.collectAsState()
    val templateSearchQuery by homeViewModel.templateSearchQuery.collectAsState()

    // Daily data (for strip + sheets)
    val selectedDate by dailyViewModel.selectedDate.collectAsState()
    val calendarTaskMap by dailyViewModel.calendarTaskMap.collectAsState()

    val isSelectionMode = selectedNoteIds.isNotEmpty() || selectedFolderIds.isNotEmpty()

    // Right panel state
    val lastOpenedState by settingsManager.lastOpenedDesktopStateFlow.collectAsState(initial = "")
    var detail by remember { mutableStateOf<DetailPane?>(null) }
    var hasRestored by remember { mutableStateOf(false) }

    LaunchedEffect(lastOpenedState, isLoading) {
        if (!hasRestored && !isLoading) {
            val restored = if (lastOpenedState.isBlank()) DetailPane.Daily(today)
            else decodeDetailPane(lastOpenedState, today)
            if (restored is DetailPane.Daily) dailyViewModel.selectDate(restored.date)
            detail = restored
            hasRestored = true
        }
    }

    LaunchedEffect(detail) {
        detail?.let { settingsManager.saveLastOpenedDesktopState(it.encode()) }
    }

    // Menus / popups
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showAddNotePopup by remember { mutableStateOf(false) }
    var showAddFolderPopup by remember { mutableStateOf(false) }
    var addNoteInput by remember { mutableStateOf("") }
    var addFolderInput by remember { mutableStateOf("") }
    var showTemplatesMenu by remember { mutableStateOf(false) }

    var isFavoritesExpanded by remember { mutableStateOf(true) }
    var isNotesExpanded by remember { mutableStateOf(true) }
    var isRecentsExpanded by remember { mutableStateOf(true) }
    var isPeeking by remember { mutableStateOf(false) }

    // Sheets
    var showScheduledTasksSheet by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        DesktopSearchShortcutBus.requests.collect { showSearchDialog = true }
    }

    // Sync
    val snackbarHostState = remember { SnackbarHostState() }
    val syncState by syncViewModel.syncStatus.collectAsState()

    LaunchedEffect(isSidebarVisible) {
        if (isSidebarVisible) isPeeking = false
    }

    val openDaily: (LocalDate) -> Unit = { date ->
        dailyViewModel.selectDate(date)
        detail = DetailPane.Daily(date)
        isPeeking = false
    }
    val openNote: (String) -> Unit = { id ->
        detail = DetailPane.Note(id)
        isPeeking = false
    }

    LaunchedEffect(syncState) {
        if (syncState != "Idle" && syncState != "Syncing...") {
            snackbarHostState.showSnackbar(message = syncState)
            syncViewModel.resetSyncStatus()
        }
    }

    LaunchedEffect(isSelectionMode) { onSelectionModeChange(isSelectionMode) }

    val handleCreateNote = { title: String ->
        homeViewModel.createNewNote(title = title, forceHomeFolder = false) { newId -> openNote(newId) }
    }
    val handleCreateFolder = { name: String -> homeViewModel.createNewFolder(name) }

    // Re-seeds any missing predefined template every time the templates menu opens.
    val handleOpenTemplates = {
        homeViewModel.onTemplatesMenuOpened()
        showAddNotePopup = false
        showTemplatesMenu = true
    }
    val handleTemplateClick = { templateId: String ->
        homeViewModel.createNoteFromTemplate(templateId) { newId -> openNote(newId) }
    }
    // Opens the template's own note directly (no clone) - the editor shows an "Editing
    // Template" pill for any note with isTemplate = true, so no separate mode is needed here.
    val handleEditTemplate = { templateId: String -> openNote(templateId) }
    val handleCreateNewTemplate = {
        homeViewModel.saveAsTemplate(title = "", content = NoteContent(blocks = emptyList())) { newId -> openNote(newId) }
    }

    // Drag
    val dragState = rememberSidebarDragState()
    val sidebarListState = rememberLazyListState()
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 46.dp.toPx() }

    val settingsMenuSlot = @Composable {
        UserSettings(
            expanded = showSettingsMenu,
            onDismiss = { showSettingsMenu = false },
            onNavigateToSettings = { showSettingsMenu = false; detail = DetailPane.Settings },
            onNavigateToSelfHostSetup = { showSettingsMenu = false; detail = DetailPane.SelfHostSetup },
            onNavigateToTrash = { showSettingsMenu = false; detail = DetailPane.Trash }
        )
    }

    // LEFT PANEL
    val leftPanel = @Composable { startPadding: Dp, endPadding: Dp ->
        val treeRows = if (isNotesExpanded) flattenFolderTree(
            null, 0, foldersByParent, notesByFolder, expandedFolderIds,
            isManualSort = currentSortType == SortType.MANUAL
        ) else emptyList()

        val rowKeys: List<String?> = buildList {
            add(null)

            if (!isSelectionMode && favoriteNotes.isNotEmpty()) {
                add(null) // Favorites header
                if (isFavoritesExpanded) favoriteNotes.forEach { add("sb_fav_${it.noteId}") }
            }

            add(null) // Notes header
            add(DROP_KEY_ROOT)

            if (isNotesExpanded) treeRows.forEach { add(it.key) }

            if (!isSelectionMode && recentNotes.isNotEmpty()) {
                add(null) // Recents header
                if (isRecentsExpanded) recentNotes.forEach { add("sb_recent_${it.noteId}") }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(start = startPadding, end = endPadding)) {

            // top: icon row
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleSidebar) {
                    Icon(painterResource(Res.drawable.sidebar), "Collapse sidebar", tint = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.weight(1f))
                Box {
                    TopBarIconButtonGroup(
                        bgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                        tint = MaterialTheme.colorScheme.onBackground,
                        items = listOf(
                            TopBarIconButtonItem(
                                icon = painterResource(Res.drawable.inbox),
                                contentDescription = "Upcoming tasks",
                                onClick = { showScheduledTasksSheet = true }
                            ),
                            TopBarIconButtonItem(
                                icon = painterResource(Res.drawable.calendar),
                                contentDescription = "Calendar",
                                onClick = { detail = DetailPane.Calendar; isPeeking = false }
                            ),
                            TopBarIconButtonItem(
                                icon = painterResource(Res.drawable.ellipsis),
                                contentDescription = "Settings",
                                onClick = { showSettingsMenu = true }
                            )
                        )
                    )
                    settingsMenuSlot()
                }
            }

            // calendar strip
            CollapsedWeekStrip(
                selectedDate = selectedDate,
                today = today,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp).padding(top = 8.dp),
                onDateSelected = { openDaily(it) }
            )

            // Scrolling: overview rows + favorites + notes tree + recents
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .hazeSource(sidebarHazeState)
                    .background(if (isSidebarVisible) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface)
                    .sidebarDragTracker(
                        dragState = dragState,
                        listState = sidebarListState,
                        rowKeys = rowKeys,
                        rowHeightPx = rowHeightPx,
                        payloadForKey = { key ->
                            when {
                                key == null -> null
                                key.startsWith("sb_folder_") -> "$DRAG_PREFIX_FOLDER${key.removePrefix("sb_folder_")}"
                                key.startsWith("sb_note_") -> "$DRAG_PREFIX_NOTE${key.removePrefix("sb_note_")}"
                                key.startsWith("sb_fav_") -> "$DRAG_PREFIX_NOTE${key.removePrefix("sb_fav_")}"
                                key.startsWith("sb_recent_") -> "$DRAG_PREFIX_NOTE${key.removePrefix("sb_recent_")}"
                                else -> null
                            }
                        },
                        isDropTarget = { key, payload ->
                            if (key == null) false
                            else when {
                                key.startsWith("sb_fav_") -> false
                                key.startsWith("sb_recent_") -> false
                                key.startsWith("sb_folder_") &&
                                        payload == "$DRAG_PREFIX_FOLDER${key.removePrefix("sb_folder_")}" -> false
                                else -> true
                            }
                        },
                        onDrop = { payload, targetKey, insertBefore ->
                            when {
                                targetKey == DROP_KEY_ROOT -> when {
                                    payload.startsWith(DRAG_PREFIX_NOTE) -> homeViewModel.moveNote(payload.removePrefix(DRAG_PREFIX_NOTE), null)
                                    payload.startsWith(DRAG_PREFIX_FOLDER) -> homeViewModel.moveFolder(payload.removePrefix(DRAG_PREFIX_FOLDER), null)
                                }
                                !insertBefore && targetKey.startsWith("sb_folder_") &&
                                        dragState.dropPosition == DropInsertPosition.INTO -> {
                                    val folderId = targetKey.removePrefix("sb_folder_")
                                    when {
                                        payload.startsWith(DRAG_PREFIX_NOTE) -> homeViewModel.moveNote(payload.removePrefix(DRAG_PREFIX_NOTE), folderId)
                                        payload.startsWith(DRAG_PREFIX_FOLDER) -> homeViewModel.moveFolder(payload.removePrefix(DRAG_PREFIX_FOLDER), folderId)
                                    }
                                }
                                else -> {
                                    val draggedKey = when {
                                        payload.startsWith(DRAG_PREFIX_NOTE) -> "sb_note_${payload.removePrefix(DRAG_PREFIX_NOTE)}"
                                        payload.startsWith(DRAG_PREFIX_FOLDER) -> "sb_folder_${payload.removePrefix(DRAG_PREFIX_FOLDER)}"
                                        else -> return@sidebarDragTracker
                                    }
                                    homeViewModel.reorderItems(
                                        draggedKey = draggedKey,
                                        targetKey = targetKey,
                                        insertBefore = insertBefore,
                                        orderedKeys = treeRows.map { it.key }
                                    )
                                }
                            }
                        }
                    )
            ) {
                LazyColumn(
                    state = sidebarListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            OverviewRow(painterResource(Res.drawable.check_square), "Tasks", "$remindersCount left", isSelected = detail == DetailPane.Reminders) { detail = DetailPane.Reminders; isPeeking = false }
                            OverviewRow(painterResource(Res.drawable.bookmark), "Bookmarks", "$bookmarksCount saved", isSelected = detail == DetailPane.Bookmarks) { detail = DetailPane.Bookmarks; isPeeking = false }
                            OverviewRow(painterResource(Res.drawable.images), "Images", "$imagesCount saved", isSelected = detail == DetailPane.Images) { detail = DetailPane.Images; isPeeking = false }
                            OverviewRow(painterResource(Res.drawable.files), "Documents", "$documentsCount attached", isSelected = detail == DetailPane.Documents) { detail = DetailPane.Documents; isPeeking = false }
                        }
                    }

                    if (!isSelectionMode && favoriteNotes.isNotEmpty()) {
                        item { SidebarSectionHeader("Favorites", isFavoritesExpanded, { isFavoritesExpanded = !isFavoritesExpanded }) }
                        if (isFavoritesExpanded) {
                            items(favoriteNotes, key = { "sb_fav_${it.noteId}" }) { note ->
                                SidebarNoteRow(
                                    note = note, level = 0,
                                    isActive = (detail as? DetailPane.Note)?.noteId == note.noteId,
                                    isSelected = selectedNoteIds.contains(note.noteId),
                                    dragState = dragState,
                                    onClick = { openNote(note.noteId) },
                                    onRename = { newTitle -> homeViewModel.renameNote(note.noteId, newTitle) },
                                    onDelete = { homeViewModel.trashNote(note.noteId) },
                                    rowKey = "sb_fav_${note.noteId}"
                                )
                            }
                        }
                    }

                    item {
                        SidebarSectionHeader(
                            title = "Notes", isExpanded = isNotesExpanded,
                            onToggle = { isNotesExpanded = !isNotesExpanded },
                            trailing = if (isSelectionMode) null else {
                                {
                                    Box {
                                        Icon(painterResource(Res.drawable.arrow_up_down), "Sort", modifier = Modifier.size(19.dp).noRippleClickable { showSortMenu = true }, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                        InlyDesktopMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                            DesktopSortMenu(currentSortType = currentSortType, currentSortOrder = currentSortOrder, onDismiss = { showSortMenu = false }, onSortChanged = { type, order -> homeViewModel.updateSort(type, order); showSortMenu = false })
                                        }
                                    }
                                    Box {
                                        Icon(painterResource(Res.drawable.pen_square), "New note", modifier = Modifier.size(20.dp).noRippleClickable { addNoteInput = ""; showAddNotePopup = true }, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                        InlyDesktopMenu(expanded = showAddNotePopup, onDismissRequest = { showAddNotePopup = false }, modifier = Modifier.width(280.dp)) {
                                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("New Note", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                                    Icon(
                                                        painter = painterResource(Res.drawable.template),
                                                        contentDescription = "Templates",
                                                        tint = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.size(22.dp).noRippleClickable { handleOpenTemplates() }
                                                    )
                                                }
                                                InlyTextField(value = addNoteInput, onValueChange = { addNoteInput = it }, placeholder = "Note title...", modifier = Modifier.fillMaxWidth())
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    InlyButtonSecondary(text = "Cancel", onClick = { showAddNotePopup = false }, modifier = Modifier.weight(1f))
                                                    InlyButtonPrimary(text = "Create", onClick = { if (addNoteInput.isNotBlank()) { handleCreateNote(addNoteInput.trim()); showAddNotePopup = false } }, modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                        // Anchored to the same Box as the New Note icon, since that's the
                                        // only entry point that opens this menu on this screen.
                                        TemplatesDesktopMenu(
                                            expanded = showTemplatesMenu,
                                            templates = templates,
                                            searchQuery = templateSearchQuery,
                                            onSearchQueryChange = { homeViewModel.updateTemplateSearchQuery(it) },
                                            onDismissRequest = { showTemplatesMenu = false },
                                            onTemplateClick = { id -> showTemplatesMenu = false; handleTemplateClick(id) },
                                            onEditTemplate = { id -> showTemplatesMenu = false; handleEditTemplate(id) },
                                            onDeleteTemplate = { id -> homeViewModel.deleteTemplate(id) },
                                            onCreateNewTemplate = { showTemplatesMenu = false; handleCreateNewTemplate() }
                                        )
                                    }
                                    Box {
                                        Icon(painterResource(Res.drawable.folder_plus), "New folder", modifier = Modifier.size(22.dp).noRippleClickable { addFolderInput = ""; showAddFolderPopup = true }, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                        InlyDesktopMenu(expanded = showAddFolderPopup, onDismissRequest = { showAddFolderPopup = false }, modifier = Modifier.width(280.dp)) {
                                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                                Text("New Folder", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 10.dp))
                                                InlyTextField(value = addFolderInput, onValueChange = { addFolderInput = it }, placeholder = "e.g. Personal, Work...", modifier = Modifier.fillMaxWidth())
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    InlyButtonSecondary(text = "Cancel", onClick = { showAddFolderPopup = false }, modifier = Modifier.weight(1f))
                                                    InlyButtonPrimary(text = "Create", onClick = { if (addFolderInput.isNotBlank()) { handleCreateFolder(addFolderInput.trim()); showAddFolderPopup = false } }, modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }

                    item(key = DROP_KEY_ROOT) { SidebarRootDropZone(dragState = dragState) }

                    if (isNotesExpanded) {
                        items(treeRows, key = { it.key }) { row ->
                            when (row) {
                                is SidebarTreeRow.Folder -> SidebarFolderRow(
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(220, easing = FastOutSlowInEasing),
                                        fadeOutSpec = tween(180, easing = FastOutSlowInEasing),
                                        placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                    ),
                                    folder = row.folder,
                                    level = row.level,
                                    isExpanded = expandedFolderIds.contains(row.folder.folderId),
                                    isSelected = selectedFolderIds.contains(row.folder.folderId),
                                    dragState = dragState,
                                    onClick = { homeViewModel.toggleFolderExpansion(row.folder.folderId) },
                                    onAddNote = { homeViewModel.createNoteInParent(row.folder.folderId, autoExpand = true) { newId -> openNote(newId) } },
                                    onAddSubfolder = { name -> homeViewModel.createFolderInParent(row.folder.folderId, name = name, autoExpand = true) },
                                    onRename = { newName -> homeViewModel.renameFolder(row.folder.folderId, newName) },
                                    onDelete = { homeViewModel.trashFolder(row.folder.folderId) }
                                )
                                is SidebarTreeRow.Note -> SidebarNoteRow(
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(220, easing = FastOutSlowInEasing),
                                        fadeOutSpec = tween(180, easing = FastOutSlowInEasing),
                                        placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                    ),
                                    note = row.note,
                                    level = row.level,
                                    isActive = (detail as? DetailPane.Note)?.noteId == row.note.noteId,
                                    isSelected = selectedNoteIds.contains(row.note.noteId),
                                    dragState = dragState,
                                    onClick = { openNote(row.note.noteId) },
                                    onRename = { newTitle -> homeViewModel.renameNote(row.note.noteId, newTitle) },
                                    onDelete = { homeViewModel.trashNote(row.note.noteId) }
                                )
                            }
                        }
                    }

                    if (!isSelectionMode && recentNotes.isNotEmpty()) {
                        item { SidebarSectionHeader("Recents", isRecentsExpanded, { isRecentsExpanded = !isRecentsExpanded }) }
                        if (isRecentsExpanded) {
                            items(recentNotes, key = { "sb_recent_${it.noteId}" }) { note ->
                                SidebarNoteRow(
                                    note = note, level = 0,
                                    isActive = (detail as? DetailPane.Note)?.noteId == note.noteId,
                                    isSelected = selectedNoteIds.contains(note.noteId),
                                    dragState = dragState,
                                    onClick = { openNote(note.noteId) },
                                    onRename = { newTitle -> homeViewModel.renameNote(note.noteId, newTitle) },
                                    onDelete = { homeViewModel.trashNote(note.noteId) },
                                    rowKey = "sb_recent_${note.noteId}"
                                )
                            }
                        }
                    }
                }

                SidebarDragChip(
                    dragState = dragState,
                    labelForPayload = { payload ->
                        when {
                            payload.startsWith(DRAG_PREFIX_NOTE) -> {
                                val id = payload.removePrefix(DRAG_PREFIX_NOTE)
                                notesByFolder.values.flatten().find { it.noteId == id }?.title?.ifEmpty { "Untitled" } ?: "Note"
                            }
                            payload.startsWith(DRAG_PREFIX_FOLDER) -> {
                                val id = payload.removePrefix(DRAG_PREFIX_FOLDER)
                                foldersByParent.values.flatten().find { it.folderId == id }?.name ?: "Folder"
                            }
                            else -> ""
                        }
                    }
                )
            }

        }

        // floating search + AI assistant buttons, mirrors mobile's InlyBottomBar circles
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = endPadding + 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.25f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(52.dp)
                    .customInlyShadow(CircleShape)
                    .clip(CircleShape)
                    .hazeEffect(sidebarHazeState, HazeStyle.Unspecified, null)
                    .clickable { showSearchDialog = true }
                    .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), shape = CircleShape)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(painterResource(Res.drawable.search), "Search", modifier = Modifier.size(20.dp))
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.25f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(52.dp)
                    .customInlyShadow(CircleShape)
                    .clip(CircleShape)
                    .hazeEffect(sidebarHazeState, HazeStyle.Unspecified, null)
                    .clickable { onAiIconTap() }
                    .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), shape = CircleShape)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(painterResource(Res.drawable.astroid), "Ask AI", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
    }

    // RIGHT PANEL
    val rightPanel = @Composable {
        Box(Modifier.fillMaxSize().hazeSource(state = rightPanelHazeState)) {
            when (val d = detail) {
                null -> Box(Modifier.fillMaxSize())
                is DetailPane.Daily -> DailyEditorPane(
                    viewModel = dailyViewModel,
                    hazeState = rightPanelHazeState,
                    isSidebarVisible = isSidebarVisible,
                    onPickImage = onPickImage,
                    onTakePhoto = onTakePhoto,
                    onPickDocument = onPickDocument,
                    onOpenFile = onOpenFile,
                    onNavigateToEditor = { openNote(it) },
                    onExportMarkdown = onExportMarkdown,
                    onExportPdf = onExportPdf,
                    onSelectionModeChange = onSelectionModeChange
                )
                is DetailPane.Note -> key(d.noteId) {
                    NoteScreen(
                        noteId = d.noteId,
                        onNavigateBack = { detail = DetailPane.Daily(selectedDate) },
                        showBackButton = isSidebarVisible,
                        onSelectionModeChange = onSelectionModeChange,
                        onPickImage = onPickImage, onTakePhoto = onTakePhoto, onPickDocument = onPickDocument,
                        onOpenFile = onOpenFile, onExportMarkdown = onExportMarkdown, onExportPdf = onExportPdf,
                        onNavigateToEditor = { openNote(it) }
                    )
                }
                DetailPane.Settings -> key("settings") {
                    SettingsScreen(
                        onNavigateBack = { detail = DetailPane.Daily(selectedDate) },
                        onExportReady = onExportBackup,
                        onImportClick = onImportBackupClick,
                        onNavigateToSelfHostSetup = { detail = DetailPane.SelfHostSetup },
                        syncViewModel = syncViewModel
                    )
                }
                DetailPane.SelfHostSetup -> key("selfhost_setup") {
                    SelfHostSetupScreen(onNavigateBack = { detail = DetailPane.Settings })
                }
                DetailPane.Trash -> key("trash") { TrashScreen(onNavigateBack = { detail = DetailPane.Daily(selectedDate) }) }
                DetailPane.Reminders -> key("reminders") { RemindersScreen(onNavigateBack = { detail = DetailPane.Daily(selectedDate) }, onOpenFile = onOpenFile, onNavigateToEditor = { openNote(it) }) }
                DetailPane.Images -> key("images") { ImagesScreen(onNavigateBack = { detail = DetailPane.Daily(selectedDate) }, onTriggerImagePicker = { onPickImage { } }) }
                DetailPane.Documents -> key("documents") { DocumentsScreen(onNavigateBack = { detail = DetailPane.Daily(selectedDate) }, onTriggerDocumentPicker = { onPickDocument { } }, onOpenFile = onOpenFile) }
                DetailPane.Bookmarks -> key("bookmarks") { BookmarksScreen(onNavigateBack = { detail = DetailPane.Daily(selectedDate) }) }
                DetailPane.Calendar -> key("calendar") { CalendarScreen(onNavigateBack = { detail = DetailPane.Daily(selectedDate) }) }
            }
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0)) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).consumeWindowInsets(paddingValues)) {
            Row(modifier = Modifier.fillMaxSize()) {

                AnimatedVisibility(
                    visible = isSidebarVisible,
                    enter = expandHorizontally(expandFrom = Alignment.Start, animationSpec = tween(280, easing = FastOutSlowInEasing)),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start, animationSpec = tween(280, easing = FastOutSlowInEasing))
                ) {
                    Box(modifier = Modifier.width(panelWidth).fillMaxHeight().background(MaterialTheme.colorScheme.background)) {
                        leftPanel(0.dp, 0.dp)
                    }
                }

                AnimatedVisibility(
                    visible = isSidebarVisible,
                    enter = fadeIn(tween(280)),
                    exit = fadeOut(tween(280))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(4.dp)
                            .background(Color.Transparent)
                            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        settingsManager.saveDesktopSidebarWidth(panelWidth.value)
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    val deltaDp = with(density) { dragAmount.toDp() }
                                    panelWidth = (panelWidth + deltaDp).coerceIn(MIN_PANEL_WIDTH, MAX_PANEL_WIDTH)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        VerticalDivider(
                            modifier = Modifier.fillMaxHeight(),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.background)) {
                    if (!isSidebarVisible) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 16.dp, top = 16.dp)
                                .zIndex(10f)
                        ) {
                            TopBarIconButton(
                                icon = painterResource(Res.drawable.sidebar),
                                contentDescription = "Expand sidebar",
                                bgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                                tint = MaterialTheme.colorScheme.onSurface,
                                hazeState = rightPanelHazeState,
                                onClick = onToggleSidebar
                            )
                        }
                    }
                    rightPanel()
                }

                val isRagPanelVisible = isRagChatVisible && ragViewModel != null

                AnimatedVisibility(
                    visible = isRagPanelVisible,
                    enter = fadeIn(tween(280)),
                    exit = fadeOut(tween(280))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(4.dp)
                            .background(Color.Transparent)
                            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { change, dragAmount ->
                                    change.consume()
                                    val deltaDp = with(density) { dragAmount.toDp() }
                                    ragPanelWidth = (ragPanelWidth - deltaDp).coerceIn(MIN_RAG_PANEL_WIDTH, MAX_RAG_PANEL_WIDTH)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        VerticalDivider(
                            modifier = Modifier.fillMaxHeight(),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isRagPanelVisible,
                    enter = expandHorizontally(expandFrom = Alignment.End, animationSpec = tween(280, easing = FastOutSlowInEasing)),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.End, animationSpec = tween(280, easing = FastOutSlowInEasing))
                ) {
                    Box(modifier = Modifier.width(ragPanelWidth).fillMaxHeight()) {
                        if (ragViewModel != null) {
                            com.ben.inly.presentation.rag.RagChatPanel(
                                onDismiss = onDismissRagChat,
                                viewModel = ragViewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // Hover-to-peek: thin left-edge trigger (only when collapsed)
            if (!isSidebarVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxHeight()
                        .width(16.dp)
                        .zIndex(20f)
                        .onPointerEvent(PointerEventType.Enter) { isPeeking = true }
                )
            }

            // Hover-to-peek: invisible boundary to detect when cursor leaves the panel area
            if (!isSidebarVisible && isPeeking) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = panelWidth + PANEL_PADDING)
                        .zIndex(24f)
                        .onPointerEvent(PointerEventType.Enter) { isPeeking = false }
                )
            }

            // Hover-to-peek: floating sidebar overlay
            AnimatedVisibility(
                visible = !isSidebarVisible && isPeeking,
                enter = slideInHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { -it },
                exit = slideOutHorizontally(animationSpec = tween(200, easing = FastOutSlowInEasing)) { -it },
                modifier = Modifier.align(Alignment.TopStart).zIndex(25f)
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = PANEL_PADDING, top = 12.dp, bottom = 12.dp)
                        .width(panelWidth)
                        .fillMaxHeight()
                        .shadow(16.dp, DesktopPanelShape)
                        .clip(DesktopPanelShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { }
                ) {
                    leftPanel(5.dp, 5.dp)
                }
            }

            // Sheets
            if (showScheduledTasksSheet) {
                val todayTasks = calendarTaskMap[today] ?: emptyList()
                val tomorrowTasks = calendarTaskMap[today.plus(1, DateTimeUnit.DAY)] ?: emptyList()
                InlyBottomSheet(expanded = true, onDismiss = { showScheduledTasksSheet = false }, title = "Upcoming Tasks") { _ ->
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        if (todayTasks.isEmpty() && tomorrowTasks.isEmpty()) {
                            Text("No tasks scheduled for today or tomorrow.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        } else {
                            if (todayTasks.isNotEmpty()) TaskDaySection("Today", todayTasks, dailyViewModel)
                            if (tomorrowTasks.isNotEmpty()) TaskDaySection("Tomorrow", tomorrowTasks, dailyViewModel)
                        }
                    }
                }
            }

            // Search dialog: centered modal instead of a full right-panel screen
            if (showSearchDialog) {
                Dialog(
                    onDismissRequest = { showSearchDialog = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(
                        modifier = Modifier.width(560.dp).heightIn(max = 640.dp),
                        shape = DesktopPanelShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 24.dp
                    ) {
                        SearchScreen(
                            onBack = { showSearchDialog = false },
                            onNoteClick = { noteId -> showSearchDialog = false; openNote(noteId) },
                            onDailyNoteClick = { dateString -> showSearchDialog = false; openDaily(LocalDate.parse(dateString)) }
                        )
                    }
                }
            }

            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 66.dp)) { data ->
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 6.dp, modifier = Modifier.padding(horizontal = 24.dp).wrapContentWidth()) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Sync, contentDescription = "Sync", modifier = Modifier.size(30.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = data.visuals.message, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}