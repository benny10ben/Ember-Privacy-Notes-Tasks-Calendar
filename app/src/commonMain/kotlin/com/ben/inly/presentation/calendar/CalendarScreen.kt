package com.ben.inly.presentation.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.ben.inly.presentation.shared.stableStatusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ben.inly.domain.util.isDesktopPlatform
import com.ben.inly.presentation.shared.components.InlyDesktopMenu
import com.ben.inly.presentation.shared.components.KmpBackHandler
import com.ben.inly.presentation.shared.components.TopBarIconButton
import com.ben.inly.ui.theme.LocalAppIsDark
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import inly.app.generated.resources.Res
import inly.app.generated.resources.chevron_left
import inly.app.generated.resources.sidebar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

enum class CalendarViewMode { DAY, THREE_DAY, WEEK, MONTH }

@Composable
fun CalendarScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: CalendarViewModel = koinViewModel()
) {
    val internalHazeState = remember { HazeState() }

    var isPanelOpen by remember { mutableStateOf(false) }
    val viewMode by viewModel.viewMode.collectAsState()
    var selectedDate by remember { mutableStateOf(Clock.System.todayIn(TimeZone.currentSystemDefault())) }
    val categories by viewModel.categories.collectAsState()
    val events by remember(selectedDate) { viewModel.eventsForDate(selectedDate.toString()) }
        .collectAsState(initial = emptyList())
    var eventEditorState by remember { mutableStateOf<EventEditorState?>(null) }
    var slideDirection by remember { mutableStateOf(AnimatedContentTransitionScope.SlideDirection.Left) }

    val scrollState = remember(viewMode) { ScrollState(0) }
    val isScrolled by remember(scrollState) { derivedStateOf { scrollState.value > 0 } }

    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableFloatStateOf(0f) }
    val topBarHeightDp = with(density) { topBarHeightPx.toDp() }

    val dayCount = if (viewMode == CalendarViewMode.THREE_DAY) 3 else 7
    val multiDayDates = remember(selectedDate, dayCount) {
        val anchor = if (dayCount == 7) selectedDate.startOfWeek() else selectedDate
        (0 until dayCount).map { offset -> anchor.plus(offset.toLong(), DateTimeUnit.DAY) }
    }

    KmpBackHandler(enabled = isPanelOpen) {
        isPanelOpen = false
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = internalHazeState)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val onEventClick: (CalendarEvent) -> Unit = { event ->
                    val dt = Instant.fromEpochMilliseconds(event.reminderTimestamp)
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                    eventEditorState = EventEditorState(
                        original = event,
                        name = event.text,
                        date = dt.date,
                        hour = dt.hour,
                        minute = dt.minute,
                        categoryId = event.categoryId,
                        durationMinutes = event.durationMinutes,
                        url = event.url.orEmpty(),
                        description = event.description.orEmpty()
                    )
                }

                when (viewMode) {
                    CalendarViewMode.DAY -> {
                        CalendarTimeGrid(
                            selectedDate = selectedDate,
                            slideDirection = slideDirection,
                            events = events,
                            categories = categories,
                            scrollState = scrollState,
                            topBarHeightDp = topBarHeightDp,
                            onSwipePreviousDay = {
                                slideDirection = AnimatedContentTransitionScope.SlideDirection.Right
                                selectedDate = selectedDate.plus(-1, DateTimeUnit.DAY)
                            },
                            onSwipeNextDay = {
                                slideDirection = AnimatedContentTransitionScope.SlideDirection.Left
                                selectedDate = selectedDate.plus(1, DateTimeUnit.DAY)
                            },
                            onHourClick = { hour ->
                                eventEditorState = EventEditorState(
                                    original = null,
                                    name = "",
                                    date = selectedDate,
                                    hour = hour,
                                    minute = 0,
                                    categoryId = null,
                                    durationMinutes = 30
                                )
                            },
                            onEventClick = onEventClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                    CalendarViewMode.MONTH -> {
                        MonthGrid(
                            anchorMonth = selectedDate,
                            slideDirection = slideDirection,
                            viewModel = viewModel,
                            categories = categories,
                            onSwipePreviousMonth = {
                                slideDirection = AnimatedContentTransitionScope.SlideDirection.Right
                                selectedDate =
                                    LocalDate(selectedDate.year, selectedDate.month, 1).plus(-1, DateTimeUnit.MONTH)
                            },
                            onSwipeNextMonth = {
                                slideDirection = AnimatedContentTransitionScope.SlideDirection.Left
                                selectedDate =
                                    LocalDate(selectedDate.year, selectedDate.month, 1).plus(1, DateTimeUnit.MONTH)
                            },
                            onDayClick = { date ->
                                selectedDate = date
                                viewModel.setViewMode(CalendarViewMode.DAY)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(top = topBarHeightDp)
                        )
                    }
                    else -> {
                        MultiDayTimeGrid(
                            startDate = selectedDate,
                            dayCount = dayCount,
                            slideDirection = slideDirection,
                            viewModel = viewModel,
                            categories = categories,
                            scrollState = scrollState,
                            topBarHeightDp = topBarHeightDp,
                            onSwipePrevious = {
                                slideDirection = AnimatedContentTransitionScope.SlideDirection.Right
                                selectedDate = selectedDate.plus(-dayCount.toLong(), DateTimeUnit.DAY)
                            },
                            onSwipeNext = {
                                slideDirection = AnimatedContentTransitionScope.SlideDirection.Left
                                selectedDate = selectedDate.plus(dayCount.toLong(), DateTimeUnit.DAY)
                            },
                            onHourClick = { date, hour ->
                                eventEditorState = EventEditorState(
                                    original = null,
                                    name = "",
                                    date = date,
                                    hour = hour,
                                    minute = 0,
                                    categoryId = null,
                                    durationMinutes = 30
                                )
                            },
                            onEventClick = onEventClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(10f)
                    .onGloballyPositioned { coordinates -> topBarHeightPx = coordinates.size.height.toFloat() }
                    .pointerInput(Unit) { detectTapGestures {} }
                    .then(
                        if (isScrolled) {
                            Modifier.hazeEffect(
                                state = internalHazeState,
                                style = HazeStyle.Unspecified,
                                block = null)
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.65f))
                        } else {
                            Modifier
                        }
                    )
                    .then(if (isDesktopPlatform) Modifier else Modifier.stableStatusBarsPadding())
            ) {
                CalendarTopBar(
                    selectedDate = selectedDate,
                    viewMode = viewMode,
                    slideDirection = slideDirection,
                    onBackClick = onNavigateBack,
                    onMenuClick = { isPanelOpen = true },
                    onViewModeChange = viewModel::setViewMode,
                    categories = categories,
                    onAddCategory = viewModel::addCategory,
                    onUpdateCategory = viewModel::updateCategory,
                    onDeleteCategory = viewModel::deleteCategory
                )
            }

            if (viewMode == CalendarViewMode.THREE_DAY || viewMode == CalendarViewMode.WEEK) {
                MultiDayHeaderBar(
                    dates = multiDayDates,
                    slideDirection = slideDirection,
                    hazeState = internalHazeState,
                    isScrolled = isScrolled,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .zIndex(9f)
                        .padding(top = topBarHeightDp)
                )
            }

            if (!isDesktopPlatform) {
                RightSidePanel(
                    modifier = Modifier.zIndex(11f),
                    isOpen = isPanelOpen,
                    onOpenChange = { isPanelOpen = it },
                    viewMode = viewMode,
                    onViewModeChange = viewModel::setViewMode,
                    categories = categories,
                    onAddCategory = viewModel::addCategory,
                    onUpdateCategory = viewModel::updateCategory,
                    onDeleteCategory = viewModel::deleteCategory
                )
            }
        }
    }

    EventEditorSheet(
        state = eventEditorState,
        categories = categories,
        onNameChange = { name -> eventEditorState = eventEditorState?.copy(name = name) },
        onDateChange = { date -> eventEditorState = eventEditorState?.copy(date = date) },
        onTimeChange = { hour, minute -> eventEditorState = eventEditorState?.copy(hour = hour, minute = minute) },
        onDurationChange = { minutes -> eventEditorState = eventEditorState?.copy(durationMinutes = minutes) },
        onCategoryChange = { categoryId -> eventEditorState = eventEditorState?.copy(categoryId = categoryId) },
        onUrlChange = { url -> eventEditorState = eventEditorState?.copy(url = url) },
        onDescriptionChange = { description -> eventEditorState = eventEditorState?.copy(description = description) },
        onEditClick = { eventEditorState = eventEditorState?.copy(isEditing = true) },
        onSave = {
            eventEditorState?.let { state ->
                viewModel.saveEvent(
                    original = state.original,
                    dateString = state.date.toString(),
                    timestamp = state.toEpochMillis(),
                    name = state.name,
                    categoryId = state.categoryId,
                    durationMinutes = state.durationMinutes,
                    url = state.url,
                    description = state.description
                )
            }
            eventEditorState = null
        },
        onDelete = eventEditorState?.original?.let { original ->
            {
                viewModel.deleteEvent(original)
                eventEditorState = null
            }
        },
        onDismiss = { eventEditorState = null }
    )
}

