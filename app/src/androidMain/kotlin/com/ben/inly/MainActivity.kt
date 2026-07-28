package com.ben.inly

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.ben.inly.data.local.room.NoteMetadataEntity
import com.ben.inly.domain.model.BookmarkBlock
import com.ben.inly.domain.model.NoteContent
import com.ben.inly.domain.repository.NoteRepository
import com.ben.inly.presentation.InlyApp
import com.ben.inly.ui.theme.InlyTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.KoinAndroidContext
import java.util.UUID
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ben.inly.domain.sync.AutoSyncTrigger
import com.ben.inly.domain.sync.discovery.SyncDiscoveryManager
import com.ben.inly.presentation.sync.SyncViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ben.inly.ui.theme.FontSizePreference
import com.ben.inly.ui.theme.FontStylePreference
import androidx.compose.ui.platform.LocalContext
import com.ben.inly.data.worker.BackupScheduler
import com.ben.inly.domain.model.NoteBlock
import com.ben.inly.domain.util.generateAndSaveAndroidPdf
import com.ben.inly.presentation.navigation.Screen
import kotlinx.coroutines.CoroutineScope
import org.koin.core.qualifier.named
import kotlin.time.Duration.Companion.milliseconds


class MainActivity : ComponentActivity() {

    private val repository: NoteRepository by inject()
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> imagePickerCallback?.invoke(uri?.toString() ?: "") }

    private val pickDocument = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> documentPickerCallback?.invoke(uri?.toString() ?: "") }

    private var imagePickerCallback: ((String) -> Unit)? = null
    private var documentPickerCallback: ((String) -> Unit)? = null
    private var takePhotoCallback: ((String) -> Unit)? = null
    private var currentPhotoUri: Uri? = null

    private val mediaStorageHelper: com.ben.inly.domain.util.MediaStorageHelper by inject()

    private val takePhoto = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoUri != null) {
            takePhotoCallback?.invoke(currentPhotoUri.toString())
        }
    }

    private val settingsViewModel: com.ben.inly.presentation.settings.SettingsViewModel by inject()
    private val backupScheduler: BackupScheduler by inject()
    private val appScope: CoroutineScope by inject(named("AppScope"))

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !InlyApplication.isReady }
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        enableEdgeToEdge()
        handleIntent(intent)
        backupScheduler.toString()

        val syncViewModel: SyncViewModel by inject()
        val discoveryManager: SyncDiscoveryManager by inject()

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    syncViewModel.triggerAutoSync(discoveryManager)
                    syncViewModel.startForegroundWatchdog()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    syncViewModel.stopForegroundWatchdog()
                }
                else -> {}
            }
        })

        @OptIn(FlowPreview::class)
        lifecycleScope.launch {
            AutoSyncTrigger.syncRequests
                .debounce(1500L.milliseconds)
                .collect {
                    syncViewModel.triggerFastSync()
                }
        }

        setContent {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context)
                    .components {
                        add(KtorNetworkFetcherFactory(HttpClient(OkHttp)))
                    }
                    .crossfade(true)
                    .build()
            }

            val fontSizePreferenceName by settingsViewModel.fontSizePreference.collectAsState()
            val fontSizePreference = runCatching { FontSizePreference.valueOf(fontSizePreferenceName) }
                .getOrDefault(FontSizePreference.DEFAULT)

            val fontStylePreferenceName by settingsViewModel.fontStylePreference.collectAsState()
            val fontStylePreference = runCatching { FontStylePreference.valueOf(fontStylePreferenceName) }
                .getOrDefault(FontStylePreference.POPPINS)

            InlyTheme(fontSizePreference = fontSizePreference, fontStylePreference = fontStylePreference) {
                Surface(color = Color.Transparent, modifier = Modifier.fillMaxSize()) {
                    KoinAndroidContext {
                        val context = LocalContext.current

                        val backupFolderPickerLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.OpenDocumentTree()
                        ) { uri ->
                            uri?.let {
                                try {
                                    // Take persistable permission so the background worker can use it forever
                                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    context.contentResolver.takePersistableUriPermission(it, takeFlags)

                                    // Save the URI and turn on the toggle in the ViewModel
                                    settingsViewModel.setBackupDirectory(it.toString())
                                    settingsViewModel.setAutoBackupEnabled(true)

                                    Toast.makeText(context, "Backup folder linked!", Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Failed to link folder.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        // State to hold payloads while the OS file picker is open
                        var pendingMarkdownContent by remember { mutableStateOf("") }
                        var pendingPdfTitle by remember { mutableStateOf("") }
                        var pendingPdfBlocks by remember { mutableStateOf(emptyList<NoteBlock>()) }

                        // Markdown Saver
                        val exportMarkdownLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.CreateDocument("text/markdown")
                        ) { uri ->
                            uri?.let {
                                try {
                                    context.contentResolver.openOutputStream(it)?.use { stream ->
                                        stream.write(pendingMarkdownContent.toByteArray())
                                    }
                                    Toast.makeText(context, "Markdown saved", Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        // PDF Saver
                        val exportPdfLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.CreateDocument("application/pdf")
                        ) { uri ->
                            uri?.let { generateAndSaveAndroidPdf(context, it, pendingPdfTitle, pendingPdfBlocks, mediaStorageHelper) }
                        }

                        var pendingBackupJson by remember { mutableStateOf("") }
                        val exportBackupLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.CreateDocument("application/zip")
                        ) { uri ->
                            uri?.let { destinationUri ->
                                lifecycleScope.launch(Dispatchers.IO) {
                                    try {
                                        val filesDir = context.filesDir
                                        val exporter = com.ben.inly.domain.util.AndroidBackupExporter(context)
                                        exporter.exportToZip(destinationUri, pendingBackupJson, filesDir)

                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Backup saved!", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (_: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Export failed.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }

                        // BACKUP IMPORT LAUNCHER
                        val importBackupLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.OpenDocument()
                        ) { uri ->
                            uri?.let { sourceUri ->
                                lifecycleScope.launch(Dispatchers.IO) {
                                    try {
                                        val filesDir = context.filesDir
                                        val exporter = com.ben.inly.domain.util.AndroidBackupExporter(context)

                                        // Unzip and extract JSON
                                        val jsonString = exporter.importFromZip(sourceUri, filesDir)

                                        if (jsonString != null) {
                                            // Send JSON directly to our commonMain ViewModel to handle the merge!
                                            settingsViewModel.mergeBackupJson(jsonString)

                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Invalid backup file.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        }

                        InlyApp(
                            startRoute = Screen.Daily.route,
                            onPickImage = { callback ->
                                imagePickerCallback = callback
                                pickImage.launch("image/*")
                            },
                            onTakePhoto = { callback ->
                                takePhotoCallback = callback

                                val photoFile = java.io.File(this@MainActivity.filesDir, "camera_${UUID.randomUUID()}.jpg")
                                if (!photoFile.exists()) {
                                    photoFile.createNewFile()
                                }

                                currentPhotoUri = androidx.core.content.FileProvider.getUriForFile(
                                    this@MainActivity,
                                    "${applicationContext.packageName}.fileprovider",
                                    photoFile
                                )
                                takePhoto.launch(currentPhotoUri!!)
                            },
                            onPickDocument = { callback ->
                                documentPickerCallback = callback
                                pickDocument.launch("*/*")
                            },
                            onOpenFile = { filePath, mimeType ->
                                try {
                                    // Use our smart helper to perfectly locate the file!
                                    val absolutePath = mediaStorageHelper.getAbsoluteMediaPath(filePath)
                                    val file = java.io.File(absolutePath)

                                    if (!file.exists()) {
                                        Toast.makeText(this@MainActivity, "This file is no longer available on this device.", Toast.LENGTH_LONG).show()
                                    } else {
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            this@MainActivity,
                                            "${applicationContext.packageName}.fileprovider",
                                            file
                                        )

                                        var finalMimeType = mimeType
                                        if (finalMimeType == "*/*" || finalMimeType.isBlank()) {
                                            val extension = file.extension.lowercase()
                                            finalMimeType = android.webkit.MimeTypeMap.getSingleton()
                                                .getMimeTypeFromExtension(extension) ?: "*/*"
                                        }

                                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, finalMimeType)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        startActivity(viewIntent)
                                    }
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(this@MainActivity, "No app found to open this document type.", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(this@MainActivity, "Failed to open file: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            },
                            onExportMarkdown = { fileName, content ->
                                pendingMarkdownContent = content
                                exportMarkdownLauncher.launch(fileName)
                            },
                            onExportPdf = { fileName, title, blocks ->
                                pendingPdfTitle = title
                                pendingPdfBlocks = blocks
                                exportPdfLauncher.launch(fileName)
                            },
                            onExportBackup = { jsonContent ->
                                pendingBackupJson = jsonContent
                                val fileName = "InlyBackup_${System.currentTimeMillis()}.inly"
                                exportBackupLauncher.launch(fileName)
                            },
                            onImportBackupClick = {
                                importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
                            },
                            onRequestBackupFolder = {
                                backupFolderPickerLauncher.launch(null)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                saveToInbox(sharedText)
            }
        }
    }

    private fun saveToInbox(sharedText: String) {
        val extractedUrl = android.util.Patterns.WEB_URL.matcher(sharedText).let {
            if (it.find()) it.group() else sharedText
        }.trim()

        Toast.makeText(this, "Saved to Inbox!", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val newBlock = BookmarkBlock(
                    id = UUID.randomUUID().toString(),
                    indentationLevel = 0,
                    url = extractedUrl,
                    title = "Loading preview...",
                    description = null,
                    previewImageUrl = null
                )

                lateinit var inboxNote: NoteMetadataEntity
                // Read-modify-write of the Inbox note must be serialized against any other writer
                // (autosave, sync, etc.) or a stale content read here could overwrite their change
                com.ben.inly.domain.util.SyncCoordinator.mutex.withLock {
                    val allNotes: List<NoteMetadataEntity> = repository.getAllNotes().first()
                    var meta: NoteMetadataEntity? = allNotes.find { it.title.equals("Inbox", ignoreCase = true) }

                    val noteId: String
                    val content: NoteContent

                    if (meta == null) {
                        noteId = UUID.randomUUID().toString()
                        meta = NoteMetadataEntity(
                            noteId = noteId,
                            title = "Inbox",
                            icon = "📥",
                            folderId = null,
                            isDaily = false,
                            dateString = null,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            filePath = "note_$noteId.json",
                            snippet = "Saved links and ideas."
                        )
                        content = NoteContent(blocks = emptyList())
                    } else {
                        noteId = meta.noteId
                        content = repository.getNoteContent(noteId) ?: NoteContent(blocks = emptyList())
                    }

                    val updatedBlocks = content.blocks + newBlock
                    repository.saveNote(
                        meta.copy(updatedAt = System.currentTimeMillis()),
                        NoteContent(blocks = updatedBlocks)
                    )
                    inboxNote = meta
                }

                appScope.launch(Dispatchers.IO) {
                    try {
                        val metadata = com.ben.inly.domain.util.HtmlMetadataFetcher.fetchMetadata(extractedUrl)
                        if (metadata.description == "Could not load preview") return@launch

                        com.ben.inly.domain.util.SyncCoordinator.mutex.withLock {
                            val currentContent = repository.getNoteContent(inboxNote.noteId) ?: return@withLock

                            val finalizedBlocks = currentContent.blocks.map {
                                if (it.id == newBlock.id && it is BookmarkBlock) {
                                    it.copy(
                                        title = metadata.title ?: "Unknown Link",
                                        description = metadata.description,
                                        previewImageUrl = metadata.imageUrl,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                } else it
                            }

                            repository.saveNote(
                                inboxNote.copy(updatedAt = System.currentTimeMillis()),
                                NoteContent(blocks = finalizedBlocks)
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                withContext(Dispatchers.Main) {
                    if (intent?.action == Intent.ACTION_SEND) {
                        finish()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to save link.", Toast.LENGTH_SHORT).show()
                    if (intent?.action == Intent.ACTION_SEND) finish()
                }
            }
        }
    }
}