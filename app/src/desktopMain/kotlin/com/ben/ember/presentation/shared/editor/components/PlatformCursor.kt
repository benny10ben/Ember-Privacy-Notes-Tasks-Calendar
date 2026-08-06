package com.ben.ember.presentation.shared.editor.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import org.jetbrains.skiko.Cursor

actual fun Modifier.desktopPointerCursor(cursor: DesktopCursor): Modifier {
    val awtCursor = when (cursor) {
        DesktopCursor.HAND -> Cursor(Cursor.HAND_CURSOR)
        DesktopCursor.RESIZE_HORIZONTAL -> Cursor(Cursor.E_RESIZE_CURSOR)
    }
    return this.pointerHoverIcon(PointerIcon(awtCursor))
}