@Composable
private fun CalendarTopBar(
    selectedDate: LocalDate,
    viewMode: CalendarViewMode,
    slideDirection: AnimatedContentTransitionScope.SlideDirection,
    onBackClick: () -> Unit,
    onMenuClick: () -> Unit,
    onViewModeChange: (CalendarViewMode) -> Unit,
    categories: List<CalendarCategory>,
    onAddCategory: (name: String, colorHex: String) -> Unit,
    onUpdateCategory: (id: String, name: String, colorHex: String) -> Unit,
    onDeleteCategory: (id: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultBgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.45f)
    val defaultContentColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (isDesktopPlatform) 16.dp else 10.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopBarIconButton(
            icon = painterResource(Res.drawable.chevron_left),
            contentDescription = "Back",
            bgColor = defaultBgColor,
            tint = defaultContentColor,
            onClick = onBackClick
        )

        AnimatedContent(
            targetState = selectedDate,
            transitionSpec = {
                (slideIntoContainer(slideDirection, tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300))) togetherWith
                        (slideOutOfContainer(slideDirection, tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300)))
            },
            label = "CalendarTitleTransition"
        ) { date ->
            if (viewMode == CalendarViewMode.MONTH) {
                Text(
                    text = formatMonthYear(date),
                    style = MaterialTheme.typography.bodyLarge,
                    color = defaultContentColor,
                    textAlign = TextAlign.Center
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatSelectedDateTitle(date),
                        style = MaterialTheme.typography.bodyLarge,
                        color = defaultContentColor,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = formatDayOfWeek(date),
                        style = MaterialTheme.typography.labelSmall,
                        color = defaultContentColor.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (isDesktopPlatform) {
            var showOptionsMenu by remember { mutableStateOf(false) }
            Box {
                TopBarIconButton(
                    icon = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    bgColor = defaultBgColor,
                    tint = defaultContentColor,
                    onClick = { showOptionsMenu = true }
                )
                InlyDesktopMenu(
                    expanded = showOptionsMenu,
                    onDismissRequest = { showOptionsMenu = false },
                    modifier = Modifier.width(260.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        ViewModeSection(
                            viewMode = viewMode,
                            onViewModeChange = {
                                onViewModeChange(it)
                                showOptionsMenu = false
                            }
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )

                        CategorySection(
                            categories = categories,
                            onAddCategory = onAddCategory,
                            onUpdateCategory = onUpdateCategory,
                            onDeleteCategory = onDeleteCategory
                        )
                    }
                }
            }
        } else {
            TopBarIconButton(
                icon = painterResource(Res.drawable.sidebar),
                contentDescription = "Open Menu",
                bgColor = defaultBgColor,
                tint = defaultContentColor,
                onClick = onMenuClick
            )
        }
    }
}

@Composable
private fun MultiDayHeaderBar(
    dates: List<LocalDate>,
    slideDirection: AnimatedContentTransitionScope.SlideDirection,
    hazeState: HazeState,
    isScrolled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .then(
                if (isScrolled) {
                    Modifier
                        .hazeEffect(state = hazeState, style = HazeStyle.Unspecified, block = null)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.65f))
                } else {
                    Modifier.background(MaterialTheme.colorScheme.background)
                }
            )
    ) {
        AnimatedContent(
            targetState = dates,
            transitionSpec = {
                (slideIntoContainer(slideDirection, tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300))) togetherWith
                        (slideOutOfContainer(slideDirection, tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300)))
            },
            label = "MultiDayHeaderTransition"
        ) { windowDates ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(56.dp))
                windowDates.forEachIndexed { index, date ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (windowDates.size >= 7) formatSingleLetterDayLabel(date) else formatShortDayLabel(date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                    if (index != windowDates.lastIndex) {
                        VerticalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxHeight()
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 1.dp)
    }
}

