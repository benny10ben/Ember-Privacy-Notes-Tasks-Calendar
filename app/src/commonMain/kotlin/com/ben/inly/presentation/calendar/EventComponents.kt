package com.ben.inly.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ben.inly.domain.util.isDesktopPlatform
import com.ben.inly.presentation.shared.components.InlyBottomSheet
import com.ben.inly.presentation.shared.components.InlyButtonPrimary
import com.ben.inly.presentation.shared.components.InlyButtonSecondary
import com.ben.inly.presentation.shared.components.InlyTextField
import com.ben.inly.presentation.shared.components.MinimalDatePickerDialog
import com.ben.inly.presentation.shared.components.MinimalTimePickerDialog
import com.ben.inly.presentation.shared.components.TopBarIconButtonGroup
import com.ben.inly.presentation.shared.components.TopBarIconButtonItem
import com.ben.inly.ui.theme.LocalAppIsDark
import inly.app.generated.resources.Res
import inly.app.generated.resources.calendar
import inly.app.generated.resources.category2
import inly.app.generated.resources.clock_circle
import inly.app.generated.resources.doc_text
import inly.app.generated.resources.link
import inly.app.generated.resources.pen
import inly.app.generated.resources.trash
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource

private val EventChipTextColor = Color(0xFF1A1A1A)

// Shared shape/spacing scale for the editor sheet/menu - kept as one constant so every
// interactive row (EventFieldRow, CategoryChip, EventChip) clips its ripple identically instead
// of each one hand-rolling its own radius.
private val InteractiveShape = RoundedCornerShape(12.dp)
private val FieldPadding = 14.dp
private val SectionSpacing = 16.dp

@Composable
fun EventChip(
    text: String,
    color: Color,
    hasCategory: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val textColor = if (hasCategory) EventChipTextColor else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = InteractiveShape,
        color = color,
        // clip BEFORE clickable so the ripple is bounded by the rounded corners instead of
        // bleeding into a square - Surface's own internal clip happens after this modifier
        // chain, too late to constrain the ripple drawn by clickable.
        modifier = modifier
            .height(height)
            .clip(InteractiveShape)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.TopStart) {
            Text(
                text = text.ifBlank { "Untitled event" },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

data class EventEditorState(
    val original: CalendarEvent?,
    val name: String,
    val date: LocalDate,
    val hour: Int,
    val minute: Int,
    val categoryId: String?,
    val durationMinutes: Int = 30,
    val url: String = "",
    val description: String = "",
    // New events (original == null) have nothing to view, so they start in edit mode.
    // Tapping an existing event starts in read-only view mode until "Edit" is tapped.
    val isEditing: Boolean = original == null
)

fun EventEditorState.toEpochMillis(): Long {
    val localDateTime = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, minute)
    return localDateTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}
fun EventEditorState.toEndEpochMillis(): Long = toEpochMillis() + durationMinutes * 60_000L

fun EventEditorState.endHour(): Int {
    val startTotal = hour * 60 + minute
    return ((startTotal + durationMinutes) / 60) % 24
}

fun EventEditorState.endMinute(): Int = (minute + durationMinutes) % 60

fun formatTimeOfDay(hour: Int, minute: Int): String {
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val period = if (hour < 12) "AM" else "PM"
    return "$displayHour:${minute.toString().padStart(2, '0')} $period"
}

fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours == 0 -> "${mins}min"
        mins == 0 -> "${hours}h"
        else -> "${hours}h ${mins}min"
    }
}

@Composable
fun EventEditorSheet(
    state: EventEditorState?,
    categories: List<CalendarCategory>,
    onNameChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    onDurationChange: (minutes: Int) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onUrlChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onEditClick: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val title = when {
        state == null || state.original == null -> "Add Event"
        state.isEditing -> "Edit Event"
        else -> null
    }

    InlyBottomSheet(
        expanded = state != null,
        onDismiss = onDismiss,
        title = title,
    ) { closeAnd ->
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            if (state != null) {
                EventEditorFields(
                    state = state,
                    categories = categories,
                    onNameChange = onNameChange,
                    onDateChange = onDateChange,
                    onTimeChange = onTimeChange,
                    onDurationChange = onDurationChange,
                    onCategoryChange = onCategoryChange,
                    onUrlChange = onUrlChange,
                    onDescriptionChange = onDescriptionChange,
                    onEditClick = onEditClick,
                    onCancel = { closeAnd(onDismiss) },
                    onSave = { closeAnd(onSave) },
                    onDelete = onDelete?.let { delete -> { closeAnd(delete) } }
                )
            }
        }
    }
}

