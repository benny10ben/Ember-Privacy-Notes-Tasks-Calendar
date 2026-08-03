package com.ben.inly.presentation.shared.editor.blockViews

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ben.inly.domain.model.TableBlock
import com.ben.inly.domain.model.TableCellContentType
import com.ben.inly.domain.model.TableCellStyle
import com.ben.inly.domain.model.TextAlignment
import com.ben.inly.domain.util.isDesktopPlatform
import com.ben.inly.presentation.shared.components.InlyBottomSheet
import com.ben.inly.presentation.shared.components.InlyDesktopMenu
import com.ben.inly.presentation.shared.components.rememberKeyboardHandoff
import com.ben.inly.presentation.shared.editor.blockViews.databaseBlockView.DbOptionRow
import com.ben.inly.presentation.shared.editor.mouseScrollable
import inly.app.generated.resources.Res
import inly.app.generated.resources.arrow_down
import inly.app.generated.resources.arrow_left
import inly.app.generated.resources.arrow_right
import inly.app.generated.resources.arrow_up
import inly.app.generated.resources.move_left
import inly.app.generated.resources.move_right
import inly.app.generated.resources.plus
import inly.app.generated.resources.trash
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds

private val CellWidth = 140.dp
private val CellMinHeight = 44.dp
private val GutterSize = 44.dp
private val SidePadding = 18.dp

private val TableStylePalette = listOf(
    "#FFADAD", "#FFD6A5", "#FDFFB6", "#CAFFBF",
    "#9BF6FF", "#A0C4FF", "#BDB2FF", "#FFC6FF"
)

private fun String.toColorOrNull(): Color? = try {
    Color(this.removePrefix("#").toLong(16) or 0xFF000000)
} catch (_: Exception) {
    null
}

