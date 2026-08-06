package com.ben.ember.domain.ai.models

/**
 * Resolves the absolute path to a model file on the current platform.
 *
 * Android (dev):    /data/data/com.ben.ember/files/<fileName>
 *                   (uploaded manually via Device File Explorer)
 *
 * Android (prod):   Will point to Context.filesDir once OTA download lands.
 *
 * Desktop (dev):    ~/.ember/models/<fileName>
 *                   (copied manually during development)
 *
 * Desktop (prod):   Same ~/.ember/models/ path, populated by OTA downloader.
 *                   No code change needed — just the download logic fills it.
 */
expect fun resolveModelPath(fileName: String): String
expect fun modelFileExists(path: String): Boolean