package com.ben.inly.presentation.rag

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import com.ben.inly.presentation.shared.rememberStableStatusBarsPadding
import com.ben.inly.presentation.shared.stableStatusBarsPadding
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ben.inly.domain.util.AiEventBus
import com.ben.inly.domain.util.isDesktopPlatform
import com.ben.inly.presentation.customInlyShadow
import com.ben.inly.presentation.shared.components.InlyBottomSheet
import com.ben.inly.presentation.shared.components.InlyButtonPrimary
import com.ben.inly.presentation.shared.components.InlyTextField
import com.ben.inly.presentation.shared.components.KmpBackHandler
import com.ben.inly.presentation.shared.components.TopBarIconButton
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import inly.app.generated.resources.Res
import inly.app.generated.resources.chevron_left
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

private val DesktopMaxContentWidth = 720.dp

private enum class ExternalAiProvider(val displayName: String) {
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic"),
    GEMINI("Gemini"),
    CUSTOM("Custom")
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RagChatOverlay(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    viewModel: RagViewModel,
    sharedTransitionScope: SharedTransitionScope
) {
    KmpBackHandler(enabled = isVisible) { onDismiss() }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth } +
                fadeIn(tween(250)),
        exit  = slideOutHorizontally(animationSpec = tween(250, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth } +
                fadeOut(tween(200)),
        modifier = Modifier.fillMaxSize()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                RagChatContent(
                    viewModel = viewModel,
                    onDismiss = onDismiss,
                    isVisible = isVisible,
                    sharedTransitionScope = sharedTransitionScope,
                    chatAnimatedVisibilityScope = this@AnimatedVisibility,
                    contentModifier = Modifier
                        .fillMaxHeight()
                        .then(
                            if (isDesktopPlatform) Modifier.width(DesktopMaxContentWidth)
                            else Modifier.fillMaxWidth()
                        )
                )
            }
        }
    }
}

// Desktop-only: persistent, resizable side-panel variant.
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RagChatPanel(
    onDismiss: () -> Unit,
    viewModel: RagViewModel,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.background
    ) {
        RagChatContent(
            viewModel = viewModel,
            onDismiss = onDismiss,
            isVisible = true,
            contentModifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RagChatContent(
    viewModel: RagViewModel,
    onDismiss: () -> Unit,
    isVisible: Boolean,
    contentModifier: Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    chatAnimatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val messages        by viewModel.messages.collectAsState()
    val isLoading       by viewModel.isLoading.collectAsState()
    val isModelAvailable by viewModel.isModelAvailable.collectAsState()
    val listState       = rememberLazyListState()
    val hazeState       = remember { HazeState() }

    var inputText by remember { mutableStateOf("") }
    var showAiSettingsSheet by remember { mutableStateOf(false) }
    var showExternalAiSheet by remember { mutableStateOf(false) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            AiEventBus.requestImmediateIndex()
        } else {
            inputText = ""
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    LaunchedEffect(messages.lastOrNull()?.text) {
        if (messages.isNotEmpty() && isLoading) listState.animateScrollToItem(messages.lastIndex)
    }

    val submit: () -> Unit = {
        val trimmed = inputText.trim()
        if (trimmed.isNotEmpty() && !isLoading) {
            viewModel.submitQuery(trimmed)
            inputText = ""
        }
    }

    var isInputBarCompact by remember { mutableStateOf(false) }
    val inputBarScrollAccumulator = remember { FloatArray(1) }
    val inputBarNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta == 0f) return Offset.Zero

                val accumulated = inputBarScrollAccumulator[0]
                if ((delta < 0f && accumulated > 0f) || (delta > 0f && accumulated < 0f)) {
                    inputBarScrollAccumulator[0] = 0f
                }
                inputBarScrollAccumulator[0] += delta

                val toggleThresholdPx = 60f
                if (inputBarScrollAccumulator[0] <= -toggleThresholdPx && !isInputBarCompact) {
                    isInputBarCompact = true
                    inputBarScrollAccumulator[0] = 0f
                } else if (inputBarScrollAccumulator[0] >= toggleThresholdPx && isInputBarCompact) {
                    isInputBarCompact = false
                    inputBarScrollAccumulator[0] = 0f
                }
                return Offset.Zero
            }
        }
    }

    val sidePadding = if (isDesktopPlatform) 32.dp else 16.dp

    Box(modifier = contentModifier.nestedScroll(inputBarNestedScrollConnection)) {
        // Body
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
        ) {
            when {
                // Still checking
                isModelAvailable == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                // Model not found
                isModelAvailable == false -> {
                    ModelUnavailablePrompt(
                        sidePadding = sidePadding,
                        onDownloadClick = { /* TODO: implement download */ },
                        onApiKeyClick = { /* TODO: implement API key entry */ }
                    )
                }

                // Model ready, no messages yet
                messages.isEmpty() -> {
                    EmptyState(
                        sidePadding = sidePadding,
                        onSuggestionTap = { suggestion ->
                            inputText = suggestion
                            submit()
                        }
                    )
                }

                // Chat
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = sidePadding,
                            end = sidePadding,
                            top = rememberStableStatusBarsPadding().calculateTopPadding() + 70.dp,
                            bottom = 140.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(items = messages, key = { it.id }) { message ->
                            ChatBubble(message)
                        }
                        if (isLoading && messages.lastOrNull()?.text?.isEmpty() == true) {
                            item { ThinkingIndicator() }
                        }
                    }
                }
            }
        }

        // Header: back button + "Ask Inly" title
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .then(if (isDesktopPlatform) Modifier else Modifier.stableStatusBarsPadding())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = if (isDesktopPlatform) 16.dp else 10.dp,
                    bottom = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TopBarIconButton(
                icon = painterResource(Res.drawable.chevron_left),
                contentDescription = "Back",
                bgColor = if (isDesktopPlatform) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                tint = MaterialTheme.colorScheme.onSurface,
                hazeState = hazeState,
                onClick = onDismiss
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Ask Inly",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            TopBarIconButton(
                icon = Icons.Default.Settings,
                contentDescription = "AI settings",
                bgColor = if (isDesktopPlatform) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                tint = MaterialTheme.colorScheme.onSurface,
                hazeState = hazeState,
                onClick = { showAiSettingsSheet = true }
            )
        }

        // Input bar — floating pill, styled exactly like InlyBottomBar's pill
        ChatInputBar(
            value = inputText,
            onValueChange = { inputText = it },
            onSubmit = submit,
            enabled = !isLoading && isModelAvailable == true,
            isCompact = isInputBarCompact,
            hazeState = hazeState,
            sharedTransitionScope = sharedTransitionScope,
            chatAnimatedVisibilityScope = chatAnimatedVisibilityScope,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    AiSettingsSheet(
        expanded = showAiSettingsSheet,
        onDismiss = { showAiSettingsSheet = false },
        onExternalAiClick = { showExternalAiSheet = true }
    )

    ExternalAiSettingsSheet(
        expanded = showExternalAiSheet,
        onDismiss = { showExternalAiSheet = false }
    )
}

