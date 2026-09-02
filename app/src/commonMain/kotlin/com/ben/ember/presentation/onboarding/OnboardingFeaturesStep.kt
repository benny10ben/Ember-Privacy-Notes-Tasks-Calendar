package com.ben.ember.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import ember.app.generated.resources.Res
import ember.app.generated.resources.calendar_clock
import ember.app.generated.resources.refresh_cw
import ember.app.generated.resources.timer_reset
import org.jetbrains.compose.resources.painterResource

@Composable
fun OnboardingFeaturesStep() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            OnboardingHeader(
                title = "Everything In One Place",
                subtitle = "Ember combines notes, journaling and reminders into a single block-based editor."
            )

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OnboardingPastelCard(
                        color = OnboardingPastelColors.Blue,
                        icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Notes),
                        title = "Block-Based Editor",
                        description = "Mix text, checklists, tables, images, voice notes and documents in a single note.",
                        modifier = Modifier.weight(1f)
                    )
                    OnboardingPastelCard(
                        color = OnboardingPastelColors.Amber,
                        icon = painterResource(Res.drawable.calendar_clock),
                        title = "Daily Journaling",
                        description = "A running daily log that automatically carries unfinished tasks into today.",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OnboardingPastelCard(
                        color = OnboardingPastelColors.Green,
                        icon = painterResource(Res.drawable.timer_reset),
                        title = "Universal Reminders",
                        description = "Turn any checkbox into an exact-time reminder that notifies you.",
                        modifier = Modifier.weight(1f)
                    )
                    OnboardingPastelCard(
                        color = OnboardingPastelColors.Purple,
                        icon = painterResource(Res.drawable.refresh_cw),
                        title = "LAN Sync",
                        description = "Sync directly between your own devices over your local network.",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