internal fun formatFullDate(date: LocalDate): String {
    val monthAbbrev = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "$monthAbbrev ${date.dayOfMonth}${ordinalSuffix(date.dayOfMonth)}, ${date.year}"
}

private fun formatSelectedDateTitle(date: LocalDate): String = formatFullDate(date)

private fun formatDayOfWeek(date: LocalDate): String =
    date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }

private fun formatMonthYear(date: LocalDate): String {
    val monthName = date.month.name.lowercase().replaceFirstChar { it.uppercase() }
    return "$monthName ${date.year}"
}

internal fun ordinalSuffix(day: Int): String = when {
    day in 11..13 -> "th"
    else -> when (day % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
}

@Composable
private fun CalendarTimeGrid(
    selectedDate: LocalDate,
    slideDirection: AnimatedContentTransitionScope.SlideDirection,
    events: List<CalendarEvent>,
    categories: List<CalendarCategory>,
    scrollState: ScrollState,
    topBarHeightDp: Dp,
    onSwipePreviousDay: () -> Unit,
    onSwipeNextDay: () -> Unit,
    onHourClick: (hour: Int) -> Unit,
    onEventClick: (event: CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var accumulatedDragPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 80.dp.toPx() }
    val nowMillis = rememberNowMillis()

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { accumulatedDragPx = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDragPx += dragAmount
                    },
                    onDragEnd = {
                        if (accumulatedDragPx <= -swipeThresholdPx) {
                            onSwipeNextDay()
                        } else if (accumulatedDragPx >= swipeThresholdPx) {
                            onSwipePreviousDay()
                        }
                        accumulatedDragPx = 0f
                    },
                    onDragCancel = { accumulatedDragPx = 0f }
                )
            }
    ) {
        AnimatedContent(
            targetState = selectedDate,
            transitionSpec = {
                (slideIntoContainer(slideDirection, tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300))) togetherWith
                        (slideOutOfContainer(slideDirection, tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300)))
            },
            label = "CalendarDayGridTransition",
            modifier = Modifier.fillMaxSize()
        ) { date ->
            DayHourGrid(
                date = date,
                events = events,
                categories = categories,
                nowMillis = nowMillis,
                scrollState = scrollState,
                topBarHeightDp = topBarHeightDp,
                onHourClick = onHourClick,
                onEventClick = onEventClick
            )
        }
    }
}

