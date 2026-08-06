package com.ben.ember.data.worker

class DesktopBackupRescheduler : BackupRescheduler {
    override fun rescheduleNow(frequency: String, time: String, day: String) = Unit
    override fun cancel() = Unit
}