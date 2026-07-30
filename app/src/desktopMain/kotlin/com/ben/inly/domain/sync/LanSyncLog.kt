package com.ben.inly.domain.sync

actual object LanSyncLog {
    private const val TAG = "InlyLanSync"

    actual fun d(message: String) {
        println("[$TAG] $message")
    }

    actual fun e(message: String, throwable: Throwable?) {
        println("[$TAG] $message")
        throwable?.printStackTrace()
    }
}
