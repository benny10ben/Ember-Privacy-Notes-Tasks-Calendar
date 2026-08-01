package com.ben.inly.domain.ai.models

import java.io.File

private fun deletionMarkerPath(fileName: String) = "${resolveModelPath(fileName)}.deleted"

actual fun softDeleteModelFile(fileName: String): Boolean {
    val file = File(resolveModelPath(fileName))
    if (!file.exists()) return false
    val marker = File(deletionMarkerPath(fileName))
    if (marker.exists()) marker.delete()
    return file.renameTo(marker)
}

actual fun restoreModelFile(fileName: String): Boolean {
    val marker = File(deletionMarkerPath(fileName))
    if (!marker.exists()) return false
    val file = File(resolveModelPath(fileName))
    if (file.exists()) file.delete()
    return marker.renameTo(file)
}

actual fun hasPendingModelDeletion(fileName: String): Boolean = File(deletionMarkerPath(fileName)).exists()

actual fun cleanupPendingModelDeletions() {
    File(resolveModelPath("")).listFiles { file -> file.name.endsWith(".deleted") }?.forEach { it.delete() }
}