@Composable
private fun DayHourGrid(
    date: LocalDate,
    events: List<CalendarEvent>,
    categories: List<CalendarCategory>,
    nowMillis: Long,
    scrollState: ScrollState,
    topBarHeightDp: Dp,
    onHourClick: (hour: Int) -> Unit,
    onEventClick: (event: CalendarEvent) -> Unit
) {
    val hourHeight = 72.dp
    val hours = remember { 0..23 }
    val density = LocalDensity.current
    val hourHeightPx = with(density) { hourHeight.toPx() }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = topBarHeightDp)
    ) {
        HourLabelColumn(hours = hours, hourHeight = hourHeight)

        Box(modifier = Modifier.fillMaxWidth().height(hourHeight * hours.count())) {
            DayColumnBody(
                date = date,
                hours = hours,
                hourHeight = hourHeight,
                hourHeightPx = hourHeightPx,
                events = events,
                categories = categories,
                nowMillis = nowMillis,
                onHourClick = onHourClick,
                onEventClick = onEventClick
            )
        }
    }
}

@Composable
private fun HourLabelColumn(hours: IntRange, hourHeight: Dp) {
    Column(modifier = Modifier.width(56.dp)) {
        hours.forEach { hour ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(hourHeight),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = formatHourLabel(hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun DayColumnBody(
    date: LocalDate,
    hours: IntRange,
    hourHeight: Dp,
    hourHeightPx: Float,
    events: List<CalendarEvent>,
    categories: List<CalendarCategory>,
    nowMillis: Long,
    onHourClick: (hour: Int) -> Unit,
    onEventClick: (event: CalendarEvent) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(hourHeight * hours.count())
            .pointerInput(hourHeightPx) {
                detectTapGestures { offset ->
                    val hour = (offset.y / hourHeightPx).toInt().coerceIn(0, 23)
                    onHourClick(hour)
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            hours.forEach { _ ->
                Box(modifier = Modifier.fillMaxWidth().height(hourHeight)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        thickness = 1.dp
                    )
                }
            }
        }

        val horizontalInset = 6.dp
        val chipGap = 4.dp
        val availableWidth = maxWidth - horizontalInset * 2
        val positionedEvents = remember(events) { layoutEventsForColumn(events) }

        positionedEvents.forEach { positioned ->
            val event = positioned.event
            val dt = Instant.fromEpochMilliseconds(event.reminderTimestamp)
                .toLocalDateTime(TimeZone.currentSystemDefault())
            val topOffset = hourHeight * dt.hour + hourHeight * (dt.minute / 60f)
            val chipHeight = maxOf(24.dp, hourHeight * (event.durationMinutes / 60f))
            val category = categories.firstOrNull { it.id == event.categoryId }

            val chipWidth = (availableWidth - chipGap * (positioned.columnCount - 1)) / positioned.columnCount
            val xOffset = horizontalInset + (chipWidth + chipGap) * positioned.columnIndex

            EventChip(
                text = event.text,
                color = category?.colorHex?.toCategoryColor() ?: MaterialTheme.colorScheme.surface,
                hasCategory = category != null,
                height = chipHeight,
                onClick = { onEventClick(event) },
                modifier = Modifier
                    .width(chipWidth)
                    .padding(top = topOffset)
                    .offset(x = xOffset)
            )
        }

        val nowDt = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(TimeZone.currentSystemDefault())
        if (date == nowDt.date) {
            val nowOffset = hourHeight * nowDt.hour + hourHeight * (nowDt.minute / 60f)
            CurrentTimeLine(modifier = Modifier.fillMaxWidth().padding(top = nowOffset))
        }
    }
}

@Composable
private fun rememberNowMillis(): Long {
    var nowMillis by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000.milliseconds)
            nowMillis = Clock.System.now().toEpochMilliseconds()
        }
    }
    return nowMillis
}

