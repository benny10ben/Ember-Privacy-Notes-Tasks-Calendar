package com.ben.emberr.domain.util

import androidx.compose.runtime.Composable

@Composable
expect fun rememberMicrophonePermissionLauncher(
    onResult: (Boolean) -> Unit
): () -> Unit