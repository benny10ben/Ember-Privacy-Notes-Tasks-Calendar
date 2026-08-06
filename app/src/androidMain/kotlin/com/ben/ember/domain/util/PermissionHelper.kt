package com.ben.ember.domain.util

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
actual fun rememberMicrophonePermissionLauncher(
    onResult: (Boolean) -> Unit
): () -> Unit {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Mic permission denied", Toast.LENGTH_SHORT).show()
        }
        onResult(isGranted)
    }

    return {
        val isAlreadyGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (isAlreadyGranted) {
            Toast.makeText(context, "Permission was already granted!", Toast.LENGTH_SHORT).show()
            onResult(true)
        } else {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}