@Composable
private fun CurrentTimeLine(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

private fun formatHourLabel(hour: Int): String {
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val period = if (hour < 12) "AM" else "PM"
    return "$displayHour $period"
}

private fun formatShortDayLabel(date: LocalDate): String {
    val shortDay = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "$shortDay ${date.dayOfMonth}"
}

private fun formatSingleLetterDayLabel(date: LocalDate): String {
    val initial = date.dayOfWeek.name.take(1)
    return "$initial ${date.dayOfMonth}"
}

private fun LocalDate.startOfWeek(): LocalDate {
    val daysSinceSunday = dayOfWeek.isoDayNumber % 7
    return this.plus(-daysSinceSunday.toLong(), DateTimeUnit.DAY)
}

@Composable
private fun MultiDayTimeGrid(
    startDate: LocalDate,
    dayCount: Int,
    slideDirection: AnimatedContentTransitionScope.SlideDirection,
    viewModel: CalendarViewModel,
    categories: List<CalendarCategory>,
    scrollState: ScrollState,
    topBarHeightDp: Dp,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit,
    onHourClick: (date: LocalDate, hour: Int) -> Unit,
    onEventClick: (event: CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var accumulatedDragPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 80.dp.toPx() }
    val nowMillis = rememberNowMillis()

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(dayCount) {
                detectHorizontalDragGestures(
                    onDragStart = { accumulatedDragPx = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDragPx += dragAmount
                    },
                    onDragEnd = {
                        if (accumulatedDragPx <= -swipeThresholdPx) {
                            onSwipeNext()
                        } else if (accumulatedDragPx >= swipeThresholdPx) {
                            onSwipePrevious()
                        }
                        accumulatedDragPx = 0f
                    },
                    onDragCancel = { accumulatedDragPx = 0f }
                )
            }
    ) {
        AnimatedContent(
            targetState = startDate,
            transitionSpec = {
                (slideIntoContainer(slideDirection, tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300))) togetherWith
                        (slideOutOfContainer(slideDirection, tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300)))
            },
            label = "MultiDayGridTransition",
            modifier = Modifier.fillMaxSize()
        ) { windowStart ->
            MultiDayGridContent(
                windowStart = windowStart,
                dayCount = dayCount,
                viewModel = viewModel,
                categories = categories,
                nowMillis = nowMillis,
                scrollState = scrollState,
                topBarHeightDp = topBarHeightDp,
                onHourClick = onHourClick,
                onEventClick = onEventClick
            )
        }
    }
}

