package com.ben.inly.presentation.rag

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ben.inly.domain.util.isDesktopPlatform
import com.ben.inly.presentation.shared.components.InlyBlur
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import inly.app.generated.resources.Res
import inly.app.generated.resources.logo_chatgpt
import inly.app.generated.resources.logo_claude
import inly.app.generated.resources.logo_gemini
import inly.app.generated.resources.logo_grok
import inly.app.generated.resources.logo_llama
import inly.app.generated.resources.logo_mistral
import inly.app.generated.resources.setupscreen
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds

private enum class SetupSlide { Privacy, Models, Download }

private enum class ModelProvider(
    val label: String,
    val logo: DrawableResource,
    val isMonochromeMark: Boolean
) {
    Claude("Claude", Res.drawable.logo_claude, isMonochromeMark = false),
    ChatGpt("ChatGPT", Res.drawable.logo_chatgpt, isMonochromeMark = true),
    Gemini("Gemini", Res.drawable.logo_gemini, isMonochromeMark = false),
    Grok("Grok", Res.drawable.logo_grok, isMonochromeMark = true),
    Llama("Llama", Res.drawable.logo_llama, isMonochromeMark = true),
    Mistral("Mistral", Res.drawable.logo_mistral, isMonochromeMark = false)
}

private data class ProviderTilePlacement(
    val provider: ModelProvider,
    val offsetX: Dp,
    val offsetY: Dp,
    val rotation: Float,
    val entranceDelayMillis: Int
)

private val ProviderTilePlacements = listOf(
    ProviderTilePlacement(ModelProvider.Claude, (-104).dp, (-56).dp, -8f, 40),
    ProviderTilePlacement(ModelProvider.ChatGpt, (-34).dp, (-84).dp, 5f, 80),
    ProviderTilePlacement(ModelProvider.Gemini, 42.dp, (-70).dp, -4f, 120),
    ProviderTilePlacement(ModelProvider.Grok, 104.dp, (-30).dp, 9f, 160),
    ProviderTilePlacement(ModelProvider.Llama, (-92).dp, 26.dp, 6f, 200),
    ProviderTilePlacement(ModelProvider.Mistral, 96.dp, 44.dp, -7f, 240)
)

private val ScreenBackground = Color(0xFF0D0D0F)
private val BorderStrong = Color(0x1FFFFFFF)
private val BorderSubtle = Color(0x0FFFFFFF)
private val TextPrimary = Color(0xFFF5F5F6)
private val TextSecondary = Color(0xB3FFFFFF)
private val TextTertiary = Color(0x80FFFFFF)

private val CardShape = RoundedCornerShape(20.dp)
private val TileShape = RoundedCornerShape(13.dp)
private val ChipShape = RoundedCornerShape(9.dp)

private val ActionBarHeight = 56.dp
private val ActionBarSideInset = 16.dp
private val ActionBarBottomMargin = 34.dp
private val HeadlineSideInset = 16.dp
private val HeadlineBottomGap = 18.dp
private val BackButtonSize = 42.dp
private val ProviderTileSize = 52.dp
private val ProviderLogoSize = 26.dp
private val BottomAreaReserve = 116.dp
private val StatusBarReserve = 56.dp

private const val HeadlineRevealMillis = 620
private const val HeadlineRevealWindow = 22f

