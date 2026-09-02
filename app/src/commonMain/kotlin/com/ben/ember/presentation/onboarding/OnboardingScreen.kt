package com.ben.ember.presentation.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ben.ember.domain.util.isDesktopPlatform
import com.ben.ember.presentation.shared.components.EmberButtonPrimary
import com.ben.ember.presentation.shared.components.EmberButtonSecondary
import com.ben.ember.presentation.shared.components.EmberBlur
import com.ben.ember.presentation.shared.components.emberBlur
import com.ben.ember.presentation.shared.stableStatusBarsPadding
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel()
) {
    val steps = remember {
        buildList<@Composable () -> Unit> {
            add { OnboardingWelcomeStep() }
            add { OnboardingFeaturesStep() }
            add { OnboardingPrivacyStep() }
            add { OnboardingAppearanceStep(viewModel) }
            add { OnboardingAiStep(viewModel) }
            if (isDesktopPlatform) add { OnboardingDesktopPreferencesStep(viewModel) }
            add { OnboardingFinishStep() }
        }
    }

    var currentStepIndex by rememberSaveable { mutableIntStateOf(0) }
    val isLastStep = currentStepIndex == steps.lastIndex

    val hazeState = remember { HazeState() }
    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableFloatStateOf(0f) }
    var bottomBarHeightPx by remember { mutableFloatStateOf(0f) }
    val topBarHeightDp = with(density) { topBarHeightPx.toDp() }
    val bottomBarHeightDp = with(density) { bottomBarHeightPx.toDp() }

    fun goToNextStep() {
        if (isLastStep) {
            viewModel.deletePreviewNoteIfExists()
            viewModel.completeOnboarding()
            onFinished()
        } else {
            if (currentStepIndex == 0) {
                viewModel.ensurePreviewNoteCreated()
            }
            currentStepIndex++
        }
    }

    fun goToPreviousStep() {
        if (currentStepIndex > 0) currentStepIndex--
    }

    fun skipOnboarding() {
        viewModel.deletePreviewNoteIfExists()
        viewModel.completeOnboarding()
        onFinished()
    }

    if (isDesktopPlatform) {
        val wizardPaneWeight by animateFloatAsState(
            targetValue = if (currentStepIndex == 0) 1f else 0.5f,
            animationSpec = tween(600),
            label = "wizard-pane-weight"
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            val wizardWidth = maxWidth * wizardPaneWeight
            val editorWidth = maxWidth - wizardWidth
            val editorAlpha = ((1f - wizardPaneWeight) / 0.5f).coerceIn(0f, 1f)

            Row(modifier = Modifier.fillMaxSize()) {
                OnboardingWizardPane(
                    modifier = Modifier.fillMaxHeight().width(wizardWidth),
                    steps = steps,
                    currentStepIndex = currentStepIndex,
                    isLastStep = isLastStep,
                    hazeState = hazeState,
                    topBarHeightDp = topBarHeightDp,
                    bottomBarHeightDp = bottomBarHeightDp,
                    onTopBarHeightChanged = { topBarHeightPx = it },
                    onBottomBarHeightChanged = { bottomBarHeightPx = it },
                    onBack = ::goToPreviousStep,
                    onNext = ::goToNextStep,
                    onSkip = ::skipOnboarding
                )

                OnboardingEditorPreview(
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(editorWidth)
                        .graphicsLayer(alpha = editorAlpha)
                )
            }
        }
    } else {
        OnboardingWizardPane(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            steps = steps,
            currentStepIndex = currentStepIndex,
            isLastStep = isLastStep,
            hazeState = hazeState,
            topBarHeightDp = topBarHeightDp,
            bottomBarHeightDp = bottomBarHeightDp,
            onTopBarHeightChanged = { topBarHeightPx = it },
            onBottomBarHeightChanged = { bottomBarHeightPx = it },
            onBack = ::goToPreviousStep,
            onNext = ::goToNextStep,
            onSkip = ::skipOnboarding
        )
    }
}

@Composable
private fun OnboardingWizardPane(
    modifier: Modifier,
    steps: List<@Composable () -> Unit>,
    currentStepIndex: Int,
    isLastStep: Boolean,
    hazeState: HazeState,
    topBarHeightDp: androidx.compose.ui.unit.Dp,
    bottomBarHeightDp: androidx.compose.ui.unit.Dp,
    onTopBarHeightChanged: (Float) -> Unit,
    onBottomBarHeightChanged: (Float) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .padding(top = topBarHeightDp, bottom = bottomBarHeightDp)
        ) {
            AnimatedContent(
                targetState = currentStepIndex,
                transitionSpec = {
                    val movingForward = targetState >= initialState
                    val enter = fadeIn(tween(500)) + slideInHorizontally(tween(500)) { width ->
                        if (movingForward) width / 5 else -width / 5
                    }
                    val exit = fadeOut(tween(400)) + slideOutHorizontally(tween(400)) { width ->
                        if (movingForward) -width / 5 else width / 5
                    }
                    enter togetherWith exit
                },
                label = "onboarding-step"
            ) { stepIndex ->
                steps[stepIndex]()
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(10f)
                .onGloballyPositioned { coordinates -> onTopBarHeightChanged(coordinates.size.height.toFloat()) }
        ) {
            OnboardingProgressBar(
                progress = (currentStepIndex + 1) / steps.size.toFloat(),
                showSkip = !isLastStep,
                onSkip = onSkip,
                hazeState = hazeState
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .zIndex(10f)
                .onGloballyPositioned { coordinates -> onBottomBarHeightChanged(coordinates.size.height.toFloat()) }
        ) {
            OnboardingNavigationBar(
                showBack = currentStepIndex > 0,
                isLastStep = isLastStep,
                onBack = onBack,
                onNext = onNext,
                hazeState = hazeState
            )
        }
    }
}

@Composable
private fun OnboardingProgressBar(
    progress: Float,
    showSkip: Boolean,
    onSkip: () -> Unit,
    hazeState: HazeState
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .emberBlur(hazeState, EmberBlur.Regular)
            .then(if (isDesktopPlatform) Modifier else Modifier.stableStatusBarsPadding())
            .padding(top = 16.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(100)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap
            )

            if (showSkip) {
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Skip",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { onSkip() }
                )
            }
        }
    }
}

@Composable
private fun OnboardingNavigationBar(
    showBack: Boolean,
    isLastStep: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    hazeState: HazeState
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .emberBlur(hazeState, EmberBlur.Regular)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showBack) {
                EmberButtonSecondary(
                    text = "Back",
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                )
            }

            EmberButtonPrimary(
                text = if (isLastStep) "Start Using Ember" else "Next",
                onClick = onNext,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