// Model unavailable

@Composable
private fun ModelUnavailablePrompt(
    sidePadding: androidx.compose.ui.unit.Dp,
    onDownloadClick: () -> Unit,
    onApiKeyClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = sidePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Local AI model not found",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Download the on-device model to chat privately, or connect an external AI with an API key.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        // Download option
        ModelOptionCard(
            icon = { Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
            title = "Download local model",
            subtitle = "Runs fully on-device. Private & offline.",
            onClick = onDownloadClick
        )
        Spacer(Modifier.height(10.dp))

        // API key option
        ModelOptionCard(
            icon = { Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
            title = "Use an API key",
            subtitle = "Connect an external AI provider.",
            onClick = onApiKeyClick
        )
    }
}

@Composable
private fun ModelOptionCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            icon()
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// Empty state

@Composable
private fun EmptyState(
    sidePadding: androidx.compose.ui.unit.Dp,
    onSuggestionTap: (String) -> Unit
) {
    val suggestions = listOf(
        "What are my deadlines this week?",
        "Summarize my recent notes",
        "What tasks are still pending?"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = sidePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "What's on your mind?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Everything runs privately on your device.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(36.dp))

        suggestions.forEachIndexed { index, suggestion ->
            var chipVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(80L * (index + 1)); chipVisible = true }

            val chipAlpha  by animateFloatAsState(if (chipVisible) 1f else 0f, tween(250), label = "a$index")
            val chipOffset by animateDpAsState(if (chipVisible) 0.dp else 10.dp, tween(250), label = "o$index")

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .graphicsLayer { alpha = chipAlpha; translationY = chipOffset.toPx() }
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSuggestionTap(suggestion) }
            ) {
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                )
            }
        }
    }
}

//  Chat bubble

