package com.ben.ember.presentation.rag

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.ben.ember.domain.ai.models.ModelDownloadProgress
import com.ben.ember.domain.ai.models.ModelFileNames
import com.ben.ember.presentation.shared.components.EmberAlertDialog
import com.ben.ember.presentation.shared.components.EmberBottomSheet
import com.ben.ember.presentation.shared.components.EmberButtonPrimary
import com.ben.ember.presentation.shared.components.EmberButtonSecondary
import com.ben.ember.presentation.shared.components.EmberTextField

@Composable
internal fun LocalAiSettingsSheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: RagViewModel,
    onPickDocument: (onPathSelected: (String) -> Unit) -> Unit
) {
    var isResumable by remember { mutableStateOf(false) }
    val downloadProgress by viewModel.localGeneratorDownloadProgress.collectAsState()
    val uploadState by viewModel.localModelUploadState.collectAsState()
    val uploadProgress by viewModel.localModelUploadProgress.collectAsState()
    val installedLocalModels by viewModel.installedLocalModels.collectAsState()
    val selectedLocalModelFileName by viewModel.selectedLocalModelFileName.collectAsState()
    val pendingDeletionFileNames by viewModel.pendingDeletionLocalModelFileNames.collectAsState()
    val localContextLength by viewModel.localContextLength.collectAsState()
    val finetuneWarning by viewModel.uploadedModelFinetuneWarning.collectAsState()
    var showUploadWarning by remember { mutableStateOf(false) }
    var contextLengthInput by remember(localContextLength) { mutableStateOf(localContextLength.toString()) }

    val isDefaultModelInstalled = installedLocalModels.any { it.fileName == ModelFileNames.GENERATOR }

    LaunchedEffect(expanded) {
        if (expanded) {
            viewModel.refreshInstalledLocalModels()
            isResumable = viewModel.hasResumableGeneratorDownload()
        } else {
            viewModel.resetLocalModelUploadState()
        }
    }

    LaunchedEffect(downloadProgress) {
        if (downloadProgress is ModelDownloadProgress.Failed || downloadProgress is ModelDownloadProgress.Paused) {
            isResumable = viewModel.hasResumableGeneratorDownload()
        }
        if (downloadProgress is ModelDownloadProgress.Completed) {
            isResumable = false
        }
    }

    EmberBottomSheet(
        expanded = expanded,
        onDismiss = onDismiss,
        title = "Local AI",
        subtitle = "Manage the on-device models used to generate replies.",
    ) { _ ->
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            ModelOptionCard(
                icon = null,
                title = if (isResumable && downloadProgress !is ModelDownloadProgress.Downloading)
                    "Resume qwen2.5-1.5b-instruct-q8_0 download"
                else
                    "Download qwen2.5-1.5b-instruct-q8_0",
                subtitle = when (val progress = downloadProgress) {
                    is ModelDownloadProgress.Downloading -> "Downloading… ${(progress.fraction * 100).toInt()}%"
                    is ModelDownloadProgress.Failed -> if (isResumable) "${progress.message} Tap to resume." else progress.message
                    ModelDownloadProgress.Paused -> "Paused — tap to resume the download."
                    else -> when {
                        isDefaultModelInstalled -> "Recommended — already downloaded."
                        isResumable -> "Paused — tap to resume the download."
                        else -> "Recommended — balanced quality and speed."
                    }
                },
                onClick = {
                    if (downloadProgress !is ModelDownloadProgress.Downloading && !isDefaultModelInstalled) {
                        viewModel.downloadGeneratorModel()
                    }
                }
            )

            val currentDownloadProgress = downloadProgress
            if (currentDownloadProgress is ModelDownloadProgress.Downloading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { currentDownloadProgress.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
                TextButton(
                    onClick = { viewModel.pauseGeneratorModelDownload() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pause Download", style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(10.dp))

            ModelOptionCard(
                icon = null,
                title = "Upload your own model",
                subtitle = when (val upload = uploadState) {
                    LocalModelUploadState.Uploading -> "Copying model file… ${(uploadProgress * 100).toInt()}%"
                    LocalModelUploadState.Success -> "Uploaded successfully."
                    is LocalModelUploadState.Failed -> upload.message
                    LocalModelUploadState.Idle -> "Add another custom GGUF model from your device."
                },
                onClick = {
                    if (uploadState !is LocalModelUploadState.Uploading) showUploadWarning = true
                }
            )

            if (uploadState is LocalModelUploadState.Uploading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { uploadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Context Length",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            EmberTextField(
                value = contextLengthInput,
                onValueChange = { input ->
                    val digitsOnly = input.filter { it.isDigit() }
                    contextLengthInput = digitsOnly
                    digitsOnly.toIntOrNull()?.let { tokens ->
                        if (tokens > 0) viewModel.selectLocalContextLength(tokens)
                    }
                },
                placeholder = "4096",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Applies to whichever local model is currently selected — the default (4096) suits the bundled Qwen model. Using a different model? Set this to its actual training context length.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            if (installedLocalModels.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Installed models",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(8.dp))

                installedLocalModels.forEach { model ->
                    val isPendingDeletion = model.fileName in pendingDeletionFileNames
                    ModelOptionCard(
                        title = model.displayName,
                        subtitle = when {
                            isPendingDeletion -> "Deleted — will be removed permanently on next app restart."
                            model.fileName == selectedLocalModelFileName -> "Currently used to generate replies."
                            else -> "Tap to use this model."
                        },
                        onClick = {
                            if (!isPendingDeletion) viewModel.selectLocalModel(model.fileName)
                        },
                        trailing = {
                            if (isPendingDeletion) {
                                TextButton(onClick = { viewModel.restoreLocalModel(model.fileName) }) {
                                    Text("Restore", style = MaterialTheme.typography.bodyLarge)
                                }
                            } else {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete model",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { viewModel.deleteLocalModel(model.fileName) }
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }

    if (showUploadWarning) {
        EmberAlertDialog(
            onDismissRequest = { showUploadWarning = false },
            title = "Uploading a custom model"
        ) {
            Text(
                text = "Heavy or high-parameter models can use a lot of RAM and may freeze or crash your device. Only upload a model you know your device can handle.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EmberButtonSecondary(
                    text = "Cancel",
                    onClick = { showUploadWarning = false },
                    modifier = Modifier.weight(1f)
                )
                EmberButtonPrimary(
                    text = "Proceed",
                    onClick = {
                        showUploadWarning = false
                        onPickDocument { path -> viewModel.uploadGeneratorModel(path) }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (finetuneWarning != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUploadedModelFinetuneWarning() },
            title = {
                Text("Unverified model type", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Text(finetuneWarning.orEmpty(), style = MaterialTheme.typography.labelSmall)
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissUploadedModelFinetuneWarning() }) {
                    Text("Got it", style = MaterialTheme.typography.bodyLarge)
                }
            }
        )
    }
}