@Composable
internal fun EmbeddingSetupScreen(
    state: EmbeddingSetupState,
    sidePadding: Dp,
    isResumable: Boolean,
    onDownloadClick: () -> Unit,
    onPauseClick: () -> Unit,
    onProceedClick: () -> Unit
) {
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
    val accent = MaterialTheme.colorScheme.primary
    val scrim = MaterialTheme.colorScheme.scrim

    val onBackClick: (() -> Unit)? = when {
        slide == SetupSlide.Models -> {
            { slide = SetupSlide.Privacy }
        }

        slide == SetupSlide.Download && state.allowsReturningToModels() -> {
            { slide = SetupSlide.Models }
        }

        else -> null
    }

    val onPrimaryClick: () -> Unit = {
        when (slide) {
            SetupSlide.Privacy -> slide = SetupSlide.Models
            SetupSlide.Models -> slide = SetupSlide.Download
            SetupSlide.Download -> when (state) {
                EmbeddingSetupState.Required -> onDownloadClick()
                is EmbeddingSetupState.Downloading -> onPauseClick()
                is EmbeddingSetupState.DownloadFailed -> onDownloadClick()
                EmbeddingSetupState.DownloadComplete -> onProceedClick()
                else -> Unit
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(ScreenBackground)) {
        SetupBackdrop(hazeState = hazeState, scrim = scrim)

        AnimatedContent(
            targetState = slide,
            modifier = Modifier.fillMaxSize(),
            label = "setupSlides",
            transitionSpec = { slideCrossFade(targetState.ordinal > initialState.ordinal) }
        ) { visibleSlide ->
            when (visibleSlide) {
                SetupSlide.Privacy -> SetupSlideLayout(
                    sidePadding = sidePadding,
                    headline = "Ask your notes anything, and nothing leaves your device",
                    dotIndex = 0
                ) {
                    PrivacyPropCluster(hazeState = hazeState)
                }

                SetupSlide.Models -> SetupSlideLayout(
                    sidePadding = sidePadding,
                    headline = "Local first. Cloud only when you want it",
                    dotIndex = 1
                ) {
                    ModelsPropCluster(hazeState = hazeState)
                }

                SetupSlide.Download -> SetupSlideLayout(
                    sidePadding = sidePadding,
                    headline = state.downloadHeadline(isResumable),
                    dotIndex = 2
                ) {
                    DownloadPropCluster(
                        state = state,
                        accent = accent,
                        hazeState = hazeState
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = sidePadding)
                .padding(bottom = ActionBarBottomMargin)
        ) {
            if (state is EmbeddingSetupState.Indexing) {
                IndexingProgressBar(state = state, accent = accent)
            } else {
                SetupActionBar(
                    label = primaryActionLabel(slide, state, isResumable),
                    accent = accent,
                    hazeState = hazeState,
                    onPrimaryClick = onPrimaryClick,
                    onBackClick = onBackClick
                )
            }
        }
    }
}

@Composable
private fun SetupBackdrop(hazeState: HazeState, scrim: Color) {
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
                        0.00f to scrim.copy(alpha = 0.20f),
                        0.42f to scrim.copy(alpha = 0.42f),
                        0.78f to scrim.copy(alpha = 0.78f),
                        1.00f to scrim.copy(alpha = 0.90f)
                    )
                )
        )
    }
}

@Composable
private fun SetupActionBar(
    label: String,
    accent: Color,
    hazeState: HazeState,
    onPrimaryClick: () -> Unit,
    onBackClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ActionBarSideInset)
            .height(ActionBarHeight)
            .clip(CircleShape)
            .hazeEffect(state = hazeState, style = InlyBlur.Regular)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, BorderStrong, CircleShape)
            .clickable(onClick = onPrimaryClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent
        )

        AnimatedVisibility(
            visible = onBackClick != null,
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.7f),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.7f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 7.dp)
        ) {
            ActionBarBackButton(
                accent = accent,
                onClick = { onBackClick?.invoke() }
            )
        }
    }
}

@Composable
private fun ActionBarBackButton(accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(BackButtonSize)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, BorderSubtle, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Go back",
            tint = accent,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun IndexingProgressBar(
    state: EmbeddingSetupState.Indexing,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = ActionBarSideInset)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Indexing notes",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (state.total > 0) "${state.completed} of ${state.total}" else "Starting",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = TextTertiary
            )
        }
        Spacer(Modifier.height(12.dp))
        ProgressTrack(
            progress = state.progressFraction() ?: 0f,
            accent = accent,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SetupSlideLayout(
    sidePadding: Dp,
    headline: String,
    dotIndex: Int,
    propCluster: @Composable BoxScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = StatusBarReserve),
            contentAlignment = Alignment.Center,
            content = propCluster
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sidePadding + HeadlineSideInset),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HeadlineRevealText(text = headline, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(HeadlineBottomGap))
            SlideDots(current = dotIndex, total = 3)
        }
        Spacer(Modifier.height(BottomAreaReserve))
    }
}

