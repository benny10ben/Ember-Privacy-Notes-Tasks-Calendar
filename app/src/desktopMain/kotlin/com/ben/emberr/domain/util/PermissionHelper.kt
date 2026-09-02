package com.ben.emberr.domain.util

import androidx.compose.runtime.Composable

@Composable
actual fun rememberMicrophonePermissionLauncher(
    onResult: (Boolean) -> Unit
): () -> Unit {
    // Desktop platforms generally don't require explicit runtime permission
    // requests in the same way mobile platforms do.
    // We return a lambda that immediately grants the permission.
    return {
        onResult(true)
    }
}