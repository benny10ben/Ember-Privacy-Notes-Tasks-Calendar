package com.ben.inly.presentation.rag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ben.inly.presentation.shared.components.InlyButtonPrimary

@Composable
internal fun EmbeddingSetupScreen(
    state: EmbeddingSetupState,
    sidePadding: Dp,
    isResumable: Boolean,
    onDownloadClick: () -> Unit,
    onPauseClick: () -> Unit,
    onProceedClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = sidePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val isSuccess = state is EmbeddingSetupState.DownloadComplete
        Icon(
            if (isSuccess) Icons.Default.Check else Icons.Default.Download,
            contentDescription = null,
            tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = when (state) {
                EmbeddingSetupState.Required -> if (isResumable) "Resume embedding model download" else "Embedding model required"
                is EmbeddingSetupState.Downloading -> if (isResumable) "Resuming embedding model download…" else "Downloading embedding model…"
                is EmbeddingSetupState.DownloadFailed -> "Download interrupted"
                EmbeddingSetupState.DownloadComplete -> "Download successful"
                is EmbeddingSetupState.Indexing -> "Indexing your notes…"
                else -> ""
            },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = when (state) {
                EmbeddingSetupState.Required -> if (isResumable)
                    "Your last download was interrupted. Tap resume to continue where it left off."
                else
                    "To use this AI feature, please download an embedding model. It powers semantic search across your notes and runs fully on-device."
                is EmbeddingSetupState.Downloading -> "${(state.progress * 100).toInt()}% — please keep the app open."
                is EmbeddingSetupState.DownloadFailed -> if (isResumable)
                    "${state.message} Tap resume to continue from where it stopped."
                else
                    state.message
                EmbeddingSetupState.DownloadComplete -> "Tap proceed to index your existing notes so the AI can search them."
                is EmbeddingSetupState.Indexing -> if (state.total > 0) "${state.completed} of ${state.total} notes indexed" else "Preparing your notes…"
                else -> ""
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        when (state) {
            is EmbeddingSetupState.Downloading -> {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
                TextButton(onClick = onPauseClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Pause Download", style = MaterialTheme.typography.bodyLarge)
                }
            }

            is EmbeddingSetupState.Indexing -> {
                val fraction = if (state.total > 0) state.completed / state.total.toFloat() else 0f
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            }

            EmbeddingSetupState.Required -> {
                InlyButtonPrimary(
                    text = if (isResumable) "Resume Download" else "Download Model",
                    onClick = onDownloadClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            is EmbeddingSetupState.DownloadFailed -> {
                InlyButtonPrimary(
                    text = if (isResumable) "Resume Download" else "Try Again",
                    onClick = onDownloadClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            EmbeddingSetupState.DownloadComplete -> {
                InlyButtonPrimary(text = "Proceed", onClick = onProceedClick, modifier = Modifier.fillMaxWidth())
            }

            else -> Unit
        }
    }
}
