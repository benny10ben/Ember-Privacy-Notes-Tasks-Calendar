package com.ben.inly.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ben.inly.data.local.prefs.SettingsManager
import com.ben.inly.data.worker.BackupRescheduler
import com.ben.inly.domain.model.backup.InlyBackupData
import com.ben.inly.domain.repository.BackupRepository
import com.ben.inly.domain.repository.NoteRepository
import com.ben.inly.domain.util.SyncEventBus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

class SettingsViewModel(
    private val backupRepository: BackupRepository,
    private val noteRepository: NoteRepository,
    private val settingsManager: SettingsManager,
    private val backupRescheduler: BackupRescheduler
) : ViewModel() {

    // A safe JSON parser that won't crash if future app versions add new fields
    private val safeJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val autoBackupEnabled: StateFlow<Boolean> = settingsManager.autoBackupEnabledFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val backupFrequency: StateFlow<String> = settingsManager.backupFrequencyFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Daily"
    )

    val backupDirectoryUri: StateFlow<String?> = settingsManager.backupDirectoryUriFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun setAutoBackupEnabled(enabled: Boolean) {
        settingsManager.saveAutoBackupEnabled(enabled)
    }

    suspend fun getBackupJson(): String {
        val backupData = backupRepository.createBackupData()
        return safeJson.encodeToString(backupData)
    }

    fun setBackupDirectory(uriString: String) {
        settingsManager.saveBackupDirectory(uriString)
    }

    val backupTime: StateFlow<String> = settingsManager.backupTimeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "02:00"
    )

    val backupDay: StateFlow<String> = settingsManager.backupDayFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Sunday"
    )

    fun saveBackupSchedule(frequency: String, time: String, day: String) {
        settingsManager.saveBackupFrequency(frequency)
        settingsManager.saveBackupTime(time)
        settingsManager.saveBackupDay(day)
        backupRescheduler.rescheduleNow(frequency, time, day)
    }

    val fontSizePreference: StateFlow<String> = settingsManager.fontSizePreferenceFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.ben.inly.data.local.prefs.SyncConstants.DEFAULT_FONT_SIZE_PREFERENCE
    )

    fun setFontSizePreference(preference: String) {
        settingsManager.saveFontSizePreference(preference)
    }

    val fontStylePreference: StateFlow<String> = settingsManager.fontStylePreferenceFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.ben.inly.data.local.prefs.SyncConstants.DEFAULT_FONT_STYLE_PREFERENCE
    )

    fun setFontStylePreference(preference: String) {
        settingsManager.saveFontStylePreference(preference)
    }

    val subNoteOpenMode: StateFlow<String> = settingsManager.subNoteOpenModeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.ben.inly.data.local.prefs.SyncConstants.DEFAULT_SUBNOTE_OPEN_MODE
    )

    fun setSubNoteOpenMode(mode: String) {
        settingsManager.saveSubNoteOpenMode(mode)
    }

    /**
     * Parses the JSON backup and merges it into the local database.
     */
    suspend fun mergeBackupJson(jsonString: String) {
        val backupData = safeJson.decodeFromString<InlyBackupData>(jsonString)

        // FUTURE MIGRATION CHECK:
        // If backupData.version > 1, pass it through a migration mapper here
        // before handing it to the repository to ensure old data structures
        // are correctly converted to the newest schema.

        /** val migratedData = when (backupData.version) {
        1 -> runMigrationV1toV2(backupData) // Manually re-map the old structure to the new one
        2 -> backupData
        else -> backupData
        } */

        backupRepository.restoreBackup(backupData)

        // Wipe the stale memory caches so the next read hits the raw Room DB!
        noteRepository.clearCaches()

        // Tell the UI an import just finished so it can reload immediately
        kotlinx.coroutines.delay(100.milliseconds) // Brief pause to ensure DB transactions settle
        SyncEventBus.emitSyncCompleted("import_complete")
    }
}