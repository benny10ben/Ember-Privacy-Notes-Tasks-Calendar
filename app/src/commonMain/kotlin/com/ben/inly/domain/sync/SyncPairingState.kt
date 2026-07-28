package com.ben.inly.domain.sync

import com.ben.inly.data.local.prefs.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SyncPairingState(private val settingsManager: SettingsManager) {
    private val _isPaired = MutableStateFlow(settingsManager.getSyncAuthToken().isNotBlank())
    val isPaired = _isPaired.asStateFlow()

    fun markPaired() {
        _isPaired.value = true
    }

    fun unpairLocally() {
        settingsManager.clearSyncPairing()
        _isPaired.value = false
    }
}
