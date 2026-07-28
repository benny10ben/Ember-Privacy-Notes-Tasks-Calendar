package com.ben.inly.presentation.mobile.home

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import com.ben.inly.presentation.shared.rememberStableStatusBarsPadding
import com.ben.inly.presentation.shared.stableStatusBarsPadding
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ben.inly.data.local.room.FolderEntity
import com.ben.inly.data.local.room.NoteMetadataEntity
import com.ben.inly.domain.model.NoteContent
import com.ben.inly.domain.util.isDesktopPlatform
import com.ben.inly.presentation.mobile.daily.DailyEditorViewModel
import com.ben.inly.presentation.mobile.daily.TaskDaySection
import com.ben.inly.presentation.shared.UserSettings
import com.ben.inly.presentation.shared.components.InlyBottomSheet
import com.ben.inly.presentation.shared.components.InlyDesktopMenu
import com.ben.inly.presentation.shared.components.KmpBackHandler
import com.ben.inly.presentation.shared.components.TopBarIconButtonGroup
import com.ben.inly.presentation.shared.components.TopBarIconButtonItem
import com.ben.inly.presentation.sync.SyncViewModel
import com.ben.inly.presentation.sync.showSyncToast
import com.ben.inly.ui.theme.LocalAppIsDark
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.ben.inly.presentation.shared.components.InlyButtonPrimary
import com.ben.inly.presentation.shared.components.InlyButtonSecondary
import com.ben.inly.presentation.shared.components.InlyTextField
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import inly.app.generated.resources.Res
import inly.app.generated.resources.arrow_up_down
import inly.app.generated.resources.calendar
import inly.app.generated.resources.circle_plus
import inly.app.generated.resources.ellipsis
import inly.app.generated.resources.file_text
import inly.app.generated.resources.inbox
import inly.app.generated.resources.pen
import inly.app.generated.resources.pen_square
import inly.app.generated.resources.folder
import inly.app.generated.resources.folder_plus
import inly.app.generated.resources.template
import inly.app.generated.resources.trash
import org.jetbrains.compose.resources.painterResource

private val HORIZONTAL_PADDING = 16.dp
private val DefaultCornerShape = RoundedCornerShape(12.dp)