@Composable
private fun PrivacyPropCluster(hazeState: HazeState) {
    FloatingPropStage(floatDistance = if (isDesktopPlatform) 0.dp else 3.dp) {
        FrostedNoteCard(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (-46).dp, y = 30.dp),
            hazeState = hazeState,
            rotation = -9f,
            entranceDelayMillis = 60,
            lineCount = 3
        )
        FrostedNoteCard(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 52.dp, y = 44.dp),
            hazeState = hazeState,
            rotation = 8f,
            entranceDelayMillis = 140,
            lineCount = 2
        )

        PropEntrance(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-28).dp),
            delayMillis = 240,
            fromRotation = -4f
        ) {
            FrostedCard(
                hazeState = hazeState,
                modifier = Modifier.width(246.dp)
            ) {
                Text(
                    text = "What did I write about my research last month?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(16.dp))
                ComposerActionRow {
                    ComposerChip { ComposerChipIcon(Icons.Default.Add) }
                    Spacer(Modifier.width(6.dp))
                    ComposerChip { ComposerChipIcon(Icons.Default.Image) }
                    Spacer(Modifier.width(6.dp))
                    ComposerChip {
                        Text(
                            text = "On device",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelsPropCluster(hazeState: HazeState) {
    FloatingPropStage(floatDistance = if (isDesktopPlatform) 0.dp else 3.dp) {
        ProviderTilePlacements.forEach { placement ->
            FrostedProviderTile(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = placement.offsetX, y = placement.offsetY),
                hazeState = hazeState,
                provider = placement.provider,
                rotation = placement.rotation,
                entranceDelayMillis = placement.entranceDelayMillis
            )
        }

        PropEntrance(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-6).dp),
            delayMillis = 300,
            fromRotation = 3f
        ) {
            FrostedCard(
                hazeState = hazeState,
                modifier = Modifier.width(214.dp)
            ) {
                Text(
                    text = "Add a cloud model for more powerful AI",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(14.dp))
                ComposerActionRow {
                    ComposerChip { ComposerChipIcon(Icons.Default.Lock) }
                    Spacer(Modifier.width(6.dp))
                    ComposerChip {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ComposerChipIcon(Icons.Default.CloudQueue)
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "Optional",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        PropEntrance(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 96.dp),
            delayMillis = 420
        ) {
            FrostedBadge(text = "Local model included", hazeState = hazeState)
        }
    }
}

@Composable
private fun DownloadPropCluster(
    state: EmbeddingSetupState,
    accent: Color,
    hazeState: HazeState
) {
    val progress = state.progressFraction()
    val isComplete = state == EmbeddingSetupState.DownloadComplete
    val isIndexing = state is EmbeddingSetupState.Indexing
    val isFailed = state is EmbeddingSetupState.DownloadFailed

    FloatingPropStage(floatDistance = if (isDesktopPlatform) 0.dp else 3.dp) {
        FrostedNoteCard(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (-58).dp, y = 62.dp),
            hazeState = hazeState,
            rotation = -7f,
            entranceDelayMillis = 60,
            lineCount = 2
        )
        FrostedNoteCard(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 60.dp, y = 74.dp),
            hazeState = hazeState,
            rotation = 7f,
            entranceDelayMillis = 130,
            lineCount = 3
        )

        PropEntrance(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-30).dp),
            delayMillis = 220,
            fromRotation = -2f
        ) {
            FrostedCard(
                hazeState = hazeState,
                modifier = Modifier.width(252.dp),
                dashedOutline = if (progress != null) accent.copy(alpha = 0.45f) else null
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(TileShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(1.dp, BorderStrong, TileShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isComplete) Icons.Default.Check else Icons.Default.Download,
                            contentDescription = null,
                            tint = if (isComplete) accent else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Embedding model",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Runs offline once installed",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                    }
                    if (progress != null) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = accent
                        )
                    }
                }

                AnimatedVisibility(
                    visible = progress != null,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(120))
                ) {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        ProgressTrack(
                            progress = progress ?: 0f,
                            accent = accent,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                SetupStepRow(
                    label = "Model downloaded",
                    isDone = isComplete || isIndexing,
                    isActive = state is EmbeddingSetupState.Downloading,
                    isFailed = isFailed,
                    accent = accent
                )
                Spacer(Modifier.height(8.dp))
                SetupStepRow(
                    label = "Notes indexed",
                    isDone = false,
                    isActive = isIndexing,
                    isFailed = false,
                    accent = accent
                )
            }
        }
    }
}

private val PropFloatDistance = 3.dp
private const val PropFloatDurationMillis = 7000

