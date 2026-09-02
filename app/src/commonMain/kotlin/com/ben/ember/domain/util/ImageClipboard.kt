package com.ben.ember.domain.util

expect object ImageClipboard {
    suspend fun copyImageToClipboard(filePath: String): Boolean
}