@Composable
private fun MultiDayGridContent(
    windowStart: LocalDate,
    dayCount: Int,
    viewModel: CalendarViewModel,
    categories: List<CalendarCategory>,
    nowMillis: Long,
    scrollState: ScrollState,
    topBarHeightDp: Dp,
    onHourClick: (date: LocalDate, hour: Int) -> Unit,
    onEventClick: (event: CalendarEvent) -> Unit
) {
    val dates = remember(windowStart, dayCount) {
        val anchor = if (dayCount == 7) windowStart.startOfWeek() else windowStart
        (0 until dayCount).map { offset -> anchor.plus(offset.toLong(), DateTimeUnit.DAY) }
    }
    val eventsByDate = dates.associateWith { date ->
        val state by remember(date) { viewModel.eventsForDate(date.toString()) }
            .collectAsState(initial = emptyList())
        state
    }

    val hourHeight = 72.dp
    val hours = remember { 0..23 }
    val density = LocalDensity.current
    val hourHeightPx = with(density) { hourHeight.toPx() }
    val headerHeight = 40.dp

    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = topBarHeightDp + headerHeight)
    ) {
        HourLabelColumn(hours = hours, hourHeight = hourHeight)

        dates.forEachIndexed { index, date ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(hourHeight * hours.count())
            ) {
                DayColumnBody(
                    date = date,
                    hours = hours,
                    hourHeight = hourHeight,
                    hourHeightPx = hourHeightPx,
                    events = eventsByDate[date] ?: emptyList(),
                    categories = categories,
                    nowMillis = nowMillis,
                    onHourClick = { hour -> onHourClick(date, hour) },
                    onEventClick = onEventClick
                )
            }
            if (index != dates.lastIndex) {
                VerticalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    modifier = Modifier.height(hourHeight * hours.count())
                )
            }
        }
    }
}

private fun buildMonthGridDates(anchorMonth: LocalDate): List<LocalDate> {
    val firstOfMonth = LocalDate(anchorMonth.year, anchorMonth.month, 1)
    val leadingBlanks = firstOfMonth.dayOfWeek.isoDayNumber % 7
    val gridStart = firstOfMonth.plus(-leadingBlanks.toLong(), DateTimeUnit.DAY)
    val nextMonthFirst = firstOfMonth.plus(1, DateTimeUnit.MONTH)
    val daysInMonth = firstOfMonth.daysUntil(nextMonthFirst)
    val totalCells = ((leadingBlanks + daysInMonth + 6) / 7) * 7
    return (0 until totalCells).map { offset -> gridStart.plus(offset.toLong(), DateTimeUnit.DAY) }
}

