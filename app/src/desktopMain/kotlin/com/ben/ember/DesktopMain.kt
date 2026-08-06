package com.ben.ember

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.ben.ember.data.local.prefs.SettingsManager
import com.ben.ember.data.local.prefs.SyncConstants
import com.ben.ember.di.desktopModule
import com.ben.ember.di.sharedModule
import com.ben.ember.domain.media.LocalMediaGarbageCollector
import com.ben.ember.domain.media.LocalMediaGcTrigger
import com.ben.ember.domain.selfhost.sync.ForegroundSyncPoller
import com.ben.ember.domain.selfhost.crypto.SecureSyncKeyStorage
import com.ben.ember.domain.selfhost.sync.SelfHostSyncLog
import com.ben.ember.domain.selfhost.sync.SelfHostSyncScheduler
import com.ben.ember.domain.sync.SyncRepository
import com.ben.ember.presentation.EmberApp
import com.ben.ember.presentation.desktop.DesktopSearchShortcutBus
import com.ben.ember.presentation.mobile.home.note.NoteScreen
import com.ben.ember.presentation.shared.StickyNoteWindowBus
import com.ben.ember.domain.sync.startSyncServer
import com.ben.ember.ui.theme.FontSizePreference
import com.ben.ember.ui.theme.FontStylePreference
import com.ben.ember.ui.theme.EmberTheme
import com.ben.ember.domain.util.handleExportBackup
import com.ben.ember.domain.util.handleExportMarkdown
import com.ben.ember.domain.util.handleExportPdf
import com.ben.ember.domain.util.handleImportBackup
import com.ben.ember.presentation.navigation.Screen
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import java.awt.Frame
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds

