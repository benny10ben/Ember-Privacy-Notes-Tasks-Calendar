package com.ben.ember.presentation.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ember.app.generated.resources.Res
import ember.app.generated.resources.astroid
import org.jetbrains.compose.resources.painterResource

@Composable
fun OnboardingAiStep(viewModel: OnboardingViewModel) {
    val aiFeaturesDisabled by viewModel.aiFeaturesDisabled.collectAsState()

    OnboardingStepScaffold(
        icon = painterResource(Res.drawable.astroid),
        title = "Your AI Assistant",
        description = "Ember can answer questions about your own notes using a private, on-device assistant. Nothing leaves your device unless you add your own API key later."
    ) {
        OnboardingToggleCard(
            title = "Enable AI Assistant",
            description = "You can turn this off anytime from Settings.",
            checked = !aiFeaturesDisabled,
            onCheckedChange = { enabled -> viewModel.setAiFeaturesDisabled(!enabled) }
        )
    }
}
