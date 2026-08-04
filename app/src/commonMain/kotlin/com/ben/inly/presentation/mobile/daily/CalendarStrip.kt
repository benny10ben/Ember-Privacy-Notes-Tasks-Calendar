package com.ben.inly.presentation.mobile.daily

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ben.inly.data.local.room.CalendarTaskEntity
import com.ben.inly.domain.util.isDesktopPlatform
import com.ben.inly.presentation.shared.components.InlyBlur
import com.ben.inly.presentation.shared.components.NoRippleIndicationNodeFactory
import com.ben.inly.presentation.shared.components.inlyBlur
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun CollapsedWeekStrip(
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    onDateSelected: (LocalDate) -> Unit,
    pagerState: PagerState? = null,
    initialPage: Int = 0,
    initialDate: LocalDate = selectedDate
) {
    var anchorDate by remember { mutableStateOf(selectedDate) }
    LaunchedEffect(selectedDate) {
        if (abs(anchorDate.daysUntil(selectedDate)) > 7) anchorDate = selectedDate
    }

    val dates = remember(anchorDate) { (-15..15).map { anchorDate.plus(it, DateTimeUnit.DAY) } }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val leadingOffset = if (isDesktopPlatform) 3 else 4

    if (pagerState != null) {
        LaunchedEffect(pagerState, initialPage, initialDate, anchorDate, dates) {
            val itemExtentPx = with(density) { (44.dp + 14.dp).toPx() }
            snapshotFlow { pagerState.currentPage - initialPage + pagerState.currentPageOffsetFraction }
                .collect { continuousOffsetFromInitial ->
                    val continuousOffsetFromAnchor = anchorDate.daysUntil(initialDate) + continuousOffsetFromInitial
                    val continuousIndex = 15f + continuousOffsetFromAnchor - leadingOffset
                    val flooredIndex = floor(continuousIndex).toInt().coerceIn(0, dates.lastIndex)
                    val fraction = continuousIndex - floor(continuousIndex)
                    listState.scrollToItem(flooredIndex, (fraction * itemExtentPx).roundToInt())
                }
        }
    } else {
        LaunchedEffect(selectedDate, anchorDate) {
            val targetIndex = dates.indexOf(selectedDate)
            if (targetIndex != -1 && !listState.isScrollInProgress) {
                listState.animateScrollToItem(maxOf(0, targetIndex - leadingOffset))
            }
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.first().scrollDelta.y
                            coroutineScope.launch {
                                listState.animateScrollBy(delta * 60f)
                            }
                        }
                    }
                }
            },
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(dates, key = { it.toString() }) { date ->
            DateCard(
                date = date,
                isSelected = date == selectedDate,
                showDayText = true,
                onDateClick = { onDateSelected(date) }
            )
        }
    }
}

@Composable
fun DailyBottomWeekStrip(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    hazeState: HazeState,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    var anchorDate by remember { mutableStateOf(selectedDate) }
    LaunchedEffect(selectedDate) {
        if (abs(anchorDate.daysUntil(selectedDate)) > 10) anchorDate = selectedDate
    }

    val dates = remember(anchorDate) { (-20..20).map { anchorDate.plus(it, DateTimeUnit.DAY) } }
    val listState = rememberLazyListState()

    val sizeSpec = tween<Dp>(durationMillis = 350, easing = FastOutSlowInEasing)
    val pillWidth = 74.dp
    val maxPillHeight = 32.dp
    val pillHeight by animateDpAsState(if (isCompact) 30.dp else maxPillHeight, sizeSpec)
    val pillSpacing = 8.dp
    val collapsedWidth = pillWidth * 3 + pillSpacing * 2

    var isExpanded by remember { mutableStateOf(false) }
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }
    LaunchedEffect(isScrolling) {
        if (isScrolling && !isProgrammaticScroll) {
            isExpanded = true
        } else if (!isScrolling) {
            delay(3000.milliseconds)
            if (!listState.isScrollInProgress) isExpanded = false
        }
    }

    val selectedIndex = remember(dates, selectedDate) { dates.indexOf(selectedDate) }
    LaunchedEffect(selectedIndex, isExpanded, anchorDate) {
        if (!isExpanded && selectedIndex != -1) {
            isProgrammaticScroll = true
            listState.animateScrollToItem((selectedIndex - 1).coerceAtLeast(0))
            isProgrammaticScroll = false
        }
    }

    val fadeWidth by animateDpAsState(
        targetValue = if (isExpanded) 0.dp else 22.dp,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().height(maxPillHeight),
        contentAlignment = Alignment.Center
    ) {
        val fullWidth = maxWidth
        val animatedWidth by animateDpAsState(
            targetValue = if (isExpanded) fullWidth else collapsedWidth,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        )

        LazyRow(
            state = listState,
            modifier = Modifier
                .align(Alignment.Center)
                .width(animatedWidth)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
//                .drawWithContent {
//                    drawContent()
//                    val fw = fadeWidth.toPx()
//                    if (fw > 0f) {
//                        drawRect(
//                            brush = Brush.horizontalGradient(
//                                0f to Color.Transparent, 1f to Color.Black,
//                                startX = 0f, endX = fw
//                            ),
//                            blendMode = BlendMode.DstIn
//                        )
//                        drawRect(
//                            brush = Brush.horizontalGradient(
//                                0f to Color.Black, 1f to Color.Transparent,
//                                startX = size.width - fw, endX = size.width
//                            ),
//                            blendMode = BlendMode.DstIn
//                        )
//                    }
//                },
            horizontalArrangement = Arrangement.spacedBy(pillSpacing, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(dates, key = { it.toString() }) { date ->
                WeekStripChip(
                    date = date,
                    width = pillWidth,
                    height = pillHeight,
                    hazeState = hazeState,
                    onClick = { onDateSelected(date) }
                )
            }
        }
    }
}