@Composable
private fun MonthGrid(
    anchorMonth: LocalDate,
    slideDirection: AnimatedContentTransitionScope.SlideDirection,
    viewModel: CalendarViewModel,
    categories: List<CalendarCategory>,
    onSwipePreviousMonth: () -> Unit,
    onSwipeNextMonth: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var accumulatedDragPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 80.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { accumulatedDragPx = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDragPx += dragAmount
                    },
                    onDragEnd = {
                        if (accumulatedDragPx <= -swipeThresholdPx) {
                            onSwipeNextMonth()
                        } else if (accumulatedDragPx >= swipeThresholdPx) {
                            onSwipePreviousMonth()
                        }
                        accumulatedDragPx = 0f
                    },
                    onDragCancel = { accumulatedDragPx = 0f }
                )
            }
    ) {
        AnimatedContent(
            targetState = anchorMonth,
            transitionSpec = {
                (slideIntoContainer(slideDirection, tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300))) togetherWith
                        (slideOutOfContainer(slideDirection, tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300)))
            },
            label = "MonthGridTransition",
            modifier = Modifier.fillMaxSize()
        ) { month ->
            MonthGridContent(
                anchorMonth = month,
                viewModel = viewModel,
                categories = categories,
                onDayClick = onDayClick
            )
        }
    }
}

