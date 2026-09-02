package com.ben.emberr.data.worker

class DesktopBackupRescheduler : BackupRescheduler {
    override fun rescheduleNow(frequency: String, time: String, day: String) = Unit
    override fun cancel() = Unit
}