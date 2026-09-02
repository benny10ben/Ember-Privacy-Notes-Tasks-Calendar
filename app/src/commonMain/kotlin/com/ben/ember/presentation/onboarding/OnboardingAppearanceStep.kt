package com.ben.ember.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ben.ember.presentation.shared.components.SelectedOptionBackground
import com.ben.ember.ui.theme.FontStylePreference
import com.ben.ember.ui.theme.fontFamilyFor
import ember.app.generated.resources.Res
import ember.app.generated.resources.palette
import org.jetbrains.compose.resources.painterResource

private val OnboardingFontSizeOptions = listOf(
    "SMALL" to "Small",
    "DEFAULT" to "Default",
    "LARGE" to "Large"
)

@Composable
fun OnboardingAppearanceStep(viewModel: OnboardingViewModel) {
    val fontStyleName by viewModel.fontStylePreference.collectAsState()
    val fontSizeName by viewModel.fontSizePreference.collectAsState()
    val selectedFontStyle = runCatching { FontStylePreference.valueOf(fontStyleName) }
        .getOrDefault(FontStylePreference.POPPINS)

    OnboardingStepScaffold(
        icon = painterResource(Res.drawable.palette),
        title = "Make It Yours",
        description = "Pick the font and text size you'll enjoy reading the most."
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FontStylePreference.entries.chunked(3).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    rowOptions.forEach { option ->
                        OnboardingFontStyleOption(
                            option = option,
                            isSelected = option == selectedFontStyle,
                            onClick = { viewModel.setFontStylePreference(option.name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OnboardingSegmentedControl(
            options = OnboardingFontSizeOptions,
            selectedKey = fontSizeName,
            onSelect = viewModel::setFontSizePreference
        )
    }
}

@Composable
private fun OnboardingFontStyleOption(
    option: FontStylePreference,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(108.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (isSelected) SelectedOptionBackground else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Aa",
                fontFamily = fontFamilyFor(option),
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = option.displayName,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
