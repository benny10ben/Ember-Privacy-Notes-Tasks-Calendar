package com.ben.inly.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ben.inly.core.security.SyncEncryptionManager
import com.ben.inly.core.security.SyncHmacSigner
import com.ben.inly.data.local.prefs.SettingsManager
import com.ben.inly.domain.sync.SyncClient
import com.ben.inly.domain.sync.SyncPairingData
import com.ben.inly.domain.sync.SyncPairingState
import com.ben.inly.domain.sync.SyncRepository
import com.ben.inly.domain.util.isDesktopPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

class SyncViewModel(
    private val syncRepository: SyncRepository,
    private val settingsManager: SettingsManager,
    private val hmacSigner: SyncHmacSigner,
    private val syncEncryptionManager: SyncEncryptionManager,
    private val pairingState: SyncPairingState
) : ViewModel() {

    private val _syncStatus = MutableStateFlow("Idle")
    val syncStatus = _syncStatus.asStateFlow()

    val isPaired = pairingState.isPaired

    fun resetSyncStatus() {
        _syncStatus.value = "Idle"
    }

    fun generatePairingData(): SyncPairingData {
        val token = generateSecureToken()
        val encryptionKey = generateSecureToken() + generateSecureToken()
        settingsManager.saveSyncAuthToken(token)
        settingsManager.saveSyncEncryptionKey(encryptionKey)
        pairingState.markPaired()
        return SyncPairingData(
            ipAddress = getLocalNetworkIp(),
            port = settingsManager.getSyncPort(),
            authToken = token,
            encryptionKey = encryptionKey
        )
    }

    fun applyScannedPairing(pairingData: SyncPairingData) {
        settingsManager.saveSyncIpAddress(pairingData.ipAddress)
        settingsManager.saveSyncPort(pairingData.port)
        settingsManager.saveSyncAuthToken(pairingData.authToken)
        settingsManager.saveSyncEncryptionKey(pairingData.encryptionKey)
        pairingState.markPaired()
    }

    fun unpair() {
        viewModelScope.launch {
            val isCurrentlyPaired = settingsManager.getSyncIpAddress().isNotBlank() &&
                settingsManager.getSyncAuthToken().isNotBlank()

            if (!isDesktopPlatform && isCurrentlyPaired) {
                withTimeoutOrNull(3_000L) {
                    SyncClient(settingsManager, hmacSigner, syncEncryptionManager).requestUnpair()
                }
            }

            pairingState.unpairLocally()
            _syncStatus.value = "Idle"
        }
    }

    fun triggerManualSync() {
        viewModelScope.launch {
            val ip = settingsManager.getSyncIpAddress()
            val token = settingsManager.getSyncAuthToken()

            if (ip.isBlank() || token.isBlank()) {
                _syncStatus.value = "Not Paired!"
                return@launch
            }

            _syncStatus.value = "Syncing..."

            // Collecting and transferring changes do not hold SyncCoordinator.mutex.
            // applyRemoteChanges acquires the mutex individually per envelope when writing to Room.
            val syncStart = System.currentTimeMillis()
            val lastSyncTimestamp = settingsManager.getLastSyncTimestamp()

            try {
                val client = SyncClient(settingsManager, hmacSigner, syncEncryptionManager)

                val localChanges = syncRepository.collectLocalChanges(lastSyncTimestamp)
                if (localChanges.isNotEmpty()) {
                    client.pushChanges(localChanges)
                }

                _syncStatus.value = "Fetching from Desktop..."
                val remoteChanges = client.fetchChanges(lastSyncTimestamp)
                val appliedCleanly = if (remoteChanges.isNotEmpty()) {
                    syncRepository.applyRemoteChanges(remoteChanges)
                } else true

                if (appliedCleanly) {
                    // Only advance the sync timestamp if all fetched changes were applied successfully.
                    // If any change failed, keeping the old timestamp ensures it is retried.
                    settingsManager.saveLastSyncTimestamp(syncStart)
                    _syncStatus.value = "Success!"

                    // Clean up orphaned media only during manual syncs to avoid unnecessary disk scans in background tasks.
                    syncRepository.cleanupOrphanedMedia()
                } else {
                    _syncStatus.value = "Partial sync, will retry"
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _syncStatus.value = "Failed: ${e.message}"
            }
        }
    }

    fun triggerAutoSync(discoveryManager: com.ben.inly.domain.sync.discovery.SyncDiscoveryManager) {
        viewModelScope.launch {
            val currentAuth = settingsManager.getSyncAuthToken()
            if (currentAuth.isBlank()) return@launch

            discoveryManager.startScanning()

            for (i in 1..15) {
                kotlinx.coroutines.delay(200.milliseconds)
                val devices = discoveryManager.discoveredDevices.value
                if (devices.isNotEmpty()) {
                    settingsManager.saveSyncIpAddress(devices.first().ipAddress)
                    break
                }
            }

            discoveryManager.stopScanning()

            performSilentSync()
        }
    }

    private suspend fun performSilentSync(): Boolean = withContext(Dispatchers.IO) {
        // Runs network requests un-locked to prevent blocking local editor saves during frequent background polling.
        val syncStart = System.currentTimeMillis()
        val lastSyncTimestamp = settingsManager.getLastSyncTimestamp()
        try {
            _syncStatus.value = "Auto-Syncing..."
            val client = SyncClient(settingsManager, hmacSigner, syncEncryptionManager)

            val localChanges = syncRepository.collectLocalChanges(lastSyncTimestamp)
            if (localChanges.isNotEmpty()) {
                client.pushChanges(localChanges)
            }

            val remoteChanges = client.fetchChanges(lastSyncTimestamp)
            val appliedCleanly = if (remoteChanges.isNotEmpty()) {
                syncRepository.applyRemoteChanges(remoteChanges)
            } else true

            if (appliedCleanly) {
                settingsManager.saveLastSyncTimestamp(syncStart)
                _syncStatus.value = "Synced Successfully"
                true
            } else {
                _syncStatus.value = "Partial sync, will retry"
                false
            }
        } catch (_: java.net.ConnectException) {
            _syncStatus.value = "Desktop Offline"
            false
        } catch (e: Exception) {
            e.printStackTrace()
            _syncStatus.value = "Sync Error: ${e.javaClass.simpleName}"
            false
        }
    }

    fun triggerFastSync() {
        viewModelScope.launch {
            val currentAuth = settingsManager.getSyncAuthToken()
            if (currentAuth.isBlank()) return@launch

            performSilentSync()
        }
    }

    private var watchdogJob: kotlinx.coroutines.Job? = null

    fun startForegroundWatchdog() {
        if (watchdogJob?.isActive == true) return

        watchdogJob = viewModelScope.launch {
            var currentDelay = 1500L
            val maxDelay = 30000L // Cap at 30 seconds

            while (true) {
                kotlinx.coroutines.delay(currentDelay.milliseconds)

                if (settingsManager.getSyncIpAddress().isNotBlank()) {
                    val success = performSilentSync()

                    currentDelay = if (success) {
                        // Reset to aggressive polling if the server is alive
                        1500L
                    } else {
                        // Back off exponentially if the server is dead
                        (currentDelay * 2).coerceAtMost(maxDelay)
                    }
                }
            }
        }
    }

    fun stopForegroundWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }
}