fun main() = application {

    remember {
        startKoin {
            modules(sharedModule, desktopModule)
        }
        Unit
    }

    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory())
            }
            .build()
    }

    LaunchedEffect(Unit) {
        val koin = GlobalContext.get()
        val settingsManager = koin.get<SettingsManager>()
        val syncRepository = koin.get<SyncRepository>()
        val hmacSigner = koin.get<com.ben.ember.core.security.SyncHmacSigner>()
        val syncEncryptionManager = koin.get<com.ben.ember.core.security.SyncEncryptionManager>()
        val pairingState = koin.get<com.ben.ember.domain.sync.SyncPairingState>()

        startSyncServer(settingsManager, syncRepository, hmacSigner, syncEncryptionManager, pairingState)

        val discoveryManager = koin.get<com.ben.ember.domain.sync.discovery.SyncDiscoveryManager>()
        val port = settingsManager.getSyncPort().let { if (it <= 0) 8080 else it }
        discoveryManager.startBroadcasting(port, "Ember Desktop")
    }

    LaunchedEffect(Unit) {
        val koin = GlobalContext.get()
        val secureSyncKeyStorage = koin.get<SecureSyncKeyStorage>()
        val selfHostSyncScheduler = koin.get<SelfHostSyncScheduler>()
        val foregroundSyncPoller = koin.get<ForegroundSyncPoller>()

        val isVaultConfigured = secureSyncKeyStorage.getServerCredentials() != null &&
                secureSyncKeyStorage.getEncryptionKey() != null

        if (isVaultConfigured) {
            SelfHostSyncLog.d("DesktopMain: vault already configured, arming background sync schedules on launch")
            selfHostSyncScheduler.scheduleDailySync()
            selfHostSyncScheduler.scheduleMediaSync()
            foregroundSyncPoller.start()
        } else {
            SelfHostSyncLog.d("DesktopMain: no self-host vault configured, skipping background sync schedules")
        }
    }

    @OptIn(FlowPreview::class)
    LaunchedEffect(Unit) {
        val koin = GlobalContext.get()
        val localMediaGarbageCollector = koin.get<LocalMediaGarbageCollector>()
        LocalMediaGcTrigger.cleanupRequests
            .debounce(2000L.milliseconds)
            .collect {
                localMediaGarbageCollector.deleteExpiredTempFiles()
            }
    }

    var isMainWindowOpen by remember { mutableStateOf(true) }

    Tray(
        icon = painterResource("app_icon.png"),
        tooltip = "Ember",
        onAction = { isMainWindowOpen = true },
        menu = {
            Item("Open Ember", onClick = { isMainWindowOpen = true })
            Item("Quit", onClick = { exitApplication() })
        }
    )

    if (isMainWindowOpen) {
    Window(
        onCloseRequest = { isMainWindowOpen = false },
        title = "Ember",
        state = rememberWindowState(width = 1200.dp, height = 800.dp),
        icon = painterResource("app_icon.png"),
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.F && (event.isCtrlPressed || event.isMetaPressed)) {
                DesktopSearchShortcutBus.requestOpen()
                true
            } else {
                false
            }
        }
    ) {
        val currentWindow = this.window as Frame

        val settingsManager = remember { GlobalContext.get().get<SettingsManager>() }
        val fontSizePreferenceName by settingsManager.fontSizePreferenceFlow.collectAsState(
            initial = SyncConstants.DEFAULT_FONT_SIZE_PREFERENCE
        )
        val fontSizePreference = runCatching { FontSizePreference.valueOf(fontSizePreferenceName) }
            .getOrDefault(FontSizePreference.DEFAULT)

        val fontStylePreferenceName by settingsManager.fontStylePreferenceFlow.collectAsState(
            initial = SyncConstants.DEFAULT_FONT_STYLE_PREFERENCE
        )
        val fontStylePreference = runCatching { FontStylePreference.valueOf(fontStylePreferenceName) }
            .getOrDefault(FontStylePreference.POPPINS)

        EmberTheme(fontSizePreference = fontSizePreference, fontStylePreference = fontStylePreference) {
            EmberApp(
                startRoute = Screen.Splash.route,
                onPickImage = { onPathSelected ->
                    val dialog = java.awt.FileDialog(currentWindow, "Select Image", java.awt.FileDialog.LOAD)
                    dialog.file = "*.png;*.jpg;*.jpeg;*.webp"
                    dialog.isVisible = true
                    dialog.files.firstOrNull()?.let { file -> onPathSelected(file.absolutePath) }
                },
                onPickDocument = { onPathSelected ->
                    val dialog = java.awt.FileDialog(currentWindow, "Select Document", java.awt.FileDialog.LOAD)
                    dialog.isVisible = true
                    dialog.files.firstOrNull()?.let { file -> onPathSelected(file.absolutePath) }
                },
                onOpenFile = { path, _ ->
                    try {
                        val cleanPath = path.removePrefix("file://")
                        val originalFile = if (cleanPath.contains("/") || cleanPath.contains("\\")) {
                            java.io.File(cleanPath)
                        } else {
                            java.io.File(System.getProperty("user.home"), ".ember/media/$cleanPath")
                        }

                        if (!originalFile.exists()) {
                            SwingUtilities.invokeLater {
                                JOptionPane.showMessageDialog(
                                    currentWindow,
                                    "This file is no longer available on this device.",
                                    "File Not Found",
                                    JOptionPane.WARNING_MESSAGE
                                )
                            }
                        } else {
                            val tmpDir = java.io.File(System.getProperty("java.io.tmpdir"), "ember_view").apply { mkdirs() }
                            val viewFile = java.io.File(tmpDir, originalFile.name)

                            if (!viewFile.exists() || viewFile.length() != originalFile.length()) {
                                originalFile.copyTo(viewFile, overwrite = true)
                            }

                            java.awt.Desktop.getDesktop().open(viewFile)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        SwingUtilities.invokeLater {
                            JOptionPane.showMessageDialog(
                                currentWindow,
                                "Failed to open file: ${e.message}",
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                            )
                        }
                    }
                },
                onExportMarkdown = { fileName, content ->
                    Thread { handleExportMarkdown(currentWindow, fileName, content) }.start()
                },
                onExportPdf = { fileName, title, blocks ->
                    Thread { handleExportPdf(currentWindow, fileName, title, blocks) }.start()
                },
                onExportBackup = { jsonContent ->
                    Thread { handleExportBackup(currentWindow, jsonContent) }.start()
                },
                onImportBackupClick = {
                    Thread { handleImportBackup(currentWindow) }.start()
                },
                onRequestBackupFolder = {
                    SwingUtilities.invokeLater {
                        JOptionPane.showMessageDialog(
                            currentWindow,
                            "Automated background backups are currently only supported on the Android app. You can still use the manual 'Export Backup' button!",
                            "Desktop Feature",
                            JOptionPane.INFORMATION_MESSAGE
                        )
                    }
                }
            )
        }
    }
    }

    StickyNoteWindowBus.openNoteIds.forEach { stickyNoteId ->
        key(stickyNoteId) {
            Window(
                onCloseRequest = { StickyNoteWindowBus.close(stickyNoteId) },
                title = "Sticky Note",
                state = rememberWindowState(width = 320.dp, height = 360.dp),
                icon = painterResource("app_icon.png")
            ) {
                val settingsManager = remember { GlobalContext.get().get<SettingsManager>() }
                val fontSizePreferenceName by settingsManager.fontSizePreferenceFlow.collectAsState(
                    initial = SyncConstants.DEFAULT_FONT_SIZE_PREFERENCE
                )
                val fontSizePreference = runCatching { FontSizePreference.valueOf(fontSizePreferenceName) }
                    .getOrDefault(FontSizePreference.DEFAULT)

                val fontStylePreferenceName by settingsManager.fontStylePreferenceFlow.collectAsState(
                    initial = SyncConstants.DEFAULT_FONT_STYLE_PREFERENCE
                )
                val fontStylePreference = runCatching { FontStylePreference.valueOf(fontStylePreferenceName) }
                    .getOrDefault(FontStylePreference.POPPINS)

                val stickyWindow = this.window as Frame

                EmberTheme(fontSizePreference = fontSizePreference, fontStylePreference = fontStylePreference) {
                    NoteScreen(
                        noteId = stickyNoteId,
                        isStickyNote = true,
                        showBackButton = false,
                        onNavigateBack = {},
                        onPickImage = { onPathSelected ->
                            val dialog = java.awt.FileDialog(stickyWindow, "Select Image", java.awt.FileDialog.LOAD)
                            dialog.file = "*.png;*.jpg;*.jpeg;*.webp"
                            dialog.isVisible = true
                            dialog.files.firstOrNull()?.let { file -> onPathSelected(file.absolutePath) }
                        },
                        onPickDocument = { onPathSelected ->
                            val dialog = java.awt.FileDialog(stickyWindow, "Select Document", java.awt.FileDialog.LOAD)
                            dialog.isVisible = true
                            dialog.files.firstOrNull()?.let { file -> onPathSelected(file.absolutePath) }
                        },
                        onOpenFile = { path, _ ->
                            try {
                                val cleanPath = path.removePrefix("file://")
                                val originalFile = if (cleanPath.contains("/") || cleanPath.contains("\\")) {
                                    java.io.File(cleanPath)
                                } else {
                                    java.io.File(System.getProperty("user.home"), ".ember/media/$cleanPath")
                                }

                                if (!originalFile.exists()) {
                                    SwingUtilities.invokeLater {
                                        JOptionPane.showMessageDialog(
                                            stickyWindow,
                                            "This file is no longer available on this device.",
                                            "File Not Found",
                                            JOptionPane.WARNING_MESSAGE
                                        )
                                    }
                                } else {
                                    val tmpDir = java.io.File(System.getProperty("java.io.tmpdir"), "ember_view").apply { mkdirs() }
                                    val viewFile = java.io.File(tmpDir, originalFile.name)

                                    if (!viewFile.exists() || viewFile.length() != originalFile.length()) {
                                        originalFile.copyTo(viewFile, overwrite = true)
                                    }

                                    java.awt.Desktop.getDesktop().open(viewFile)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                SwingUtilities.invokeLater {
                                    JOptionPane.showMessageDialog(
                                        stickyWindow,
                                        "Failed to open file: ${e.message}",
                                        "Error",
                                        JOptionPane.ERROR_MESSAGE
                                    )
                                }
                            }
                        },
                        onExportMarkdown = { fileName, content ->
                            Thread { handleExportMarkdown(stickyWindow, fileName, content) }.start()
                        },
                        onExportPdf = { fileName, title, blocks ->
                            Thread { handleExportPdf(stickyWindow, fileName, title, blocks) }.start()
                        }
                    )
                }
            }
        }
    }
}