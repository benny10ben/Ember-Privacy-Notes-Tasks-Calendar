package com.ben.inly.presentation.rag

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ben.inly.presentation.shared.components.TopBarIconButton
import com.ben.inly.presentation.shared.components.TopBarPillButton
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import inly.app.generated.resources.Res
import inly.app.generated.resources.setupscreen
import org.jetbrains.compose.resources.painterResource

private enum class SetupSlide { Privacy, Models, Download }

@Composable
internal fun EmbeddingSetupScreen(
    state: EmbeddingSetupState,
    sidePadding: Dp,
    isResumable: Boolean,
    onDownloadClick: () -> Unit,
    onPauseClick: () -> Unit,
    onProceedClick: () -> Unit
) {
    // Skip the intro if a download/index is already in flight or resumable.
    var slide by remember {
        mutableStateOf(
            if (state == EmbeddingSetupState.Required && !isResumable) {
                SetupSlide.Privacy
            } else {
                SetupSlide.Download
            }
        )
    }

    LaunchedEffect(state) {
        if (state is EmbeddingSetupState.Downloading ||
            state is EmbeddingSetupState.Indexing ||
            state == EmbeddingSetupState.DownloadComplete
        ) {
            slide = SetupSlide.Download
        }
    }

    val hazeState = remember { HazeState() }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
        ) {
            Image(
                painter = painterResource(Res.drawable.setupscreen),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0f),
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)
                            ),
                            startY = 0.35f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )
        }

        AnimatedContent(
            targetState = slide,
            modifier = Modifier.fillMaxSize(),
            label = "embeddingSetupSlides",
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                val enter = slideInHorizontally(
                    animationSpec = tween(420, easing = FastOutSlowInEasing)
                ) { full -> dir * full / 3 } + fadeIn(tween(300, delayMillis = 60))
                val exit = slideOutHorizontally(
                    animationSpec = tween(420, easing = FastOutSlowInEasing)
                ) { full -> -dir * full / 3 } + fadeOut(tween(180))
                enter togetherWith exit using SizeTransform(clip = false)
            }
        ) { current ->
            when (current) {
                SetupSlide.Privacy -> PrivacySlide(
                    sidePadding = sidePadding,
                    hazeState = hazeState,
                    onNext = { slide = SetupSlide.Models }
                )

                SetupSlide.Models -> ModelsSlide(
                    sidePadding = sidePadding,
                    hazeState = hazeState,
                    onBack = { slide = SetupSlide.Privacy },
                    onNext = { slide = SetupSlide.Download }
                )

                SetupSlide.Download -> DownloadSlide(
                    state = state,
                    sidePadding = sidePadding,
                    isResumable = isResumable,
                    hazeState = hazeState,
                    onBack = { slide = SetupSlide.Models },
                    onDownloadClick = onDownloadClick,
                    onPauseClick = onPauseClick,
                    onProceedClick = onProceedClick
                )
            }
        }
    }
}

@Composable
private fun PrivacySlide(
    sidePadding: Dp,
    hazeState: HazeState,
    onNext: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = sidePadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Built for Privacy.",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Open for Everyone",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            SlideIndicator(current = 0, total = 3)
            Spacer(Modifier.height(32.dp))
            SlideNav(onBack = null, onNext = onNext, hazeState = hazeState)
        }
    }
}

@Composable
private fun ModelsSlide(
    sidePadding: Dp,
    hazeState: HazeState,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = sidePadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Local first.\nCloud when you need it.",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            FeatureRow(
                icon = Icons.Default.Lock,
                title = "Private by default",
                body = "A local AI model runs on your device, so your notes never leave it."
            )
            Spacer(Modifier.height(18.dp))
            FeatureRow(
                icon = Icons.Default.CloudQueue,
                title = "More power, optional",
                body = "Connect an external AI provider whenever you want deeper answers."
            )
            Spacer(Modifier.height(24.dp))
            SlideIndicator(current = 1, total = 3)
            Spacer(Modifier.height(32.dp))
            SlideNav(onBack = onBack, onNext = onNext, hazeState = hazeState)
        }
    }
}

