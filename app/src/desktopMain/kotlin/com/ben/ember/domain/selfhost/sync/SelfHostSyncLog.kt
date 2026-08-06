package com.ben.ember.domain.selfhost.sync

actual object SelfHostSyncLog {
    private const val TAG = "EmberSyncEngine"

    actual fun d(message: String) {
        println("[$TAG] $message")
    }

    actual fun e(message: String, throwable: Throwable?) {
        println("[$TAG] $message")
        throwable?.printStackTrace()
    }
}