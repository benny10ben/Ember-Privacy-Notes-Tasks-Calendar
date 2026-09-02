package com.ben.emberr.data.worker

interface BackupRescheduler {
    fun rescheduleNow(frequency: String, time: String, day: String)
    fun cancel()
}