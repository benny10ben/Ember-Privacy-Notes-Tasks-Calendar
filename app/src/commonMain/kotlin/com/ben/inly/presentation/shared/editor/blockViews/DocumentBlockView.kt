package com.ben.inly.presentation.shared.editor.blockViews

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ben.inly.domain.model.DocumentBlock
import com.ben.inly.presentation.shared.editor.DefaultBlockShape
import inly.app.generated.resources.Res
import inly.app.generated.resources.file_text
import org.jetbrains.compose.resources.painterResource

private fun documentFormatLabel(fileName: String, mimeType: String?): String {
    val extension = fileName.substringAfterLast('.', "").uppercase()
    if (extension.isNotEmpty() && extension.length <= 5) return extension
    return when (mimeType) {
        "application/pdf" -> "PDF"
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "DOCX"
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "XLSX"
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "PPTX"
        "image/png" -> "PNG"
        "image/jpeg" -> "JPEG"
        "image/gif" -> "GIF"
        "image/webp" -> "WEBP"
        "text/plain" -> "TXT"
        "application/zip" -> "ZIP"
        else -> "FILE"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentBlockView(
    block: DocumentBlock,
    inSelectionMode: Boolean,
    onToggleSelection: () -> Unit,
    onRequestPicker: () -> Unit,
    onOpenFile: (filePath: String, mimeType: String) -> Unit)
{
    if (block.localFilePath == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .defaultMinSize(minHeight = 52.dp)
                .clip(DefaultBlockShape)
                .background(MaterialTheme.colorScheme.surface)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (inSelectionMode) onToggleSelection()
                        else onRequestPicker()
                    },
                    onLongClick = onToggleSelection
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                Icon(painterResource(Res.drawable.file_text), contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Attach a file", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(DefaultBlockShape)
                .background(MaterialTheme.colorScheme.surface)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (inSelectionMode) {
                            onToggleSelection()
                        } else {
                            block.localFilePath?.let { path ->
                                onOpenFile(path, block.mimeType ?: "*/*")
                            }
                        }
                    },
                    onLongClick = onToggleSelection
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 48.dp, top = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = block.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${block.fileSizeString}    ${documentFormatLabel(block.fileName, block.mimeType)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(18.dp)
            )
        }
    }
}