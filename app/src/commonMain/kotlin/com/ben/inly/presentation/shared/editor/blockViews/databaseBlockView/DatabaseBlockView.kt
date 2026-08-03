package com.ben.inly.presentation.shared.editor.blockViews.databaseBlockView

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ben.inly.data.local.room.DatabaseTemplateEntity
import com.ben.inly.data.local.room.TagEntity
import com.ben.inly.domain.model.CellData
import com.ben.inly.domain.model.ColumnType
import com.ben.inly.domain.model.DatabaseBlock
import com.ben.inly.domain.model.DatabaseView
import com.ben.inly.domain.model.DEFAULT_STATUS_OPTIONS
import com.ben.inly.domain.model.GalleryCardSize
import com.ben.inly.domain.model.ViewType
import com.ben.inly.domain.model.displayText
import com.ben.inly.domain.model.propertyLabel
import com.ben.inly.domain.util.isDesktopPlatform
import com.ben.inly.domain.util.triggerHapticFeedback
import com.ben.inly.presentation.shared.components.InlyBottomSheet
import com.ben.inly.presentation.shared.components.MinimalDatePickerDialog
import com.ben.inly.presentation.shared.editor.EditorActions
import com.ben.inly.presentation.shared.editor.mouseScrollable
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.collections.get
import kotlin.text.equals
import com.ben.inly.data.local.room.NoteMetadataEntity
import com.ben.inly.presentation.shared.editor.RichTextVisualTransformation
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.ben.inly.presentation.shared.components.InlyDesktopMenu
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.ben.inly.presentation.shared.components.InlyButtonPrimary
import com.ben.inly.presentation.shared.components.InlyButtonSecondary
import com.ben.inly.presentation.shared.components.InlyTextField
import inly.app.generated.resources.Res
import inly.app.generated.resources.arrow_down
import inly.app.generated.resources.arrow_left
import inly.app.generated.resources.arrow_right
import inly.app.generated.resources.arrow_up
import inly.app.generated.resources.arrow_up_down
import inly.app.generated.resources.badge_dollar_sign
import inly.app.generated.resources.bookmark
import inly.app.generated.resources.check
import inly.app.generated.resources.chevron_left
import inly.app.generated.resources.chevron_right
import inly.app.generated.resources.check_square
import inly.app.generated.resources.file_text
import inly.app.generated.resources.files
import inly.app.generated.resources.funnel
import inly.app.generated.resources.group
import inly.app.generated.resources.hash
import inly.app.generated.resources.link
import inly.app.generated.resources.list_sort_descending
import inly.app.generated.resources.maximize_2
import inly.app.generated.resources.microphone
import inly.app.generated.resources.minimize_2
import inly.app.generated.resources.minus
import inly.app.generated.resources.paperclip
import inly.app.generated.resources.pause
import inly.app.generated.resources.pen
import inly.app.generated.resources.play
import inly.app.generated.resources.plus
import inly.app.generated.resources.sigma
import inly.app.generated.resources.slider_h2
import inly.app.generated.resources.sliders_horizontal
import inly.app.generated.resources.square
import inly.app.generated.resources.square_arrow_out_up_right
import inly.app.generated.resources.square_check
import inly.app.generated.resources.square_kanban
import inly.app.generated.resources.table
import inly.app.generated.resources.transfer_h
import inly.app.generated.resources.trash
import inly.app.generated.resources.widget
import inly.app.generated.resources.x
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds

enum class DbSheetType { NONE, COLUMN_OPTIONS, RENAME, RENAME_VIEW, FORMULA, FILTER, SORT, GROUP_BY, CELL_OPTIONS, TAG_SELECTION, FILE_OPTIONS, PRIORITY_SELECTION, STATUS_SELECTION, AGGREGATION, CURRENCY_SELECTION, SAVE_AS_TEMPLATE, ADD_VIEW, TABLE_SETTINGS, CARD_SIZE }

private object NoRippleIndicationNodeFactory : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun equals(other: Any?) = other === this
    override fun hashCode(): Int = -1
}