@Composable
private fun EventEditorFields(
    state: EventEditorState,
    categories: List<CalendarCategory>,
    onNameChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    onDurationChange: (minutes: Int) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onUrlChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onEditClick: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?
) {
    if (!state.isEditing) {
        // View mode passes onDelete/onCancel through so the header action row (edit/delete/close)
        // can wire directly to the same callbacks the edit-mode footer uses.
        EventViewFields(
            state = state,
            categories = categories,
            onEditClick = onEditClick,
            onDelete = onDelete,
        )
        return
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(
                top = if (isDesktopPlatform) 16.dp else 4.dp,
                bottom = if (isDesktopPlatform) 0.dp else 20.dp
            ),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing)
    ) {
        InlyTextField(
            value = state.name,
            onValueChange = onNameChange,
            placeholder = "Event name",
            modifier = Modifier.fillMaxWidth()
        )

        InlyTextField(
            value = state.url,
            onValueChange = onUrlChange,
            placeholder = "URL (optional)",
            modifier = Modifier.fillMaxWidth()
        )

        EventFieldRow(
            icon = painterResource(Res.drawable.calendar),
            label = formatFullDate(state.date),
            onClick = { showDatePicker = true },
            modifier = Modifier.fillMaxWidth()
        )
        if (showDatePicker) {
            MinimalDatePickerDialog(
                initialTimestamp = state.toEpochMillis(),
                onDismiss = { showDatePicker = false },
                onConfirm = { millis ->
                    val instant = Instant.fromEpochMilliseconds(millis)
                    onDateChange(instant.toLocalDateTime(TimeZone.UTC).date)
                }
            )
        }

        // Time row and its duration caption are kept as one tight group (small internal gap)
        // rather than the section-wide SectionSpacing, since the caption reads as a label for
        // the row directly above it.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    EventFieldRow(
                        icon = painterResource(Res.drawable.clock_circle),
                        label = formatTimeOfDay(state.hour, state.minute),
                        onClick = { showStartTimePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (showStartTimePicker) {
                        MinimalTimePickerDialog(
                            initialTimestamp = state.toEpochMillis(),
                            onDismiss = { showStartTimePicker = false },
                            onConfirm = { hour, minute -> onTimeChange(hour, minute) }
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    EventFieldRow(
                        icon = painterResource(Res.drawable.clock_circle),
                        label = formatTimeOfDay(state.endHour(), state.endMinute()),
                        onClick = { showEndTimePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (showEndTimePicker) {
                        MinimalTimePickerDialog(
                            initialTimestamp = state.toEndEpochMillis(),
                            onDismiss = { showEndTimePicker = false },
                            onConfirm = { hour, minute ->
                                val startTotal = state.hour * 60 + state.minute
                                var endTotal = hour * 60 + minute
                                if (endTotal <= startTotal) endTotal += 24 * 60
                                onDurationChange((endTotal - startTotal).coerceAtLeast(5))
                            }
                        )
                    }
                }
            }

            Text(
                text = "Duration: ${formatDuration(state.durationMinutes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Category",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChip(
                    label = "None",
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    hasCategory = false,
                    isSelected = state.categoryId == null,
                    onClick = { onCategoryChange(null) }
                )
                categories.forEach { category ->
                    CategoryChip(
                        label = category.name,
                        color = category.colorHex.toCategoryColor(),
                        hasCategory = true,
                        isSelected = state.categoryId == category.id,
                        onClick = { onCategoryChange(category.id) }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Description",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            InlyTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                placeholder = "Add a description (optional)",
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (onDelete != null) {
            TextButton(onClick = onDelete) {
                Text(
                    text = "Delete event",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InlyButtonSecondary(
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            )
            InlyButtonPrimary(
                text = if (state.original == null) "Add" else "Save",
                onClick = onSave,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// Read-only view shown when an existing event is tapped. Redesigned to match the
// accent-bar + title/subtitle + plain icon rows layout: a colored bar (from the event's
// category, falling back to the theme primary) sits beside the name and date/time range,
// with edit/delete/close actions in a header row instead of a single bottom "Edit" button.
// Static Text throughout (no InlyTextField) so no keyboard ever comes up in view mode.
@Composable
private fun EventViewFields(
    state: EventEditorState,
    categories: List<CalendarCategory>,
    onEditClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val category = categories.firstOrNull { it.id == state.categoryId }
    val accentColor = category?.colorHex?.toCategoryColor() ?: MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(
                top = if (isDesktopPlatform) 16.dp else 4.dp,
                bottom = if (isDesktopPlatform) 0.dp else 32.dp
            ),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing)
    ) {
        // Action row - edit / delete / close, top-right like the reference card. Grouped in a
        // single pill (shared bg/shadow/border) via TopBarIconButtonGroup instead of three
        // separate IconButtons.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.clip(CircleShape)) {
                TopBarIconButtonGroup(
                    bgColor = if (LocalAppIsDark.current) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background,
                    tint = MaterialTheme.colorScheme.onSurface,
                    items = buildList {
                        add(
                            TopBarIconButtonItem(
                                icon = painterResource(Res.drawable.pen),
                                contentDescription = "Edit",
                                onClick = onEditClick
                            )
                        )
                        if (onDelete != null) {
                            add(
                                TopBarIconButtonItem(
                                    icon = painterResource(Res.drawable.trash),
                                    contentDescription = "Delete",
                                    onClick = onDelete
                                )
                            )
                        }
                    }
                )
            }
        }

        // Accent bar + title/subtitle block.
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = state.name.ifBlank { "Untitled event" },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${formatFullDate(state.date)}, ${formatTimeOfDay(state.hour, state.minute)} – " +
                            formatTimeOfDay(state.endHour(), state.endMinute()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        // Plain icon + label rows - no Surface/box background, matching the reference layout.
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (category != null) {
                InfoRow(icon = painterResource(Res.drawable.category2), label = category.name)
            }
            if (state.url.isNotBlank()) {
                val uriHandler = LocalUriHandler.current
                InfoRow(
                    icon = painterResource(Res.drawable.link),
                    label = state.url,
                    isLink = true,
                    onClick = { try { uriHandler.openUri(state.url) } catch (_: Exception) {} }
                )
            }
            if (state.description.isNotBlank()) {
                InfoRow(icon = painterResource(Res.drawable.doc_text), label = state.description)
            }
        }
    }
}

// Single icon + text line used by the read-only view - no background, no ripple bounds,
// just an optional click target for the URL row.
@Composable
private fun InfoRow(
    icon: Painter,
    label: String,
    isLink: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base },
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isLink) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (isLink) TextDecoration.Underline else null
        )
    }
}

@Composable
private fun EventFieldRow(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = InteractiveShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        // clip BEFORE clickable, same reasoning as EventChip above - keeps the ripple inside
        // the rounded rect instead of drawing a square highlight past the corners.
        modifier = modifier
            .clip(InteractiveShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = FieldPadding, vertical = FieldPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    color: Color,
    hasCategory: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val textColor = if (hasCategory) EventChipTextColor else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = InteractiveShape,
        color = color,
        modifier = Modifier
            .clip(InteractiveShape)
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else Color.Transparent,
                shape = InteractiveShape
            )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = FieldPadding, vertical = 8.dp)
        )
    }
}

