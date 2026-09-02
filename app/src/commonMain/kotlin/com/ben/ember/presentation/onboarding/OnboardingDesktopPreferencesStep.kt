package com.ben.ember.presentation.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ben.ember.presentation.shared.SubNoteOpenMode
import ember.app.generated.resources.Res
import ember.app.generated.resources.sidebar
import org.jetbrains.compose.resources.painterResource

@Composable
fun OnboardingDesktopPreferencesStep(viewModel: OnboardingViewModel) {
    val subNoteOpenModeName by viewModel.subNoteOpenMode.collectAsState()
    val showScrollbar by viewModel.showScrollbar.collectAsState()
    val selectedSubNoteOpenMode = runCatching { SubNoteOpenMode.valueOf(subNoteOpenModeName) }
        .getOrDefault(SubNoteOpenMode.SIDE_PANEL)

    OnboardingStepScaffold(
        icon = painterResource(Res.drawable.sidebar),
        title = "Desktop Layout",
        description = "A couple of extra touches for the desktop app."
    ) {
        OnboardingSubNoteModePreview(mode = selectedSubNoteOpenMode)

        Spacer(modifier = Modifier.height(20.dp))

        OnboardingSegmentedControl(
            options = SubNoteOpenMode.entries.map { it.name to it.displayName },
            selectedKey = subNoteOpenModeName,
            onSelect = viewModel::setSubNoteOpenMode
        )

        Spacer(modifier = Modifier.height(20.dp))

        OnboardingToggleCard(
            title = "Show Scrollbar",
            description = "Shows scrollbars in the sidebar, editor and note lists.",
            checked = showScrollbar,
            onCheckedChange = viewModel::setShowScrollbar
        )
    }
}

@Composable
private fun OnboardingSubNoteModePreview(mode: SubNoteOpenMode) {
    Box(
        modifier = Modifier
            .size(width = 280.dp, height = 170.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
    ) {
        Crossfade(targetState = mode, animationSpec = tween(300), label = "subnote-preview") { currentMode ->
            val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                when (currentMode) {
                    SubNoteOpenMode.SIDE_PANEL -> Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .fillMaxWidth(0.32f)
                            .background(fillColor)
                    )
                    SubNoteOpenMode.CENTER_DIALOG -> Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxHeight(0.65f)
                            .fillMaxWidth(0.55f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(fillColor)
                    )
                    SubNoteOpenMode.FULL_RIGHT_PANEL -> Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .fillMaxWidth(0.58f)
                            .background(fillColor)
                    )
                }
            }
        }
    }
}
