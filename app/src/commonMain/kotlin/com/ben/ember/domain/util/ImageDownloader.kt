package com.ben.ember.domain.util

interface ImageDownloader {
    suspend fun downloadImage(sourceFilePath: String, displayName: String): Boolean
}
