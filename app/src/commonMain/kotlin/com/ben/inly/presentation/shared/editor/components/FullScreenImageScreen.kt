package com.ben.inly.presentation.shared.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ben.inly.presentation.shared.components.KmpBackHandler
import com.ben.inly.presentation.shared.components.TopBarIconButton
import com.ben.inly.presentation.shared.editor.DefaultBlockShape
import com.ben.inly.presentation.shared.stableStatusBarsPadding
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import org.jetbrains.compose.resources.painterResource
import inly.app.generated.resources.Res
import inly.app.generated.resources.chevron_left
import inly.app.generated.resources.download
import inly.app.generated.resources.trash

@Composable
fun FullScreenImageScreen(
    request: Any?,
    hasLocalFile: Boolean,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    // ✅ 1. Replaced with your native KMP back handler
    KmpBackHandler(enabled = true) { onBack() }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val hazeState = remember { HazeState() }
    val pillColor = MaterialTheme.colorScheme.background.copy(alpha = 0.65f)
    val tint = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ✅ 1. Wrap the Image in its own Box and put the .haze modifier HERE
        Box(modifier = Modifier.fillMaxSize().haze(state = hazeState).background(MaterialTheme.colorScheme.background)) {
            AsyncImage(
                model = request,
                contentDescription = "Full Screen Image",
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else scale = 2.5f
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                val maxX = (size.width * (scale - 1)) / 2
                                val maxY = (size.height * (scale - 1)) / 2
                                offset = Offset(
                                    x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                                )
                            } else offset = Offset.Zero
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                contentScale = ContentScale.Fit
            )
        } // End of Haze Capture Box

        // ✅ 2. Your Top Bar and Bottom Bar overlays remain exactly the same below this!

        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .stableStatusBarsPadding()
                .padding(top = 18.dp, start = 18.dp, end = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TopBarIconButton(
                icon = painterResource(Res.drawable.chevron_left),
                contentDescription = "Back",
                bgColor = pillColor,
                tint = MaterialTheme.colorScheme.onSurface,
                hazeState = hazeState,
                onClick = onBack
            )
        }

        // Bottom Bar
        // ✅ 3. Swapped Surface for Box and corrected the modifier chain
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp, start = 24.dp, end = 24.dp)
                .clip(DefaultBlockShape)
                .hazeChild(state = hazeState)
                .background(pillColor) // Background MUST come after hazeChild
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shape = DefaultBlockShape
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                val iconSize = 18.dp

                Icon(
                    painter = painterResource(Res.drawable.download),
                    contentDescription = "Download",
                    modifier = Modifier.size(iconSize).clickable {
                        if (hasLocalFile) onDownload()
                    },
                    tint = tint
                )

                Box(Modifier.width(1.dp).height(18.dp).background(tint.copy(alpha = 0.2f)))

                Icon(
                    painter = painterResource(Res.drawable.trash),
                    contentDescription = "Delete",
                    modifier = Modifier.size(iconSize).clickable {
                        onDelete()
                        onBack()
                    },
                    tint = tint
                )
            }
        }
    }
}