@Composable
private fun DownloadSlide(
    state: EmbeddingSetupState,
    sidePadding: Dp,
    isResumable: Boolean,
    onBack: () -> Unit,
    hazeState: HazeState,
    onDownloadClick: () -> Unit,
    onPauseClick: () -> Unit,
    onProceedClick: () -> Unit
) {
    val contentPadding = sidePadding + 26.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val isSuccess = state is EmbeddingSetupState.DownloadComplete
            Icon(
                imageVector = if (isSuccess) Icons.Default.Check else Icons.Default.Download,
                contentDescription = null,
                tint = if (isSuccess) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White.copy(alpha = 0.6f)
                },
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = when (state) {
                    EmbeddingSetupState.Required -> if (isResumable) "Resume Download" else "Download AI Model"
                    is EmbeddingSetupState.Downloading -> "Downloading… ${(state.progress * 100).toInt()}%"
                    is EmbeddingSetupState.DownloadFailed -> if (isResumable) "Download Interrupted" else "Download Failed"
                    EmbeddingSetupState.DownloadComplete -> "Model Ready"
                    is EmbeddingSetupState.Indexing ->
                        if (state.total > 0) "Indexing… ${state.completed}/${state.total}" else "Indexing Notes…"
                    else -> ""
                },
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when (state) {
                    EmbeddingSetupState.Required ->
                        if (isResumable) "Continue where you left off." else "Required to use this AI feature."
                    is EmbeddingSetupState.Downloading -> "Keep the app open."
                    is EmbeddingSetupState.DownloadFailed -> "Tap to retry."
                    EmbeddingSetupState.DownloadComplete -> "Tap proceed to index your notes."
                    is EmbeddingSetupState.Indexing -> "Almost done."
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))
            SlideIndicator(current = 2, total = 3)

            val canGoBack = state == EmbeddingSetupState.Required ||
                    state is EmbeddingSetupState.DownloadFailed
            if (canGoBack) {
                Spacer(Modifier.height(24.dp))
                SlideNav(onBack = onBack, onNext = null, hazeState = hazeState)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = contentPadding)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                is EmbeddingSetupState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )
                    Spacer(Modifier.height(12.dp))
                    TopBarPillButton(
                        text = "Pause Download",
                        bgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                        tint = MaterialTheme.colorScheme.primary,
                        hazeState = hazeState,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onPauseClick
                    )
                }

                is EmbeddingSetupState.Indexing -> {
                    val fraction = if (state.total > 0) state.completed / state.total.toFloat() else 0f
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                }

                EmbeddingSetupState.Required -> {
                    TopBarPillButton(
                        text = if (isResumable) "Resume Download" else "Download Model",
                        bgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                        tint = MaterialTheme.colorScheme.primary,
                        hazeState = hazeState,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onDownloadClick
                    )
                }

                is EmbeddingSetupState.DownloadFailed -> {
                    TopBarPillButton(
                        text = if (isResumable) "Resume Download" else "Try Again",
                        bgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                        tint = MaterialTheme.colorScheme.primary,
                        hazeState = hazeState,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onDownloadClick
                    )
                }

                EmbeddingSetupState.DownloadComplete -> {
                    TopBarPillButton(
                        text = "Proceed",
                        bgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                        tint = MaterialTheme.colorScheme.primary,
                        hazeState = hazeState,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onProceedClick
                    )
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    body: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun SlideNav(
    hazeState: HazeState,
    onBack: (() -> Unit)?,
    onNext: (() -> Unit)?
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            TopBarIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                bgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                tint = MaterialTheme.colorScheme.primary,
                hazeState = hazeState,
                size = 44.dp,
                onClick = onBack
            )
        }
        if (onNext != null) {
            TopBarIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Next",
                bgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                tint = MaterialTheme.colorScheme.primary,
                hazeState = hazeState,
                size = 52.dp,
                onClick = onNext
            )
        }
    }
}

@Composable
private fun SlideIndicator(
    current: Int,
    total: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { index ->
            val active = index == current
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(if (active) 18.dp else 6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = if (active) 0.95f else 0.35f))
            )
        }
    }
}