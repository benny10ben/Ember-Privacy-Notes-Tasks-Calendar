package com.ben.ember.presentation.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter

@Composable
fun OnboardingFinishStep() {
    OnboardingStepScaffold(
        icon = rememberVectorPainter(Icons.Default.CheckCircle),
        title = "You're All Set",
        description = "Ember is ready to go. Everything stays on this device — start capturing your first note."
    )
}
