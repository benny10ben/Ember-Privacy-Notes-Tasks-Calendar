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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import com.ben.inly.presentation.shared.rememberStableStatusBarsPadding
import com.ben.inly.presentation.shared.stableStatusBarsPadding
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ben.inly.domain.ai.AiGenerationMode
import com.ben.inly.domain.ai.chat.ChatMessage
import com.ben.inly.domain.ai.chat.ChatSession
import com.ben.inly.domain.ai.KnowledgeMode
import com.ben.inly.domain.ai.external.ExternalAiProvider
import com.ben.inly.domain.ai.external.ExternalAiProviderConfig
import com.ben.inly.domain.util.AiEventBus
import com.ben.inly.domain.util.isDesktopPlatform
import com.ben.inly.presentation.customInlyShadow
import com.ben.inly.presentation.shared.components.InlyBottomSheet
import com.ben.inly.presentation.shared.components.InlyButtonPrimary
import com.ben.inly.presentation.shared.components.InlyButtonSecondary
import com.ben.inly.presentation.shared.components.InlyTextField
import com.ben.inly.presentation.shared.components.MarkdownText
import com.ben.inly.presentation.shared.components.TopBarIconButton
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import inly.app.generated.resources.Res
import inly.app.generated.resources.chevron_left
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RagChatScreen(
    onDismiss: () -> Unit,
    viewModel: RagViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedVisibilityScope,
    onPickDocument: (onPathSelected: (String) -> Unit) -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        RagChatContent(
            viewModel = viewModel,
            onDismiss = onDismiss,
            isVisible = true,
            sharedTransitionScope = sharedTransitionScope,
            chatAnimatedVisibilityScope = animatedContentScope,
            onPickDocument = onPickDocument,
            contentModifier = Modifier.fillMaxSize()
        )
    }
}

