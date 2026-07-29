package com.ben.inly.presentation.shared.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

fun Modifier.customInlyShadow(shape: Shape): Modifier = this.shadow(
    elevation = 14.dp,
    shape = shape,
    spotColor = Color.Black.copy(alpha = 0.35f),
    ambientColor = Color.Black.copy(alpha = 0.20f)
)

// indication = null is unreliable on Compose Multiplatform (known ripple-resolution quirk) -
// an explicit no-op IndicationNodeFactory consistently suppresses the ripple on both Android and
// desktop. Modifier.Node-based per the current (non-deprecated) Indication API.
object NoRippleIndicationNodeFactory : IndicationNodeFactory {
    private class NoRippleIndicationNode : Modifier.Node(), DrawModifierNode {
        override fun ContentDrawScope.draw() {
            drawContent()
        }
    }

    override fun create(interactionSource: InteractionSource): DelegatableNode = NoRippleIndicationNode()

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = -1
}

@Composable
fun TopBarIconButton(
    icon: Painter,
    contentDescription: String,
    bgColor: Color,
    tint: Color,
    hazeState: HazeState? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = bgColor,
        contentColor = tint,
        modifier = Modifier
            .size(44.dp)
            .customInlyShadow(CircleShape)
            .clip(CircleShape)
            .then(if (hazeState == null) Modifier else Modifier.hazeEffect(hazeState, HazeStyle.Unspecified, null))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = NoRippleIndicationNodeFactory,
                onClick = onClick
            )
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = CircleShape
            )
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                painter = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun TopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    bgColor: Color,
    tint: Color,
    hazeState: HazeState? = null,
    onClick: () -> Unit
) {
    TopBarIconButton(
        icon = rememberVectorPainter(icon),
        contentDescription = contentDescription,
        bgColor = bgColor,
        tint = tint,
        hazeState = hazeState,
        onClick = onClick
    )
}

data class TopBarIconButtonItem(
    val icon: Painter,
    val contentDescription: String,
    val onClick: () -> Unit
)

// Wraps multiple icons in a single pill Surface - shares one bg/shadow/border/haze instead of
// each icon getting its own circle, while each icon keeps its own independent click target.
@Composable
fun TopBarIconButtonGroup(
    items: List<TopBarIconButtonItem>,
    bgColor: Color,
    tint: Color,
    hazeState: HazeState? = null,
    horizontalPadding: Dp = 6.dp
) {
    Surface(
        shape = CircleShape,
        color = bgColor,
        contentColor = tint,
        modifier = Modifier
            .height(44.dp)
            .customInlyShadow(CircleShape)
            .clip(CircleShape)
            .then(if (hazeState == null) Modifier else Modifier.hazeEffect(hazeState, HazeStyle.Unspecified, null))
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = CircleShape
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = NoRippleIndicationNodeFactory,
                            onClick = item.onClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = item.icon,
                        contentDescription = item.contentDescription,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}