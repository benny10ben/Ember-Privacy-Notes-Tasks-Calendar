package com.ben.emberr.presentation.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import emberr.app.generated.resources.Res
import emberr.app.generated.resources.folder_sync
import emberr.app.generated.resources.shield_alert
import org.jetbrains.compose.resources.painterResource

@Composable
fun OnboardingPrivacyStep() {
    OnboardingStepPage {
        OnboardingHeader(
            title = "Private By Design",
            subtitle = null
        )

        OnboardingHighlightGrid(
            highlights = listOf(
                OnboardingHighlight(
                    color = OnboardingPastelColors.Green,
                    icon = painterResource(Res.drawable.shield_alert),
                    title = "Local-First",
                    description = "Every note is processed and stored on this device, never uploaded by default."
                ),
                OnboardingHighlight(
                    color = OnboardingPastelColors.Purple,
                    icon = rememberVectorPainter(Icons.Default.VisibilityOff),
                    title = "No Trackers, No Ads",
                    description = "Emberr collects no analytics and shows no ads."
                ),
                OnboardingHighlight(
                    color = OnboardingPastelColors.Blue,
                    icon = rememberVectorPainter(Icons.Default.Lock),
                    title = "Encrypted Storage",
                    description = "Your note content is encrypted at rest on disk."
                ),
                OnboardingHighlight(
                    color = OnboardingPastelColors.Amber,
                    icon = painterResource(Res.drawable.folder_sync),
                    title = "Private Sync",
                    description = "LAN sync is secured with a key derived only on your own devices."
                )
            )
        )
    }
}
