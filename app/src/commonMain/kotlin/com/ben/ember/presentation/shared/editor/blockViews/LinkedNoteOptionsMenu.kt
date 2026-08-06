package com.ben.ember.presentation.shared.editor.blockViews
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.ben.ember.domain.model.LinkedNoteBlock
import com.ben.ember.domain.util.isDesktopPlatform
import com.ben.ember.presentation.shared.components.EmberBottomSheet
import com.ben.ember.presentation.shared.components.EmberDesktopMenu
@Composable
fun LinkedNoteOptionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    block: LinkedNoteBlock,
    onUpdateOptions: (showIcon: Boolean, showCoverImage: Boolean) -> Unit
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 0.dp).padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Show icon",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = block.showIcon,
                onCheckedChange = { showIcon -> onUpdateOptions(showIcon, block.showCoverImage) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Show cover image",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = block.showCoverImage,
                onCheckedChange = { showCover -> onUpdateOptions(block.showIcon, showCover) }
            )
        }
    }
    if (isDesktopPlatform) {
        EmberDesktopMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true),
            modifier = Modifier.width(260.dp)
        ) {
            content()
        }
    } else {
        EmberBottomSheet(
            expanded = expanded,
            onDismiss = onDismiss,
            title = "Preview Options"
        ) { _ ->
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 26.dp)) {
                content()
            }}
    }
}