// An event plus where it lands in the day column's horizontal layout: `columnIndex` of
// `columnCount` equal-width slots, so overlapping events sit side by side instead of stacking.
data class PositionedEvent(
    val event: CalendarEvent,
    val columnIndex: Int,
    val columnCount: Int
)

private fun CalendarEvent.endTimestamp(): Long = reminderTimestamp + durationMinutes * 60_000L
fun layoutEventsForColumn(events: List<CalendarEvent>): List<PositionedEvent> {
    if (events.isEmpty()) return emptyList()
    val sorted = events.sortedBy { it.reminderTimestamp }

    val result = mutableListOf<PositionedEvent>()
    var clusterColumns = mutableListOf<MutableList<CalendarEvent>>()
    var clusterEnd = Long.MIN_VALUE

    fun flushCluster() {
        val columnCount = clusterColumns.size
        if (columnCount == 0) return
        clusterColumns.forEachIndexed { columnIndex, column ->
            column.forEach { event -> result.add(PositionedEvent(event, columnIndex, columnCount)) }
        }
        clusterColumns = mutableListOf()
        clusterEnd = Long.MIN_VALUE
    }

    for (event in sorted) {
        if (clusterColumns.isNotEmpty() && event.reminderTimestamp >= clusterEnd) {
            flushCluster()
        }
        val targetColumn = clusterColumns.firstOrNull { column -> column.last().endTimestamp() <= event.reminderTimestamp }
        if (targetColumn != null) {
            targetColumn.add(event)
        } else {
            clusterColumns.add(mutableListOf(event))
        }
        clusterEnd = maxOf(clusterEnd, event.endTimestamp())
    }
    flushCluster()

    return result
}