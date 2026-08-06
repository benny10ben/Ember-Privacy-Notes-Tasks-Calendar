package com.ben.ember.domain.selfhost.sync

import android.util.Log

actual object SelfHostSyncLog {
    private const val TAG = "EmberSyncEngine"

    actual fun d(message: String) {
        Log.d(TAG, message)
    }

    actual fun e(message: String, throwable: Throwable?) {
        Log.e(TAG, message, throwable)
    }
}