@Composable
private fun Modifier.mouseScrollable(scrollState: ScrollableState): Modifier {
    val scope = rememberCoroutineScope()
    return this.pointerInput(scrollState) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Scroll) {
                    val change = event.changes.firstOrNull()
                    val delta = change?.scrollDelta?.y ?: change?.scrollDelta?.x ?: 0f
                    if (delta != 0f) {
                        scope.launch { scrollState.scrollBy(delta * 75f) }
                        change?.consume()
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
    onSelectionModeChange: (Boolean) -> Unit = {},
    onNavigateToEditor: (String) -> Unit,
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToReminders: () -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onNavigateToImages: () -> Unit,
    bottomContentPadding: Dp = 0.dp,
    onNavigateToTrash: () -> Unit,
    onNavigateToDocuments: () -> Unit,
    onToggleSidebar: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    syncViewModel: SyncViewModel = koinViewModel(),
    dailyEditorViewModel: DailyEditorViewModel = koinViewModel(),
) {
    val hazeState = remember { HazeState() }

    var showScheduledTasksSheet by remember { mutableStateOf(false) }
    val calendarTaskMap by dailyEditorViewModel.calendarTaskMap.collectAsState()

    val isLoading by viewModel.isLoading.collectAsState()
    val subFolders by viewModel.currentSubFolders.collectAsState()
    val breadcrumbs by viewModel.breadcrumbs.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val recentNotes by viewModel.recentNotes.collectAsState()
    val selectedFolderId by viewModel.selectedFolderId.collectAsState()
    val selectedNoteIds by viewModel.selectedNoteIds.collectAsState()
    val selectedFolderIds by viewModel.selectedFolderIds.collectAsState()
    val favoriteNotes by viewModel.favoriteNotes.collectAsState()

    val remindersCount by viewModel.remindersCount.collectAsState()
    val bookmarksCount by viewModel.bookmarksCount.collectAsState()
    val imagesCount by viewModel.imagesCount.collectAsState()
    val documentsCount by viewModel.documentsCount.collectAsState()

    val currentSortType by viewModel.sortType.collectAsState()
    val currentSortOrder by viewModel.sortOrder.collectAsState()

    val templates by viewModel.filteredTemplates.collectAsState()
    val templateSearchQuery by viewModel.templateSearchQuery.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }
    var showNotesMenu by remember { mutableStateOf(false) }

    var showAddNoteDialog by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }

    var showAddNotePopup by remember { mutableStateOf(false) }
    var showAddFolderPopup by remember { mutableStateOf(false) }
    var addNoteInput by remember { mutableStateOf("") }
    var addFolderInput by remember { mutableStateOf("") }

    // Mobile sheet + desktop popup toggles for the Templates menu opened from the New Note flow.
    var showTemplatesSheet by remember { mutableStateOf(false) }
    var showTemplatesMenu by remember { mutableStateOf(false) }

    var isFavoritesExpanded by remember { mutableStateOf(true) }
    var isNotesExpanded by remember { mutableStateOf(true) }
    var isRecentsExpanded by remember { mutableStateOf(true) }

    val favListState = rememberLazyListState()
    val folderListState = rememberLazyListState()
    val recentListState = rememberLazyListState()

    val gridState = rememberLazyStaggeredGridState()

    val isSelectionMode = selectedNoteIds.isNotEmpty() || selectedFolderIds.isNotEmpty()

    val syncState by syncViewModel.syncStatus.collectAsState()

    LaunchedEffect(syncState) {
        if (syncState != "Idle" && syncState != "Syncing...") {
            showSyncToast(syncState)
            syncViewModel.resetSyncStatus()
        }
    }

    KmpBackHandler(enabled = isSelectionMode) { viewModel.clearSelection() }
    KmpBackHandler(enabled = selectedFolderId != null) { viewModel.navigateUp() }

    LaunchedEffect(isSelectionMode) { onSelectionModeChange(isSelectionMode) }

    val handleCreateFolder = { name: String ->
        viewModel.createNewFolder(name)
        showAddFolderDialog = false
    }

    val handleCreateNote = { title: String ->
        viewModel.createNewNote(title = title, forceHomeFolder = false) { newNoteId ->
            onNavigateToEditor(newNoteId)
        }
        showAddNoteDialog = false
    }

    // Re-seeds any missing predefined template every time either Templates entry point opens.
    // Also closes the mobile New Note sheet it's invoked from - closeAnd() only runs the hide
    // animation, it doesn't flip showAddNoteDialog itself (see AddNoteBottomSheet's onCreate,
    // which does that inline), so this has to be the one to reset it, or the sheet is left
    // mounted (hidden but expanded = true) after the templates sheet opens on top of it.
    val handleOpenTemplates = {
        viewModel.onTemplatesMenuOpened()
        showAddNoteDialog = false
        if (isDesktopPlatform) showTemplatesMenu = true else showTemplatesSheet = true
    }
    val handleTemplateClick = { templateId: String ->
        viewModel.createNoteFromTemplate(templateId) { newNoteId -> onNavigateToEditor(newNoteId) }
    }
    // Opens the template's own note directly - unlike handleTemplateClick, this does NOT clone
    // it into a new note. The editor already renders the "Editing Template" pill for any note
    // with isTemplate = true, so no separate "template edit mode" is needed here.
    val handleEditTemplate = { templateId: String -> onNavigateToEditor(templateId) }
    val handleCreateNewTemplate = {
        viewModel.saveAsTemplate(title = "", content = NoteContent(blocks = emptyList())) { newTemplateId ->
            onNavigateToEditor(newTemplateId)
        }
    }

    // Mobile grid panel
    val leftPanelContent = @Composable {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val cardWidth = (maxWidth - (HORIZONTAL_PADDING * 2) - 10.dp) / 2

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyVerticalStaggeredGrid(
                    state = gridState,
                    columns = StaggeredGridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        top = (if (isDesktopPlatform) 64.dp else 76.dp) + rememberStableStatusBarsPadding().calculateTopPadding(),
                        bottom = bottomContentPadding + 80.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
                    verticalItemSpacing = 10.dp,
                    modifier = Modifier.fillMaxSize().hazeSource(state = hazeState).background(MaterialTheme.colorScheme.background)
                ) {
                    item {
                        Box(Modifier.padding(start = HORIZONTAL_PADDING)) {
                            OverviewCard("Tasks", "$remindersCount left", onClick = { onNavigateToReminders() })
                        }
                    }
                    item {
                        Box(Modifier.padding(end = HORIZONTAL_PADDING)) {
                            OverviewCard("Bookmarks", "$bookmarksCount saved", onClick = { onNavigateToBookmarks() })
                        }
                    }
                    item {
                        Box(Modifier.padding(start = HORIZONTAL_PADDING)) {
                            OverviewCard("Images", "$imagesCount saved", onClick = { onNavigateToImages() })
                        }
                    }
                    item {
                        Box(Modifier.padding(end = HORIZONTAL_PADDING)) {
                            OverviewCard("Documents", "$documentsCount attached", onClick = { onNavigateToDocuments() })
                        }
                    }

                    if (favoriteNotes.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Row(modifier = Modifier.fillMaxWidth().padding(start = HORIZONTAL_PADDING, end = HORIZONTAL_PADDING, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(modifier = Modifier.clip(RoundedCornerShape(4.dp)).noRippleClickable { isFavoritesExpanded = !isFavoritesExpanded }.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Favorites", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Icon(imageVector = if (isFavoritesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = "Toggle Favorites", modifier = Modifier.padding(start = 4.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                        if (isFavoritesExpanded) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                LazyRow(
                                    state = favListState,
                                    modifier = Modifier.fillMaxWidth()
                                        .mouseScrollable(favListState),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(horizontal = HORIZONTAL_PADDING)
                                ) {
                                    items(favoriteNotes, key = { "fav_${it.noteId}" }) { note ->
                                        Box(Modifier.width(cardWidth)) {
                                            NoteCard(
                                                note = note,
                                                isSelected = selectedNoteIds.contains(note.noteId),
                                                onClick = {
                                                    if (isSelectionMode) viewModel.toggleNoteSelection(
                                                        note.noteId
                                                    ) else onNavigateToEditor(note.noteId)
                                                },
                                                onLongClick = { viewModel.toggleNoteSelection(note.noteId) })
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (notes.isNotEmpty() || !isSelectionMode) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Row(modifier = Modifier.fillMaxWidth().padding(start = HORIZONTAL_PADDING, end = HORIZONTAL_PADDING, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(modifier = Modifier.clip(RoundedCornerShape(4.dp)).noRippleClickable { isNotesExpanded = !isNotesExpanded }.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Notes", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Icon(imageVector = if (isNotesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = "Toggle Notes", modifier = Modifier.padding(start = 4.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                                }
                                if (!isSelectionMode) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box {
                                            Icon(painterResource(Res.drawable.arrow_up_down), "Sort", modifier = Modifier.size(20.dp).clip(CircleShape).noRippleClickable { showSortMenu = true }, tint = MaterialTheme.colorScheme.onSurface)
                                            if (isDesktopPlatform) {
                                                InlyDesktopMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                                    DesktopSortMenu(currentSortType = currentSortType, currentSortOrder = currentSortOrder, onDismiss = { showSortMenu = false }, onSortChanged = { type, order -> viewModel.updateSort(type, order); showSortMenu = false })
                                                }
                                            }
                                        }
                                        Box {
                                            Icon(painterResource(Res.drawable.pen_square), "New Note", modifier = Modifier.size(22.dp).noRippleClickable { if (isDesktopPlatform) { addNoteInput = ""; showAddNotePopup = true } else showAddNoteDialog = true }, tint = MaterialTheme.colorScheme.onSurface)
                                            if (isDesktopPlatform) {
                                                InlyDesktopMenu(expanded = showAddNotePopup, onDismissRequest = { showAddNotePopup = false }, modifier = Modifier.width(280.dp)) {
                                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                                            Text("New Note", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                            Icon(
                                                                painter = painterResource(Res.drawable.template),
                                                                contentDescription = "Templates",
                                                                tint = MaterialTheme.colorScheme.onSurface,
                                                                modifier = Modifier.size(24.dp).noRippleClickable {
                                                                    showAddNotePopup = false
                                                                    handleOpenTemplates()
                                                                }
                                                            )
                                                        }
                                                        InlyTextField(value = addNoteInput, onValueChange = { addNoteInput = it }, placeholder = "Note title...", modifier = Modifier.fillMaxWidth())
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            InlyButtonSecondary(text = "Cancel", onClick = { showAddNotePopup = false }, modifier = Modifier.weight(1f))
                                                            InlyButtonPrimary(text = "Create", onClick = { if (addNoteInput.isNotBlank()) { handleCreateNote(addNoteInput.trim()); showAddNotePopup = false } }, modifier = Modifier.weight(1f))
                                                        }
                                                    }
                                                }
                                            }
                                            TemplatesDesktopMenu(
                                                expanded = showTemplatesMenu,
                                                templates = templates,
                                                searchQuery = templateSearchQuery,
                                                onSearchQueryChange = { viewModel.updateTemplateSearchQuery(it) },
                                                onDismissRequest = { showTemplatesMenu = false },
                                                onTemplateClick = { id -> showTemplatesMenu = false; handleTemplateClick(id) },
                                                onEditTemplate = { id -> showTemplatesMenu = false; handleEditTemplate(id) },
                                                onDeleteTemplate = { id -> viewModel.deleteTemplate(id) },
                                                onCreateNewTemplate = { showTemplatesMenu = false; handleCreateNewTemplate() }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isNotesExpanded) {
                        if (subFolders.isNotEmpty() || !isSelectionMode) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                LazyRow(state = folderListState, contentPadding = PaddingValues(horizontal = HORIZONTAL_PADDING), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).mouseScrollable(folderListState)) {
                                    if (!isSelectionMode) {
                                        item {
                                            if (isDesktopPlatform) {
                                                Box(modifier = Modifier.wrapContentSize(Alignment.TopStart).height(36.dp)) {
                                                    FolderPill(name = "New", isSelected = false, isNewButton = true, onClick = { addFolderInput = ""; showAddFolderPopup = true }, onLongClick = {})
                                                    InlyDesktopMenu(expanded = showAddFolderPopup, onDismissRequest = { showAddFolderPopup = false }, modifier = Modifier.width(280.dp)) {
                                                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                                            Text("New Folder", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 10.dp))
                                                            InlyTextField(value = addFolderInput, onValueChange = { addFolderInput = it }, placeholder = "e.g. Personal, Work...", modifier = Modifier.fillMaxWidth())
                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                InlyButtonSecondary(text = "Cancel", onClick = { showAddFolderPopup = false }, modifier = Modifier.weight(1f))
                                                                InlyButtonPrimary(text = "Create", onClick = { if (addFolderInput.isNotBlank()) { handleCreateFolder(addFolderInput.trim()); showAddFolderPopup = false } }, modifier = Modifier.weight(1f))
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                FolderPill(name = "New", isSelected = false, isNewButton = true, onClick = { showAddFolderDialog = true }, onLongClick = {})
                                            }
                                        }
                                    }
                                    items(subFolders, key = { it.folderId }) { folder ->
                                        FolderPill(
                                            name = folder.name,
                                            isSelected = selectedFolderIds.contains(folder.folderId),
                                            onClick = {
                                                if (isSelectionMode) viewModel.toggleFolderSelection(
                                                    folder.folderId
                                                ) else viewModel.selectFolder(folder.folderId)
                                            },
                                            onLongClick = { viewModel.toggleFolderSelection(folder.folderId) })
                                    }
                                }
                            }
                        }

                        if (!isDesktopPlatform && !isSelectionMode && selectedFolderId != null) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                BreadcrumbTrail(
                                    selectedFolderId = selectedFolderId,
                                    breadcrumbs = breadcrumbs,
                                    onNavigate = { viewModel.selectFolder(it) },
                                    modifier = Modifier.padding(horizontal = HORIZONTAL_PADDING)
                                )
                            }
                        }

                        itemsIndexed(notes, key = { _, note -> note.noteId }) { index, note ->
                            val sidePad = if (index % 2 == 0) Modifier.padding(start = HORIZONTAL_PADDING) else Modifier.padding(end = HORIZONTAL_PADDING)
                            Box(modifier = sidePad) {
                                NoteCard(
                                    note = note, isSelected = selectedNoteIds.contains(note.noteId),
                                    onClick = {
                                        if (isSelectionMode) viewModel.toggleNoteSelection(
                                            note.noteId
                                        ) else onNavigateToEditor(note.noteId)
                                    },
                                    onLongClick = { viewModel.toggleNoteSelection(note.noteId) })
                            }
                        }
                    }

                    if (recentNotes.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Row(modifier = Modifier.fillMaxWidth().padding(start = HORIZONTAL_PADDING, end = HORIZONTAL_PADDING, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(modifier = Modifier.clip(RoundedCornerShape(4.dp)).noRippleClickable { isRecentsExpanded = !isRecentsExpanded }.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Recents", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Icon(imageVector = if (isRecentsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = "Toggle Recents", modifier = Modifier.padding(start = 4.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                        if (isRecentsExpanded) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                LazyRow(
                                    state = recentListState,
                                    modifier = Modifier.fillMaxWidth()
                                        .mouseScrollable(recentListState),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(horizontal = HORIZONTAL_PADDING)
                                ) {
                                    items(recentNotes, key = { "recent_${it.noteId}" }) { note ->
                                        Box(Modifier.width(cardWidth)) {
                                            NoteCard(
                                                note = note,
                                                isSelected = selectedNoteIds.contains(note.noteId),
                                                onClick = {
                                                    if (isSelectionMode) viewModel.toggleNoteSelection(
                                                        note.noteId
                                                    ) else onNavigateToEditor(note.noteId)
                                                },
                                                onLongClick = { viewModel.toggleNoteSelection(note.noteId) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Scroll state detection
            val isScrolled by remember {
                derivedStateOf {
                    gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0
                }
            }

            // Sticky Top Bar Overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures {} }
                    .then(
                        if (isScrolled) {
                            Modifier
                                .hazeEffect(
                                    state = hazeState,
                                    style = HazeStyle.Unspecified,
                                    block = null
                                )
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.65f))
                        } else {
                            Modifier
                        }
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .stableStatusBarsPadding()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                            top = 10.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left side: Title / Breadcrumbs
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (!isSelectionMode) {
                            if (isDesktopPlatform) {
                                IconButton(
                                    onClick = onToggleSidebar,
                                    modifier = Modifier.offset(x = (-8).dp)
                                ) {
                                    Icon(
                                        Icons.Default.Menu,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                BreadcrumbTrail(
                                    selectedFolderId = selectedFolderId,
                                    breadcrumbs = breadcrumbs,
                                    onNavigate = { viewModel.selectFolder(it) },
                                    modifier = Modifier.weight(1f).offset(x = (-6).dp)
                                )
                            } else {
                                Text(
                                    "Home",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }

                    // Right side: Settings Menu
                    Box {
                        TopBarIconButtonGroup(
                            bgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                            tint = MaterialTheme.colorScheme.onSurface,
                            items = listOf(
                                TopBarIconButtonItem(
                                    icon = painterResource(Res.drawable.calendar),
                                    contentDescription = "Open Calendar",
                                    onClick = onNavigateToCalendar
                                ),
                                TopBarIconButtonItem(
                                    icon = painterResource(Res.drawable.inbox),
                                    contentDescription = "Notifications",
                                    onClick = { showScheduledTasksSheet = true }
                                ),
                                TopBarIconButtonItem(
                                    icon = painterResource(Res.drawable.ellipsis),
                                    contentDescription = "Settings",
                                    onClick = { showNotesMenu = true }
                                )
                            )
                        )

                        UserSettings(
                            expanded = showNotesMenu, onDismiss = { showNotesMenu = false },
                            onNavigateToSettings = {
                                onNavigateToSettings(); showNotesMenu = false
                            },
                            onNavigateToTrash = { onNavigateToTrash(); showNotesMenu = false }
                        )
                    }
                }
            }
        }
    }

    // Scaffold
    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0)) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).consumeWindowInsets(paddingValues)) {

            leftPanelContent()

            NotesSelectionPill(isVisible = isSelectionMode, selectedCount = selectedNoteIds.size + selectedFolderIds.size, onClearSelection = { viewModel.clearSelection() }, onDelete = { viewModel.deleteSelectedItems() }, modifier = Modifier.align(Alignment.BottomCenter))

            if (!isDesktopPlatform) {
                AddFolderBottomSheet(expanded = showAddFolderDialog, onDismiss = { showAddFolderDialog = false }, onCreate = handleCreateFolder)
                AddNoteBottomSheet(expanded = showAddNoteDialog, onDismiss = { showAddNoteDialog = false }, onCreate = handleCreateNote, onOpenTemplates = handleOpenTemplates)
                SortBottomSheet(expanded = showSortMenu, currentSortType = currentSortType, currentSortOrder = currentSortOrder, onDismiss = { showSortMenu = false }, onSortChanged = { type, order -> viewModel.updateSort(type, order); showSortMenu = false })
                TemplatesBottomSheet(
                    expanded = showTemplatesSheet,
                    templates = templates,
                    searchQuery = templateSearchQuery,
                    onSearchQueryChange = { viewModel.updateTemplateSearchQuery(it) },
                    onDismiss = { showTemplatesSheet = false },
                    onTemplateClick = handleTemplateClick,
                    onEditTemplate = handleEditTemplate,
                    onDeleteTemplate = { id -> viewModel.deleteTemplate(id) },
                    onCreateNewTemplate = handleCreateNewTemplate
                )
            }


            if (showScheduledTasksSheet) {
                val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
                val todayTasks = calendarTaskMap[today] ?: emptyList()
                val tomorrowTasks = calendarTaskMap[today.plus(1, DateTimeUnit.DAY)] ?: emptyList()

                InlyBottomSheet(
                    expanded = true,
                    onDismiss = { showScheduledTasksSheet = false },
                    title = "Upcoming Tasks",
                ) { _ ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
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
                            val onTaskNoteLinkClick: (String) -> Unit = { noteId ->
                                showScheduledTasksSheet = false
                                onNavigateToEditor(noteId)
                            }

                            if (todayTasks.isNotEmpty()) {
                                TaskDaySection("Today", todayTasks, dailyEditorViewModel, onTaskNoteLinkClick)
                            }

                            if (tomorrowTasks.isNotEmpty()) {
                                TaskDaySection("Tomorrow", tomorrowTasks, dailyEditorViewModel, onTaskNoteLinkClick)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DesktopSortMenu(currentSortType: SortType, currentSortOrder: SortOrder, onDismiss: () -> Unit, onSortChanged: (SortType, SortOrder) -> Unit) {
    Column(modifier = Modifier.width(200.dp).padding(vertical = 4.dp)) {
        DesktopSortOptionItem("Last Edited", currentSortType == SortType.LAST_EDITED) { onDismiss(); onSortChanged(SortType.LAST_EDITED, currentSortOrder) }
        DesktopSortOptionItem("Date Created", currentSortType == SortType.DATE_CREATED) { onDismiss(); onSortChanged(SortType.DATE_CREATED, currentSortOrder) }
        DesktopSortOptionItem("Name (A-Z)", currentSortType == SortType.NAME) { onDismiss(); onSortChanged(SortType.NAME, currentSortOrder) }
        DesktopSortOptionItem("Manual", currentSortType == SortType.MANUAL) {
            onDismiss(); onSortChanged(SortType.MANUAL, currentSortOrder)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        DesktopSortOptionItem("Ascending", currentSortOrder == SortOrder.ASCENDING) { onDismiss(); onSortChanged(currentSortType, SortOrder.ASCENDING) }
        DesktopSortOptionItem("Descending", currentSortOrder == SortOrder.DESCENDING) { onDismiss(); onSortChanged(currentSortType, SortOrder.DESCENDING) }
    }
}

@Composable
private fun DesktopSortOptionItem(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        if (isSelected) Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun OverviewCard(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(shape = DefaultCornerShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().clip(DefaultCornerShape).noRippleClickable { onClick() }) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun BreadcrumbTrail(selectedFolderId: String?, breadcrumbs: List<FolderEntity>, onNavigate: (String?) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp)) {
        item {
            val isRoot = selectedFolderId == null
            Text("Home", style = MaterialTheme.typography.bodyLarge, fontWeight = if (isRoot) FontWeight.Bold else FontWeight.Medium, color = if (isRoot) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface, modifier = Modifier.noRippleClickable { onNavigate(null) })
        }
        items(breadcrumbs) { folder ->
            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.padding(horizontal = 6.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
            val isLast = folder.folderId == selectedFolderId
            Text(folder.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium, color = if (isLast) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface, modifier = Modifier.noRippleClickable { onNavigate(folder.folderId) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderPill(name: String, isSelected: Boolean, isNewButton: Boolean = false, onClick: () -> Unit, onLongClick: () -> Unit) {
    val bgColor = when { isNewButton -> MaterialTheme.colorScheme.primary; isSelected -> MaterialTheme.colorScheme.onSurface; isDesktopPlatform -> MaterialTheme.colorScheme.background; else -> MaterialTheme.colorScheme.surface }
    val textColor = when { isNewButton -> MaterialTheme.colorScheme.onPrimary; isSelected -> MaterialTheme.colorScheme.background; else -> MaterialTheme.colorScheme.onSurface }
    Surface(shape = RoundedCornerShape(8.dp), color = bgColor, contentColor = textColor, modifier = Modifier.height(36.dp).defaultMinSize(minWidth = 72.dp).clip(RoundedCornerShape(8.dp)).combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick, onLongClick = onLongClick)) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 0.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(if (isNewButton) painterResource(Res.drawable.folder_plus) else painterResource(Res.drawable.folder), null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(name, style = MaterialTheme.typography.bodyLarge)
            AnimatedVisibility(visible = isSelected && !isNewButton) { Row { Spacer(Modifier.width(6.dp)); Icon(Icons.Default.Check, "Selected", modifier = Modifier.size(14.dp)) } }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(note: NoteMetadataEntity, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val mediaStorageHelper: com.ben.inly.domain.util.MediaStorageHelper = koinInject()
    val bgColor = when { isSelected -> MaterialTheme.colorScheme.onSurface; isDesktopPlatform -> MaterialTheme.colorScheme.background; else -> MaterialTheme.colorScheme.surface }
    val titleColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val mutedColor = if (isSelected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
    val coverHeight = 72.dp; val iconOverhang = 12.dp
    val hasCover = note.coverImagePath != null; val hasIcon = !note.icon.isNullOrEmpty(); val hasHeader = hasCover || hasIcon

    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(DefaultCornerShape).background(bgColor).combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick, onLongClick = onLongClick)) {
        Column(Modifier.fillMaxSize()) {
            if (hasHeader) {
                Box(modifier = Modifier.fillMaxWidth().height(coverHeight)) {
                    if (note.coverImagePath != null) {
                        val absolutePath = mediaStorageHelper.getAbsoluteMediaPath(note.coverImagePath)
                        val context = coil3.compose.LocalPlatformContext.current
                        val request = remember(absolutePath) { coil3.request.ImageRequest.Builder(context).data(absolutePath).memoryCacheKey(absolutePath).diskCacheKey(absolutePath).build() }
                        AsyncImage(model = request, contentDescription = "Cover", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (isSelected) 0.12f else 0.05f)))
                    }
                }
            }
            Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(start = 12.dp, end = if (note.isFavorite && !hasHeader) 26.dp else 12.dp, top = if (hasIcon) iconOverhang + 10.dp else 10.dp, bottom = 10.dp)) {
                Text(note.title.ifEmpty { "Untitled" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(text = note.snippet.takeIf { it.isNotBlank() } ?: "Empty note...", style = MaterialTheme.typography.labelSmall, color = mutedColor, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (hasIcon) Text(text = note.icon!!, fontSize = 22.sp, modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp).offset(y = coverHeight - iconOverhang))
        if (note.isFavorite) Icon(Icons.Default.Star, "Favorite", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(14.dp))
        if (isSelected) Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).background(MaterialTheme.colorScheme.onPrimary, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp)) }
    }
}

@Composable
fun NotesSelectionPill(isVisible: Boolean, selectedCount: Int, onClearSelection: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val isDark = LocalAppIsDark.current
    val pillColor = if (isDark) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary
    val tint = if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
    AnimatedVisibility(visible = isVisible, enter = slideInVertically(initialOffsetY = { it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(), modifier = modifier.padding(horizontal = 24.dp)) {
        Surface(shape = DefaultCornerShape, color = pillColor, modifier = Modifier.padding(bottom = 28.dp).shadow(6.dp, DefaultCornerShape, spotColor = Color.Black.copy(alpha = 0.2f))) {
            val pillScroll = rememberScrollState()
            Row(modifier = Modifier.horizontalScroll(pillScroll).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Icon(Icons.Default.Close, "Clear", modifier = Modifier.size(18.dp).noRippleClickable { onClearSelection() }, tint = tint)
                Text("$selectedCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint)
                Box(Modifier.width(1.dp).height(18.dp).background(tint.copy(alpha = 0.2f)))
                Icon(Icons.Default.Delete, "Move to Trash", modifier = Modifier.size(18.dp).noRippleClickable { onDelete() }, tint = tint)
            }
        }
    }
}

@Composable
fun AddFolderBottomSheet(expanded: Boolean, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var folderName by remember { mutableStateOf("") }
    InlyBottomSheet(expanded = expanded, onDismiss = onDismiss, title = "New Folder", subtitle = "Organize your notes with a new category.") { closeAnd ->
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            InlyTextField(
                value = folderName,
                onValueChange = { folderName = it },
                placeholder = "e.g. Personal, Work...",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InlyButtonSecondary(
                    text = "Cancel",
                    onClick = { closeAnd(onDismiss) },
                    modifier = Modifier.weight(1f)
                )
                InlyButtonPrimary(
                    text = "Create",
                    onClick = { if (folderName.isNotBlank()) closeAnd { onCreate(folderName.trim()) } },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun AddNoteBottomSheet(expanded: Boolean, onDismiss: () -> Unit, onCreate: (String) -> Unit, onOpenTemplates: () -> Unit = {}) {
    var noteTitle by remember { mutableStateOf("") }
    // title/subtitle passed as null here (instead of via InlyBottomSheet's own params) so we can
    // slot the Templates icon into the same row as the "New Note" heading.
    InlyBottomSheet(expanded = expanded, onDismiss = onDismiss, title = null, subtitle = null) { closeAnd ->
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "New Note",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    painter = painterResource(Res.drawable.template),
                    contentDescription = "Templates",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp).noRippleClickable { closeAnd(onOpenTemplates) }
                )
            }
            Text(
                "Give your note a title, or leave it blank.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)
            )
            InlyTextField(
                value = noteTitle,
                onValueChange = { noteTitle = it },
                placeholder = "Note title...",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InlyButtonSecondary(
                    text = "Cancel",
                    onClick = { closeAnd(onDismiss) },
                    modifier = Modifier.weight(1f)
                )
                InlyButtonPrimary(
                    text = "Create",
                    onClick = { closeAnd { onCreate(noteTitle.trim()) } },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// Shared list body for the Templates menu - used both inside the mobile InlyBottomSheet and the
// desktop InlyDesktopMenu, so search/create/delete only need to be laid out once. horizontalPadding
// differs per shell (bottom sheet vs. dropdown) to match each container's existing inset.
@Composable
fun TemplatesMenuContent(
    modifier: Modifier = Modifier,
    templates: List<NoteMetadataEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onTemplateClick: (String) -> Unit,
    onEditTemplate: (String) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onCreateNewTemplate: () -> Unit,
    horizontalPadding: Dp = 20.dp
) {
    Column(modifier = modifier.fillMaxWidth()) {
        InlyTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = "Search templates...",
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding).padding(bottom = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .noRippleClickable(onCreateNewTemplate)
                .padding(horizontal = horizontalPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(Res.drawable.circle_plus),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text("Create New Template", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp, horizontal = horizontalPadding), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

        if (templates.isEmpty()) {
            Text(
                text = if (searchQuery.isBlank()) "No templates yet." else "No templates match \"$searchQuery\".",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 14.dp)
            )
        } else {
            templates.forEach { template ->
                TemplateRow(
                    template = template,
                    onClick = { onTemplateClick(template.noteId) },
                    onEdit = { onEditTemplate(template.noteId) },
                    onDelete = { onDeleteTemplate(template.noteId) },
                    horizontalPadding = horizontalPadding
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun TemplateRow(template: NoteMetadataEntity, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, horizontalPadding: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .noRippleClickable(onClick)
            .padding(horizontal = horizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (!template.icon.isNullOrEmpty()) {
                Text(template.icon, fontSize = 15.sp)
            } else {
                Icon(
                    painter = painterResource(Res.drawable.file_text),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                template.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            painter = painterResource(Res.drawable.pen),
            contentDescription = "Edit template",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            modifier = Modifier.size(15.dp).noRippleClickable(onEdit)
        )
        Spacer(Modifier.width(14.dp))
        Icon(
            painter = painterResource(Res.drawable.trash),
            contentDescription = "Delete template",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            modifier = Modifier.size(16.dp).noRippleClickable(onDelete)
        )
    }
}

// Mobile shell: same InlyBottomSheet used by every other mobile menu in this file.
@Composable
fun TemplatesBottomSheet(
    expanded: Boolean,
    templates: List<NoteMetadataEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onTemplateClick: (String) -> Unit,
    onEditTemplate: (String) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onCreateNewTemplate: () -> Unit
) {
    InlyBottomSheet(expanded = expanded, onDismiss = onDismiss, title = "Templates", subtitle = "Start a new note from a template.") { closeAnd ->
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            TemplatesMenuContent(
                templates = templates,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onTemplateClick = { id -> closeAnd { onTemplateClick(id) } },
                onEditTemplate = { id -> closeAnd { onEditTemplate(id) } },
                onDeleteTemplate = onDeleteTemplate,
                onCreateNewTemplate = { closeAnd(onCreateNewTemplate) }
            )
        }
    }
}

// Desktop shell: same InlyDesktopMenu used by the Sort/New Note/New Folder popups in this file
// and in DesktopMainScreen.kt, so both call sites (HomeScreen's own desktop branch and the
// desktop sidebar) share one implementation instead of two copies of this layout.
@Composable
fun TemplatesDesktopMenu(
    expanded: Boolean,
    templates: List<NoteMetadataEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onTemplateClick: (String) -> Unit,
    onEditTemplate: (String) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onCreateNewTemplate: () -> Unit
) {
    InlyDesktopMenu(expanded = expanded, onDismissRequest = onDismissRequest, modifier = Modifier.width(300.dp)) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                "Templates", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            TemplatesMenuContent(
                templates = templates,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onTemplateClick = { id -> onDismissRequest(); onTemplateClick(id) },
                onEditTemplate = { id -> onDismissRequest(); onEditTemplate(id) },
                onDeleteTemplate = onDeleteTemplate,
                onCreateNewTemplate = { onDismissRequest(); onCreateNewTemplate() },
                horizontalPadding = 16.dp
            )
        }
    }
}

@Composable
fun SortBottomSheet(expanded: Boolean, currentSortType: SortType, currentSortOrder: SortOrder, onDismiss: () -> Unit, onSortChanged: (SortType, SortOrder) -> Unit) {
    InlyBottomSheet(expanded = expanded, onDismiss = onDismiss, title = null) { closeAnd ->
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                "Sort by",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
            SortOptionItem(
                "Last Edited",
                currentSortType == SortType.LAST_EDITED
            ) { closeAnd { onSortChanged(SortType.LAST_EDITED, currentSortOrder) } }
            SortOptionItem(
                "Date Created",
                currentSortType == SortType.DATE_CREATED
            ) { closeAnd { onSortChanged(SortType.DATE_CREATED, currentSortOrder) } }
            SortOptionItem(
                "Name (A-Z)",
                currentSortType == SortType.NAME
            ) { closeAnd { onSortChanged(SortType.NAME, currentSortOrder) } }
            SortOptionItem("Manual", currentSortType == SortType.MANUAL) {
                closeAnd { onSortChanged(SortType.MANUAL, currentSortOrder) }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp, horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )
            Text(
                "Order",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
            SortOptionItem(
                "Ascending",
                currentSortOrder == SortOrder.ASCENDING
            ) { closeAnd { onSortChanged(currentSortType, SortOrder.ASCENDING) } }
            SortOptionItem(
                "Descending",
                currentSortOrder == SortOrder.DESCENDING
            ) { closeAnd { onSortChanged(currentSortType, SortOrder.DESCENDING) } }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SortOptionItem(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        if (isSelected) Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}