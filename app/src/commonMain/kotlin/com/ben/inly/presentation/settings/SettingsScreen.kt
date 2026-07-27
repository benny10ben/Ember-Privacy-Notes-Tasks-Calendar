package com.ben.inly.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ben.inly.domain.util.isDesktopPlatform
import com.ben.inly.presentation.shared.stableStatusBarsPadding
import com.ben.inly.presentation.shared.components.InlyBottomSheet
import com.ben.inly.presentation.shared.components.InlyButtonPrimary
import com.ben.inly.presentation.shared.components.InlyButtonSecondary
import com.ben.inly.presentation.shared.components.TopBarIconButton
import com.ben.inly.ui.theme.FontStylePreference
import com.ben.inly.ui.theme.fontFamilyFor
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import inly.app.generated.resources.Res
import inly.app.generated.resources.badge_plus
import inly.app.generated.resources.badge_question_mark
import inly.app.generated.resources.calendar_clock
import inly.app.generated.resources.chevron_left
import inly.app.generated.resources.chevron_right
import inly.app.generated.resources.file_down
import inly.app.generated.resources.folder_input
import inly.app.generated.resources.folder_sync
import inly.app.generated.resources.info
import inly.app.generated.resources.palette
import inly.app.generated.resources.refresh_cw
import inly.app.generated.resources.shield_alert
import inly.app.generated.resources.sidebar
import inly.app.generated.resources.timer_reset
import inly.app.generated.resources.triangle_alert
import com.ben.inly.presentation.shared.SubNoteOpenMode
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onImportClick: () -> Unit = {},
    onExportReady: (String) -> Unit = {},
    onRequestBackupFolder: () -> Unit = {},
    onNavigateToSelfHostSetup: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    var showImportExportSheet by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    // Backup States
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsState()
    val backupFrequency by viewModel.backupFrequency.collectAsState()
    val backupDirectoryUri by viewModel.backupDirectoryUri.collectAsState()

    val backupTime by viewModel.backupTime.collectAsState()
    val backupDay by viewModel.backupDay.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }
    var showDayPicker by remember { mutableStateOf(false) }

    val fontSizePreference by viewModel.fontSizePreference.collectAsState()
    val fontStylePreference by viewModel.fontStylePreference.collectAsState()
    var showFontStyleSheet by remember { mutableStateOf(false) }

    val subNoteOpenMode by viewModel.subNoteOpenMode.collectAsState()
    var showSubNoteOpenModeSheet by remember { mutableStateOf(false) }

    val internalHazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    val isScrolled by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableFloatStateOf(0f) }
    val topBarHeightDp = with(density) { topBarHeightPx.toDp() }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = internalHazeState)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(top = topBarHeightDp + 8.dp, bottom = 48.dp)
        ) {
            item {
                SettingsGroup(title = "Data & Storage") {
                    SettingsActionRow(
                        icon = painterResource(Res.drawable.file_down),
                        title = "Import / Export",
                        onClick = { showImportExportSheet = true }
                    )

                    if (!com.ben.inly.domain.util.isDesktopPlatform) {
                        SettingsToggleRow(
                            icon = painterResource(Res.drawable.folder_sync),
                            title = "Automatic Backups",
                            isChecked = autoBackupEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (backupDirectoryUri == null) {
                                        onRequestBackupFolder()
                                    } else {
                                        viewModel.setAutoBackupEnabled(true)
                                    }
                                } else {
                                    viewModel.setAutoBackupEnabled(false)
                                }
                            }
                        )

                        AnimatedVisibility(
                            visible = autoBackupEnabled,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f))
                            ) {
                                SettingsDivider()

                                val frequencies = listOf("Hourly", "Daily", "Weekly")
                                frequencies.forEach { freq ->
                                    SettingsSelectionRow(
                                        title = freq,
                                        isSelected = backupFrequency == freq,
                                        onClick = {
                                            viewModel.saveBackupSchedule(
                                                freq,
                                                backupTime,
                                                backupDay
                                            )
                                        }
                                    )
                                }

                                if (backupFrequency != "Hourly") {
                                    SettingsDivider()

                                    if (backupFrequency == "Weekly") {
                                        SettingsActionRow(
                                            icon = painterResource(Res.drawable.calendar_clock),
                                            title = "Backup Day",
                                            trailingLabel = backupDay,
                                            onClick = { showDayPicker = true }
                                        )
                                    }

                                    SettingsActionRow(
                                        icon = painterResource(Res.drawable.timer_reset),
                                        title = "Backup Time",
                                        trailingLabel = backupTime,
                                        onClick = { showTimePicker = true }
                                    )
                                }

                                SettingsDivider()
                                SettingsActionRow(
                                    icon = painterResource(Res.drawable.folder_input),
                                    title = "Backup Location",
                                    trailingLabel = "Change",
                                    onClick = { onRequestBackupFolder() }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsGroup(title = "Sync & Backup") {
                    SettingsActionRow(
                        icon = painterResource(Res.drawable.refresh_cw),
                        title = "Self-Host",
                        onClick = onNavigateToSelfHostSetup
                    )
                }
            }

            item {
                SettingsGroup(title = "Appearance") {
                    SettingsActionRow(
                        icon = painterResource(Res.drawable.palette),
                        title = "Theme",
                        trailingLabel = "System",
                        onClick = {}
                    )
                    SettingsDivider()
                    SettingsFontSizeSliderRow(
                        fontSizePreference = fontSizePreference,
                        onFontSizeChange = { viewModel.setFontSizePreference(it) }
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = painterResource(Res.drawable.palette),
                        title = "Font Style",
                        trailingLabel = runCatching { FontStylePreference.valueOf(fontStylePreference) }
                            .getOrDefault(FontStylePreference.POPPINS).displayName,
                        onClick = { showFontStyleSheet = true }
                    )

                    if (isDesktopPlatform) {
                        SettingsDivider()
                        SettingsActionRow(
                            icon = painterResource(Res.drawable.sidebar),
                            title = "Subnote Opening",
                            trailingLabel = runCatching { SubNoteOpenMode.valueOf(subNoteOpenMode) }
                                .getOrDefault(SubNoteOpenMode.SIDE_PANEL).displayName,
                            onClick = { showSubNoteOpenModeSheet = true }
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "Need Help?") {
                    SettingsActionRow(
                        icon = painterResource(Res.drawable.badge_question_mark),
                        title = "FAQ",
                        onClick = {}
                    )
                    SettingsActionRow(
                        icon = painterResource(Res.drawable.badge_plus),
                        title = "What's New",
                        onClick = {}
                    )
                    SettingsActionRow(
                        icon = painterResource(Res.drawable.shield_alert),
                        title = "Privacy Policy",
                        onClick = {}
                    )
                    SettingsActionRow(
                        icon = painterResource(Res.drawable.info),
                        title = "About Inly",
                        trailingLabel = "v1.0.0",
                        onClick = {}
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.07f))
                        .clickable {}
                        .padding(horizontal = 14.dp, vertical = 15.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painterResource(Res.drawable.triangle_alert),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "Clear All Data",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            painterResource(Res.drawable.chevron_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(10f)
                .onGloballyPositioned { coordinates -> topBarHeightPx = coordinates.size.height.toFloat() }
                .then(
                    if (isScrolled) {
                        Modifier
                            .hazeEffect(state = internalHazeState, style = HazeStyle.Unspecified, block = null)
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.65f))
                    } else {
                        Modifier
                    }
                )
        ) {
            SettingsTopBar(onNavigateBack = onNavigateBack)
        }

        if (showImportExportSheet) {
            InlyBottomSheet(
                expanded = true,
                onDismiss = { showImportExportSheet = false },
                title = "Import / Export",
                subtitle = "Backup your data securely to a local file, or restore a previous backup."
            ) { closeAnd ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InlyButtonPrimary(
                        text = if (isExporting) "Preparing..." else "Export",
                        onClick = {
                            if (!isExporting) {
                                isExporting = true
                                coroutineScope.launch {
                                    try {
                                        val json = viewModel.getBackupJson()
                                        showImportExportSheet = false
                                        isExporting = false
                                        onExportReady(json)
                                    } catch (e: Exception) {
                                        isExporting = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    InlyButtonSecondary(
                        text = "Import",
                        onClick = {
                            showImportExportSheet = false
                            onImportClick()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showTimePicker) {
        // Convert "HH:mm" into a dummy timestamp so the picker initializes at the correct time
        val timeParts = backupTime.split(":")
        val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 2
        val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
        }

        com.ben.inly.presentation.shared.components.MinimalTimePickerDialog(
            expanded = showTimePicker,
            initialTimestamp = cal.timeInMillis,
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m ->
                val formattedTime = "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
                viewModel.saveBackupSchedule(backupFrequency, formattedTime, backupDay)
            }
        )
    }

    if (showDayPicker) {
        val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        var selectedIndex by remember { mutableIntStateOf(days.indexOf(backupDay).coerceAtLeast(0)) }

        val wheelContent = @Composable {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                com.ben.inly.presentation.shared.components.WheelPicker(
                    items = days,
                    selectedIndex = selectedIndex,
                    onItemSelected = { selectedIndex = it },
                    itemHeight = if (com.ben.inly.domain.util.isDesktopPlatform) 40.dp else 44.dp
                )
            }
        }

        if (com.ben.inly.domain.util.isDesktopPlatform) {
            com.ben.inly.presentation.shared.components.InlyDesktopMenu(
                expanded = showDayPicker,
                onDismissRequest = { showDayPicker = false }
            ) {
                Column(modifier = Modifier.width(280.dp).wrapContentHeight()) {
                    wheelContent()
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InlyButtonSecondary(text = "Cancel", onClick = { showDayPicker = false }, modifier = Modifier.weight(1f))
                        InlyButtonPrimary(text = "Save", onClick = { viewModel.saveBackupSchedule(backupFrequency, backupTime, days[selectedIndex]); showDayPicker = false }, modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            InlyBottomSheet(
                expanded = showDayPicker,
                onDismiss = { showDayPicker = false },
                title = "Select Backup Day"
            ) {
                wheelContent()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InlyButtonSecondary(text = "Cancel", onClick = { showDayPicker = false }, modifier = Modifier.weight(1f))
                    InlyButtonPrimary(text = "Save", onClick = { viewModel.saveBackupSchedule(backupFrequency, backupTime, days[selectedIndex]); showDayPicker = false }, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (showFontStyleSheet) {
        InlyBottomSheet(
            expanded = true,
            onDismiss = { showFontStyleSheet = false },
            title = "Font Style"
        ) {
            val selectedFontStyle = runCatching { FontStylePreference.valueOf(fontStylePreference) }
                .getOrDefault(FontStylePreference.POPPINS)

            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                FontStylePreference.entries.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setFontStylePreference(option.name)
                                showFontStyleSheet = false
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option.displayName,
                            fontFamily = fontFamilyFor(option),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )

                        if (option == selectedFontStyle) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (index != FontStylePreference.entries.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                        )
                    }
                }
            }
        }
    }

    if (showSubNoteOpenModeSheet) {
        InlyBottomSheet(
            expanded = true,
            onDismiss = { showSubNoteOpenModeSheet = false },
            title = "Subnote Opening",
            subtitle = "Choose how notes linked inside another note (subnotes) open on desktop."
        ) {
            val selectedMode = runCatching { SubNoteOpenMode.valueOf(subNoteOpenMode) }
                .getOrDefault(SubNoteOpenMode.SIDE_PANEL)

            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                SubNoteOpenMode.entries.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setSubNoteOpenMode(option.name)
                                showSubNoteOpenModeSheet = false
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )

                        if (option == selectedMode) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (index != SubNoteOpenMode.entries.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSelectionRow(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .padding(start = 50.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SettingsTopBar(onNavigateBack: () -> Unit) {
    val defaultBgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.45f)
    val defaultContentColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isDesktopPlatform) Modifier else Modifier.stableStatusBarsPadding())
            .padding(
                top = if (isDesktopPlatform) 16.dp else 10.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = 16.dp
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        TopBarIconButton(
            icon = painterResource(Res.drawable.chevron_left),
            contentDescription = "Back",
            bgColor = defaultBgColor,
            tint = defaultContentColor,
            onClick = onNavigateBack
        )

        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleLarge,
            color = defaultContentColor,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 66.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    )
}

@Composable
fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp, bottom = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 1f),
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
fun SettingsActionRow(
    icon: Painter,
    title: String,
    trailingLabel: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        if (trailingLabel != null) {
            Text(
                text = trailingLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        Icon(
            painterResource(Res.drawable.chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun SettingsToggleRow(
    icon: Painter,
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

private val FontSizeSteps = listOf(
    com.ben.inly.ui.theme.FontSizePreference.SMALL.name to "Small",
    com.ben.inly.ui.theme.FontSizePreference.DEFAULT.name to "Default",
    com.ben.inly.ui.theme.FontSizePreference.LARGE.name to "Large"
)

@Composable
fun SettingsFontSizeSliderRow(
    fontSizePreference: String,
    onFontSizeChange: (String) -> Unit
) {
    val selectedIndex = FontSizeSteps.indexOfFirst { it.first == fontSizePreference }
        .coerceIn(0, FontSizeSteps.lastIndex)
    var dragPosition by remember(fontSizePreference) { mutableFloatStateOf(selectedIndex.toFloat()) }
    val displayedIndex = dragPosition.roundToInt().coerceIn(0, FontSizeSteps.lastIndex)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(Res.drawable.palette),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = "Font Size",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = FontSizeSteps[displayedIndex].second,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }

        Slider(
            value = dragPosition,
            onValueChange = { dragPosition = it },
            onValueChangeFinished = {
                val snappedIndex = dragPosition.roundToInt().coerceIn(0, FontSizeSteps.lastIndex)
                dragPosition = snappedIndex.toFloat()
                onFontSizeChange(FontSizeSteps[snappedIndex].first)
            },
            valueRange = 0f..(FontSizeSteps.lastIndex).toFloat(),
            steps = FontSizeSteps.size - 2,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = 50.dp)
        )
    }
}