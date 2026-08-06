package com.ben.ember.domain.ai.models

actual fun resolveModelPath(fileName: String): String = "/data/data/com.ben.ember/files/$fileName"
actual fun modelFileExists(path: String): Boolean {
    val f = java.io.File(path)
    return f.exists() && f.length() > 0
}