private enum class TableStyleScope { CELL, ROW, COLUMN }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TableBlockView(
    block: TableBlock,
    inSelectionMode: Boolean,
    onUpdateTable: (rows: List<List<String>>) -> Unit,
    onUpdateTableStyle: (
        cellStyles: Map<String, TableCellStyle>,
        rowStyles: Map<String, TableCellStyle>,
        columnStyles: Map<String, TableCellStyle>
    ) -> Unit
) {
    val rows = block.rows
    val columnCount = rows.firstOrNull()?.size ?: 0
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
    val borderColor1 = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val keyboardHandoff = rememberKeyboardHandoff()

    var activeRowIndex by remember { mutableStateOf(-1) }
    var activeColIndex by remember { mutableStateOf(-1) }
    var showCellActions by remember { mutableStateOf(false) }
    var styleScope by remember { mutableStateOf<TableStyleScope?>(null) }

    fun closeMenu() {
        showCellActions = false
        styleScope = null
        activeRowIndex = -1
        activeColIndex = -1
    }

    fun openCellActions(rowIndex: Int, colIndex: Int) {
        activeRowIndex = rowIndex
        activeColIndex = colIndex
        styleScope = null
        keyboardHandoff.run { showCellActions = true }
    }

    // Deliberately closes the Cell Actions sheet/menu and opens the Style one as two distinct,
    // sequential animations rather than swapping content within a single sheet instance. Routes
    // the actual open through keyboardHandoff too - dismissing the first sheet can transiently
    // hand focus back to the cell's text field, and if that pops the IME back up mid-transition,
    // this waits it back out instead of racing the style sheet's own open animation against it.
    fun openStyleFromCellActions(scope: TableStyleScope) {
        showCellActions = false
        coroutineScope.launch {
            delay(250.milliseconds)
            keyboardHandoff.run { styleScope = scope }
        }
    }

    fun updateCell(rowIndex: Int, colIndex: Int, newValue: String) {
        onUpdateTable(
            rows.mapIndexed { r, row ->
                if (r != rowIndex) row else row.mapIndexed { c, cell -> if (c == colIndex) newValue else cell }
            }
        )
    }

    fun addRow() = onUpdateTable(rows + listOf(List(columnCount) { "" }))
    fun addColumn() = onUpdateTable(rows.map { it + "" })
    fun insertRowAt(index: Int) = onUpdateTable(rows.toMutableList().apply { add(index, List(columnCount) { "" }) })
    fun insertColumnAt(index: Int) = onUpdateTable(rows.map { row -> row.toMutableList().apply { add(index, "") } })

    fun deleteRowAt(index: Int) {
        if (rows.size <= 1) return
        onUpdateTable(rows.filterIndexed { i, _ -> i != index })
    }

    fun deleteColumnAt(index: Int) {
        if (columnCount <= 1) return
        onUpdateTable(rows.map { row -> row.filterIndexed { i, _ -> i != index } })
    }

    fun moveRow(index: Int, targetIndex: Int) {
        if (targetIndex !in rows.indices) return
        onUpdateTable(
            rows.toMutableList().apply {
                val tmp = this[index]
                this[index] = this[targetIndex]
                this[targetIndex] = tmp
            }
        )

        val newRowStyles = block.rowStyles.toMutableMap()
        val a = newRowStyles.remove("$index")
        val b = newRowStyles.remove("$targetIndex")
        if (b != null) newRowStyles["$index"] = b
        if (a != null) newRowStyles["$targetIndex"] = a

        val newCellStyles = block.cellStyles.toMutableMap()
        for (c in 0 until columnCount) {
            val keyA = "$index:$c"
            val keyB = "$targetIndex:$c"
            val sa = newCellStyles.remove(keyA)
            val sb = newCellStyles.remove(keyB)
            if (sb != null) newCellStyles[keyA] = sb
            if (sa != null) newCellStyles[keyB] = sa
        }
        onUpdateTableStyle(newCellStyles, newRowStyles, block.columnStyles)
    }

    fun moveColumn(index: Int, targetIndex: Int) {
        if (targetIndex !in 0 until columnCount) return
        onUpdateTable(
            rows.map { row ->
                row.toMutableList().apply {
                    val tmp = this[index]
                    this[index] = this[targetIndex]
                    this[targetIndex] = tmp
                }
            }
        )

        val newColumnStyles = block.columnStyles.toMutableMap()
        val a = newColumnStyles.remove("$index")
        val b = newColumnStyles.remove("$targetIndex")
        if (b != null) newColumnStyles["$index"] = b
        if (a != null) newColumnStyles["$targetIndex"] = a

        val newCellStyles = block.cellStyles.toMutableMap()
        for (r in rows.indices) {
            val keyA = "$r:$index"
            val keyB = "$r:$targetIndex"
            val sa = newCellStyles.remove(keyA)
            val sb = newCellStyles.remove(keyB)
            if (sb != null) newCellStyles[keyA] = sb
            if (sa != null) newCellStyles[keyB] = sa
        }
        onUpdateTableStyle(newCellStyles, block.rowStyles, newColumnStyles)
    }

    fun styleKeyFor(scope: TableStyleScope): String = when (scope) {
        TableStyleScope.CELL -> "$activeRowIndex:$activeColIndex"
        TableStyleScope.ROW -> "$activeRowIndex"
        TableStyleScope.COLUMN -> "$activeColIndex"
    }

    fun currentStyleFor(scope: TableStyleScope): TableCellStyle {
        val key = styleKeyFor(scope)
        return when (scope) {
            TableStyleScope.CELL -> block.cellStyles[key]
            TableStyleScope.ROW -> block.rowStyles[key]
            TableStyleScope.COLUMN -> block.columnStyles[key]
        } ?: TableCellStyle()
    }

    fun applyStyle(scope: TableStyleScope, newStyle: TableCellStyle) {
        val key = styleKeyFor(scope)
        when (scope) {
            TableStyleScope.CELL -> onUpdateTableStyle(block.cellStyles + (key to newStyle), block.rowStyles, block.columnStyles)
            TableStyleScope.ROW -> onUpdateTableStyle(block.cellStyles, block.rowStyles + (key to newStyle), block.columnStyles)
            TableStyleScope.COLUMN -> onUpdateTableStyle(block.cellStyles, block.rowStyles, block.columnStyles + (key to newStyle))
        }
    }

    fun resetStyle(scope: TableStyleScope) {
        val key = styleKeyFor(scope)
        when (scope) {
            TableStyleScope.CELL -> onUpdateTableStyle(block.cellStyles - key, block.rowStyles, block.columnStyles)
            TableStyleScope.ROW -> onUpdateTableStyle(block.cellStyles, block.rowStyles - key, block.columnStyles)
            TableStyleScope.COLUMN -> onUpdateTableStyle(block.cellStyles, block.rowStyles, block.columnStyles - key)
        }
    }

    fun effectiveStyle(rowIndex: Int, colIndex: Int): TableCellStyle {
        return block.cellStyles["$rowIndex:$colIndex"]
            ?: block.rowStyles["$rowIndex"]
            ?: block.columnStyles["$colIndex"]
            ?: TableCellStyle()
    }

    val styleSheetTitle = when (styleScope) {
        TableStyleScope.CELL -> "Style Cell"
        TableStyleScope.ROW -> "Style Row"
        TableStyleScope.COLUMN -> "Style Column"
        null -> ""
    }

    val cellActionsBody = @Composable {
        val rowIndex = activeRowIndex
        val colIndex = activeColIndex
        if (rowIndex in rows.indices && colIndex in 0 until columnCount) {
            DbOptionRow(painterResource(Res.drawable.arrow_up), "Insert Row Above") {
                insertRowAt(rowIndex); closeMenu()
            }
            DbOptionRow(painterResource(Res.drawable.arrow_down), "Insert Row Below") {
                insertRowAt(rowIndex + 1); closeMenu()
            }
            DbOptionRow(painterResource(Res.drawable.arrow_left), "Insert Column Left") {
                insertColumnAt(colIndex); closeMenu()
            }
            DbOptionRow(painterResource(Res.drawable.arrow_right), "Insert Column Right") {
                insertColumnAt(colIndex + 1); closeMenu()
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )

            if (rowIndex > 0) {
                DbOptionRow(rememberVectorPainter(Icons.Default.ArrowUpward), "Move Row Up") {
                    moveRow(rowIndex, rowIndex - 1); closeMenu()
                }
            }
            if (rowIndex < rows.lastIndex) {
                DbOptionRow(rememberVectorPainter(Icons.Default.ArrowDownward), "Move Row Down") {
                    moveRow(rowIndex, rowIndex + 1); closeMenu()
                }
            }
            if (colIndex > 0) {
                DbOptionRow(painterResource(Res.drawable.move_left), "Move Column Left") {
                    moveColumn(colIndex, colIndex - 1); closeMenu()
                }
            }
            if (colIndex < columnCount - 1) {
                DbOptionRow(painterResource(Res.drawable.move_right), "Move Column Right") {
                    moveColumn(colIndex, colIndex + 1); closeMenu()
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )

            val paletteIcon = rememberVectorPainter(Icons.Default.Palette)
            DbOptionRow(paletteIcon, "Style Cell") { openStyleFromCellActions(TableStyleScope.CELL) }
            DbOptionRow(paletteIcon, "Style Row") { openStyleFromCellActions(TableStyleScope.ROW) }
            DbOptionRow(paletteIcon, "Style Column") { openStyleFromCellActions(TableStyleScope.COLUMN) }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )

            DbOptionRow(painterResource(Res.drawable.trash), "Delete Row", MaterialTheme.colorScheme.error) {
                deleteRowAt(rowIndex); closeMenu()
            }
            DbOptionRow(painterResource(Res.drawable.trash), "Delete Column", MaterialTheme.colorScheme.error) {
                deleteColumnAt(colIndex); closeMenu()
            }
        }
    }

    val styleSheetBody = @Composable {
        val scope = styleScope
        if (scope != null) {
            TableStyleSheetContent(
                style = currentStyleFor(scope),
                onStyleChange = { applyStyle(scope, it) },
                onReset = { resetStyle(scope); closeMenu() }
            )
        }
    }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .mouseScrollable(scrollState)
        ) {
            Column(modifier = Modifier.padding(horizontal = SidePadding)) {
                Surface(
                    shape = RoundedCornerShape(0.dp),
                    color = Color.Transparent,
                    border = BorderStroke(0.6.dp, borderColor1)
                ) {
                    Column {
                        rows.forEachIndexed { rowIndex, row ->
                            Row(modifier = Modifier.height(IntrinsicSize.Max).defaultMinSize(minHeight = CellMinHeight)) {
                                row.forEachIndexed { columnIndex, cellValue ->
                                    val isActiveCell = activeRowIndex == rowIndex && activeColIndex == columnIndex
                                    val isHighlighted = isActiveCell && (showCellActions || styleScope != null)
                                    val cellStyle = effectiveStyle(rowIndex, columnIndex)

                                    Box {
                                        TableGridCell(
                                            value = cellValue,
                                            style = cellStyle,
                                            inSelectionMode = inSelectionMode,
                                            isHighlighted = isHighlighted,
                                            borderColor = borderColor,
                                            onValueChange = { updateCell(rowIndex, columnIndex, it) },
                                            onLongPress = { openCellActions(rowIndex, columnIndex) }
                                        )

                                        if (isDesktopPlatform) {
                                            InlyDesktopMenu(
                                                expanded = isActiveCell && showCellActions,
                                                onDismissRequest = { closeMenu() }
                                            ) {
                                                TableMenuPopupContent(title = "Cell Actions") { cellActionsBody() }
                                            }
                                            InlyDesktopMenu(
                                                expanded = isActiveCell && styleScope != null,
                                                onDismissRequest = { closeMenu() }
                                            ) {
                                                TableMenuPopupContent(title = styleSheetTitle) { styleSheetBody() }
                                            }
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .width(GutterSize)
                                        .fillMaxHeight()
                                        .defaultMinSize(minHeight = CellMinHeight)
                                        .drawBehind {
                                            val px = 0.5.dp.toPx()
                                            drawLine(borderColor, Offset(0f, size.height), Offset(size.width, size.height), px)
                                        }
                                        .then(
                                            if (rowIndex == 0 && !inSelectionMode) Modifier.clickable { addColumn() } else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (rowIndex == 0 && !inSelectionMode) {
                                        Icon(painterResource(Res.drawable.plus), contentDescription = "Add column", modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }

                if (!inSelectionMode) {
                    Row(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { addRow() }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(painterResource(Res.drawable.plus), contentDescription = "Add row", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(7.dp))
                        Text(text = "New Row", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }

    if (!isDesktopPlatform) {
        InlyBottomSheet(
            expanded = showCellActions,
            onDismiss = { closeMenu() },
            title = "Cell Actions"
        ) { _ ->
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                cellActionsBody()
            }
        }
        InlyBottomSheet(
            expanded = styleScope != null,
            onDismiss = { closeMenu() },
            title = styleSheetTitle
        ) { _ ->
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                styleSheetBody()
            }
        }
    }
}

@Composable
private fun TableMenuPopupContent(title: String, content: @Composable () -> Unit) {
    Box(modifier = Modifier.widthIn(min = 240.dp, max = 300.dp).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 10.dp, top = 4.dp).padding(horizontal = 12.dp)
            )
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TableGridCell(
    value: String,
    style: TableCellStyle,
    inSelectionMode: Boolean,
    isHighlighted: Boolean,
    borderColor: Color,
    onValueChange: (String) -> Unit,
    onLongPress: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val backgroundColor = style.backgroundColorHex?.toColorOrNull() ?: Color.Transparent
    val textColor = style.textColorHex?.toColorOrNull() ?: MaterialTheme.colorScheme.onBackground
    val textDecoration = when {
        style.isStrikeThrough && style.isUnderlined -> TextDecoration.LineThrough + TextDecoration.Underline
        style.isStrikeThrough -> TextDecoration.LineThrough
        style.isUnderlined -> TextDecoration.Underline
        else -> TextDecoration.None
    }
    val textAlign = when (style.alignment) {
        TextAlignment.LEFT -> TextAlign.Left
        TextAlignment.RIGHT -> TextAlign.Right
        TextAlignment.CENTER -> TextAlign.Center
        else -> TextAlign.Start
    }

    Box(
        modifier = Modifier
            .width(CellWidth)
            .fillMaxHeight()
            .defaultMinSize(minHeight = CellMinHeight)
            .background(backgroundColor)
            .drawBehind {
                val px = 0.5.dp.toPx()
                drawLine(borderColor, Offset(size.width, 0f), Offset(size.width, size.height), px)
                drawLine(borderColor, Offset(0f, size.height), Offset(size.width, size.height), px)
            }
            .then(if (isHighlighted) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary) else Modifier)
            .pointerInput(inSelectionMode) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var isLongPress = false
                    try {
                        withTimeout(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation()
                        }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        isLongPress = true
                        currentEvent.changes.forEach { it.consume() }
                    }
                    if (isLongPress && !inSelectionMode) {
                        onLongPress()
                    }
                }
            }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = !inSelectionMode,
            textStyle = TextStyle(
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                fontFamily = if (style.isCode) FontFamily.Monospace else FontFamily.Default,
                fontWeight = if (style.isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (style.isItalic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = textDecoration,
                textAlign = textAlign,
                color = textColor
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
        )

        // Raw touches only reach the BasicTextField once it's already focused - while unfocused,
        // this overlay claims the tap so the field's own long-press-to-select gesture (which would
        // otherwise fire alongside the gesture above and pop the system copy/paste/autofill toolbar)
        // never gets a chance to start.
        if (!isFocused && !inSelectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { focusRequester.requestFocus() })
                    }
            )
        }
    }
}

@Composable
private fun TableStyleSheetContent(
    style: TableCellStyle,
    onStyleChange: (TableCellStyle) -> Unit,
    onReset: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        StyleSectionLabel("Background Color")
        ColorSwatchRow(selectedHex = style.backgroundColorHex) { onStyleChange(style.copy(backgroundColorHex = it)) }

        StyleSectionLabel("Text Color")
        ColorSwatchRow(selectedHex = style.textColorHex) { onStyleChange(style.copy(textColorHex = it)) }

        StyleSectionLabel("Format")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StyleToggleButton(Icons.Default.FormatBold, style.isBold) { onStyleChange(style.copy(isBold = !style.isBold)) }
            StyleToggleButton(Icons.Default.FormatItalic, style.isItalic) { onStyleChange(style.copy(isItalic = !style.isItalic)) }
            StyleToggleButton(Icons.Default.FormatUnderlined, style.isUnderlined) { onStyleChange(style.copy(isUnderlined = !style.isUnderlined)) }
            StyleToggleButton(Icons.Default.StrikethroughS, style.isStrikeThrough) { onStyleChange(style.copy(isStrikeThrough = !style.isStrikeThrough)) }
            StyleToggleButton(Icons.Default.Code, style.isCode) { onStyleChange(style.copy(isCode = !style.isCode)) }
        }

        StyleSectionLabel("Content Type")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StyleToggleButton(Icons.Default.Link, style.contentType == TableCellContentType.LINK) {
                onStyleChange(style.copy(contentType = if (style.contentType == TableCellContentType.LINK) TableCellContentType.NONE else TableCellContentType.LINK))
            }
            StyleToggleButton(Icons.Default.Phone, style.contentType == TableCellContentType.PHONE) {
                onStyleChange(style.copy(contentType = if (style.contentType == TableCellContentType.PHONE) TableCellContentType.NONE else TableCellContentType.PHONE))
            }
            StyleToggleButton(Icons.Default.Email, style.contentType == TableCellContentType.EMAIL) {
                onStyleChange(style.copy(contentType = if (style.contentType == TableCellContentType.EMAIL) TableCellContentType.NONE else TableCellContentType.EMAIL))
            }
        }

        StyleSectionLabel("Alignment")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StyleToggleButton(Icons.Default.FormatAlignLeft, style.alignment == TextAlignment.LEFT) {
                onStyleChange(style.copy(alignment = if (style.alignment == TextAlignment.LEFT) null else TextAlignment.LEFT))
            }
            StyleToggleButton(Icons.Default.FormatAlignCenter, style.alignment == TextAlignment.CENTER) {
                onStyleChange(style.copy(alignment = if (style.alignment == TextAlignment.CENTER) null else TextAlignment.CENTER))
            }
            StyleToggleButton(Icons.Default.FormatAlignRight, style.alignment == TextAlignment.RIGHT) {
                onStyleChange(style.copy(alignment = if (style.alignment == TextAlignment.RIGHT) null else TextAlignment.RIGHT))
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )

        DbOptionRow(painterResource(Res.drawable.trash), "Reset Style", MaterialTheme.colorScheme.error) {
            onReset()
        }
    }
}

@Composable
private fun StyleSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
    )
}

@Composable
private fun ColorSwatchRow(selectedHex: String?, onSelect: (String?) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape)
                .clickable { onSelect(null) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "No color",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        TableStylePalette.forEach { hex ->
            val isSelected = hex == selectedHex
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(hex.toColorOrNull() ?: Color.Gray, CircleShape)
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        shape = CircleShape
                    )
                    .clickable { onSelect(hex) }
            )
        }
    }
}

@Composable
private fun StyleToggleButton(icon: ImageVector, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
