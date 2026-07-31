package com.ben.inly.presentation.search

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.ben.inly.presentation.shared.rememberStableStatusBarsPadding
import com.ben.inly.presentation.shared.stableStatusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ben.inly.data.local.room.NoteMetadataEntity
import com.ben.inly.domain.model.NoteSearchResult
import com.ben.inly.domain.util.isDesktopPlatform
import com.ben.inly.presentation.customInlyShadow
import com.ben.inly.presentation.shared.components.TopBarIconButton
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import inly.app.generated.resources.Res
import inly.app.generated.resources.chevron_left
import inly.app.generated.resources.search
import inly.app.generated.resources.x
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Full-screen cross-note search. The input field floats at the bottom of the Box and picks
 * up `.imePadding()`, so it's always pinned directly above the software keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onNoteClick: (String) -> Unit,
    onDailyNoteClick: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    searchIconAnimatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: SearchViewModel = koinViewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val hazeState = remember { HazeState() }

    var isBottomBarCompact by remember { mutableStateOf(false) }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .nestedScroll(bottomBarNestedScrollConnection)
        ) {
            // Main content background with Haze blur applied
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .background(if (isDesktopPlatform) Color(0xFF121212) else MaterialTheme.colorScheme.background)
            ) {
                when {
                    query.isBlank() -> SearchHint()
                    results.isEmpty() -> SearchEmptyState()
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = rememberStableStatusBarsPadding().calculateTopPadding() + 70.dp,
                            bottom = 120.dp
                        )
                    ) {
                        items(results, key = { it.note.noteId }) { result ->
                            SearchResultRow(
                                result = result,
                                query = query,
                                onClick = {
                                    if (result.note.isDaily) {
                                        result.note.dateString?.let(onDailyNoteClick)
                                    } else {
                                        onNoteClick(result.note.noteId)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .then(if (isDesktopPlatform) Modifier else Modifier.stableStatusBarsPadding())
                    .padding(top = if (isDesktopPlatform) 16.dp else 10.dp, start = 16.dp)
            ) {
                TopBarIconButton(
                    icon = painterResource(Res.drawable.chevron_left),
                    contentDescription = "Back",
                    bgColor = if (isDesktopPlatform) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                    tint = MaterialTheme.colorScheme.onSurface,
                    hazeState = hazeState,
                    onClick = onBack
                )
            }

            // Floating search pill, styled exactly like InlyBottomBar's pill
            val defaultBgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.65f)
            val isMorphing = searchIconAnimatedVisibilityScope.transition.isRunning
            val shadowElevation by animateDpAsState(
                targetValue = if (isMorphing) 0.dp else 14.dp,
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
            )

            val barAnimationSpec = tween<Dp>(durationMillis = 350, easing = FastOutSlowInEasing)
            val barSize by animateDpAsState(
                targetValue = if (isBottomBarCompact) 44.dp else 52.dp,
                animationSpec = barAnimationSpec
            )
            val bottomInset by animateDpAsState(
                targetValue = if (isBottomBarCompact) 0.dp else 6.dp,
                animationSpec = barAnimationSpec
            )
            val horizontalInset by animateDpAsState(
                targetValue = if (isBottomBarCompact) 24.dp else 12.dp,
                animationSpec = barAnimationSpec
            )

            Surface(
                shape = CircleShape,
                color = defaultBgColor,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(bottom = bottomInset, start = 16.dp, end = 16.dp)
                    .padding(horizontal = horizontalInset)
                    .height(barSize)
                    .then(
                        with(sharedTransitionScope) {
                            Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState(key = "calendarBottomBarPill"),
                                animatedVisibilityScope = searchIconAnimatedVisibilityScope,
                                boundsTransform = { _, _ -> tween(durationMillis = 300, easing = FastOutSlowInEasing) }
                            )
                        }
                    )
                    .customInlyShadow(CircleShape, elevation = shadowElevation)
                    .clip(CircleShape)
                    .hazeEffect(hazeState, HazeStyle.Unspecified, null)
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        shape = CircleShape
                    )
            ) {
                Row(
                    modifier = with(sharedTransitionScope) {
                        Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .skipToLookaheadSize()
                    },
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painterResource(Res.drawable.search),
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(20.dp)
                            .then(
                                with(sharedTransitionScope) {
                                    Modifier.sharedElement(
                                        sharedContentState = rememberSharedContentState(key = "searchIcon"),
                                        animatedVisibilityScope = searchIconAnimatedVisibilityScope,
                                        boundsTransform = { _, _ -> tween(durationMillis = 300, easing = FastOutSlowInEasing) }
                                    )
                                }
                            )
                    )
                    BasicTextField(
                        value = query,
                        onValueChange = viewModel::onQueryChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f),
                        decorationBox = { innerTextField ->
                            if (query.isBlank()) {
                                Text(
                                    text = "Search all notes...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            innerTextField()
                        }
                    )
//                    Box(
//                        modifier = Modifier
//                            .size(40.dp)
//                            .clip(CircleShape)
//                            .clickable(onClick = onBack),
//                        contentAlignment = Alignment.Center
//                    ) {
//                        Icon(
//                            painterResource(Res.drawable.x),
//                            contentDescription = "Close Search",
//                            tint = MaterialTheme.colorScheme.onSurface,
//                            modifier = Modifier.size(20.dp)
//                        )
//                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHint() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Start typing to search titles, snippets, and note content.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun SearchEmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No matching notes found.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun SearchResultRow(
    result: NoteSearchResult,
    query: String,
    onClick: () -> Unit
) {
    val note: NoteMetadataEntity = result.note
    val highlightStyle = defaultHighlightStyle(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = highlightMatches(note.title.ifBlank { "Untitled" }, query, highlightStyle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (result.matchedText.isNotBlank() && result.matchedText != note.title) {
                Text(
                    text = highlightMatches(result.matchedText, query, highlightStyle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (note.isDaily && note.dateString != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Daily · ${note.dateString}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}