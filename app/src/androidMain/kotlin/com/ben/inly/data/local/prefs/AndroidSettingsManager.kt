package com.ben.inly.data.local.prefs

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import androidx.core.content.edit

class AndroidSettingsManager(
    private val sharedPreferences: SharedPreferences
) : SettingsManager {

    override val sortTypeFlow: Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == SyncConstants.KEY_SORT_TYPE) {
                trySend(prefs.getString(SyncConstants.KEY_SORT_TYPE, SyncConstants.DEFAULT_SORT_TYPE) ?: SyncConstants.DEFAULT_SORT_TYPE)
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

        trySend(sharedPreferences.getString(SyncConstants.KEY_SORT_TYPE, SyncConstants.DEFAULT_SORT_TYPE) ?: SyncConstants.DEFAULT_SORT_TYPE)

        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override val sortOrderFlow: Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == SyncConstants.KEY_SORT_ORDER) {
                trySend(prefs.getString(SyncConstants.KEY_SORT_ORDER, SyncConstants.DEFAULT_SORT_ORDER) ?: SyncConstants.DEFAULT_SORT_ORDER)
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

        trySend(sharedPreferences.getString(SyncConstants.KEY_SORT_ORDER, SyncConstants.DEFAULT_SORT_ORDER) ?: SyncConstants.DEFAULT_SORT_ORDER)

        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override val lastOpenedDesktopStateFlow: Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == SyncConstants.KEY_LAST_OPENED_STATE) {
                trySend(prefs.getString(SyncConstants.KEY_LAST_OPENED_STATE, "") ?: "")
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

        trySend(sharedPreferences.getString(SyncConstants.KEY_LAST_OPENED_STATE, "") ?: "")

        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override fun saveSortSettings(type: String, order: String) {
        sharedPreferences.edit {
            putString(SyncConstants.KEY_SORT_TYPE, type)
                .putString(SyncConstants.KEY_SORT_ORDER, order)
        }
    }

    override fun saveLastOpenedDesktopState(state: String) {
        sharedPreferences.edit {
            putString(SyncConstants.KEY_LAST_OPENED_STATE, state)
        }
    }

    override fun getLastSyncTimestamp(): Long {
        return sharedPreferences.getLong(SyncConstants.KEY_SYNC_TIMESTAMP, 0L)
    }

    override fun saveLastSyncTimestamp(timestamp: Long) {
        sharedPreferences.edit(commit = true) { putLong(SyncConstants.KEY_SYNC_TIMESTAMP, timestamp) }
    }

    override fun getSelfHostLastSyncTimestamp(): Long {
        return sharedPreferences.getLong(SyncConstants.KEY_SELF_HOST_SYNC_TIMESTAMP, 0L)
    }

    override fun saveSelfHostLastSyncTimestamp(timestamp: Long) {
        sharedPreferences.edit { putLong(SyncConstants.KEY_SELF_HOST_SYNC_TIMESTAMP, timestamp) }
    }

    override fun getSelfHostSupportsETags(): Boolean? {
        if (!sharedPreferences.contains(SyncConstants.KEY_SELF_HOST_SUPPORTS_ETAGS)) return null
        return sharedPreferences.getBoolean(SyncConstants.KEY_SELF_HOST_SUPPORTS_ETAGS, false)
    }

    override fun saveSelfHostSupportsETags(supports: Boolean) {
        sharedPreferences.edit { putBoolean(SyncConstants.KEY_SELF_HOST_SUPPORTS_ETAGS, supports) }
    }

    override fun getSelfHostManifestEtag(): String? {
        return sharedPreferences.getString(SyncConstants.KEY_SELF_HOST_MANIFEST_ETAG, null)
    }

    override fun saveSelfHostManifestEtag(etag: String?) {
        sharedPreferences.edit { putString(SyncConstants.KEY_SELF_HOST_MANIFEST_ETAG, etag) }
    }

    override fun getSyncAuthToken(): String {
        return sharedPreferences.getString(SyncConstants.KEY_SYNC_AUTH_TOKEN, "") ?: ""
    }

    override fun saveSyncAuthToken(token: String) {
        sharedPreferences.edit { putString(SyncConstants.KEY_SYNC_AUTH_TOKEN, token) }
    }

    override fun getSyncIpAddress(): String {
        return sharedPreferences.getString(SyncConstants.KEY_SYNC_IP_ADDRESS, "") ?: ""
    }

    override fun saveSyncIpAddress(ip: String) {
        sharedPreferences.edit { putString(SyncConstants.KEY_SYNC_IP_ADDRESS, ip) }
    }

    override fun getSyncPort(): Int {
        return sharedPreferences.getInt(SyncConstants.KEY_SYNC_PORT, SyncConstants.DEFAULT_PORT)
    }

    override fun saveSyncPort(port: Int) {
        sharedPreferences.edit { putInt(SyncConstants.KEY_SYNC_PORT, port) }
    }

    override fun getSyncEncryptionKey(): String {
        return sharedPreferences.getString(SyncConstants.KEY_SYNC_ENCRYPTION_KEY, "") ?: ""
    }

    override fun saveSyncEncryptionKey(key: String) {
        sharedPreferences.edit { putString(SyncConstants.KEY_SYNC_ENCRYPTION_KEY, key) }
    }

    override fun clearSyncPairing() {
        sharedPreferences.edit {
            putString(SyncConstants.KEY_SYNC_AUTH_TOKEN, "")
            putString(SyncConstants.KEY_SYNC_IP_ADDRESS, "")
            putString(SyncConstants.KEY_SYNC_ENCRYPTION_KEY, "")
            putInt(SyncConstants.KEY_SYNC_PORT, SyncConstants.DEFAULT_PORT)
        }
    }

    // Automatic backups
    private val _autoBackupEnabled = MutableStateFlow(sharedPreferences.getBoolean("KEY_AUTO_BACKUP", false))
    private val _backupFrequency = MutableStateFlow(sharedPreferences.getString("KEY_BACKUP_FREQ", "Daily") ?: "Daily")
    private val _backupDirectoryUri = MutableStateFlow(
        sharedPreferences.getString("KEY_BACKUP_DIR", null)?.takeIf { it.isNotBlank() }
    )

    override val autoBackupEnabledFlow: Flow<Boolean> = _autoBackupEnabled
    override val backupFrequencyFlow: Flow<String> = _backupFrequency
    override val backupDirectoryUriFlow: Flow<String?> = _backupDirectoryUri

    override fun saveAutoBackupEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("KEY_AUTO_BACKUP", enabled) }
        _autoBackupEnabled.value = enabled
    }

    override fun saveBackupFrequency(frequency: String) {
        sharedPreferences.edit { putString("KEY_BACKUP_FREQ", frequency) }
        _backupFrequency.value = frequency
    }

    override fun saveBackupDirectory(uriString: String) {
        sharedPreferences.edit { putString("KEY_BACKUP_DIR", uriString) }
        _backupDirectoryUri.value = uriString
    }

    private val _backupTime = MutableStateFlow(sharedPreferences.getString("KEY_BACKUP_TIME", "02:00") ?: "02:00")
    private val _backupDay = MutableStateFlow(sharedPreferences.getString("KEY_BACKUP_DAY", "Sunday") ?: "Sunday")

    override val backupTimeFlow: Flow<String> = _backupTime
    override val backupDayFlow: Flow<String> = _backupDay

    override fun saveBackupTime(time: String) {
        sharedPreferences.edit { putString("KEY_BACKUP_TIME", time) }
        _backupTime.value = time
    }

    override fun saveBackupDay(day: String) {
        sharedPreferences.edit { putString("KEY_BACKUP_DAY", day) }
        _backupDay.value = day
    }

    // panel resizing
    override val desktopSidebarWidthFlow: Flow<Float> = MutableStateFlow(340f)
    override fun saveDesktopSidebarWidth(width: Float) { /* desktop-only */ }

    private val _calendarViewMode = MutableStateFlow(
        sharedPreferences.getString(SyncConstants.KEY_CALENDAR_VIEW_MODE, SyncConstants.DEFAULT_CALENDAR_VIEW_MODE)
            ?: SyncConstants.DEFAULT_CALENDAR_VIEW_MODE
    )
    override val calendarViewModeFlow: Flow<String> = _calendarViewMode

    override fun saveCalendarViewMode(mode: String) {
        sharedPreferences.edit { putString(SyncConstants.KEY_CALENDAR_VIEW_MODE, mode) }
        _calendarViewMode.value = mode
    }

    private val _fontSizePreference = MutableStateFlow(
        sharedPreferences.getString(SyncConstants.KEY_FONT_SIZE_PREFERENCE, SyncConstants.DEFAULT_FONT_SIZE_PREFERENCE)
            ?: SyncConstants.DEFAULT_FONT_SIZE_PREFERENCE
    )
    override val fontSizePreferenceFlow: Flow<String> = _fontSizePreference

    override fun saveFontSizePreference(preference: String) {
        sharedPreferences.edit { putString(SyncConstants.KEY_FONT_SIZE_PREFERENCE, preference) }
        _fontSizePreference.value = preference
    }

    private val _fontStylePreference = MutableStateFlow(
        sharedPreferences.getString(SyncConstants.KEY_FONT_STYLE_PREFERENCE, SyncConstants.DEFAULT_FONT_STYLE_PREFERENCE)
            ?: SyncConstants.DEFAULT_FONT_STYLE_PREFERENCE
    )
    override val fontStylePreferenceFlow: Flow<String> = _fontStylePreference

    override fun saveFontStylePreference(preference: String) {
        sharedPreferences.edit { putString(SyncConstants.KEY_FONT_STYLE_PREFERENCE, preference) }
        _fontStylePreference.value = preference
    }

    private val _subNoteOpenMode = MutableStateFlow(
        sharedPreferences.getString(SyncConstants.KEY_SUBNOTE_OPEN_MODE, SyncConstants.DEFAULT_SUBNOTE_OPEN_MODE)
            ?: SyncConstants.DEFAULT_SUBNOTE_OPEN_MODE
    )
    override val subNoteOpenModeFlow: Flow<String> = _subNoteOpenMode

    override fun saveSubNoteOpenMode(mode: String) {
        sharedPreferences.edit { putString(SyncConstants.KEY_SUBNOTE_OPEN_MODE, mode) }
        _subNoteOpenMode.value = mode
    }
}