package com.ben.inly.domain.util

interface ImageDownloader {
    suspend fun downloadImage(sourceFilePath: String, displayName: String): Boolean
}