@Composable
private fun MonthGridContent(
    anchorMonth: LocalDate,
    viewModel: CalendarViewModel,
    categories: List<CalendarCategory>,
    onDayClick: (LocalDate) -> Unit
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val gridDates = remember(anchorMonth) { buildMonthGridDates(anchorMonth) }
    val yearMonth = remember(anchorMonth) {
        "${anchorMonth.year}-${anchorMonth.monthNumber.toString().padStart(2, '0')}"
    }
    val monthEvents by remember(yearMonth) { viewModel.eventsForMonth(yearMonth) }
        .collectAsState(initial = emptyList())
    val eventsByDate = remember(monthEvents) { monthEvents.groupBy { it.dateString } }
    val weekRows = remember(gridDates) { gridDates.chunked(7) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            weekRows.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    week.forEach { date ->
                        MonthDayCell(
                            date = date,
                            isCurrentMonth = date.month == anchorMonth.month,
                            isToday = date == today,
                            events = eventsByDate[date.toString()] ?: emptyList(),
                            categories = categories,
                            onClick = { onDayClick(date) },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthDayCell(
    date: LocalDate,
    isCurrentMonth: Boolean,
    isToday: Boolean,
    events: List<CalendarEvent>,
    categories: List<CalendarCategory>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(26.dp)
                .then(if (isToday) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    isToday -> MaterialTheme.colorScheme.onPrimary
                    isCurrentMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                }
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(top = 4.dp).height(5.dp)
        ) {
            events.take(3).forEach { event ->
                val category = categories.firstOrNull { it.id == event.categoryId }
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(
                            category?.colorHex?.toCategoryColor() ?: MaterialTheme.colorScheme.outline,
                            CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun RightSidePanel(
    isOpen: Boolean,
    onOpenChange: (Boolean) -> Unit,
    viewMode: CalendarViewMode,
    onViewModeChange: (CalendarViewMode) -> Unit,
    categories: List<CalendarCategory>,
    onAddCategory: (name: String, colorHex: String) -> Unit,
    onUpdateCategory: (id: String, name: String, colorHex: String) -> Unit,
    onDeleteCategory: (id: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val panelHazeState = remember { HazeState() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val panelWidthFraction = 0.75f
        val panelWidthPx = with(density) { (maxWidth * panelWidthFraction).toPx() }
        val scope = rememberCoroutineScope()

        val offsetX = remember { Animatable(panelWidthPx) }
        var isDragging by remember { mutableStateOf(false) }
        var dragOffsetPx by remember { mutableFloatStateOf(panelWidthPx) }

        LaunchedEffect(isOpen, panelWidthPx) {
            val target = if (isOpen) 0f else panelWidthPx
            if (!isDragging && offsetX.value != target) {
                offsetX.animateTo(target, tween(durationMillis = 300, easing = FastOutSlowInEasing))
            }
        }

        val currentOffset = if (isDragging) dragOffsetPx else offsetX.value
        val openFraction = if (panelWidthPx > 0f) 1f - (currentOffset / panelWidthPx) else 0f
        val scrimAlpha = openFraction.coerceIn(0f, 1f) * 0.4f

        fun settle(finalOffset: Float, shouldOpen: Boolean) {
            isDragging = false
            scope.launch {
                offsetX.snapTo(finalOffset)
                offsetX.animateTo(
                    targetValue = if (shouldOpen) 0f else panelWidthPx,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                )
            }
            onOpenChange(shouldOpen)
        }

        val dragModifier = Modifier.pointerInput(panelWidthPx) {
            detectHorizontalDragGestures(
                onDragStart = {
                    isDragging = true
                    dragOffsetPx = offsetX.value
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(0f, panelWidthPx)
                },
                onDragEnd = {
                    settle(dragOffsetPx, shouldOpen = dragOffsetPx < panelWidthPx / 2f)
                },
                onDragCancel = {
                    settle(dragOffsetPx, shouldOpen = isOpen)
                }
            )
        }

        if (scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .pointerInput(Unit) {
                        detectTapGestures { onOpenChange(false) }
                    }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(32.dp)
                .align(Alignment.CenterEnd)
                .padding(top = 90.dp)
                .then(dragModifier)
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(panelWidthFraction)
                .align(Alignment.CenterEnd)
                .graphicsLayer { translationX = currentOffset }
                .background( if (LocalAppIsDark.current) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background)
                .then(dragModifier)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = panelHazeState)
                        .background(if (LocalAppIsDark.current) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp, top = 61.dp)
                ) {
                    ViewModeSection(
                        viewMode = viewMode,
                        onViewModeChange = onViewModeChange
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    CategorySection(
                        categories = categories,
                        onAddCategory = onAddCategory,
                        onUpdateCategory = onUpdateCategory,
                        onDeleteCategory = onDeleteCategory
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .then(if (isDesktopPlatform) Modifier else Modifier.stableStatusBarsPadding())
                        .padding(top = if (isDesktopPlatform) 14.dp else 18.dp, end = 16.dp)
                        .zIndex(1f)
                ) {
                    TopBarIconButton(
                        icon = painterResource(Res.drawable.sidebar),
                        contentDescription = "Close Menu",
                        bgColor = if (isDesktopPlatform) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.background.copy(alpha = 0.25f),
                        tint = MaterialTheme.colorScheme.onSurface,
                        hazeState = panelHazeState,
                        onClick = { onOpenChange(false) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewModeSection(
    viewMode: CalendarViewMode,
    onViewModeChange: (CalendarViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "View",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        ViewModeRow(
            label = "Day",
            isSelected = viewMode == CalendarViewMode.DAY,
            onClick = { onViewModeChange(CalendarViewMode.DAY) }
        )
        ViewModeRow(
            label = "3 Day",
            isSelected = viewMode == CalendarViewMode.THREE_DAY,
            onClick = { onViewModeChange(CalendarViewMode.THREE_DAY) }
        )
        ViewModeRow(
            label = "Week",
            isSelected = viewMode == CalendarViewMode.WEEK,
            onClick = { onViewModeChange(CalendarViewMode.WEEK) }
        )
        ViewModeRow(
            label = "Month",
            isSelected = viewMode == CalendarViewMode.MONTH,
            onClick = { onViewModeChange(CalendarViewMode.MONTH) }
        )
    }
}

@Composable
private fun ViewModeRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

