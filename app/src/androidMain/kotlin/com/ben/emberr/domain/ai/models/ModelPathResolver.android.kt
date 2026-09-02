package com.ben.emberr.domain.ai.models

actual fun resolveModelPath(fileName: String): String = "/data/data/com.ben.emberr/files/$fileName"
actual fun modelFileExists(path: String): Boolean {
    val f = java.io.File(path)
    return f.exists() && f.length() > 0
}