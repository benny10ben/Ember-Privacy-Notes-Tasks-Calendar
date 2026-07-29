package com.ben.inly.presentation.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ben.inly.domain.util.isDesktopPlatform
import androidx.compose.ui.graphics.painter.Painter
import com.ben.inly.presentation.shared.stableStatusBarsPadding
import com.ben.inly.ui.theme.LocalAppIsDark
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private val BottomSheetShape = RoundedCornerShape(topEnd = 26.dp, topStart = 26.dp)
private val FloatingDialogShape = RoundedCornerShape(16.dp)
private val SheetHorizontalPadding = 20.dp

class InlyBottomSheetAction(
    val icon: Painter,
    val contentDescription: String,
    val tint: Color? = null,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InlyBottomSheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    applyNavPadding: Boolean = false,
    headerAction: InlyBottomSheetAction? = null,
    content: @Composable ColumnScope.(closeAnd: (() -> Unit) -> Unit) -> Unit
) {
    if (!expanded) return

    if (isDesktopPlatform) {
        InlyFloatingDialog(
            onDismiss = onDismiss,
            title = title,
            subtitle = subtitle,
            headerAction = headerAction,
            content = content
        )
    } else {
        InlyModalBottomSheet(
            onDismiss = onDismiss,
            title = title,
            subtitle = subtitle,
            applyNavPadding = applyNavPadding,
            headerAction = headerAction,
            content = content
        )
    }
}

@Composable
private fun InlyFloatingDialog(
    onDismiss: () -> Unit,
    title: String?,
    subtitle: String?,
    headerAction: InlyBottomSheetAction? = null,
    content: @Composable ColumnScope.(closeAnd: (() -> Unit) -> Unit) -> Unit
) {
    fun closeAnd(action: () -> Unit) {
        action()
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.9f)
                .clip(FloatingDialogShape)
                .background(
                    if (LocalAppIsDark.current) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.background
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                if (title != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                                .padding(horizontal = SheetHorizontalPadding, vertical = 8.dp)
                        )
                        if (headerAction != null) {
                            Box(modifier = Modifier.padding(end = 4.dp)) {
                                TopBarIconButton(
                                    icon = headerAction.icon,
                                    contentDescription = headerAction.contentDescription,
                                    bgColor = Color.Transparent,
                                    tint = headerAction.tint ?: MaterialTheme.colorScheme.onSurface,
                                    onClick = headerAction.onClick
                                )
                            }
                        }
                        Box(modifier = Modifier.padding(end = SheetHorizontalPadding)) {
                            TopBarIconButton(
                                icon = Icons.Default.Close,
                                contentDescription = "Close",
                                bgColor = Color.Transparent,
                                tint = MaterialTheme.colorScheme.onSurface,
                                onClick = onDismiss
                            )
                        }
                    }
                }

                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = SheetHorizontalPadding).padding(bottom = 16.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = SheetHorizontalPadding)
                        .padding(bottom = 20.dp)
                ) {
                    content { action -> closeAnd(action) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InlyModalBottomSheet(
    onDismiss: () -> Unit,
    title: String?,
    subtitle: String?,
    applyNavPadding: Boolean,
    headerAction: InlyBottomSheetAction? = null,
    content: @Composable ColumnScope.(closeAnd: (() -> Unit) -> Unit) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun closeAnd(action: () -> Unit) {
        coroutineScope.launch {
            try {
                kotlinx.coroutines.withTimeoutOrNull(250.milliseconds) {
                    sheetState.hide()
                }
            } finally {
                action()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets(0) },
        containerColor = Color.Transparent,
        shape = RoundedCornerShape(0.dp),
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = null,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = false
        )
    ) {
        KmpBackHandler(enabled = true) {
            coroutineScope.launch {
                try {
                    kotlinx.coroutines.withTimeoutOrNull(250.milliseconds) {
                        sheetState.hide()
                    }
                } finally {
                    onDismiss()
                }
            }
        }

        // card
        Box(
            modifier = Modifier
                .stableStatusBarsPadding()
                .fillMaxWidth()
                .clip(BottomSheetShape)
                .background(
                    if (LocalAppIsDark.current) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.background
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .then(if (applyNavPadding) Modifier.padding(bottom = 16.dp) else Modifier)
            ) {
                if (title != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 26.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                                .padding(horizontal = SheetHorizontalPadding, vertical = 8.dp)
                        )
                        if (headerAction != null) {
                            Box(modifier = Modifier.padding(end = 4.dp)) {
                                TopBarIconButton(
                                    icon = headerAction.icon,
                                    contentDescription = headerAction.contentDescription,
                                    bgColor = Color.Transparent,
                                    tint = headerAction.tint ?: MaterialTheme.colorScheme.onSurface,
                                    onClick = headerAction.onClick
                                )
                            }
                        }
                        Box(modifier = Modifier.padding(end = SheetHorizontalPadding)) {
                            TopBarIconButton(
                                icon = Icons.Default.Close,
                                contentDescription = "Close",
                                bgColor = Color.Transparent,
                                tint = MaterialTheme.colorScheme.onSurface,
                                onClick = onDismiss
                            )
                        }
                    }
                }

                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = SheetHorizontalPadding).padding(bottom = 8.dp)
                    )
                }

                if (title != null || subtitle != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = SheetHorizontalPadding, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = SheetHorizontalPadding)
                ) {
                    content { action -> closeAnd(action) }
                }
            }
        }
    }
}