@Composable
private fun WeekStripChip(
    date: LocalDate,
    width: Dp,
    height: Dp,
    hazeState: HazeState,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val shortDayName = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(shape)
            .inlyBlur(hazeState, InlyBlur.Regular)
            .background(Color.Transparent)
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = NoRippleIndicationNodeFactory
            ) { onClick() }
    ) {
        Text(
            text = shortDayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun BottomSheetMonthCalendar(
    selectedDate: LocalDate,
    today: LocalDate,
    taskMap: Map<LocalDate, List<CalendarTaskEntity>>,
    onDateSelected: (LocalDate) -> Unit,
    onGoToToday: () -> Unit
) {
    var currentMonth by remember(selectedDate) { mutableStateOf(LocalDate(selectedDate.year, selectedDate.month, 1)) }
    val months = arrayOf("", "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { currentMonth = currentMonth.minus(1, DateTimeUnit.MONTH) }) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous", tint = MaterialTheme.colorScheme.onSurface)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${months[currentMonth.monthNumber]} ${currentMonth.year}",
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Go to Today",
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onGoToToday() }.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            IconButton(onClick = { currentMonth = currentMonth.plus(1, DateTimeUnit.MONTH) }) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                Text(
                    text = day, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val daysInMonth = remember(currentMonth) { currentMonth.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth }
        val startOffset = currentMonth.dayOfWeek.ordinal
        val totalCells = daysInMonth + startOffset
        val rows = if (totalCells % 7 == 0) totalCells / 7 else totalCells / 7 + 1

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (col in 0..6) {
                        val dayIndex = (row * 7) + col - startOffset
                        if (dayIndex in 0 until daysInMonth) {
                            val cellDate = LocalDate(currentMonth.year, currentMonth.month, dayIndex + 1)
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                BottomSheetDateCell(
                                    date = cellDate,
                                    isSelected = cellDate == selectedDate,
                                    isToday = cellDate == today,
                                    hasTasks = (taskMap[cellDate]?.size ?: 0) > 0,
                                    onClick = { onDateSelected(cellDate) }
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun BottomSheetDateCell(
    date: LocalDate, isSelected: Boolean, isToday: Boolean, hasTasks: Boolean, onClick: () -> Unit
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected || isToday) FontWeight.Medium else FontWeight.Normal,
            color = textColor, modifier = Modifier.offset(y = if (hasTasks) (-3).dp else 0.dp)
        )
        if (hasTasks) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(4.dp)
                    .clip(CircleShape).background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun DateCard(
    date: LocalDate,
    isSelected: Boolean,
    showDayText: Boolean,
    onDateClick: () -> Unit
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val mutedTextColor = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val shortDayName = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDateClick() }
            .padding(vertical = 8.dp)
    ) {
        if (showDayText) {
            Text(
                text = shortDayName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = mutedTextColor
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            color = textColor
        )
    }
}