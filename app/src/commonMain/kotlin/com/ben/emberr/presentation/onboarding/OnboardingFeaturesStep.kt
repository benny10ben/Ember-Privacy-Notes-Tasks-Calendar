package com.ben.emberr.presentation.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import emberr.app.generated.resources.Res
import emberr.app.generated.resources.calendar_clock
import emberr.app.generated.resources.refresh_cw
import emberr.app.generated.resources.timer_reset
import org.jetbrains.compose.resources.painterResource

@Composable
fun OnboardingFeaturesStep() {
    OnboardingStepPage {
        OnboardingHeader(
            title = "Everything In One Place",
            subtitle = null
        )

        OnboardingHighlightGrid(
            highlights = listOf(
                OnboardingHighlight(
                    color = OnboardingPastelColors.Blue,
                    icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Notes),
                    title = "Block-Based Editor",
                    description = "Mix text, checklists, tables, images, voice notes and documents in one note."
                ),
                OnboardingHighlight(
                    color = OnboardingPastelColors.Amber,
                    icon = painterResource(Res.drawable.calendar_clock),
                    title = "Daily Journaling",
                    description = "A running daily log that carries unfinished tasks into today."
                ),
                OnboardingHighlight(
                    color = OnboardingPastelColors.Green,
                    icon = painterResource(Res.drawable.timer_reset),
                    title = "Universal Reminders",
                    description = "Turn any checkbox into an exact-time reminder that notifies you."
                ),
                OnboardingHighlight(
                    color = OnboardingPastelColors.Purple,
                    icon = painterResource(Res.drawable.refresh_cw),
                    title = "LAN Sync",
                    description = "Sync directly between your own devices over your local network."
                )
            )
        )
    }
}