@Composable
fun ChatBubble(message: ChatMessage) {
    if (message.text.isEmpty() && !message.isUser) return

    val isUser  = message.isUser
    val bgColor = if (isUser) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    val shape = if (isUser) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
    else         RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    val align = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val widthMod = if (isDesktopPlatform) Modifier.fillMaxWidth(0.85f) else Modifier.widthIn(max = 300.dp)

    Box(Modifier.fillMaxWidth(), contentAlignment = align) {
        Box(
            modifier = Modifier
                .then(widthMod)
                .clip(shape)
                .background(bgColor)
                .animateContentSize(animationSpec = tween(120))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text  = message.text,
                color = textColor,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

// Input bar

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
    isCompact: Boolean,
    hazeState: HazeState,
    sharedTransitionScope: SharedTransitionScope?,
    chatAnimatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier
) {
    val canSend = value.isNotBlank() && enabled

    val sendColor by animateColorAsState(
        targetValue = if (canSend) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(200),
        label = "sendColor"
    )

    val defaultBgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.65f)

    val barAnimationSpec = tween<Dp>(durationMillis = 350, easing = FastOutSlowInEasing)
    val barSize by animateDpAsState(
        targetValue = if (isCompact) 44.dp else 52.dp,
        animationSpec = barAnimationSpec
    )
    val bottomInset by animateDpAsState(
        targetValue = if (isCompact) 0.dp else 6.dp,
        animationSpec = barAnimationSpec
    )
    val horizontalInset by animateDpAsState(
        targetValue = if (isCompact) 24.dp else 12.dp,
        animationSpec = barAnimationSpec
    )
    val navItemHeight = barSize - 12.dp

    val isMorphing = chatAnimatedVisibilityScope?.transition?.isRunning == true
    val shadowElevation by animateDpAsState(
        targetValue = if (isMorphing) 0.dp else 14.dp,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
    )

    val sharedPillModifier = if (sharedTransitionScope != null && chatAnimatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "calendarBottomBarPill"),
                animatedVisibilityScope = chatAnimatedVisibilityScope,
                boundsTransform = { _, _ -> tween(durationMillis = 300, easing = FastOutSlowInEasing) }
            )
        }
    } else Modifier

    val innerRowModifier = if (sharedTransitionScope != null) {
        with(sharedTransitionScope) {
            Modifier
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .skipToLookaheadSize()
        }
    } else {
        Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
    }

    val aiIconModifier = if (sharedTransitionScope != null && chatAnimatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "aiIcon"),
                animatedVisibilityScope = chatAnimatedVisibilityScope,
                boundsTransform = { _, _ -> tween(durationMillis = 300, easing = FastOutSlowInEasing) }
            )
        }
    } else Modifier

    Surface(
        shape = CircleShape,
        color = defaultBgColor,
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(bottom = bottomInset, start = 16.dp, end = 16.dp)
            .padding(horizontal = horizontalInset)
            .heightIn(min = barSize)
            .then(sharedPillModifier)
            .customInlyShadow(CircleShape, elevation = shadowElevation)
            .animateContentSize(animationSpec = tween(150))
            .clip(CircleShape)
            .hazeEffect(hazeState, HazeStyle.Unspecified, null)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = CircleShape
            )
    ) {
        Row(
            modifier = innerRowModifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(20.dp)
                    .then(aiIconModifier)
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "Ask about your notes…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(
                modifier = Modifier
                    .size(navItemHeight)
                    .clip(CircleShape)
                    .background(sendColor)
                    .clickable(enabled = canSend, onClick = onSubmit),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Send",
                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// Thinking indicator

@Composable
private fun ThinkingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
    ) {
        Text(
            text = "Thinking",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(4.dp))
        val transition = rememberInfiniteTransition(label = "dots")
        val alpha by transition.animateFloat(
            initialValue = 0.2f,
            targetValue  = 1f,
            animationSpec = infiniteRepeatable(
                animation  = tween(500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dotAlpha"
        )
        Text(
            text = "•••",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
        )
    }
}

// AI settings sheet

@Composable
private fun AiSettingsSheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onExternalAiClick: () -> Unit
) {
    InlyBottomSheet(
        expanded = expanded,
        onDismiss = onDismiss,
        title = "AI Settings",
        applyNavPadding = true
    ) {
        ModelOptionCard(
            icon = { Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
            title = "Local AI",
            subtitle = "Runs fully on-device. Private & offline.",
            onClick = {}
        )
        Spacer(Modifier.height(10.dp))
        ModelOptionCard(
            icon = { Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
            title = "External AI",
            subtitle = "Connect an external provider with your own API key.",
            onClick = onExternalAiClick
        )
        Spacer(Modifier.height(10.dp))
        ModelOptionCard(
            icon = { Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
            title = "Fine-tuning",
            subtitle = "Customize how the assistant responds.",
            onClick = {}
        )
        Spacer(Modifier.height(10.dp))
    }
}

// External AI settings sheet

@Composable
private fun ExternalAiSettingsSheet(
    expanded: Boolean,
    onDismiss: () -> Unit
) {
    var selectedProvider by remember { mutableStateOf(ExternalAiProvider.OPENAI) }
    var apiKey by remember { mutableStateOf("") }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    InlyBottomSheet(
        expanded = expanded,
        onDismiss = onDismiss,
        title = "External AI",
        subtitle = "Connect an external provider using your own API key.",
        applyNavPadding = true
    ) { closeAnd ->
        Text(
            text = "Provider",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExternalAiProvider.entries.forEach { provider ->
                ProviderChip(
                    label = provider.displayName,
                    selected = provider == selectedProvider,
                    onClick = { selectedProvider = provider }
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "API Key",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        InlyTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            placeholder = "Enter your ${selectedProvider.displayName} API key",
            singleLine = true,
            visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                    Icon(
                        imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isApiKeyVisible) "Hide API key" else "Show API key",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        InlyButtonPrimary(
            text = "Save",
            enabled = apiKey.isNotBlank(),
            onClick = { closeAnd { } },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ProviderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}