@Composable
private fun FloatingPropStage(
    floatDistance: Dp = PropFloatDistance,
    content: @Composable BoxScope.() -> Unit
) {
    if (floatDistance <= 0.dp) {
        Box(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            contentAlignment = Alignment.Center,
            content = content
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "propFloat")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(PropFloatDurationMillis, easing = LinearEasing)
        ),
        label = "propFloatPhase"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .graphicsLayer {
                translationY = floatDistance.toPx() * sin(phase * 2f * PI.toFloat())
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun PropEntrance(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    fromRotation: Float = 0f,
    content: @Composable () -> Unit
) {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong().milliseconds)
        entrance.animateTo(1f, spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessLow))
    }
    Box(
        modifier = modifier.graphicsLayer {
            val settled = entrance.value
            alpha = settled.coerceIn(0f, 1f)
            translationY = 22.dp.toPx() * (1f - settled)
            rotationZ = fromRotation * (1f - settled)
        }
    ) {
        content()
    }
}

@Composable
private fun FrostedCard(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    dashedOutline: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .shadow(26.dp, CardShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CardShape)
            .hazeEffect(state = hazeState, style = InlyBlur.Regular)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, BorderStrong, CardShape)
            .then(if (dashedOutline != null) Modifier.dashedBorder(dashedOutline) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content
    )
}

private fun Modifier.dashedBorder(color: Color) = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
        style = Stroke(
            width = 1.2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
        )
    )
}

@Composable
private fun FrostedNoteCard(
    modifier: Modifier = Modifier,
    hazeState: HazeState,
    rotation: Float,
    entranceDelayMillis: Int,
    lineCount: Int
) {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(entranceDelayMillis.toLong().milliseconds)
        entrance.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow))
    }
    Column(
        modifier = modifier
            .graphicsLayer {
                val settled = entrance.value
                alpha = 0.92f * settled
                rotationZ = rotation * (0.35f + 0.65f * settled)
                translationY = 18.dp.toPx() * (1f - settled)
            }
            .shadow(18.dp, CardShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CardShape)
            .hazeEffect(state = hazeState, style = InlyBlur.Regular)
            .background(Color.Black.copy(alpha = 0.16f))
            .border(1.dp, BorderSubtle, CardShape)
            .width(176.dp)
            .padding(14.dp)
    ) {
        repeat(lineCount) { index ->
            val isLast = index == lineCount - 1
            Box(
                modifier = Modifier
                    .height(7.dp)
                    .fillMaxWidth(if (isLast) 0.52f else 0.92f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.09f))
            )
            if (!isLast) Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FrostedProviderTile(
    modifier: Modifier,
    hazeState: HazeState,
    provider: ModelProvider,
    rotation: Float,
    entranceDelayMillis: Int
) {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(entranceDelayMillis.toLong().milliseconds)
        entrance.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                val settled = entrance.value
                alpha = settled
                scaleX = 0.86f + 0.14f * settled
                scaleY = 0.86f + 0.14f * settled
                rotationZ = rotation * (0.4f + 0.6f * settled)
            }
            .shadow(14.dp, TileShape, ambientColor = Color.Black, spotColor = Color.Black)
            .size(ProviderTileSize)
            .clip(TileShape)
            .hazeEffect(state = hazeState, style = InlyBlur.Regular)
            .background(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.03f)),
                    start = Offset.Zero,
                    end = Offset(120f, 120f)
                )
            )
            .border(1.dp, BorderStrong, TileShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(provider.logo),
            contentDescription = provider.label,
            modifier = Modifier.size(ProviderLogoSize),
            contentScale = ContentScale.Fit,
            colorFilter = if (provider.isMonochromeMark) ColorFilter.tint(TextPrimary) else null
        )
    }
}

@Composable
private fun FrostedBadge(
    text: String,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(12.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .hazeEffect(state = hazeState, style = InlyBlur.Regular)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, BorderStrong, CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = TextSecondary
        )
    }
}

@Composable
private fun ComposerActionRow(chips: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        chips()
        Spacer(Modifier.weight(1f))
        ComposerSendButton()
    }
}

@Composable
private fun ComposerChip(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .height(26.dp)
            .clip(ChipShape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, BorderSubtle, ChipShape)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun ComposerChipIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = TextSecondary,
        modifier = Modifier.size(13.dp)
    )
}