@Composable
fun DbOptionRow(
    icon: Painter,
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp).padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp)
            .padding(end = if (selected) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseTemplatePickerSheet(
    expanded: Boolean,
    templates: List<DatabaseTemplateEntity>,
    onDismiss: () -> Unit,
    onCreateBlank: () -> Unit,
    onSelectTemplate: (DatabaseTemplateEntity) -> Unit
) {
    InlyBottomSheet(expanded = expanded, onDismiss = onDismiss, title = "Add Database") { closeAnd ->
        CompositionLocalProvider(
        LocalIndication provides if (isDesktopPlatform) ripple() else NoRippleIndicationNodeFactory,
        LocalRippleConfiguration provides if (isDesktopPlatform) LocalRippleConfiguration.current else null,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
            DbOptionRow(icon = painterResource(Res.drawable.hash), text = "Create Blank Database") {
                closeAnd {
                    onDismiss()
                    onCreateBlank()
                }
            }

            if (templates.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )
                Text(
                    text = "Saved Templates",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                templates.forEach { template ->
                    DbOptionRow(icon = painterResource(Res.drawable.files), text = template.name) {
                        closeAnd {
                            onDismiss()
                            onSelectTemplate(template)
                        }
                    }
                }
            }
        }
      }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DatabaseBlockView(
    block: DatabaseBlock,
    inSelectionMode: Boolean,
    globalTags: List<TagEntity>,
    allLinkableNotes: List<NoteMetadataEntity>,
    actions: EditorActions
) {
    val hazeState = remember { HazeState() }
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val coroutineScope = rememberCoroutineScope()

    val sheetStack = remember { mutableStateListOf<DbSheetType>() }
    val currentSheet = sheetStack.lastOrNull() ?: DbSheetType.NONE
    var activeColId by remember { mutableStateOf<String?>(null) }
    var activeRowId by remember { mutableStateOf<String?>(null) }
    var renamingViewId by remember { mutableStateOf<String?>(null) }
    var textInput by remember { mutableStateOf("") }
    var textInputMax by remember { mutableStateOf("") }
    var filterOperator by remember { mutableStateOf("contains") }
    var filterPriority by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    var aggregationExpandedSection by remember { mutableStateOf<String?>(null) }

    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0) }
    var playingFileUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDuration = 0
            while (isRecording) {
                delay(1000.milliseconds)
                recordingDuration++
            }
        }
    }

    fun stopFileOptionsSideEffects() {
        if (isRecording && activeRowId != null && activeColId != null) {
            isRecording = false
            actions.onStopDbAudioRecording(block.id, activeRowId!!, activeColId!!, true)
        }
        if (playingFileUri != null) {
            playingFileUri = null
            actions.onStopAudio()
        }
    }

    fun closeSheet() {
        stopFileOptionsSideEffects()
        sheetStack.clear()
        activeRowId = null
        renamingViewId = null
        aggregationExpandedSection = null
    }

    fun openSheet(sheet: DbSheetType) {
        sheetStack.add(sheet)
    }

    fun popSheet() {
        if (currentSheet == DbSheetType.FILE_OPTIONS) stopFileOptionsSideEffects()
        if (sheetStack.isNotEmpty()) sheetStack.removeAt(sheetStack.lastIndex)
        if (sheetStack.isEmpty()) {
            activeRowId = null
            renamingViewId = null
            aggregationExpandedSection = null
        }
    }

    val visibleColumns = remember(block.columns) {
        block.columns.filter { !it.isDeleted }
    }

    // fall back to an empty Table view so we never null-check below
    val activeView = remember(block.views, block.activeViewId) {
        block.views.find { it.id == block.activeViewId }
            ?: block.views.firstOrNull()
            ?: DatabaseView(id = "", name = "Table", type = ViewType.TABLE)
    }

    fun applyAction(action: () -> Unit) {
        closeSheet()
        scope.launch {
            delay(250.milliseconds)
            action()
        }
    }

    val visibleRows = remember(block.rows, activeView.activeSorts, activeView.activeFilters) {
        var result = block.rows.filter { !it.isDeleted }

        activeView.activeFilters.forEach { filter ->
            result = result.filter { row ->
                val cellVal = row.cells[filter.columnId].displayText()
                when (filter.operator) {
                    // text checks
                    "contains"     -> cellVal.contains(filter.value, ignoreCase = true)
                    "not_contains" -> !cellVal.contains(filter.value, ignoreCase = true)
                    "equals"       -> cellVal.equals(filter.value, ignoreCase = true)
                    "not_equals"   -> !cellVal.equals(filter.value, ignoreCase = true)
                    "starts_with"  -> cellVal.startsWith(filter.value, ignoreCase = true)
                    "ends_with"    -> cellVal.endsWith(filter.value, ignoreCase = true)

                    // empty/checked state
                    "empty"        -> cellVal.isBlank() || cellVal == "—"
                    "not_empty"    -> cellVal.isNotBlank() && cellVal != "—"
                    "checked"      -> cellVal == "true"
                    "unchecked"    -> cellVal != "true"

                    // numeric comparisons
                    "gt"           -> (cellVal.toDoubleOrNull() ?: 0.0) > (filter.value.toDoubleOrNull() ?: 0.0)
                    "gte"          -> (cellVal.toDoubleOrNull() ?: 0.0) >= (filter.value.toDoubleOrNull() ?: 0.0)
                    "lt"           -> (cellVal.toDoubleOrNull() ?: 0.0) < (filter.value.toDoubleOrNull() ?: 0.0)
                    "lte"          -> (cellVal.toDoubleOrNull() ?: 0.0) <= (filter.value.toDoubleOrNull() ?: 0.0)

                    // "lo|hi" range
                    "between"      -> {
                        val parts = filter.value.split("|")
                        if (parts.size == 2 && cellVal.isNotBlank()) {
                            val lo = parts[0].toDoubleOrNull()
                            val hi = parts[1].toDoubleOrNull()
                            val v  = cellVal.toDoubleOrNull()
                            if (lo != null && hi != null && v != null) v in lo..hi
                            else cellVal >= parts[0] && cellVal <= parts[1]
                        } else true
                    }

                    // priority/date fields
                    "priority"     -> cellVal.equals(filter.value, ignoreCase = true)
                    "before"       -> cellVal.isNotBlank() && cellVal < filter.value
                    "after"        -> cellVal.isNotBlank() && cellVal > filter.value
                    else           -> true
                }
            }
        }

        if (activeView.activeSorts.isNotEmpty()) {
            result = result.sortedWith(Comparator { row1, row2 ->
                var comparisonResult = 0

                for (sortRule in activeView.activeSorts) {
                    val targetCol = block.columns.find { it.id == sortRule.columnId } ?: continue
                    val rawVal1 = row1.cells[sortRule.columnId].displayText()
                    val rawVal2 = row2.cells[sortRule.columnId].displayText()

                    comparisonResult = when (targetCol.type) {
                        ColumnType.NUMBER, ColumnType.MONEY -> {
                            val num1 = rawVal1.toDoubleOrNull() ?: Double.MAX_VALUE
                            val num2 = rawVal2.toDoubleOrNull() ?: Double.MAX_VALUE
                            num1.compareTo(num2)
                        }
                        ColumnType.CHECKBOX -> {
                            val bool1 = rawVal1 == "true"
                            val bool2 = rawVal2 == "true"
                            bool1.compareTo(bool2)
                        }
                        ColumnType.PRIORITY -> {
                            val weight = mapOf("Low" to 1, "Medium" to 2, "High" to 3, "Urgent" to 4)
                            val p1 = weight[rawVal1] ?: 0
                            val p2 = weight[rawVal2] ?: 0
                            p1.compareTo(p2)
                        }
                        else -> rawVal1.lowercase().compareTo(rawVal2.lowercase())
                    }

                    if (!sortRule.isAscending) comparisonResult = -comparisonResult
                    if (comparisonResult != 0) break
                }
                comparisonResult
            })
        }
        result
    }

    fun sheetTitleFor(sheet: DbSheetType): String? = when (sheet) {
        DbSheetType.CELL_OPTIONS -> "Cell Actions"
        DbSheetType.COLUMN_OPTIONS -> visibleColumns.find { it.id == activeColId }?.name
            ?: "Column Options"
        DbSheetType.RENAME -> "Rename Column"
        DbSheetType.RENAME_VIEW -> "Rename View"
        DbSheetType.FORMULA -> "Edit Formula"
        DbSheetType.CURRENCY_SELECTION -> "Select Currency"
        DbSheetType.SORT -> "Sort by"
        DbSheetType.FILTER -> "Filter"
        DbSheetType.GROUP_BY -> "Group By"
        DbSheetType.CARD_SIZE -> "Card Size"
        DbSheetType.FILE_OPTIONS -> "Attached Files"
        DbSheetType.PRIORITY_SELECTION -> "Set Priority"
        DbSheetType.STATUS_SELECTION -> "Set Status"
        DbSheetType.AGGREGATION -> "Calculate"
        DbSheetType.TAG_SELECTION -> "Select Tag"
        DbSheetType.SAVE_AS_TEMPLATE -> "Save as Template"
        DbSheetType.ADD_VIEW -> "Add View"
        DbSheetType.TABLE_SETTINGS -> "Table Settings"
        else -> null
    }

    fun desktopBackTargetFor(sheet: DbSheetType): DbSheetType? = when (sheet) {
        DbSheetType.RENAME, DbSheetType.FORMULA, DbSheetType.CURRENCY_SELECTION ->
            DbSheetType.COLUMN_OPTIONS
        DbSheetType.FILTER, DbSheetType.SAVE_AS_TEMPLATE, DbSheetType.ADD_VIEW,
        DbSheetType.GROUP_BY, DbSheetType.CARD_SIZE ->
            DbSheetType.TABLE_SETTINGS
        else -> null
    }

    val sheetContent = @Composable { targetSheet: DbSheetType ->
        CompositionLocalProvider(
            LocalIndication provides if (isDesktopPlatform) ripple() else NoRippleIndicationNodeFactory,
            LocalRippleConfiguration provides if (isDesktopPlatform) LocalRippleConfiguration.current else null,
        ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // on mobile, each sheet is its own stacked InlyBottomSheet with a native title bar
            // and back-press/dismiss to pop, so the manual back row + title only apply to the
            // single anchored desktop dropdown, which still swaps content in place
            if (isDesktopPlatform) {
                val backTarget = desktopBackTargetFor(targetSheet)
                if (backTarget != null) {
                    DbOptionRow(painterResource(Res.drawable.chevron_left), "Back to Options") {
                        openSheet(backTarget)
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )
                }

                val title = sheetTitleFor(targetSheet)
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp, top = 8.dp).padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp)
                    )
                }
            }

            when (targetSheet) {
                // cell options
                DbSheetType.CELL_OPTIONS -> {
                    val col = visibleColumns.find { it.id == activeColId }
                    val row = block.rows.find { it.id == activeRowId }
                    if (col != null && row != null) {
                        val colIndex = visibleColumns.indexOf(col)
                        val rowIndex = block.rows.indexOf(row)

                        DbOptionRow(
                            painterResource(Res.drawable.arrow_up),
                            "Insert Row Above"
                        ) { applyAction { actions.onAddDbRowAt(block.id, rowIndex) } }
                        DbOptionRow(
                            painterResource(Res.drawable.arrow_down),
                            "Insert Row Below"
                        ) { applyAction { actions.onAddDbRowAt(block.id, rowIndex + 1) } }
                        DbOptionRow(
                            painterResource(Res.drawable.arrow_left),
                            "Insert Column Left"
                        ) { applyAction { actions.onAddDbColumnAt(block.id, colIndex) } }
                        DbOptionRow(
                            painterResource(Res.drawable.arrow_right),
                            "Insert Column Right"
                        ) { applyAction { actions.onAddDbColumnAt(block.id, colIndex + 1) } }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )

                        DbOptionRow(
                            painterResource(Res.drawable.trash),
                            "Delete Row",
                            MaterialTheme.colorScheme.error
                        ) { applyAction { actions.onDeleteDbRow(block.id, row.id) } }
                        DbOptionRow(
                            painterResource(Res.drawable.trash),
                            "Delete Column",
                            MaterialTheme.colorScheme.error
                        ) { applyAction { actions.onDeleteDbColumn(block.id, col.id) } }
                    }
                }


                // column options
                DbSheetType.COLUMN_OPTIONS -> {
                    val col = visibleColumns.find { it.id == activeColId }
                    if (col != null) {
                        DbOptionRow(painterResource(Res.drawable.pen), "Rename Column") {
                            textInput = col.name
                            openSheet(DbSheetType.RENAME)
                        }

                        if (col.type == ColumnType.FORMULA) {
                            DbOptionRow(
                                icon = painterResource(Res.drawable.sigma),
                                text = "Edit Formula",
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                textInput = col.formulaExpression ?: ""
                                openSheet(DbSheetType.FORMULA)
                            }

                            val isCurrency = col.isFormulaCurrency
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        actions.onUpdateDbFormulaCurrency(
                                            block.id,
                                            col.id,
                                            !isCurrency
                                        )
                                    }
                                    .padding(vertical = 6.dp).padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painterResource(Res.drawable.badge_dollar_sign),
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "Format as currency",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                androidx.compose.material3.Switch(
                                    checked = isCurrency,
                                    onCheckedChange = null,
                                    modifier = Modifier.scale(0.8f)
                                )
                            }

                            if (isCurrency) {
                                val currentCurrency = col.currencySymbol ?: "$"
                                DbOptionRow(
                                    icon = painterResource(Res.drawable.badge_dollar_sign),
                                    text = "Currency: $currentCurrency",
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    openSheet(DbSheetType.CURRENCY_SELECTION)
                                }
                            }
                        }

                        if (col.type == ColumnType.MONEY) {
                            val currentCurrency = col.currencySymbol ?: "$"
                            DbOptionRow(
                                icon = painterResource(Res.drawable.badge_dollar_sign),
                                text = "Format: $currentCurrency",
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                openSheet(DbSheetType.CURRENCY_SELECTION)
                            }
                        }

                        if (!isDesktopPlatform) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )
                            Text(
                                text = "Column Width",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                            )

                            Row(
                                modifier = Modifier.padding(bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.clickable {
                                        actions.onUpdateDbColumnWidth(
                                            block.id,
                                            col.id,
                                            col.width - 20
                                        )
                                    }
                                ) {
                                    Icon(
                                        painterResource(Res.drawable.minus),
                                        contentDescription = null,
                                        modifier = Modifier.padding(8.dp).size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Text(
                                    text = "${col.width} px",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.widthIn(min = 50.dp),
                                    textAlign = TextAlign.Center
                                )

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.clickable {
                                        actions.onUpdateDbColumnWidth(
                                            block.id,
                                            col.id,
                                            col.width + 20
                                        )
                                    }
                                ) {
                                    Icon(
                                        painterResource(Res.drawable.plus),
                                        contentDescription = null,
                                        modifier = Modifier.padding(8.dp).size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )

                        Text(
                            text = "Property Type",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 10.dp, top = 12.dp).padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp)
                        )

                        FlowRow(
                            modifier = Modifier.padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ColumnType.entries.forEach { type ->
                                val isSelected = col.type == type
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        applyAction {
                                            actions.onUpdateDbColumn(
                                                block.id,
                                                col.id,
                                                if (col.isNameManuallySet) col.name else type.propertyLabel(),
                                                type,
                                                isManualNameChange = false
                                            )
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = type.propertyLabel(),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(
                                            alpha = 0.5f
                                        )
                                    ),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        labelColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                        DbOptionRow(
                            icon = painterResource(Res.drawable.trash),
                            text = "Delete Column",
                            color = MaterialTheme.colorScheme.error,
                            onClick = {
                                applyAction {
                                    actions.onDeleteDbColumn(
                                        block.id,
                                        col.id
                                    )
                                }
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }

                // rename
                DbSheetType.RENAME -> {
                    Column(modifier = Modifier.padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp)) {
                    InlyTextField(value = textInput, onValueChange = { textInput = it }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))}
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(vertical = 12.dp).padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InlyButtonSecondary(text = "Cancel", onClick = { closeSheet() }, modifier = Modifier.weight(1f))
                        InlyButtonPrimary(
                            text = "Save",
                            onClick = {
                                val c = visibleColumns.find { it.id == activeColId }
                                if (c != null && textInput.isNotBlank()) {
                                    applyAction { actions.onUpdateDbColumn(block.id, c.id, textInput.trim(), c.type) }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // rename a view
                DbSheetType.RENAME_VIEW -> {
                    Column(modifier = Modifier.padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp)) {
                    InlyTextField(value = textInput, onValueChange = { textInput = it }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))}
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(vertical = 12.dp).padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InlyButtonSecondary(text = "Cancel", onClick = { closeSheet() }, modifier = Modifier.weight(1f))
                        InlyButtonPrimary(
                            text = "Save",
                            onClick = {
                                val viewId = renamingViewId
                                if (viewId != null && textInput.isNotBlank()) {
                                    applyAction { actions.onRenameDatabaseView(block.id, viewId, textInput.trim()) }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (block.views.size > 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                        DbOptionRow(
                            icon = painterResource(Res.drawable.trash),
                            text = "Delete View",
                            color = MaterialTheme.colorScheme.error
                        ) {
                            val viewId = renamingViewId
                            if (viewId != null) {
                                applyAction { actions.onDeleteDatabaseView(block.id, viewId) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // save as template - schema only, no rows
                DbSheetType.SAVE_AS_TEMPLATE -> {
                    Column(modifier = Modifier.padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp)) {
                        InlyTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = "Template name",
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(vertical = 12.dp).padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InlyButtonSecondary(text = "Cancel", onClick = { closeSheet() }, modifier = Modifier.weight(1f))
                        InlyButtonPrimary(
                            text = "Save",
                            onClick = {
                                val name = textInput.trim()
                                if (name.isNotEmpty()) {
                                    applyAction { actions.onSaveDatabaseAsTemplate(block.id, name) }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // formula
                DbSheetType.FORMULA -> {
                    Text(
                        text = "Properties",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp, top = 12.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp),
                    ) {
                        visibleColumns.filter { it.id != activeColId }.forEach { c ->
                            SuggestionChip(
                                onClick = { textInput += "prop(\"${c.name}\") " },
                                label = {
                                    Text(
                                        c.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(
                                        alpha = 0.5f
                                    )
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                            )
                        }
                    }

                    Text(
                        text = "Operators",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp),
                    ) {
                        listOf("+", "-", "*", "/", "(", ")").forEach { op ->
                            SuggestionChip(
                                onClick = { textInput += "$op " },
                                label = {
                                    Text(
                                        op,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                            )
                        }
                    }

                    Column(modifier = Modifier.padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp)) {
                        InlyTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = "e.g. prop(\"Price\") * 2",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = 12.dp, bottom = 12.dp).padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InlyButtonSecondary(text = "Cancel", onClick = { closeSheet() }, modifier = Modifier.weight(1f))
                        InlyButtonPrimary(
                            text = "Save",
                            onClick = {
                                val colId = activeColId
                                if (colId != null) applyAction { actions.onUpdateDbFormula(block.id, colId, textInput.trim()) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // multi-layer sort, Notion style
                DbSheetType.SORT -> {
                    val sortedColIds = activeView.activeSorts.map { it.columnId }
                    val unsortedColumns = visibleColumns.filter { it.id !in sortedColIds }

                    // current sort layers
                    if (activeView.activeSorts.isNotEmpty()) {
                        Text(
                            text = "Sort order — top layer wins, lower layers break ties",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp)
                        )

                        activeView.activeSorts.forEachIndexed { index, sortRule ->
                            val col = visibleColumns.find { it.id == sortRule.columnId }
                                ?: return@forEachIndexed
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(vertical = 6.dp).padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // layer number badge
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "${index + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    col.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                // asc/desc toggle, keeps the sheet open
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp)).
                                    clickable {
                                        actions.onUpdateDbSort(
                                            block.id,
                                            col.id,
                                            !sortRule.isAscending
                                        )
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(
                                            horizontal = 10.dp,
                                            vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (sortRule.isAscending) painterResource(Res.drawable.arrow_up) else painterResource(Res.drawable.arrow_down),
                                            null, modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            if (sortRule.isAscending) "Asc" else "Desc",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    painterResource(Res.drawable.x), "Remove sort layer",
                                    modifier = Modifier.size(18.dp).clickable {
                                        actions.onUpdateDbSort(block.id, col.id, null)
                                    },
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                    }

                    if (unsortedColumns.isNotEmpty()) {
//                        Text(
//                            text = if (activeView.activeSorts.isEmpty()) "Sort by" else "Then by",
//                            style = MaterialTheme.typography.labelSmall,
//                            fontWeight = FontWeight.SemiBold,
//                            color = MaterialTheme.colorScheme.onSurface,
//                            modifier = Modifier.padding(
//                                start = 20.dp,
//                                end = 20.dp,
//                                top = 4.dp,
//                                bottom = 8.dp
//                            )
//                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp),
                        ) {
                            unsortedColumns.forEach { col ->
                                SuggestionChip(
                                    onClick = { actions.onUpdateDbSort(block.id, col.id, true) },
                                    label = {
                                        Text(
                                            col.name,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    icon = {
                                        Icon(
                                            painterResource(Res.drawable.plus),
                                            null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(
                                            alpha = 0.5f
                                        ),
                                        labelColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    ),
                                )
                            }
                        }
                    } else if (activeView.activeSorts.isNotEmpty()) {
                        Text(
                            text = "Every column is already in the sort.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = 12.dp, bottom = 12.dp).padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (activeView.activeSorts.isNotEmpty()) {
                            InlyButtonSecondary(
                                text = "Clear all",
                                onClick = {
                                    activeView.activeSorts.map { it.columnId }.forEach { cid -> actions.onUpdateDbSort(block.id, cid, null) }
                                    closeSheet()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            InlyButtonPrimary(text = "Done", onClick = { closeSheet() }, modifier = Modifier.weight(1f))
                        }
                    }
                }

                // group by, kanban only
                DbSheetType.GROUP_BY -> {
                    val eligibleColumns = visibleColumns.filter {
                        it.type == ColumnType.CHECKBOX || it.type == ColumnType.STATUS
                    }
                    val selectedGroupColumn = eligibleColumns.find { it.id == activeView.groupByColumnId }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        val isNoneSelected = activeView.groupByColumnId == null
                        DbOptionRow(
                            icon = painterResource(Res.drawable.x),
                            text = "None",
                            color = if (isNoneSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            selected = isNoneSelected
                        ) {
                            applyAction { actions.onUpdateDbGroupBy(block.id, null) }
                        }

                        eligibleColumns.forEach { col ->
                            val isSelected = activeView.groupByColumnId == col.id
                            val icon = when (col.type) {
                                ColumnType.CHECKBOX -> painterResource(Res.drawable.square_check)
                                ColumnType.STATUS -> painterResource(Res.drawable.check_square)
                                else -> rememberVectorPainter(Icons.AutoMirrored.Filled.Subject)
                            }
                            DbOptionRow(
                                icon = icon,
                                text = col.name,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                selected = isSelected
                            ) {
                                applyAction { actions.onUpdateDbGroupBy(block.id, col.id) }
                            }
                        }

                        if (eligibleColumns.isEmpty()) {
                            Text(
                                text = "Add a Checkbox or Status column to group by.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        // only show once a column's picked; mirrors bucketKeysFor() in KanbanView.kt
                        if (selectedGroupColumn != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )
                            Text(
                                text = "Visible boards",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            // local drag order, only persisted on drop via onReorderKanbanGroups
                            val defaultBucketKeys = remember(selectedGroupColumn) {
                                bucketKeysFor(
                                    selectedGroupColumn
                                )
                            }
                            val orderedKeys = remember(activeView.id, defaultBucketKeys, activeView.groupOrder) {
                                mutableStateListOf(*orderedBucketKeys(
                                    defaultBucketKeys,
                                    activeView.groupOrder
                                ).toTypedArray())
                            }
                            var draggedBucket by remember { mutableStateOf<String?>(null) }
                            var dragPointerY by remember { mutableStateOf(0f) }
                            val rowBoundsInWindow = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }

                            orderedKeys.forEach { bucketName ->
                                // key by name, not index, so a reorder mid-drag doesn't steal the gesture
                                key(bucketName) {
                                    val isVisible = bucketName !in activeView.hiddenGroups
                                    val isDragged = draggedBucket == bucketName
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onGloballyPositioned { rowBoundsInWindow[bucketName] = it.boundsInWindow() }
                                            .graphicsLayer { alpha = if (isDragged) 0.5f else 1f }
                                            .clickable {
                                                actions.onToggleKanbanGroupVisibility(block.id, activeView.id, bucketName, isVisible)
                                            }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.DragIndicator,
                                                contentDescription = "Reorder $bucketName",
                                                tint = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .pointerInput(bucketName) {
                                                        detectDragGestures(
                                                            onDragStart = {
                                                                draggedBucket = bucketName
                                                                dragPointerY = rowBoundsInWindow[bucketName]?.center?.y ?: 0f
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                dragPointerY += dragAmount.y
                                                                val hovered = rowBoundsInWindow.entries
                                                                    .firstOrNull { (_, rect) -> dragPointerY in rect.top..rect.bottom }
                                                                    ?.key
                                                                if (hovered != null && hovered != bucketName) {
                                                                    val from = orderedKeys.indexOf(bucketName)
                                                                    val to = orderedKeys.indexOf(hovered)
                                                                    if (from != -1 && to != -1) {
                                                                        orderedKeys.add(to, orderedKeys.removeAt(from))
                                                                    }
                                                                }
                                                            },
                                                            onDragEnd = {
                                                                draggedBucket = null
                                                                actions.onReorderKanbanGroups(block.id, activeView.id, orderedKeys.toList())
                                                            },
                                                            onDragCancel = { draggedBucket = null }
                                                        )
                                                    }
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(bucketName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                        androidx.compose.material3.Switch(
                                            checked = isVisible,
                                            onCheckedChange = null,
                                            modifier = Modifier.scale(0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // CARD SIZE (Gallery only) - purely a display density knob, so there's no
                // "None" option the way Group By/Filter have one; a view always has exactly one
                // of the three sizes.
                DbSheetType.CARD_SIZE -> {
                    val options = listOf(
                        Triple(GalleryCardSize.SMALL, "Small", painterResource(Res.drawable.minimize_2)),
                        Triple(GalleryCardSize.MEDIUM, "Medium", painterResource(Res.drawable.square)),
                        Triple(GalleryCardSize.LARGE, "Large", painterResource(Res.drawable.maximize_2))
                    )

                    Column(modifier = Modifier.fillMaxWidth()) {
                        options.forEach { (size, label, icon) ->
                            val isSelected = activeView.galleryCardSize == size
                            DbOptionRow(
                                icon = icon,
                                text = label,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                selected = isSelected
                            ) {
                                applyAction { actions.onUpdateDbGalleryCardSize(block.id, size) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // filter
                DbSheetType.FILTER -> {
                    val activeCol = visibleColumns.find { it.id == activeColId }
                    val isCheckbox = activeCol?.type == ColumnType.CHECKBOX
                    val isNumber =
                        activeCol?.type == ColumnType.NUMBER || activeCol?.type == ColumnType.MONEY
                    val isDate = activeCol?.type == ColumnType.DATE

                    Text(
                        text = "Column",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                            val idx = visibleColumns.indexOfFirst { it.id == activeColId }
                            activeColId = visibleColumns[(idx + 1) % visibleColumns.size].id
                            filterOperator = "contains"
                            textInput = ""
                            textInputMax = ""
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeCol?.name ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                painterResource(Res.drawable.transfer_h),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 22.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )

                    Text(
                        text = "Condition",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp).padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp)
                    )

                    val operatorOptions: List<Pair<String, String>> = when {
                        isCheckbox -> listOf(
                            "unchecked" to "Hide Checked rows",
                            "checked" to "Hide Unchecked rows"
                        )

                        isNumber -> listOf(
                            "equals" to "Equals",
                            "not_equals" to "Does not equal",
                            "gt" to "Greater than (>)",
                            "gte" to "Greater than or equal (≥)",
                            "lt" to "Less than (<)",
                            "lte" to "Less than or equal (≤)",
                            "between" to "Between (range)",
                            "not_empty" to "Is not empty",
                            "empty" to "Is empty"
                        )

                        isDate -> listOf(
                            "equals" to "On exactly date",
                            "before" to "Is before date",
                            "after" to "Is after date",
                            "between" to "Between two dates",
                            "not_empty" to "Is scheduled (Not empty)",
                            "empty" to "Is unscheduled (Empty)"
                        )

                        else -> listOf(
                            "contains" to "Contains text",
                            "not_contains" to "Does not contain",
                            "equals" to "Is exactly",
                            "not_equals" to "Is not",
                            "starts_with" to "Starts with",
                            "ends_with" to "Ends with",
                            "not_empty" to "Is not empty",
                            "empty" to "Is empty",
                            "priority" to "Priority status is"
                        )
                    }

                    if (operatorOptions.none { it.first == filterOperator }) filterOperator =
                        operatorOptions.first().first

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp),
                    ) {
                        operatorOptions.forEach { (op, label) ->
                            val isSelected = filterOperator == op
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    filterOperator = op; textInput = ""; textInputMax = ""
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(
                                        alpha = 0.5f
                                    )
                                ),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }

                    val needsTextInput = filterOperator in listOf(
                        "contains",
                        "equals",
                        "not_equals",
                        "gt",
                        "gte",
                        "lt",
                        "lte",
                        "before",
                        "after",
                        "starts_with",
                        "ends_with"
                    )
                    val needsRangeInput = filterOperator == "between"
                    val needsPriorityPicker = filterOperator == "priority"

                    if (needsTextInput) {
                        Spacer(Modifier.height(12.dp))
                        Column(modifier = Modifier.padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp)) {
                            InlyTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                placeholder = if (isNumber) "Enter number…" else "Enter value…",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (needsRangeInput) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp)) {
                                InlyTextField(
                                    value = textInput,
                                    onValueChange = { textInput = it },
                                    placeholder = if (isDate) "Start" else "Min",
                                    modifier = Modifier.weight(1f)
                                )
                                InlyTextField(
                                    value = textInputMax,
                                    onValueChange = { textInputMax = it },
                                    placeholder = if (isDate) "End" else "Max",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    if (needsPriorityPicker) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Priority level",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp),
                        ) {
                            listOf("Low", "Medium", "High", "Urgent").forEach { p ->
                                val isSelected = filterPriority == p
                                val chipColor = priorityAccentColor(p) ?: MaterialTheme.colorScheme.outline
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { filterPriority = p; textInput = p },
                                    label = { Text(p, style = MaterialTheme.typography.labelSmall) },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) chipColor else MaterialTheme.colorScheme.outline.copy(
                                            alpha = 0.5f
                                        )
                                    ),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = chipColor,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = 12.dp, bottom = 12.dp).padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InlyButtonSecondary(text = "Cancel", onClick = { closeSheet() }, modifier = Modifier.weight(1f))
                        InlyButtonPrimary(
                            text = "Apply",
                            onClick = {
                                val colId = activeColId
                                if (colId != null) {
                                    val canApply = when {
                                        isCheckbox -> true
                                        filterOperator in listOf("not_empty", "empty") -> true
                                        filterOperator == "priority" -> filterPriority.isNotBlank()
                                        filterOperator == "between" -> textInput.isNotBlank() && textInputMax.isNotBlank()
                                        else -> textInput.isNotBlank()
                                    }
                                    if (canApply) applyAction {
                                        actions.onAddDbFilter(
                                            block.id, colId, filterOperator,
                                            when (filterOperator) {
                                                "priority" -> filterPriority.trim()
                                                "between" -> "${textInput.trim()}|${textInputMax.trim()}"
                                                else -> textInput.trim()
                                            }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // tag selection
                DbSheetType.TAG_SELECTION -> {
                    var tagSearchQuery by remember { mutableStateOf("") }
                    val row = block.rows.find { it.id == activeRowId }
                    if (row != null) {
                        val currentTagIds =
                            (row.cells[activeColId] as? CellData.TagList)?.tagIds
                                ?.toMutableSet() ?: mutableSetOf()

                        Column(modifier = Modifier.padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp)) {
                        InlyTextField(value = tagSearchQuery, onValueChange = { tagSearchQuery = it }, placeholder = "Search or create a tag...", modifier = Modifier.fillMaxWidth())
                            }
                        Spacer(Modifier.height(12.dp))

                        val filteredTags = globalTags.filter {
                            it.name.contains(
                                tagSearchQuery,
                                ignoreCase = true
                            )
                        }
                        val exactMatchExists = globalTags.any {
                            it.name.equals(
                                tagSearchQuery.trim(),
                                ignoreCase = true
                            )
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (tagSearchQuery.isNotBlank() && !exactMatchExists) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val colors = listOf(
                                                "#E03E3E",
                                                "#D9730D",
                                                "#DFAB01",
                                                "#0F7B6C",
                                                "#0B6E99",
                                                "#6940A5",
                                                "#9065B0"
                                            )
                                            val newTagId = actions.onCreateGlobalTag(
                                                tagSearchQuery.trim(),
                                                colors.random()
                                            )
                                            currentTagIds.add(newTagId)
                                            actions.onUpdateDbCell(
                                                block.id,
                                                row.id,
                                                activeColId ?: return@clickable,
                                                CellData.TagList(currentTagIds.toList())
                                            )
                                            tagSearchQuery = ""
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painterResource(Res.drawable.plus),
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "Create \"${tagSearchQuery.trim()}\"",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            filteredTags.forEach { tag ->
                                val isSelected = currentTagIds.contains(tag.tagId)
                                val tagColor = try {
                                    Color(tag.colorHex.removePrefix("#").toLong(16) or 0xFF000000)
                                } catch (_: Exception) {
                                    MaterialTheme.colorScheme.primary
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isSelected) currentTagIds.remove(tag.tagId) else currentTagIds.add(
                                                tag.tagId
                                            )
                                            actions.onUpdateDbCell(
                                                block.id,
                                                row.id,
                                                activeColId ?: return@clickable,
                                                CellData.TagList(currentTagIds.toList())
                                            )
                                        }
                                        .padding(vertical = 10.dp)
                                        .padding(end = if (isSelected) 12.dp else 0.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = tagColor.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = tag.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tagColor,
                                            modifier = Modifier.padding(
                                                horizontal = 8.dp,
                                                vertical = 4.dp
                                            )
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }

                // file options
                DbSheetType.FILE_OPTIONS -> {
                    val row = block.rows.find { it.id == activeRowId }
                    val col = visibleColumns.find { it.id == activeColId }
                    if (row != null && col != null) {
                        val currentFiles =
                            (row.cells[activeColId] as? CellData.MediaList)?.files
                                ?.toMutableList() ?: mutableListOf()

                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState())
                        ) {

                            if (col.type == ColumnType.AUDIO) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isRecording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface.copy(
                                                alpha = 0.5f
                                            )
                                        )
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (isRecording) painterResource(Res.drawable.square) else painterResource(Res.drawable.microphone),
                                        contentDescription = null,
                                        tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp).clickable {
                                            if (isRecording) {
                                                isRecording = false
                                                actions.onStopDbAudioRecording(
                                                    block.id,
                                                    row.id,
                                                    col.id,
                                                    false
                                                )
                                            } else {
                                                isRecording = true
                                                actions.onStartRecording()
                                            }
                                        }
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    if (isRecording) {
                                        val mins = recordingDuration / 60
                                        val secs = recordingDuration % 60
                                        Text(
                                            text = "Recording... ${mins}:${
                                                secs.toString().padStart(2, '0')
                                            }",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else {
                                        Text(
                                            "Tap mic to record audio",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                )
                            }

                            currentFiles.forEach { resourceEntry ->
                                val cleanFileName = resourceEntry.fileName.substringAfterLast("/")
                                val resourceName = resourceEntry.originalName.ifBlank { cleanFileName }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (col.type == ColumnType.AUDIO) {
                                                if (playingFileUri == cleanFileName) {
                                                    playingFileUri = null
                                                    actions.onStopAudio()
                                                } else {
                                                    if (playingFileUri != null) actions.onStopAudio()
                                                    playingFileUri = cleanFileName
                                                    actions.onPlayAudio(cleanFileName) {
                                                        if (playingFileUri == cleanFileName) playingFileUri =
                                                            null
                                                    }
                                                }
                                            } else {
                                                actions.onOpenFile(cleanFileName, "*/*")
                                            }
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val icon: Painter = if (col.type == ColumnType.AUDIO) {
                                            if (playingFileUri == cleanFileName) painterResource(Res.drawable.pause) else painterResource(Res.drawable.play)
                                        } else painterResource(Res.drawable.link)

                                        Icon(
                                            painter = icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = resourceName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth(0.8f)
                                        )
                                    }

                                    Icon(
                                        painterResource(Res.drawable.x),
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp).clickable {
                                            if (playingFileUri == cleanFileName) {
                                                playingFileUri = null
                                                actions.onStopAudio()
                                            }
                                            currentFiles.remove(resourceEntry)
                                            actions.onUpdateDbCell(
                                                block.id,
                                                row.id,
                                                col.id,
                                                CellData.MediaList(currentFiles.toList())
                                            )
                                        }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val rId = row.id
                                        val cId = col.id
                                        val isAud = col.type == ColumnType.AUDIO
                                        applyAction {
                                            actions.onRequestDbFilePicker(
                                                block.id,
                                                rId,
                                                cId,
                                                isAud
                                            )
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painterResource(Res.drawable.plus),
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = if (col.type == ColumnType.AUDIO) "Upload audio track" else "Attach a new file",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }

                // priority selection
                DbSheetType.PRIORITY_SELECTION -> {
                    val options = listOf("Low", "Medium", "High", "Urgent").map { priority ->
                        priority to (priorityAccentColor(priority) ?: MaterialTheme.colorScheme.outline)
                    }
                    val row = block.rows.find { it.id == activeRowId }
                    if (row != null) {
                        val current = (row.cells[activeColId] as? CellData.Text)?.value ?: ""

                        options.forEach { (label, color) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val rowId = row.id
                                        val colId = activeColId ?: return@clickable
                                        applyAction {
                                            actions.onUpdateDbCell(
                                                block.id,
                                                rowId,
                                                colId,
                                                CellData.Text(label)
                                            )
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = color.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = color,
                                        modifier = Modifier.padding(
                                            horizontal = 10.dp,
                                            vertical = 4.dp
                                        )
                                    )
                                }
                                if (current == label) {
                                    Icon(
                                        painterResource(Res.drawable.check),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        if (current.isNotBlank()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )
                            DbOptionRow(
                                painterResource(Res.drawable.x),
                                text = "Clear",
                                color = MaterialTheme.colorScheme.error
                            ) {
                                val rowId = row.id
                                val colId = activeColId ?: return@DbOptionRow
                                applyAction { actions.onUpdateDbCell(block.id, rowId, colId, CellData.Text("")) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // status selection - same fixed set KanbanView.kt buckets on
                DbSheetType.STATUS_SELECTION -> {
                    val options = DEFAULT_STATUS_OPTIONS.map { status ->
                        status to (statusAccentColor(
                            status
                        ) ?: MaterialTheme.colorScheme.outline)
                    }
                    val row = block.rows.find { it.id == activeRowId }
                    if (row != null) {
                        val current = (row.cells[activeColId] as? CellData.Text)?.value ?: ""

                        options.forEach { (label, color) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val rowId = row.id
                                        val colId = activeColId ?: return@clickable
                                        applyAction {
                                            actions.onUpdateDbCell(
                                                block.id,
                                                rowId,
                                                colId,
                                                CellData.Text(label)
                                            )
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = color.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = color,
                                        modifier = Modifier.padding(
                                            horizontal = 10.dp,
                                            vertical = 4.dp
                                        )
                                    )
                                }
                                if (current == label) {
                                    Icon(
                                        painterResource(Res.drawable.check),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        if (current.isNotBlank()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )
                            DbOptionRow(
                                painterResource(Res.drawable.x),
                                text = "Clear",
                                color = MaterialTheme.colorScheme.error
                            ) {
                                val rowId = row.id
                                val colId = activeColId ?: return@DbOptionRow
                                applyAction { actions.onUpdateDbCell(block.id, rowId, colId, CellData.Text("")) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // aggregation
                DbSheetType.AGGREGATION -> {
                    val col = visibleColumns.find { it.id == activeColId }
                    if (col != null) {
                        val isNum =
                            col.type == ColumnType.NUMBER || col.type == ColumnType.FORMULA || col.type == ColumnType.MONEY
                        val currentAgg = col.aggregationType ?: "None"

                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState())
                                .animateContentSize()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        closeSheet()
                                        actions.onUpdateDbAggregation(block.id, col.id, null)
                                    }
                                    .padding(vertical = 12.dp)
                                    .padding(end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "None",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (currentAgg == "None") Icon(
                                    painterResource(Res.drawable.check),
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            val groups = mutableListOf(
                                "Count" to listOf(
                                    "Count all",
                                    "Count values",
                                    "Count unique",
                                    "Count empty",
                                    "Count not empty"
                                ),
                                "Percent" to listOf("Percent empty", "Percent not empty")
                            )
                            if (isNum) groups.add(
                                "More options" to listOf(
                                    "Sum",
                                    "Average",
                                    "Min",
                                    "Max",
                                    "Median",
                                    "Range"
                                )
                            )

                            groups.forEach { (groupName, options) ->
                                val isExpanded = aggregationExpandedSection == groupName

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            aggregationExpandedSection =
                                                if (isExpanded) null else groupName
                                        }
                                        .padding(vertical = 12.dp)
                                        .padding(end = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        groupName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val rotation by animateFloatAsState(if (isExpanded) -90f else 90f)
                                    Icon(
                                        painterResource(Res.drawable.chevron_right),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp).rotate(rotation)
                                    )
                                }

                                if (isExpanded) {
                                    options.forEach { opt ->
                                        val isSelected = currentAgg == opt
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    closeSheet()
                                                    actions.onUpdateDbAggregation(
                                                        block.id,
                                                        col.id,
                                                        opt
                                                    )
                                                }
                                                .padding(
                                                    start = 20.dp,
                                                    top = 8.dp,
                                                    bottom = 8.dp
                                                )
                                                .padding(end = if (isSelected) 12.dp else 0.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                opt,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isSelected) Icon(
                                                painterResource(Res.drawable.check),
                                                null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // currency selection
                DbSheetType.CURRENCY_SELECTION -> {
                    val col = visibleColumns.find { it.id == activeColId }
                    if (col != null) {
                        val currencies = listOf(
                            "$" to "US Dollar",
                            "€" to "Euro",
                            "£" to "British Pound",
                            "¥" to "Yen",
                            "₹" to "Rupee",
                            "A$" to "Australian Dollar",
                            "C$" to "Canadian Dollar"
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            currencies.forEach { (symbol, name) ->
                                val isSelected = (col.currencySymbol ?: "$") == symbol
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            closeSheet()
                                            actions.onUpdateDbCurrency(block.id, col.id, symbol)
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "$name ($symbol)",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) Icon(
                                        painterResource(Res.drawable.check),
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // add view
                DbSheetType.ADD_VIEW -> {
                    DbOptionRow(
                        icon = painterResource(Res.drawable.table),
                        text = "Table"
                    ) { applyAction { actions.onAddDatabaseView(block.id, ViewType.TABLE) } }
                    DbOptionRow(
                        icon = painterResource(Res.drawable.square_kanban),
                        text = "Board"
                    ) { applyAction { actions.onAddDatabaseView(block.id, ViewType.KANBAN) } }
                    DbOptionRow(
                        icon = painterResource(Res.drawable.widget),
                        text = "Gallery"
                    ) { applyAction { actions.onAddDatabaseView(block.id, ViewType.GALLERY) } }
                }

                // table settings
                DbSheetType.TABLE_SETTINGS -> {
                    DbOptionRow(
                        icon = painterResource(Res.drawable.funnel),
                        text = "Filter"
                    ) {
                        if (visibleColumns.isNotEmpty()) {
                            activeColId = visibleColumns.first().id
                            textInput = ""
                            textInputMax = ""
                            filterOperator = "contains"
                            openSheet(DbSheetType.FILTER)
                        }
                    }
                    DbOptionRow(
                        icon = painterResource(Res.drawable.bookmark),
                        text = "Save as Template"
                    ) {
                        textInput = ""
                        openSheet(DbSheetType.SAVE_AS_TEMPLATE)
                    }
                    DbOptionRow(
                        icon = painterResource(Res.drawable.plus),
                        text = "Add View"
                    ) { openSheet(DbSheetType.ADD_VIEW) }

                    if (activeView.type == ViewType.KANBAN) {
                        DbOptionRow(
                            icon = painterResource(Res.drawable.group),
                            text = "Group By"
                        ) { openSheet(DbSheetType.GROUP_BY) }
                    }
                    if (activeView.type == ViewType.GALLERY) {
                        DbOptionRow(
                            icon = painterResource(Res.drawable.slider_h2),
                            text = "Card Size"
                        ) { openSheet(DbSheetType.CARD_SIZE) }
                    }
                }
                else -> {}
            }
        }
      }
    }

    val DesktopDbDropdown = @Composable { visible: Boolean ->
        if (isDesktopPlatform && visible) {
            InlyDesktopMenu(
                expanded = true,
                onDismissRequest = { closeSheet() }
            ) {
                AnimatedContent(
                    targetState = currentSheet,
                    transitionSpec = {
                        val isGoingDeeper = targetState !in listOf(DbSheetType.COLUMN_OPTIONS, DbSheetType.CELL_OPTIONS, DbSheetType.NONE)
                        if (isGoingDeeper) {
                            (slideInHorizontally(tween(200)) { it } + fadeIn(tween(200))) togetherWith
                                    (slideOutHorizontally(tween(200)) { -it / 2 } + fadeOut(tween(200))) using SizeTransform(clip = false)
                        } else {
                            (slideInHorizontally(tween(200)) { -it / 2 } + fadeIn(tween(200))) togetherWith
                                    (slideOutHorizontally(tween(200)) { it } + fadeOut(tween(200))) using SizeTransform(clip = false)
                        }
                    },
                    label = "DesktopDbTransition"
                ) { target ->
                    Box(modifier = Modifier.widthIn(min = 280.dp, max = 340.dp).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        sheetContent(target)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (inSelectionMode) actions.onToggleSelection(block.id) },
                onLongClick = { actions.onToggleSelection(block.id) }
            )
    ) {
        // title row
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var titleTfv by remember(block.id) {
                mutableStateOf(TextFieldValue(block.title, TextRange(block.title.length)))
            }
            var lastSentTitle by remember(block.id) { mutableStateOf(block.title) }

            LaunchedEffect(block.title) {
                if (titleTfv.text != block.title && block.title != lastSentTitle) {
                    titleTfv = titleTfv.copy(
                        text = block.title,
                        selection = TextRange(titleTfv.selection.start.coerceAtMost(block.title.length))
                    )
                }
            }

            LaunchedEffect(titleTfv.text) {
                if (titleTfv.text != block.title) {
                    delay(400L.milliseconds)
                    lastSentTitle = titleTfv.text
                    actions.onUpdateDbTitle(block.id, titleTfv.text)
                }
            }

            BasicTextField(
                value = titleTfv,
                onValueChange = { titleTfv = it },
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    if (titleTfv.text.isEmpty()) {
                        Text(
                            text = "Untitled Database",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    inner()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !inSelectionMode,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = false
                )
            )
        }

        // view switcher tabs + sort/settings toolbar, same row
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            val orderedViews = remember(block.views) { mutableStateListOf(*block.views.toTypedArray()) }
            var draggedViewId by remember { mutableStateOf<String?>(null) }
            var dragStartIndex by remember { mutableStateOf(-1) }
            var dragPointerX by remember { mutableStateOf(0f) }
            val viewBoundsInWindow = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
            orderedViews.forEach { view ->
                key(view.id) {
                    val isActive = view.id == activeView.id
                    val isDragged = draggedViewId == view.id

                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { viewBoundsInWindow[view.id] = it.boundsInWindow() }
                            .graphicsLayer { alpha = if (isDragged) 0.6f else 1f }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isActive) MaterialTheme.colorScheme.surface else Color.Transparent,
                            modifier = Modifier
                                .clickable(enabled = !inSelectionMode) {
                                    if (isActive) {
                                        textInput = view.name
                                        renamingViewId = view.id
                                        openSheet(DbSheetType.RENAME_VIEW)
                                    } else {
                                        actions.onSetActiveDatabaseView(block.id, view.id)
                                    }
                                }
                                .pointerInput(view.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedViewId = view.id
                                            dragStartIndex = block.views.indexOfFirst { it.id == view.id }
                                            dragPointerX = viewBoundsInWindow[view.id]?.center?.x ?: 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragPointerX += dragAmount.x
                                            val hovered = viewBoundsInWindow.entries
                                                .firstOrNull { (_, rect) -> dragPointerX in rect.left..rect.right }
                                                ?.key
                                            if (hovered != null && hovered != view.id) {
                                                val from = orderedViews.indexOfFirst { it.id == view.id }
                                                val to = orderedViews.indexOfFirst { it.id == hovered }
                                                if (from != -1 && to != -1) {
                                                    orderedViews.add(to, orderedViews.removeAt(from))
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            draggedViewId = null
                                            val finalIndex = orderedViews.indexOfFirst { it.id == view.id }
                                            if (dragStartIndex != -1 && finalIndex != -1 && dragStartIndex != finalIndex) {
                                                actions.onReorderDatabaseViews(block.id, dragStartIndex, finalIndex)
                                            }
                                            dragStartIndex = -1
                                        },
                                        onDragCancel = { draggedViewId = null; dragStartIndex = -1 }
                                    )
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = when (view.type) {
                                        ViewType.TABLE -> painterResource(Res.drawable.table)
                                        ViewType.KANBAN -> painterResource(Res.drawable.square_kanban)
                                        ViewType.GALLERY -> painterResource(Res.drawable.widget)
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = view.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        DesktopDbDropdown(renamingViewId == view.id && currentSheet == DbSheetType.RENAME_VIEW)
                    }
                }
            }
            }

            Spacer(Modifier.width(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    val hasSort = activeView.activeSorts.isNotEmpty()
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (hasSort) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                        modifier = Modifier.clickable(enabled = !inSelectionMode) {
                            if (visibleColumns.isNotEmpty()) {
                                activeColId = activeView.activeSorts.firstOrNull()?.columnId ?: visibleColumns.first().id
                                openSheet(DbSheetType.SORT)
                            }
                        }
                    ) {
                        Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                            Icon(painterResource(Res.drawable.arrow_up_down), contentDescription = null, modifier = Modifier.size(18.dp), tint = if (hasSort) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    DesktopDbDropdown(currentSheet == DbSheetType.SORT)
                }

                // table settings entry point
                Box {
                    val hasFilter = activeView.activeFilters.isNotEmpty()
                    val hasGroupBy = activeView.type == ViewType.KANBAN && activeView.groupByColumnId != null
                    val hasCardSize = activeView.type == ViewType.GALLERY && activeView.galleryCardSize != GalleryCardSize.MEDIUM
                    val isCustomized = hasFilter || hasGroupBy || hasCardSize
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCustomized) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                        modifier = Modifier.clickable(enabled = !inSelectionMode) {
                            openSheet(DbSheetType.TABLE_SETTINGS)
                        }
                    ) {
                        Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                            Icon(painterResource(Res.drawable.sliders_horizontal), contentDescription = "Table settings", modifier = Modifier.size(18.dp), tint = if (isCustomized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    DesktopDbDropdown(
                        currentSheet in listOf(
                            DbSheetType.TABLE_SETTINGS,
                            DbSheetType.FILTER,
                            DbSheetType.SAVE_AS_TEMPLATE,
                            DbSheetType.ADD_VIEW,
                            DbSheetType.GROUP_BY,
                            DbSheetType.CARD_SIZE
                        )
                    )
                }
            }
        }

        // active filter chips
        if (activeView.activeFilters.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                activeView.activeFilters.forEach { filter ->
                    val colName = visibleColumns.find { it.id == filter.columnId }?.name ?: "?"
                    val label = when (filter.operator) {
                        "not_empty"    -> "$colName is not empty"
                        "empty"        -> "$colName is empty"
                        "checked"      -> "$colName is checked"
                        "unchecked"    -> "$colName is unchecked"
                        "priority"     -> "$colName = ${filter.value}"
                        "gt"           -> "$colName > ${filter.value}"
                        "lt"           -> "$colName < ${filter.value}"
                        "gte"          -> "$colName ≥ ${filter.value}"
                        "lte"          -> "$colName ≤ ${filter.value}"
                        "between"      -> { val p = filter.value.split("|"); if (p.size == 2) "$colName: ${p[0]} – ${p[1]}" else "$colName between" }
                        "before"       -> "$colName before ${filter.value}"
                        "after"        -> "$colName after ${filter.value}"
                        "starts_with"  -> "$colName starts with \"${filter.value}\""
                        "ends_with"    -> "$colName ends with \"${filter.value}\""
                        "not_contains" -> "$colName does not contain \"${filter.value}\""
                        "not_equals"   -> "$colName is not \"${filter.value}\""
                        else           -> "$colName ${filter.operator} \"${filter.value}\""
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        ),
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(6.dp))
                            Icon(painterResource(Res.drawable.x), contentDescription = null, modifier = Modifier.size(13.dp).clickable(enabled = !inSelectionMode) { actions.onRemoveDbFilter(block.id, filter) }, tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        // dispatch to whichever composable matches the active view's type
        when (activeView.type) {
            ViewType.TABLE -> TableView(
                block = block,
                activeView = activeView,
                visibleColumns = visibleColumns,
                visibleRows = visibleRows,
                inSelectionMode = inSelectionMode,
                globalTags = globalTags,
                allLinkableNotes = allLinkableNotes,
                actions = actions,
                hazeState = hazeState,
                scrollState = scrollState,
                coroutineScope = coroutineScope,
                focusManager = focusManager,
                currentSheet = currentSheet,
                activeColId = activeColId,
                activeRowId = activeRowId,
                onOpenSheet = { sheet, rowId, colId ->
                    activeRowId = rowId
                    activeColId = colId
                    openSheet(sheet)
                },
                onOpenDatePicker = { rowId, colId ->
                    closeSheet()
                    activeRowId = rowId
                    activeColId = colId
                    showDatePicker = true
                },
                desktopDropdown = DesktopDbDropdown
            )
            ViewType.KANBAN -> KanbanView(
                blockId = block.id,
                activeView = activeView,
                visibleColumns = visibleColumns,
                visibleRows = visibleRows,
                inSelectionMode = inSelectionMode,
                globalTags = globalTags,
                allLinkableNotes = allLinkableNotes,
                actions = actions,
                onOpenGroupBySheet = { openSheet(DbSheetType.GROUP_BY) }
            )
            ViewType.GALLERY -> GalleryView(
                blockId = block.id,
                cardSize = activeView.galleryCardSize,
                visibleColumns = visibleColumns,
                visibleRows = visibleRows,
                inSelectionMode = inSelectionMode,
                globalTags = globalTags,
                allLinkableNotes = allLinkableNotes,
                actions = actions
            )
        }
    }

    if (!isDesktopPlatform) {
        // each stacked sheet is its own InlyBottomSheet instance, so pressing an option that
        // opens a new sheet slides a fresh sheet in on top instead of swapping this one's content
        sheetStack.forEachIndexed { index, sheetType ->
            key(index) {
                InlyBottomSheet(
                    expanded = true,
                    onDismiss = { popSheet() },
                    title = sheetTitleFor(sheetType)
                ) { _ ->
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        sheetContent(sheetType)
                    }
                }
            }
        }
    }

    if (showDatePicker && activeRowId != null && activeColId != null) {
        val initialTimestamp = (block.rows.find { it.id == activeRowId }?.cells?.get(activeColId) as? CellData.Date)?.timestamp

        MinimalDatePickerDialog(
            expanded = showDatePicker,
            initialTimestamp = initialTimestamp,
            onDismiss = { showDatePicker = false },
            onConfirm = { millis ->
                val rowToUpdate = activeRowId
                val colToUpdate = activeColId
                showDatePicker = false

                if (rowToUpdate != null && colToUpdate != null) {
                    scope.launch {
                        delay(150.milliseconds)
                        actions.onUpdateDbCell(block.id, rowToUpdate, colToUpdate, CellData.Date(millis))
                    }
                }
            }
        )
    }
}

/**
 * Fixed accent colors for the four PRIORITY values, same reasoning as [statusAccentColor] in
 * KanbanView.kt: this app's theme makes `outline`/`primary` shades of gray/black/white (see
 * Theme.kt), so "Low" and "Medium" used to render as two different grays instead of a real
 * severity scale. Plain hardcoded hex rather than theme colors, same as Status/Tags.
 */
private val PRIORITY_ACCENT_COLORS = mapOf(
    "Low" to Color(0xFF7FB3D5),
    "Medium" to Color(0xFFF2C14E),
    "High" to Color(0xFFE8873A),
    "Urgent" to Color(0xFFE0574F)
)

private fun priorityAccentColor(priority: String): Color? = PRIORITY_ACCENT_COLORS[priority]

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun TableCell(
    cell: CellData?,
    columnType: ColumnType,
    cellWidth: Dp,
    globalTags: List<TagEntity>,
    inSelectionMode: Boolean,
    currencySymbol: String = "$",
    isFormulaCurrency: Boolean = false,
    onValueChange: (CellData) -> Unit,
    onDateClick: () -> Unit,
    onTagClick: () -> Unit,
    onFileClick: () -> Unit,
    onPriorityClick: () -> Unit,
    onStatusClick: () -> Unit,
    onNoteClick: () -> Unit,
    onNoteLinkClick: (String) -> Unit,
    onGetNoteTitle: suspend (String) -> String,
    allLinkableNotes: List<NoteMetadataEntity>,
    onCreateLinkedNote: (String) -> String,
    onLongPress: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current

    val validNoteIds = remember(allLinkableNotes) { allLinkableNotes.map { it.noteId }.toSet() }

    when (columnType) {
        ColumnType.TEXT, ColumnType.NUMBER, ColumnType.PHONE, ColumnType.EMAIL, ColumnType.URL, ColumnType.MONEY -> {
            var isFocused by remember { mutableStateOf(false) }
            val focusRequester = remember { FocusRequester() }
            // Number/Money is a Double? under the hood but renders as plain text either way
            val value = if (columnType == ColumnType.NUMBER || columnType == ColumnType.MONEY) {
                (cell as? CellData.Number)?.value?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: ""
            } else {
                (cell as? CellData.Text)?.value ?: ""
            }

            Box(modifier = Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (!inSelectionMode) focusRequester.requestFocus() }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {

                    if (columnType == ColumnType.MONEY && (value.isNotBlank() || isFocused)) {
                        Text(text = currencySymbol, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(end = 4.dp))
                    }

                    IsolatedTableCellTextField(
                        initialText = value,
                        columnType = columnType,
                        allLinkableNotes = allLinkableNotes,
                        visualTransformation = if (columnType == ColumnType.TEXT) RichTextVisualTransformation(
                            linkColor = MaterialTheme.colorScheme.primary,
                            fadedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            validNoteIds = validNoteIds
                        ) else androidx.compose.ui.text.input.VisualTransformation.None,
                        inSelectionMode = inSelectionMode,
                        focusRequester = focusRequester,
                        onValueChange = { raw ->
                            onValueChange(
                                if (columnType == ColumnType.NUMBER || columnType == ColumnType.MONEY) CellData.Number(raw.toDoubleOrNull())
                                else CellData.Text(raw)
                            )
                        },
                        onFocusChanged = { isFocused = it },
                        onNoteLinkClick = onNoteLinkClick,
                        onCreateLinkedNote = onCreateLinkedNote,
                        modifier = Modifier.weight(1f).defaultMinSize(minWidth = cellWidth - 24.dp)
                    )

                    val isLinkType = columnType == ColumnType.EMAIL || columnType == ColumnType.PHONE || columnType == ColumnType.URL
                    if (isLinkType && value.isNotBlank() && !isFocused) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            painterResource(Res.drawable.square_arrow_out_up_right), contentDescription = "Open Link", modifier = Modifier.size(16.dp).clickable {
                                when (columnType) {
                                    ColumnType.EMAIL -> try { uriHandler.openUri("mailto:$value") } catch (_: Exception) {}
                                    ColumnType.PHONE -> try { uriHandler.openUri("tel:$value") } catch (_: Exception) {}
                                    ColumnType.URL -> try {
                                        val url = if (!value.startsWith("http://") && !value.startsWith("https://")) "https://$value" else value
                                        uriHandler.openUri(url)
                                    } catch (_: Exception) {}
                                    else -> {}
                                }
                            }, tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        ColumnType.CHECKBOX -> {
            val isChecked = (cell as? CellData.Boolean)?.value ?: false
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Checkbox(
                        checked = isChecked, onCheckedChange = {
                            if (!inSelectionMode) {
                                triggerHapticFeedback()
                                onValueChange(CellData.Boolean(it))
                            }
                        }, modifier = Modifier.scale(0.9f).size(18.dp),
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.surface, checkmarkColor = MaterialTheme.colorScheme.primary, uncheckedColor = MaterialTheme.colorScheme.outline)
                    )
                }
            }
        }
        ColumnType.DATE -> {
            val value = cell.displayText()
            Text(
                text = value.ifEmpty { "—" }, style = MaterialTheme.typography.bodyLarge,
                color = if (value.isEmpty()) MaterialTheme.colorScheme.outline.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().clickable(enabled = !inSelectionMode) { onDateClick() }
            )
        }
        ColumnType.FORMULA -> {
            val value = (cell as? CellData.Formula)?.result ?: ""
            val formulaScrollState = rememberScrollState()
            val isInvalid = value.equals("NaN", ignoreCase = true) || value.startsWith("Error", ignoreCase = true)

            val displayValue = if (isInvalid) "" else {
                if (isFormulaCurrency && value.toDoubleOrNull() != null) {
                    val num = value.toDouble()
                    val fmt = if (num == num.toLong().toDouble()) num.toLong().toString() else ((num * 100.0).toLong() / 100.0).toString()
                    "$currencySymbol$fmt"
                } else value
            }

            Text(
                text = displayValue, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1,
                modifier = Modifier.horizontalScroll(formulaScrollState).mouseScrollable(formulaScrollState)
            )
        }
        ColumnType.TAGS -> {
            val activeTagIds = (cell as? CellData.TagList)?.tagIds ?: emptyList()
            val activeTags = activeTagIds.mapNotNull { id -> globalTags.find { it.tagId == id } }

            Box(modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 24.dp).combinedClickable(onClick = { if (!inSelectionMode) onTagClick() }, onLongClick = { if (!inSelectionMode) onLongPress() })) {
                if (activeTags.isEmpty()) {
                    Text("Empty", color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp)
                    ) {
                        activeTags.forEach { tag ->
                            val tagColor = try {
                                Color(tag.colorHex.removePrefix("#").toLong(16) or 0xFF000000)
                            } catch (_: Exception) {
                                MaterialTheme.colorScheme.primary
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = tagColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tagColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        ColumnType.FILES, ColumnType.AUDIO -> {
            val resources = (cell as? CellData.MediaList)?.files ?: emptyList()

            Box(modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 24.dp).combinedClickable(onClick = { if (!inSelectionMode) onFileClick() }, onLongClick = { if (!inSelectionMode) onLongPress() })) {
                if (resources.isEmpty()) {
                    Text("Empty", color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        resources.forEach { resourceEntry ->
                            val cleanFileName = resourceEntry.fileName.substringAfterLast("/")
                            val resourceName = resourceEntry.originalName.ifBlank { cleanFileName }

                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surface) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                                    Icon(
                                        if (columnType == ColumnType.AUDIO) painterResource(Res.drawable.microphone) else painterResource(Res.drawable.paperclip),
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = resourceName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 100.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        ColumnType.PRIORITY -> {
            val value = (cell as? CellData.Text)?.value ?: ""
            Box(modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 24.dp).combinedClickable(onClick = { if (!inSelectionMode) onPriorityClick() }, onLongClick = { if (!inSelectionMode) onLongPress() })) {
                if (value.isBlank()) {
                    Text("—", color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), style = MaterialTheme.typography.labelSmall)
                } else {
                    val chipColor = priorityAccentColor(value) ?: MaterialTheme.colorScheme.outline
                    Surface(shape = RoundedCornerShape(4.dp), color = chipColor.copy(alpha = 0.15f)) {
                        Text(text = value, style = MaterialTheme.typography.labelSmall, color = chipColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
        }

        ColumnType.STATUS -> {
            val value = (cell as? CellData.Text)?.value ?: ""
            Box(modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 24.dp).combinedClickable(onClick = { if (!inSelectionMode) onStatusClick() }, onLongClick = { if (!inSelectionMode) onLongPress() })) {
                if (value.isBlank()) {
                    Text("—", color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), style = MaterialTheme.typography.labelSmall)
                } else {
                    val chipColor = statusAccentColor(
                        value
                    ) ?: MaterialTheme.colorScheme.outline
                    Surface(shape = RoundedCornerShape(4.dp), color = chipColor.copy(alpha = 0.15f)) {
                        Text(text = value, style = MaterialTheme.typography.labelSmall, color = chipColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
        }

        ColumnType.NOTES -> {
            // model allows multiple linked notes but the cell only shows one, so just take the first
            val value = (cell as? CellData.NoteRelation)?.noteIds?.firstOrNull() ?: ""
            val reactiveNote = allLinkableNotes.find { it.noteId == value }
            var noteTitle by remember(value) { mutableStateOf("Loading...") }

            LaunchedEffect(value, reactiveNote) {
                if (value.isNotBlank()) {
                    if (reactiveNote != null) {
                        noteTitle = reactiveNote.title.ifBlank { "Untitled Note" }
                    } else {
                        val title = onGetNoteTitle(value)
                        noteTitle = title.ifBlank { "Untitled Note" }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 24.dp)
                    .combinedClickable(
                        onClick = { if (!inSelectionMode) onNoteClick() },
                        onLongClick = { if (!inSelectionMode) onLongPress() }
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(Res.drawable.plus), null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                        Spacer(Modifier.width(4.dp))
                        Text("New Note", color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Icon(painterResource(Res.drawable.file_text), null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = noteTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = cellWidth - 45.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IsolatedTableCellTextField(
    modifier: Modifier = Modifier,
    initialText: String,
    columnType: ColumnType,
    inSelectionMode: Boolean,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onNoteLinkClick: (String) -> Unit = {},
    allLinkableNotes: List<NoteMetadataEntity>,
    onCreateLinkedNote: (String) -> String,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None
) {
    var tfv by remember { mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length))) }
    var lastSentText by remember { mutableStateOf(initialText) }

    var mentionQuery by remember { mutableStateOf<String?>(null) }
    var mentionAnchorRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var mentionStartIndex by remember { mutableIntStateOf(-1) }
    var isFocused by remember { mutableStateOf(false) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val validNoteIds = remember(allLinkableNotes) { allLinkableNotes.map { it.noteId }.toSet() }

    LaunchedEffect(initialText) {
        if (tfv.text != initialText && initialText != lastSentText) {
            val safeStart = tfv.selection.start.coerceAtMost(initialText.length)
            val safeEnd = tfv.selection.end.coerceAtMost(initialText.length)
            tfv = tfv.copy(text = initialText, selection = TextRange(safeStart, safeEnd))
        }
    }

    LaunchedEffect(tfv.text) {
        if (tfv.text != initialText) {
            if (mentionQuery == null) delay(400L.milliseconds)
            lastSentText = tfv.text
            onValueChange(tfv.text)
        }
    }

    val isSingleLine = columnType != ColumnType.TEXT

    Box(modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = tfv,
            onValueChange = { newValue ->
                val newText = newValue.text
                val cursor = newValue.selection.start

                if (cursor > 0 && cursor <= newText.length) {
                    val textUpToCursor = newText.substring(0, cursor)
                    val lastAt = textUpToCursor.lastIndexOf('@')
                    val isValidAt = lastAt != -1 && (lastAt == 0 || textUpToCursor[lastAt - 1] == ' ' || textUpToCursor[lastAt - 1] == '\n')

                    if (isValidAt && !textUpToCursor.substring(lastAt).contains(" ")) {
                        mentionQuery = textUpToCursor.substring(lastAt + 1)
                        mentionStartIndex = lastAt
                    } else {
                        mentionQuery = null
                        mentionStartIndex = -1
                    }
                } else {
                    mentionQuery = null
                    mentionStartIndex = -1
                }

                tfv = newValue
            },
            onTextLayout = { result ->
                textLayoutResult = result
                if (mentionStartIndex != -1) {
                    val transformedText = visualTransformation.filter(androidx.compose.ui.text.AnnotatedString(tfv.text))
                    val mappedIndex = transformedText.offsetMapping.originalToTransformed(mentionStartIndex)
                    val safeMappedIndex = mappedIndex.coerceIn(0, transformedText.text.length)
                    mentionAnchorRect = result.getCursorRect(safeMappedIndex)
                }
            },
            enabled = !inSelectionMode,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if ((columnType == ColumnType.EMAIL || columnType == ColumnType.PHONE || columnType == ColumnType.URL) && tfv.text.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textDecoration = if ((columnType == ColumnType.EMAIL || columnType == ColumnType.PHONE || columnType == ColumnType.URL) && tfv.text.isNotBlank()) TextDecoration.Underline else TextDecoration.None
            ),
            visualTransformation = visualTransformation,
            keyboardOptions = when (columnType) {
                ColumnType.NUMBER, ColumnType.MONEY -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
                ColumnType.PHONE -> KeyboardOptions(keyboardType = KeyboardType.Phone)
                ColumnType.EMAIL -> KeyboardOptions(keyboardType = KeyboardType.Email)
                ColumnType.URL -> KeyboardOptions(keyboardType = KeyboardType.Uri)
                else -> KeyboardOptions.Default
            },
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = isSingleLine,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged {
                    isFocused = it.isFocused
                    onFocusChanged(it.isFocused)
                }
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Backspace && event.type == KeyEventType.KeyDown) {
                        val cursor = tfv.selection.start
                        if (cursor > 0 && tfv.selection.collapsed) {
                            val textBeforeCursor = tfv.text.substring(0, cursor)
                            val match = """\[([^\]]+)\]\(inly://note/([^)]+)\)$""".toRegex().find(textBeforeCursor)

                            if (match != null) {
                                val textBeforeLink = textBeforeCursor.substring(0, match.range.first)
                                val textAfterCursor = tfv.text.substring(cursor)
                                val newText = textBeforeLink + textAfterCursor

                                tfv = tfv.copy(text = newText, selection = TextRange(textBeforeLink.length))
                                onValueChange(newText)
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                    false
                }
        )

        // overlay so tapping a "@Title" link navigates
        if (columnType == ColumnType.TEXT && !isFocused && !inSelectionMode) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(tfv.text) {
                        detectTapGestures(
                            onTap = { pos ->
                                var linkTapped = false
                                textLayoutResult?.let { layoutResult ->
                                    val offset = layoutResult.getOffsetForPosition(pos)
                                    val start = maxOf(0, offset - 1)
                                    val end = minOf(layoutResult.layoutInput.text.length, offset + 1)

                                    val annotations = layoutResult.layoutInput.text.getStringAnnotations("NOTE_LINK", start, end)
                                    if (annotations.isNotEmpty()) {
                                        val noteId = annotations.first().item
                                        if (validNoteIds.contains(noteId)) {
                                            linkTapped = true
                                            onNoteLinkClick(noteId)
                                        }
                                    }
                                }
                                if (!linkTapped) {
                                    focusRequester.requestFocus()
                                }
                            }
                        )
                    }
            )
        }

        val currentQuery = mentionQuery
        if (currentQuery != null) {
            val filteredNotes = allLinkableNotes.filter {
                it.title.contains(currentQuery, ignoreCase = true)
            }

            Box(
                modifier = Modifier
                    .offset(
                        x = with(androidx.compose.ui.platform.LocalDensity.current) { mentionAnchorRect.left.toDp() },
                        y = with(androidx.compose.ui.platform.LocalDensity.current) { mentionAnchorRect.top.toDp() }
                    )
                    .size(
                        width = 1.dp,
                        height = with(androidx.compose.ui.platform.LocalDensity.current) { mentionAnchorRect.height.toDp() }
                    )
            ) {
                val positionProvider = remember {
                    object : androidx.compose.ui.window.PopupPositionProvider {
                        override fun calculatePosition(
                            anchorBounds: androidx.compose.ui.unit.IntRect,
                            windowSize: androidx.compose.ui.unit.IntSize,
                            layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                            popupContentSize: androidx.compose.ui.unit.IntSize
                        ): androidx.compose.ui.unit.IntOffset {
                            var y = anchorBounds.bottom + 8
                            if (y + popupContentSize.height > windowSize.height - 16) {
                                y = anchorBounds.top - popupContentSize.height - 8
                            }
                            var x = anchorBounds.left
                            if (x + popupContentSize.width > windowSize.width - 16) {
                                x = windowSize.width - popupContentSize.width - 16
                            }
                            return androidx.compose.ui.unit.IntOffset(x, 0.coerceAtLeast(y))
                        }
                    }
                }

                androidx.compose.ui.window.Popup(
                    popupPositionProvider = positionProvider,
                    properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .width(260.dp)
                            .heightIn(max = 300.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp).verticalScroll(rememberScrollState())) {
                            Text(
                                text = "LINK TO NOTE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )

                            filteredNotes.forEach { note ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val cursor = tfv.selection.start.coerceIn(0, tfv.text.length)
                                            val textUpToCursor = tfv.text.substring(0, cursor)
                                            val lastAt = textUpToCursor.lastIndexOf('@')

                                            if (lastAt != -1) {
                                                val safeTitle = note.title.replace("[", "").replace("]", "").ifEmpty { "Untitled" }
                                                val markdownLink = "[$safeTitle](inly://note/${note.noteId}) "

                                                val textBefore = tfv.text.substring(0, lastAt)
                                                val textAfter = tfv.text.substring(cursor)

                                                val newText = textBefore + markdownLink + textAfter
                                                val newCursor = textBefore.length + markdownLink.length

                                                tfv = tfv.copy(
                                                    text = newText,
                                                    selection = TextRange(newCursor),
                                                    composition = null
                                                )
                                                onValueChange(newText)
                                            }
                                            mentionQuery = null
                                            mentionStartIndex = -1
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painterResource(Res.drawable.list_sort_descending),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = note.title.ifEmpty { "Untitled" },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (currentQuery.isNotBlank()) {
                                if (filteredNotes.isNotEmpty()) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val cursor = tfv.selection.start.coerceIn(0, tfv.text.length)
                                            val textUpToCursor = tfv.text.substring(0, cursor)
                                            val lastAt = textUpToCursor.lastIndexOf('@')

                                            if (lastAt != -1) {
                                                val cleanTitle = currentQuery.replace("[", "").replace("]", "").trim()
                                                val safeTitle = cleanTitle.ifEmpty { "Untitled" }

                                                val newNoteId = onCreateLinkedNote(safeTitle)
                                                val markdownLink = "[$safeTitle](inly://note/$newNoteId) "

                                                val textBefore = tfv.text.substring(0, lastAt)
                                                val textAfter = tfv.text.substring(cursor)

                                                val newText = textBefore + markdownLink + textAfter
                                                val newCursor = textBefore.length + markdownLink.length

                                                tfv = tfv.copy(
                                                    text = newText,
                                                    selection = TextRange(newCursor),
                                                    composition = null
                                                )
                                                onValueChange(newText)
                                            }
                                            mentionQuery = null
                                            mentionStartIndex = -1
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(painterResource(Res.drawable.plus), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("New \"$currentQuery\" note", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            } else if (filteredNotes.isEmpty()) {
                                Text(
                                    text = "Start typing to search...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).padding(bottom = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}