// Desktop-only: persistent, resizable side-panel variant.
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RagChatPanel(
    onDismiss: () -> Unit,
    viewModel: RagViewModel,
    modifier: Modifier = Modifier,
    onPickDocument: (onPathSelected: (String) -> Unit) -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        RagChatContent(
            viewModel = viewModel,
            onDismiss = onDismiss,
            isVisible = true,
            onPickDocument = onPickDocument,
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
    onPickDocument: (onPathSelected: (String) -> Unit) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    chatAnimatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val messages        by viewModel.messages.collectAsState()
    val isLoading       by viewModel.isLoading.collectAsState()
    val isModelAvailable by viewModel.isModelAvailable.collectAsState()
    val embeddingSetupState by viewModel.embeddingSetupState.collectAsState()
    val listState       = rememberLazyListState()
    val hazeState       = remember { HazeState() }

    var userScrolledAwayFromBottom by remember { mutableStateOf(false) }

    val chatListNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && available.y != 0f) {
                    userScrolledAwayFromBottom = true
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && !listState.canScrollForward) {
                    userScrolledAwayFromBottom = false
                }
                return Offset.Zero
            }
        }
    }

    var inputText by remember { mutableStateOf("") }
    var showAiSettingsSheet by remember { mutableStateOf(false) }
    var showLocalAiSheet by remember { mutableStateOf(false) }
    var showExternalAiSheet by remember { mutableStateOf(false) }
    var showFineTuningSheet by remember { mutableStateOf(false) }
    var showChatHistorySheet by remember { mutableStateOf(false) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            AiEventBus.requestImmediateIndex()
        } else {
            inputText = ""
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            userScrolledAwayFromBottom = false
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(messages.lastOrNull()?.text) {
        if (messages.isNotEmpty() && isLoading && !userScrolledAwayFromBottom) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val submit: () -> Unit = {
        val trimmed = inputText.trim()
        if (trimmed.isNotEmpty() && !isLoading) {
            viewModel.submitQuery(trimmed)
            inputText = ""
        }
    }

    val sidePadding = if (isDesktopPlatform) 32.dp else 16.dp

    Box(modifier = contentModifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                // Embedding model missing — must download + reindex before chat is usable
                embeddingSetupState != EmbeddingSetupState.Ready -> {
                    EmbeddingSetupScreen(
                        state = embeddingSetupState,
                        sidePadding = sidePadding,
                        isResumable = viewModel.hasResumableEmbeddingDownload(),
                        onDownloadClick = viewModel::downloadEmbeddingModel,
                        onPauseClick = viewModel::pauseEmbeddingModelDownload,
                        onProceedClick = viewModel::proceedAfterEmbeddingModelDownload
                    )
                }

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
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(chatListNestedScrollConnection),
                        contentPadding = PaddingValues(
                            start = sidePadding,
                            end = sidePadding,
                            top = rememberStableStatusBarsPadding().calculateTopPadding() + 70.dp,
                            bottom = 140.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(items = messages, key = { it.id }) { message ->
                            ChatBubble(
                                message = message,
                                onEditClick = if (message.isUser) {
                                    {
                                        viewModel.beginEditingMessage(message.id)
                                        inputText = message.text
                                    }
                                } else null
                            )
                        }
                        if (isLoading && messages.lastOrNull()?.text?.isEmpty() == true) {
                            item { ThinkingIndicator() }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .then(if (isDesktopPlatform) Modifier else Modifier.stableStatusBarsPadding())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = if (isDesktopPlatform) 16.dp else 10.dp,
                    bottom = 8.dp
                )
        ) {
            Box(modifier = Modifier.align(Alignment.CenterStart)) {
                TopBarIconButton(
                    icon = painterResource(Res.drawable.chevron_left),
                    contentDescription = "Back",
                    bgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                    tint = MaterialTheme.colorScheme.onSurface,
                    hazeState = hazeState,
                    onClick = onDismiss
                )
            }

            if (embeddingSetupState == EmbeddingSetupState.Ready) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
            }

            if (embeddingSetupState == EmbeddingSetupState.Ready) {
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    TopBarIconButton(
                        icon = rememberVectorPainter(Icons.Default.Menu),
                        contentDescription = "Chat history",
                        onClick = { showChatHistorySheet = true },
                        bgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                        tint = MaterialTheme.colorScheme.onSurface,
                        hazeState = hazeState
                    )
                }
            }
        }

        // Input bar — floating pill, styled exactly like InlyBottomBar's pill
        if (embeddingSetupState == EmbeddingSetupState.Ready) {
            ChatInputBar(
                value = inputText,
                onValueChange = { inputText = it },
                onSubmit = submit,
                enabled = !isLoading && isModelAvailable == true,
                isGenerating = isLoading,
                onStopGeneration = viewModel::stopGeneration,
                hazeState = hazeState,
                viewModel = viewModel,
                sharedTransitionScope = sharedTransitionScope,
                chatAnimatedVisibilityScope = chatAnimatedVisibilityScope,
                onSettingsClick = { showAiSettingsSheet = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    AiSettingsSheet(
        expanded = showAiSettingsSheet,
        onDismiss = { showAiSettingsSheet = false },
        viewModel = viewModel,
        onLocalAiClick = { showLocalAiSheet = true },
        onExternalAiClick = { showExternalAiSheet = true },
        onFineTuningClick = { showFineTuningSheet = true }
    )

    LocalAiSettingsSheet(
        expanded = showLocalAiSheet,
        onDismiss = { showLocalAiSheet = false },
        viewModel = viewModel,
        onPickDocument = onPickDocument
    )

    ExternalAiSettingsSheet(
        expanded = showExternalAiSheet,
        onDismiss = { showExternalAiSheet = false },
        viewModel = viewModel
    )

    FineTuningSheet(
        expanded = showFineTuningSheet,
        onDismiss = { showFineTuningSheet = false },
        viewModel = viewModel
    )

    ChatHistorySheet(
        expanded = showChatHistorySheet,
        onDismiss = { showChatHistorySheet = false },
        viewModel = viewModel
    )
}

// Model unavailable

@Composable
private fun ModelUnavailablePrompt(
    sidePadding: Dp,
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
internal fun ModelOptionCard(
    icon: (@Composable () -> Unit)? = null,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                icon()
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            if (trailing != null) {
                trailing()
            }
        }
    }
}

// Empty state

@Composable
private fun EmptyState(
    sidePadding: Dp,
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
            LaunchedEffect(Unit) { delay((80L * (index + 1)).milliseconds); chipVisible = true }

            val chipAlpha  by animateFloatAsState(if (chipVisible) 1f else 0f, tween(250), label = "a$index")
            val chipOffset by animateDpAsState(if (chipVisible) 0.dp else 10.dp, tween(250), label = "o$index")

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
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
fun ChatBubble(
    message: ChatMessage,
    onEditClick: (() -> Unit)? = null
) {
    if (message.text.isEmpty() && !message.isUser) return

    val isUser  = message.isUser
    val bgColor = if (isUser) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    val shape = if (isUser) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
    else         RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    val boxAlign = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val columnAlign = if (isUser) Alignment.End else Alignment.Start
    val widthMod = if (isDesktopPlatform) Modifier.fillMaxWidth(0.85f) else Modifier.widthIn(max = 300.dp)
    val clipboardManager = LocalClipboardManager.current

    Box(Modifier.fillMaxWidth(), contentAlignment = boxAlign) {
        Column(horizontalAlignment = columnAlign) {
            Box(
                modifier = Modifier
                    .then(widthMod)
                    .clip(shape)
                    .background(bgColor)
                    .animateContentSize(animationSpec = tween(120))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                MarkdownText(
                    text  = message.text,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Row(
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MessageActionIcon(
                    icon = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    onClick = { clipboardManager.setText(AnnotatedString(message.text)) }
                )
                if (onEditClick != null) {
                    MessageActionIcon(
                        icon = Icons.Default.Edit,
                        contentDescription = "Edit prompt",
                        onClick = onEditClick
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(15.dp)
        )
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
    isGenerating: Boolean,
    onStopGeneration: () -> Unit,
    hazeState: HazeState,
    viewModel: RagViewModel,
    sharedTransitionScope: SharedTransitionScope?,
    chatAnimatedVisibilityScope: AnimatedVisibilityScope?,
    onSettingsClick: () -> Unit,
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
    val barShape = RoundedCornerShape(28.dp)

    val minHeight = 96.dp
    val bottomInset = 6.dp
    val horizontalInset = 16.dp

    val isMorphing = chatAnimatedVisibilityScope?.transition?.isRunning == true
    val shadowElevation by animateDpAsState(
        targetValue = if (isMorphing) 0.dp else 8.dp,
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

    val innerColumnModifier = if (sharedTransitionScope != null) {
        with(sharedTransitionScope) {
            Modifier
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .skipToLookaheadSize()
        }
    } else {
        Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
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

    // FIX: was `Surface(color = defaultBgColor, ...)`. Surface appends its own background paint
    // AFTER the modifier chain you pass in, so it painted a flat opaque rectangle on top of the
    // hazeEffect blur — that's what caused the "unblurs / goes translucent" artifact. Switching to
    // a plain Box lets us control paint order ourselves: hazeEffect first (captures + blurs
    // whatever's behind), then background() on top of that blur — same pattern as DailyTopBar
    // in DailyScreen.kt.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(bottom = bottomInset)
            .padding(horizontal = horizontalInset)
            .heightIn(min = minHeight)
            .then(sharedPillModifier)
            .customInlyShadow(barShape, elevation = shadowElevation)
            .animateContentSize(animationSpec = tween(150))
            .clip(barShape)
            .hazeEffect(state = hazeState, style = HazeStyle.Unspecified, block = null)
            .background(defaultBgColor)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = barShape
            )
    ) {
        Column(modifier = innerColumnModifier) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "Ask anything..",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable(enabled = true, onClick = onSettingsClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "AI settings",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .size(18.dp)
                                .then(aiIconModifier)
                        )
                    }
                    ModelPickerPill(viewModel = viewModel)
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isGenerating) MaterialTheme.colorScheme.primary else sendColor)
                        .clickable(
                            enabled = isGenerating || canSend,
                            onClick = if (isGenerating) onStopGeneration else onSubmit
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isGenerating) Icons.Default.Pause else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = if (isGenerating) "Stop generating" else "Send",
                        tint = if (isGenerating || canSend) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelPickerPill(viewModel: RagViewModel) {
    val aiGenerationMode by viewModel.aiGenerationMode.collectAsState()
    val selectedExternalAiProvider by viewModel.selectedExternalAiProvider.collectAsState()
    val installedLocalModels by viewModel.installedLocalModels.collectAsState()
    val selectedLocalModelFileName by viewModel.selectedLocalModelFileName.collectAsState()
    var showPicker by remember { mutableStateOf(false) }

    var selectedExternalModelName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedExternalAiProvider, aiGenerationMode) {
        selectedExternalModelName = viewModel.getExternalAiConfig(selectedExternalAiProvider)?.model
    }

    val label = if (aiGenerationMode == AiGenerationMode.LOCAL) {
        installedLocalModels.find { it.fileName == selectedLocalModelFileName }?.displayName ?: "Local"
    } else {
        selectedExternalModelName?.takeIf { it.isNotBlank() } ?: selectedExternalAiProvider.displayName
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable { showPicker = true }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 110.dp)
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "Choose AI model",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    InlyBottomSheet(
        expanded = showPicker,
        onDismiss = { showPicker = false },
        title = "Choose AI Model",
    ) { closeAnd ->
        val externalConfigs = remember { mutableStateMapOf<ExternalAiProvider, ExternalAiProviderConfig?>() }
        var configsLoaded by remember { mutableStateOf(false) }

        LaunchedEffect(showPicker) {
            if (!showPicker) return@LaunchedEffect
            viewModel.refreshInstalledLocalModels()
            ExternalAiProvider.entries.forEach { provider ->
                externalConfigs[provider] = viewModel.getExternalAiConfig(provider)
            }
            configsLoaded = true
        }

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            installedLocalModels.forEach { model ->
                ModelOptionCard(
                    icon = {
                        Icon(
                            Icons.Default.AutoAwesome,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    title = model.displayName,
                    subtitle = null,
                    onClick = { closeAnd { viewModel.selectLocalModel(model.fileName) } },
                    trailing = {
                        if (aiGenerationMode == AiGenerationMode.LOCAL && selectedLocalModelFileName == model.fileName) {
                            ActiveIndicator()
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
            }

            if (configsLoaded) {
                ExternalAiProvider.entries.forEach { provider ->
                    val config = externalConfigs[provider]
                    val isConfigured = !config?.apiKey.isNullOrBlank()
                    if (isConfigured) {
                        ModelOptionCard(
                            icon = {
                                Icon(
                                    Icons.Default.Key,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            title = config?.model?.takeIf { it.isNotBlank() } ?: provider.displayName,
                            subtitle = null,
                            onClick = { closeAnd { viewModel.selectExternalProvider(provider) } },
                            trailing = {
                                if (aiGenerationMode == AiGenerationMode.EXTERNAL && selectedExternalAiProvider == provider) {
                                    ActiveIndicator()
                                }
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
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
    viewModel: RagViewModel,
    onLocalAiClick: () -> Unit,
    onExternalAiClick: () -> Unit,
    onFineTuningClick: () -> Unit
) {
    val aiGenerationMode by viewModel.aiGenerationMode.collectAsState()
    val selectedExternalAiProvider by viewModel.selectedExternalAiProvider.collectAsState()
    val knowledgeMode by viewModel.knowledgeMode.collectAsState()

    InlyBottomSheet(
        expanded = expanded,
        onDismiss = onDismiss,
        title = "AI Settings",
    ) { _ ->

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            ModelOptionCard(
                title = "Local AI",
                subtitle = "Runs fully on-device. Private & offline.",
                onClick = onLocalAiClick
            )
            Spacer(Modifier.height(10.dp))
            ModelOptionCard(
                title = "External AI",
                subtitle = if (aiGenerationMode == AiGenerationMode.EXTERNAL)
                    "Active — ${selectedExternalAiProvider.displayName}"
                else
                    "Connect an external provider with your own API key.",
                onClick = onExternalAiClick
            )
            Spacer(Modifier.height(10.dp))
            ModelOptionCard(
                title = "Fine-tuning",
                subtitle = knowledgeMode.displayName,
                onClick = onFineTuningClick
            )
        }
    }
}

@Composable
internal fun ActiveIndicator() {
    Icon(
        Icons.Default.Check,
        contentDescription = "Active",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp)
    )
}

// External AI settings sheet

@Composable
private fun ExternalAiSettingsSheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: RagViewModel
) {
    InlyBottomSheet(
        expanded = expanded,
        onDismiss = onDismiss,
        title = "External AI",
        subtitle = "Connect an external provider using your own API key.",
    ) { closeAnd ->
        var isReady by remember { mutableStateOf(false) }
        var selectedProvider by remember { mutableStateOf(ExternalAiProvider.OPENAI) }
        var loadedConfig by remember { mutableStateOf<ExternalAiProviderConfig?>(null) }
        var apiKeyInput by remember { mutableStateOf("") }
        var modelInput by remember { mutableStateOf("") }
        var baseUrlInput by remember { mutableStateOf("") }
        var isApiKeyVisible by remember { mutableStateOf(false) }
        var showDeleteConfirm by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            selectedProvider = viewModel.selectedExternalAiProvider.value
            isReady = true
        }

        LaunchedEffect(selectedProvider, isReady) {
            if (!isReady) return@LaunchedEffect
            val config = viewModel.getExternalAiConfig(selectedProvider)
            loadedConfig = config
            modelInput = config?.model.orEmpty()
            baseUrlInput = config?.baseUrl.orEmpty()
            apiKeyInput = ""
        }

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                text = "Provider",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ExternalAiProvider.entries.forEach { provider ->
                    ProviderChip(
                        label = provider.displayName,
                        selected = provider == selectedProvider,
                        onClick = { selectedProvider = provider }
                    )
                }
            }

            if (selectedProvider == ExternalAiProvider.CUSTOM) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Base URL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                InlyTextField(
                    value = baseUrlInput,
                    onValueChange = { baseUrlInput = it },
                    placeholder = "https://your-endpoint.example.com/v1",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Model",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(8.dp))
            InlyTextField(
                value = modelInput,
                onValueChange = { modelInput = it },
                placeholder = selectedProvider.defaultModel ?: "Enter a model name",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = "API Key",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(8.dp))
            InlyTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                placeholder = if (loadedConfig?.apiKey?.isNotBlank() == true)
                    "API key saved — enter a new key to replace"
                else
                    "Enter your ${selectedProvider.displayName} API key",
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

            val hasUsableKey =
                apiKeyInput.isNotBlank() || loadedConfig?.apiKey?.isNotBlank() == true
            val hasUsableEndpoint =
                selectedProvider != ExternalAiProvider.CUSTOM || baseUrlInput.isNotBlank()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (loadedConfig != null) {
                    InlyButtonSecondary(
                        text = "Delete",
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.weight(1f)
                    )
                }
                InlyButtonPrimary(
                    text = "Save",
                    enabled = hasUsableKey && hasUsableEndpoint,
                    onClick = {
                        val configToSave = ExternalAiProviderConfig(
                            apiKey = apiKeyInput.ifBlank { loadedConfig?.apiKey.orEmpty() },
                            model = modelInput.ifBlank { selectedProvider.defaultModel.orEmpty() },
                            baseUrl = baseUrlInput.ifBlank { null },
                            updatedAt = System.currentTimeMillis()
                        )
                        viewModel.saveExternalAiConfig(selectedProvider, configToSave)
                        closeAnd { }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Delete this API key?", style = MaterialTheme.typography.titleLarge) },
                    text = {
                        Text(
                            "The saved key and settings for this provider will be removed from this device.",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteExternalAiConfig(selectedProvider)
                            loadedConfig = null
                            apiKeyInput = ""
                            modelInput = ""
                            baseUrlInput = ""
                            showDeleteConfirm = false
                        }) {
                            Text("Delete", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Cancel", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                )
            }
        }
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
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
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

// Fine-tuning sheet

@Composable
private fun FineTuningSheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: RagViewModel
) {
    val selectedMode by viewModel.knowledgeMode.collectAsState()
    val selectedMaxOutputTokens by viewModel.maxOutputTokens.collectAsState()

    InlyBottomSheet(
        expanded = expanded,
        onDismiss = onDismiss,
        title = "Fine-tuning",
    ) { closeAnd ->

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                text = "Knowledge Source",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(8.dp))
            KnowledgeModeOption(
                title = "Default",
                subtitle = "Combines your notes with real-world knowledge.",
                selected = selectedMode == KnowledgeMode.DEFAULT,
                onClick = { closeAnd { viewModel.selectKnowledgeMode(KnowledgeMode.DEFAULT) } }
            )
            KnowledgeModeOption(
                title = "Only notes knowledge",
                subtitle = "Answers strictly from your notes — no outside knowledge.",
                selected = selectedMode == KnowledgeMode.NOTES_ONLY,
                onClick = { closeAnd { viewModel.selectKnowledgeMode(KnowledgeMode.NOTES_ONLY) } }
            )
            KnowledgeModeOption(
                title = "Only real-world knowledge",
                subtitle = "Acts like a regular chatbot — doesn't use your notes at all.",
                selected = selectedMode == KnowledgeMode.WORLD_ONLY,
                onClick = { closeAnd { viewModel.selectKnowledgeMode(KnowledgeMode.WORLD_ONLY) } }
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Response Length",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(8.dp))
            responseLengthOptions.forEach { option ->
                KnowledgeModeOption(
                    title = option.label,
                    subtitle = option.subtitle,
                    selected = selectedMaxOutputTokens == option.tokens,
                    onClick = { closeAnd { viewModel.selectMaxOutputTokens(option.tokens) } }
                )
            }
        }
    }
}

private data class ResponseLengthOption(val label: String, val subtitle: String, val tokens: Int)

private val responseLengthOptions = listOf(
    ResponseLengthOption("Short", "Quick, concise answers.", 512),
    ResponseLengthOption("Balanced", "A good mix of detail and brevity.", 1024),
    ResponseLengthOption("Long", "More thorough, detailed answers.", 2048),
    ResponseLengthOption("Max", "The longest answers this device/provider allows.", 4096)
)

@Composable
private fun KnowledgeModeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            if (selected) ActiveIndicator()
        }
    }
}

// Chat history sheet

@Composable
private fun ChatHistorySheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: RagViewModel
) {
    val sessions by viewModel.sessions.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()

    InlyBottomSheet(
        expanded = expanded,
        onDismiss = onDismiss,
        title = "Chat History",
    ) { closeAnd ->
        var searchQuery by remember { mutableStateOf("") }

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Spacer(Modifier.height(10.dp))
            InlyTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search chats",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Transparent,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { closeAnd { viewModel.clearChat() } }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "New Chat",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )
            Spacer(Modifier.height(10.dp))

            val filteredSessions = remember(sessions, searchQuery) {
                if (searchQuery.isBlank()) sessions
                else sessions.filter { it.title.contains(searchQuery, ignoreCase = true) }
            }

            Text(
                text = "Recent Chats",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(8.dp))

            if (filteredSessions.isEmpty()) {
                Text(
                    text = if (searchQuery.isBlank()) "No chats yet." else "No chats match your search.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    filteredSessions.forEach { session ->
                        ChatSessionRow(
                            session = session,
                            isActive = session.id == currentSessionId,
                            onClick = { closeAnd { viewModel.loadSession(session.id) } },
                            onRename = { newTitle -> viewModel.renameSession(session.id, newTitle) },
                            onDelete = { viewModel.deleteSession(session.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatSessionRow(
    session: ChatSession,
    isActive: Boolean,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = session.messages.lastOrNull()?.text.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isActive) ActiveIndicator()
            Box {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Chat options",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { showMenu = true }
                )
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            showMenu = false
                            showRenameDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            showDeleteConfirm = true
                        }
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        var titleInput by remember { mutableStateOf(session.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename chat", style = MaterialTheme.typography.titleLarge) },
            text = {
                InlyTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    placeholder = "Chat name",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(titleInput)
                    showRenameDialog = false
                }) {
                    Text("Save", style = MaterialTheme.typography.bodyLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", style = MaterialTheme.typography.bodyLarge)
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this chat?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "This chat and its history will be permanently deleted.",
                    style = MaterialTheme.typography.labelSmall
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("Delete", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", style = MaterialTheme.typography.bodyLarge)
                }
            }
        )
    }
}