package com.ben.ember.data.worker

interface BackupRescheduler {
    fun rescheduleNow(frequency: String, time: String, day: String)
    fun cancel()
}