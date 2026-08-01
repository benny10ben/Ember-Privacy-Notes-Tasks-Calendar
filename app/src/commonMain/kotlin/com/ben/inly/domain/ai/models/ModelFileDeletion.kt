package com.ben.inly.domain.ai.models

expect fun softDeleteModelFile(fileName: String): Boolean
expect fun restoreModelFile(fileName: String): Boolean
expect fun hasPendingModelDeletion(fileName: String): Boolean
expect fun cleanupPendingModelDeletions()