@Composable
private fun ComposerSendButton() {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(TextPrimary),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.ArrowUpward,
            contentDescription = null,
            tint = ScreenBackground,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun ProgressTrack(
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val filled by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.95f, stiffness = Spring.StiffnessLow),
        label = "progressTrackFill"
    )
    Box(
        modifier = modifier
            .height(4.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
    ) {
        if (filled > 0.004f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(filled)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
    }
}

@Composable
private fun SetupStepRow(
    label: String,
    isDone: Boolean,
    isActive: Boolean,
    isFailed: Boolean,
    accent: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(14.dp), contentAlignment = Alignment.Center) {
            when {
                isDone -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(13.dp)
                )

                isActive -> Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accent)
                )

                else -> Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (isFailed) 0.10f else 0.16f))
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isDone || isActive) TextSecondary else TextTertiary
        )
    }
}

@Composable
private fun HeadlineRevealText(text: String, modifier: Modifier = Modifier) {
    val reveal = remember(text) { Animatable(0f) }
    LaunchedEffect(text) {
        reveal.animateTo(1f, tween(HeadlineRevealMillis, easing = LinearEasing))
    }
    val revealed = reveal.value
    val characterCount = text.length

    Text(
        text = buildAnnotatedString {
            text.forEachIndexed { index, character ->
                val visibility =
                    ((revealed * (characterCount + HeadlineRevealWindow) - index) / HeadlineRevealWindow)
                        .coerceIn(0f, 1f)
                withStyle(
                    SpanStyle(color = lerp(TextPrimary.copy(alpha = 0f), TextPrimary, visibility))
                ) {
                    append(character)
                }
            }
        },
        style = headlineTextStyle(),
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

@Composable
private fun headlineTextStyle(): TextStyle = MaterialTheme.typography.titleLarge.copy(
    fontSize = 28.sp,
    lineHeight = 34.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = (-0.6).sp
)

@Composable
private fun SlideDots(current: Int, total: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { index ->
            val isCurrent = index == current
            val dotWidth by animateDpAsState(
                targetValue = if (isCurrent) 16.dp else 5.dp,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                label = "slideDotWidth"
            )
            Box(
                modifier = Modifier
                    .height(5.dp)
                    .width(dotWidth)
                    .clip(CircleShape)
                    .background(if (isCurrent) TextPrimary else Color.White.copy(alpha = 0.20f))
            )
        }
    }
}

private fun slideCrossFade(isForward: Boolean): ContentTransform = ContentTransform(
    targetContentEnter = fadeIn(tween(300, delayMillis = 60, easing = LinearEasing)) +
            slideInVertically(
                spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = IntOffset.VisibilityThreshold
                )
            ) { full -> if (isForward) full / 14 else -full / 14 },
    initialContentExit = fadeOut(tween(170, easing = LinearEasing)) +
            slideOutVertically(tween(220)) { full ->
                if (isForward) -full / 26 else full / 26
            },
    sizeTransform = SizeTransform(clip = false)
)

private fun EmbeddingSetupState.progressFraction(): Float? = when (this) {
    is EmbeddingSetupState.Downloading -> progress.coerceIn(0f, 1f)
    is EmbeddingSetupState.Indexing ->
        if (total > 0) (completed / total.toFloat()).coerceIn(0f, 1f) else 0f

    else -> null
}

private fun EmbeddingSetupState.allowsReturningToModels(): Boolean =
    this == EmbeddingSetupState.Required || this is EmbeddingSetupState.DownloadFailed

private fun EmbeddingSetupState.downloadHeadline(isResumable: Boolean): String = when (this) {
    EmbeddingSetupState.Required ->
        if (isResumable) "Finish the download to start searching" else "Add the on-device model"

    is EmbeddingSetupState.Downloading -> "Getting the model ready"
    is EmbeddingSetupState.DownloadFailed -> "The download stopped"
    EmbeddingSetupState.DownloadComplete -> "The model is ready"
    is EmbeddingSetupState.Indexing -> "Reading through your notes"
    else -> ""
}

private fun primaryActionLabel(
    slide: SetupSlide,
    state: EmbeddingSetupState,
    isResumable: Boolean
): String = if (slide != SetupSlide.Download) "Continue" else when (state) {
    EmbeddingSetupState.Required -> if (isResumable) "Resume download" else "Download model"
    is EmbeddingSetupState.Downloading -> "Pause download"
    is EmbeddingSetupState.DownloadFailed -> if (isResumable) "Resume download" else "Try again"
    EmbeddingSetupState.DownloadComplete -> "Index my notes"
    else -> "Continue"
}