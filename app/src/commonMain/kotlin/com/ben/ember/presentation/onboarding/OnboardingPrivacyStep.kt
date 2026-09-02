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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import ember.app.generated.resources.Res
import ember.app.generated.resources.folder_sync
import ember.app.generated.resources.shield_alert
import org.jetbrains.compose.resources.painterResource

@Composable
fun OnboardingPrivacyStep() {
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
                title = "Private By Design",
                subtitle = "Ember has no accounts and no cloud requirement. Your notes stay on your device."
            )

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OnboardingPastelCard(
                        color = OnboardingPastelColors.Green,
                        icon = painterResource(Res.drawable.shield_alert),
                        title = "Local-First",
                        description = "Every note is processed and stored on this device, not uploaded by default.",
                        modifier = Modifier.weight(1f)
                    )
                    OnboardingPastelCard(
                        color = OnboardingPastelColors.Purple,
                        icon = rememberVectorPainter(Icons.Default.VisibilityOff),
                        title = "No Trackers, No Ads",
                        description = "Ember collects no analytics and shows no ads.",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OnboardingPastelCard(
                        color = OnboardingPastelColors.Blue,
                        icon = rememberVectorPainter(Icons.Default.Lock),
                        title = "Encrypted Storage",
                        description = "Your note content is encrypted at rest on disk.",
                        modifier = Modifier.weight(1f)
                    )
                    OnboardingPastelCard(
                        color = OnboardingPastelColors.Amber,
                        icon = painterResource(Res.drawable.folder_sync),
                        title = "Private Sync",
                        description = "LAN sync is secured with a key derived only on your own devices.",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
