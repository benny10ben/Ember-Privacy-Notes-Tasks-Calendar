package com.ben.inly.presentation

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ben.inly.domain.util.AiEventBus
import com.ben.inly.domain.util.isDesktopPlatform
import com.ben.inly.presentation.mobile.daily.DailyScreen
import com.ben.inly.presentation.navigation.Screen
import com.ben.inly.presentation.trash.TrashScreen
import dev.chrisbanes.haze.HazeState
import com.ben.inly.presentation.splash.LoadingScreen
import com.ben.inly.domain.model.NoteBlock
import com.ben.inly.domain.repository.EmojiRepository
import com.ben.inly.domain.util.rememberMicrophonePermissionLauncher
import inly.app.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import com.ben.inly.presentation.mobile.home.HomeScreen
import com.ben.inly.presentation.share.ShareReceiverSheet
import com.ben.inly.presentation.share.ShareViewModel
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

private val DESKTOP_SIDEBAR_WIDTH = 340.dp

val LocalImageOverlay = staticCompositionLocalOf<( (@Composable () -> Unit)? ) -> Unit> { {} }

@OptIn(ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun InlyApp(
    startRoute: String,
    HomeViewModel: com.ben.inly.presentation.mobile.home.HomeViewModel = koinViewModel(),
    onPickImage: (onPathSelected: (String) -> Unit) -> Unit = {},
    onPickDocument: (onPathSelected: (String) -> Unit) -> Unit = {},
    onOpenFile: (filePath: String, mimeType: String) -> Unit = { _, _ -> },
    onTakePhoto: (onPathSelected: (String) -> Unit) -> Unit = {},
    onExportMarkdown: (fileName: String, content: String) -> Unit = { _, _ -> },
    onExportBackup: (jsonContent: String) -> Unit = {},
    onImportBackupClick: () -> Unit = {},
    onRequestBackupFolder: () -> Unit,
    onExportPdf: (fileName: String, title: String, blocks: List<NoteBlock>) -> Unit = { _, _, _ -> }
) {

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val bytes = Res.readBytes("files/data-by-group.json")
                EmojiRepository.initialize(bytes.decodeToString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isVoiceTaskListening by HomeViewModel.isVoiceTaskListening.collectAsState()
    val partialText by HomeViewModel.voiceTaskPartialText.collectAsState()

    val hazeState = remember { HazeState() }
    val density = LocalDensity.current

    val requestMicPermission = rememberMicrophonePermissionLauncher { isGranted ->
        if (isGranted) {
            HomeViewModel.startVoiceTaskListening()
        } else {}
    }

    var activeTab by remember { mutableStateOf(Screen.Daily.route) }
    var isBottomBarCompact by remember { mutableStateOf(false) }

    LaunchedEffect(currentRoute) {
        if (currentRoute == Screen.Daily.route || currentRoute == Screen.Home.route) {
            activeTab = currentRoute
        }
        isBottomBarCompact = false
    }

    // AI chat ViewModel
    val ragViewModel: com.ben.inly.presentation.rag.RagViewModel = koinViewModel()

    // Controls the AI chat overlay
    var showRagChatOverlay by remember { mutableStateOf(false) }

    var isSelectionActive by remember { mutableStateOf(false) }

    val isTopLevelScreen = currentRoute == Screen.Daily.route ||
            currentRoute == Screen.Home.route ||
            currentRoute == Screen.Note.route
    val isBottomBarVisible = isTopLevelScreen && !isSelectionActive && !showRagChatOverlay
    var bottomBarHeightDp by remember { mutableStateOf(0.dp) }
    var suppressBottomBarEnterAnimation by remember { mutableStateOf(true) }
    LaunchedEffect(isBottomBarVisible) {
        if (isBottomBarVisible) suppressBottomBarEnterAnimation = false
    }

    var isSidebarVisible by remember { mutableStateOf(true) }

    val bottomBarScrollAccumulator = remember { FloatArray(1) }
    val bottomBarNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta == 0f) return Offset.Zero

                val accumulated = bottomBarScrollAccumulator[0]
                if ((delta < 0f && accumulated > 0f) || (delta > 0f && accumulated < 0f)) {
                    bottomBarScrollAccumulator[0] = 0f
                }
                bottomBarScrollAccumulator[0] += delta

                val toggleThresholdPx = 60f
                if (bottomBarScrollAccumulator[0] <= -toggleThresholdPx && !isBottomBarCompact) {
                    isBottomBarCompact = true
                    bottomBarScrollAccumulator[0] = 0f
                } else if (bottomBarScrollAccumulator[0] >= toggleThresholdPx && isBottomBarCompact) {
                    isBottomBarCompact = false
                    bottomBarScrollAccumulator[0] = 0f
                }
                return Offset.Zero
            }
        }
    }

    val openAiChat: () -> Unit = {
        if (showRagChatOverlay) {
            showRagChatOverlay = false
            ragViewModel.clearChat()
        } else {
            ragViewModel.clearChat()
            AiEventBus.requestImmediateIndex()
            showRagChatOverlay = true
        }
    }

    var fullScreenContent by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }

    var isStatusBarInsetReady by remember { mutableStateOf(isDesktopPlatform) }
    if (!isDesktopPlatform) {
        val statusBarInsetTopPx = WindowInsets.statusBars.getTop(density)
        LaunchedEffect(statusBarInsetTopPx) {
            if (statusBarInsetTopPx > 0) isStatusBarInsetReady = true
        }
        LaunchedEffect(Unit) {
            delay(500.milliseconds)
            isStatusBarInsetReady = true
        }
    }

    CompositionLocalProvider(
        LocalImageOverlay provides { content -> fullScreenContent = content }
    ) {
        if (isDesktopPlatform) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            ) {
                DesktopMainScreenWrapper(
                    isSidebarVisible = isSidebarVisible,
                    sidebarWidth = DESKTOP_SIDEBAR_WIDTH,
                    onToggleSidebar = { isSidebarVisible = !isSidebarVisible },
                    onSelectionModeChange = { isActive -> isSelectionActive = isActive },
                    onPickImage = onPickImage,
                    onTakePhoto = onTakePhoto,
                    onPickDocument = onPickDocument,
                    onOpenFile = onOpenFile,
                    onExportMarkdown = onExportMarkdown,
                    onExportPdf = onExportPdf,
                    onExportBackup = onExportBackup,
                    onImportBackupClick = onImportBackupClick,
                    onAiIconTap = openAiChat,
                    isRagChatVisible = showRagChatOverlay,
                    ragViewModel = ragViewModel,
                    onDismissRagChat = {
                        showRagChatOverlay = false
                        ragViewModel.clearChat()
                    }
                )

                fullScreenContent?.invoke()
            }
            return@CompositionLocalProvider
        }

        if (!isStatusBarInsetReady) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            )
            return@CompositionLocalProvider
        }

        val shareViewModel: ShareViewModel = koinViewModel()
        val currentShare by shareViewModel.currentShare.collectAsState()
        val linkableNotes by shareViewModel.linkableNotes.collectAsState()
        val shareNavigateToNoteId by shareViewModel.navigateToNoteId.collectAsState()
        val shareSavedMessage by shareViewModel.savedMessage.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(shareNavigateToNoteId) {
            shareNavigateToNoteId?.let { id ->
                navController.navigate(Screen.Note.createRoute(id))
                shareViewModel.clearNavigation()
            }
        }

        LaunchedEffect(shareSavedMessage) {
            shareSavedMessage?.let { message ->
                snackbarHostState.showSnackbar(message)
                shareViewModel.clearSavedMessage()
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            SharedTransitionLayout(
                modifier = Modifier.fillMaxSize()
            ) {
                val sharedTransitionScope = this@SharedTransitionLayout

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .nestedScroll(bottomBarNestedScrollConnection)
                ) {
                NavHost(
                    navController = navController,
                    startDestination = startRoute, // Updated to use the parameter
                    modifier = Modifier
                        .padding(top = innerPadding.calculateTopPadding())
                        .consumeWindowInsets(innerPadding)
                        .hazeSource(state = hazeState),
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None }
                ) {

                    // Unconditionally added the Splash route to prevent "destination not found" crashes
                    composable(Screen.Splash.route) {
                        LoadingScreen(
                            onLoadingComplete = {
                                navController.navigate(Screen.Daily.createRoute()) {
                                    popUpTo(Screen.Splash.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(
                        route = Screen.Daily.route,
                        arguments = listOf(navArgument("date") { type = NavType.StringType; nullable = true })
                    ) { backStackEntry ->
                        DailyScreen(
                            bottomContentPadding = if (isBottomBarVisible) bottomBarHeightDp else 0.dp,
                            onSelectionModeChange = { isActive -> isSelectionActive = isActive },
                            onPickImage = onPickImage,
                            onTakePhoto = onTakePhoto,
                            onPickDocument = onPickDocument,
                            onOpenFile = onOpenFile,
                            onNavigateToEditor = { noteId ->
                                navController.navigate(Screen.Note.createRoute(noteId))
                            },
                            onNavigateToCalendar = { navController.navigate(Screen.Calendar.route) },
                            onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                            onNavigateToTrash = { navController.navigate("trash_route") },
                            isSidebarVisible = isSidebarVisible,
                            onToggleSidebar = { isSidebarVisible = !isSidebarVisible },
                            dateArg = backStackEntry.savedStateHandle.get<String>("date")
                        )
                    }

                    composable(Screen.Home.route) {
                        HomeScreen(
                            bottomContentPadding = if (isBottomBarVisible) bottomBarHeightDp else 0.dp,
                            onNavigateToEditor = { noteId ->
                                navController.navigate(
                                    Screen.Note.createRoute(
                                        noteId
                                    )
                                )
                            },
                            onSelectionModeChange = { isActive -> isSelectionActive = isActive },
                            onNavigateToCalendar = { navController.navigate(Screen.Calendar.route) },
                            onNavigateToReminders = { navController.navigate(Screen.Reminders.route) },
                            onNavigateToBookmarks = { navController.navigate(Screen.Bookmarks.route) },
                            onNavigateToImages = { navController.navigate(Screen.Images.route) },
                            onNavigateToDocuments = { navController.navigate(Screen.Documents.route) },
                            onNavigateToTrash = { navController.navigate("trash_route") },
                            onToggleSidebar = { isSidebarVisible = !isSidebarVisible },
                            onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                        )
                    }

                    composable(
                        route = "trash_route",
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                tween(300)
                            )
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                tween(300)
                            )
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                tween(300)
                            )
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                tween(300)
                            )
                        }
                    ) {
                        TrashScreen(onNavigateBack = { navController.popBackStack() })
                    }

                    composable(
                        route = Screen.Note.route,
                        arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                tween(300)
                            )
                        },
                        exitTransition = {
                            if (targetState.destination.route == Screen.Note.route) {
                                ExitTransition.None
                            } else {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    tween(300)
                                )
                            }
                        },
                        popEnterTransition = {
                            if (initialState.destination.route == Screen.Note.route) {
                                EnterTransition.None
                            } else {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    tween(300)
                                )
                            }
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                tween(300)
                            )
                        }
                    ) { backStackEntry ->
                        _root_ide_package_.com.ben.inly.presentation.mobile.home.note.NoteScreen(
                            noteId = backStackEntry.savedStateHandle.get<String>("noteId") ?: "",
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToEditor = { subNoteId ->
                                navController.navigate(Screen.Note.createRoute(subNoteId))
                            },
                            onSelectionModeChange = { isActive -> isSelectionActive = isActive },
                            onPickImage = onPickImage,
                            onTakePhoto = onTakePhoto,
                            onPickDocument = onPickDocument,
                            onOpenFile = onOpenFile,
                            onExportMarkdown = onExportMarkdown,
                            onExportPdf = onExportPdf
                        )
                    }

                    composable(
                        route = Screen.Reminders.route,
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                tween(300)
                            )
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                tween(300)
                            )
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                tween(300)
                            )
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                tween(300)
                            )
                        }
                    ) {
                        _root_ide_package_.com.ben.inly.presentation.mobile.home.overview.reminders.RemindersScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToEditor = { noteId ->
                                navController.navigate(
                                    Screen.Note.createRoute(
                                        noteId
                                    )
                                )
                            }
                        )
                    }

                    composable(
                        route = Screen.Calendar.route,
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                tween(300)
                            )
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                tween(300)
                            )
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                tween(300)
                            )
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                tween(300)
                            )
                        }
                    ) {
                        com.ben.inly.presentation.calendar.CalendarScreen(
                            onNavigateBack = { navController.popBackStack() },
                            sharedTransitionScope = sharedTransitionScope,
                            bottomBarAnimatedVisibilityScope = this,
                        )
                    }

                    composable(
                        route = Screen.Bookmarks.route,
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                tween(300)
                            )
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                tween(300)
                            )
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                tween(300)
                            )
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                tween(300)
                            )
                        }
                    ) {
                        _root_ide_package_.com.ben.inly.presentation.mobile.home.overview.bookmarks.BookmarksScreen(
                            onNavigateBack = { navController.popBackStack() })
                    }

                    composable(
                        route = Screen.Search.route,
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                tween(300)
                            )
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                tween(300)
                            )
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                tween(300)
                            )
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                tween(300)
                            )
                        }
                    ) {
                        com.ben.inly.presentation.search.SearchScreen(
                            onBack = { navController.popBackStack() },
                            onNoteClick = { noteId ->
                                navController.navigate(Screen.Note.createRoute(noteId)) {
                                    popUpTo(Screen.Search.route) { inclusive = true }
                                }
                            },
                            onDailyNoteClick = { dateString ->
                                navController.navigate(Screen.Daily.createRoute(dateString)) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            sharedTransitionScope = sharedTransitionScope,
                            searchIconAnimatedVisibilityScope = this,
                        )
                    }

                    composable(
                        route = Screen.Images.route,
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                tween(300)
                            )
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                tween(300)
                            )
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                tween(300)
                            )
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                tween(300)
                            )
                        }
                    ) {
                        val imagesViewModel: com.ben.inly.presentation.mobile.home.overview.images.ImagesViewModel =
                            koinViewModel()
                        _root_ide_package_.com.ben.inly.presentation.mobile.home.overview.images.ImagesScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onTriggerImagePicker = {
                                onPickImage { path -> imagesViewModel.createNewImageWithFile(path) }
                            },
                            viewModel = imagesViewModel
                        )
                    }

                    composable(
                        route = Screen.Documents.route,
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                tween(300)
                            )
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                tween(300)
                            )
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                tween(300)
                            )
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                tween(300)
                            )
                        }
                    ) {
                        val documentsViewModel: com.ben.inly.presentation.mobile.home.overview.documents.DocumentsViewModel =
                            koinViewModel()
                        _root_ide_package_.com.ben.inly.presentation.mobile.home.overview.documents.DocumentsScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onTriggerDocumentPicker = {
                                onPickDocument { path ->
                                    documentsViewModel.createNewDocumentWithFile(
                                        path
                                    )
                                }
                            },
                            onOpenFile = onOpenFile,
                            viewModel = documentsViewModel
                        )
                    }

                    composable(
                        route = Screen.Settings.route,
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                tween(300)
                            )
                        },
                        exitTransition = {
                            if (targetState.destination.route == Screen.SelfHostSetup.route) {
                                ExitTransition.None
                            } else {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    tween(300)
                                )
                            }
                        },
                        popEnterTransition = {
                            if (initialState.destination.route == Screen.SelfHostSetup.route) {
                                EnterTransition.None
                            } else {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    tween(300)
                                )
                            }
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                tween(300)
                            )
                        }
                    ) {
                        com.ben.inly.presentation.settings.SettingsScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onExportReady = onExportBackup,
                            onImportClick = onImportBackupClick,
                            onRequestBackupFolder = onRequestBackupFolder,
                            onNavigateToSelfHostSetup = { navController.navigate(Screen.SelfHostSetup.route) }
                        )
                    }

                    composable(
                        route = Screen.SelfHostSetup.route,
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                tween(300)
                            )
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                tween(300)
                            )
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                tween(300)
                            )
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                tween(300)
                            )
                        }
                    ) {
                        com.ben.inly.presentation.settings.selfhost.SelfHostSetupScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }

                if (!isDesktopPlatform) {
                    AnimatedVisibility(
                        visible = isBottomBarVisible,
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(300)),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Transparent,
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }

                    AnimatedVisibility(
                        visible = isBottomBarVisible,
                        enter = if (suppressBottomBarEnterAnimation) {
                            EnterTransition.None
                        } else {
                            slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(
                                    durationMillis = 250,
                                    delayMillis = 100,
                                    easing = FastOutSlowInEasing
                                )
                            ) + fadeIn(tween(durationMillis = 250, delayMillis = 100))
                        },
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(
                                durationMillis = 200,
                                easing = FastOutSlowInEasing
                            )
                        ) + fadeOut(tween(durationMillis = 200)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .onGloballyPositioned { coords ->
                                bottomBarHeightDp = with(density) { coords.size.height.toDp() }
                            }
                    ) {
                        InlyBottomBar(
                            navController = navController,
                            hazeState = hazeState,
                            sharedTransitionScope = sharedTransitionScope,
                            bottomBarAnimatedVisibilityScope = this,
                            currentRoute = currentRoute,
                            activeTab = activeTab,
                            onAiIconTap = openAiChat,
                            onSearchClick = { navController.navigate(Screen.Search.route) },
                            onMicClick = {
                                if (isVoiceTaskListening) {
                                    HomeViewModel.stopVoiceTaskListening()
                                } else {
                                    HomeViewModel.startVoiceTaskListening(
                                        onPermissionNeeded = { requestMicPermission() }
                                    )
                                }
                            },
                            isListening = isVoiceTaskListening,
                            partialText = partialText,
                            isCompact = isBottomBarCompact
                        )
                    }
                }

                // AI chat overlay
                com.ben.inly.presentation.rag.RagChatOverlay(
                    isVisible = showRagChatOverlay,
                    onDismiss = {
                        showRagChatOverlay = false
                        ragViewModel.clearChat()
                    },
                    viewModel = ragViewModel,
                    sharedTransitionScope = sharedTransitionScope,
                )

                ShareReceiverSheet(
                    share = currentShare,
                    linkableNotes = linkableNotes,
                    onSaveToInbox = { shareViewModel.saveToInbox() },
                    onNoteSelected = { noteId -> shareViewModel.saveToNote(noteId) },
                    onCreateNote = { title -> shareViewModel.createNoteAndSave(title) },
                    onCreateBlankNote = { shareViewModel.createNoteAndSave("") },
                    onDismiss = { shareViewModel.dismiss() }
                )

                fullScreenContent?.invoke()
                }
            }
        }
    }
}