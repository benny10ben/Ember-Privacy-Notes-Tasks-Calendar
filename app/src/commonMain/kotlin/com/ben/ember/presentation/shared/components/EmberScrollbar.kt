package com.ben.ember.presentation.shared.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.ben.ember.data.local.prefs.SettingsManager
import com.ben.ember.domain.util.isDesktopPlatform
import org.koin.compose.koinInject

@Composable
fun rememberScrollbarsVisible(): Boolean {
    if (!isDesktopPlatform) return false
    val settingsManager = koinInject<SettingsManager>()
    val isVisible by settingsManager.showScrollbarFlow.collectAsState(
        initial = settingsManager.isShowScrollbarEnabled()
    )
    return isVisible
}

@Composable
fun EmberVerticalScrollbar(listState: LazyListState, modifier: Modifier = Modifier) {
    if (!rememberScrollbarsVisible()) return
    PlatformVerticalScrollbar(listState, modifier)
}

@Composable
fun EmberVerticalScrollbar(scrollState: ScrollState, modifier: Modifier = Modifier) {
    if (!rememberScrollbarsVisible()) return
    PlatformVerticalScrollbar(scrollState, modifier)
}

@Composable
fun EmberVerticalScrollbar(gridState: LazyGridState, modifier: Modifier = Modifier) {
    if (!rememberScrollbarsVisible()) return
    PlatformVerticalScrollbar(gridState, modifier)
}

@Composable
fun EmberHorizontalScrollbar(scrollState: ScrollState, modifier: Modifier = Modifier) {
    if (!rememberScrollbarsVisible()) return
    PlatformHorizontalScrollbar(scrollState, modifier)
}

@Composable
internal expect fun PlatformVerticalScrollbar(listState: LazyListState, modifier: Modifier)

@Composable
internal expect fun PlatformVerticalScrollbar(scrollState: ScrollState, modifier: Modifier)

@Composable
internal expect fun PlatformVerticalScrollbar(gridState: LazyGridState, modifier: Modifier)

@Composable
internal expect fun PlatformHorizontalScrollbar(scrollState: ScrollState, modifier: Modifier)
