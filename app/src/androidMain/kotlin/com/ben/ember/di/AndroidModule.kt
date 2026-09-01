package com.ben.ember.di

import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.cash.sqldelight.db.SqlDriver
import com.ben.ember.core.security.AesGcmEncryptionManager
import com.ben.ember.core.security.EncryptionManager
import com.ben.ember.core.security.SyncEncryptionManager
import com.ben.ember.data.local.prefs.AndroidSettingsManager
import com.ben.ember.data.local.prefs.SettingsManager
import com.ben.ember.data.local.room.AppDatabase
import com.ben.ember.data.local.room.BlockDao
import com.ben.ember.data.local.room.BookmarkBlockDao
import com.ben.ember.data.local.room.CalendarTaskDao
import com.ben.ember.data.local.room.CategoryDao
import com.ben.ember.data.local.room.DatabaseTemplateDao
import com.ben.ember.data.local.room.DocumentBlockDao
import com.ben.ember.data.local.room.FolderDao
import com.ben.ember.data.local.room.ImageBlockDao
import com.ben.ember.data.local.room.NoteDao
import com.ben.ember.data.local.room.SelfHostDeletedNoteDao
import com.ben.ember.data.local.room.TagDao
import com.ben.ember.domain.sync.SyncRepositoryImpl
import com.ben.ember.data.worker.AndroidBackupRescheduler
import com.ben.ember.data.worker.BackupNotifier
import com.ben.ember.data.worker.BackupScheduler
import com.ben.ember.data.worker.BackupWorker
import com.ben.ember.data.worker.BackupRescheduler
import com.ben.ember.database.DatabaseDriverFactory
import com.ben.ember.domain.ai.RagRepository
import com.ben.ember.domain.selfhost.crypto.KeyDerivationManager
import com.ben.ember.domain.selfhost.crypto.Pbkdf2KeyDerivationManager
import com.ben.ember.domain.selfhost.crypto.SecureSyncKeyStorage
import com.ben.ember.domain.selfhost.sync.SelfHostSyncScheduler
import com.ben.ember.domain.selfhost.sync.SelfHostSyncWorker
import com.ben.ember.domain.sync.SyncRepository
import com.ben.ember.domain.util.AndroidAudioRecorder
import com.ben.ember.domain.util.AndroidImageDownloader
import com.ben.ember.domain.util.AndroidMediaStorageHelper
import com.ben.ember.domain.util.AudioRecorder
import com.ben.ember.domain.util.ImageDownloader
import com.ben.ember.domain.util.MediaStorageHelper
import com.ben.ember.domain.util.NativeVoiceRecognizer
import com.ben.ember.domain.util.VoiceRecognizer
import com.ben.ember.presentation.rag.RagViewModel
import com.ben.ember.presentation.reminders.AndroidReminderScheduler
import com.ben.ember.presentation.reminders.ReminderScheduler
import com.ben.ember.presentation.sync.SyncViewModel
import com.ben.ember.domain.sync.discovery.AndroidDiscoveryManager
import com.ben.ember.domain.sync.discovery.SyncDiscoveryManager
import com.ember.database.EmberDatabase
import net.sqlcipher.database.SupportFactory
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val androidModule = module {

    // Platform implementations
    single<MediaStorageHelper> { AndroidMediaStorageHelper(androidContext()) }
    single<ImageDownloader> { AndroidImageDownloader(androidContext()) }
    single<VoiceRecognizer> { NativeVoiceRecognizer(androidContext()) }
    single<ReminderScheduler> { AndroidReminderScheduler(androidContext()) }
    single<AudioRecorder> { AndroidAudioRecorder(androidContext()) }

    single<SharedPreferences> {
        val masterKey = MasterKey.Builder(androidContext())
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            androidContext(),
            "ember_settings_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    single<SettingsManager> { AndroidSettingsManager(sharedPreferences = get()) }

    // Room
    single<ByteArray> { EncryptionManager.getDatabasePassphrase(androidContext()) }

    single<AppDatabase> {
        val passphrase = get<ByteArray>()
        val supportFactory = SupportFactory(passphrase)

        val builder = com.ben.ember.data.local.room.getDatabaseBuilder(androidContext())
        builder
            .openHelperFactory(supportFactory)
            .fallbackToDestructiveMigration(dropAllTables = true)

        com.ben.ember.data.local.room.getRoomDatabase(builder)
    }

    single<NoteDao> { get<AppDatabase>().noteDao() }
    single<FolderDao> { get<AppDatabase>().folderDao() }
    single<TagDao> { get<AppDatabase>().tagDao() }
    single<BlockDao> { get<AppDatabase>().blockDao() }

    single {
        com.ben.ember.presentation.widget.note.WidgetContentReader(
            noteDao = get(),
            blockDao = get(),
            noteRepository = get()
        )
    }

    single { com.ben.ember.presentation.widget.WidgetNoteSource(noteDao = get()) }

    single {
        com.ben.ember.presentation.widget.calendaragenda.CalendarAgendaWidgetContentReader(
            calendarTaskDao = get(),
            categoryDao = get()
        )
    }

    single {
        com.ben.ember.presentation.widget.calendaragenda.CalendarAgendaWidgetCoordinator(
            context = androidContext(),
            calendarTaskDao = get()
        )
    }

    single {
        com.ben.ember.presentation.widget.calendar.CalendarWidgetContentReader(
            calendarTaskDao = get(),
            categoryDao = get()
        )
    }

    single {
        com.ben.ember.presentation.widget.calendar.CalendarWidgetCoordinator(
            context = androidContext(),
            calendarTaskDao = get()
        )
    }

    single {
        com.ben.ember.presentation.widget.todaytasks.TodayTasksWidgetContentReader(
            calendarTaskDao = get()
        )
    }

    single {
        com.ben.ember.presentation.widget.todaytasks.TodayTasksWidgetCoordinator(
            context = androidContext(),
            calendarTaskDao = get()
        )
    }

    single {
        com.ben.ember.presentation.widget.upcomingevents.UpcomingEventsWidgetContentReader(
            calendarTaskDao = get(),
            categoryDao = get()
        )
    }

    single {
        com.ben.ember.presentation.widget.upcomingevents.UpcomingEventsWidgetCoordinator(
            context = androidContext(),
            calendarTaskDao = get()
        )
    }

    single { com.ben.ember.presentation.widget.notelist.NoteListWidgetContentReader(noteDao = get()) }

    single { com.ben.ember.presentation.widget.noteshortcut.NoteShortcutWidgetContentReader(noteDao = get()) }

    single {
        com.ben.ember.presentation.widget.noteshortcut.NoteShortcutWidgetCoordinator(
            context = androidContext(),
            noteSource = get()
        )
    }

    single {
        com.ben.ember.presentation.widget.notelist.NoteListWidgetCoordinator(
            context = androidContext(),
            contentReader = get()
        )
    }

    single {
        com.ben.ember.presentation.widget.tasks.TasksWidgetContentReader(
            calendarTaskDao = get(),
            noteDao = get()
        )
    }

    single {
        com.ben.ember.presentation.widget.tasks.TasksWidgetCoordinator(
            context = androidContext(),
            contentReader = get()
        )
    }

    single {
        com.ben.ember.presentation.widget.note.NoteWidgetCoordinator(
            context = androidContext(),
            noteRepository = get(),
            contentReader = get()
        )
    }
    single<CalendarTaskDao> { get<AppDatabase>().calendarTaskDao() }
    single<com.ben.ember.data.local.room.CalendarEventExceptionDao> { get<AppDatabase>().calendarEventExceptionDao() }
    single<ImageBlockDao> { get<AppDatabase>().imageBlockDao() }
    single<DocumentBlockDao> { get<AppDatabase>().documentBlockDao() }
    single<BookmarkBlockDao> { get<AppDatabase>().bookmarkBlockDao() }
    single<DatabaseTemplateDao> { get<AppDatabase>().databaseTemplateDao() }
    single<CategoryDao> { get<AppDatabase>().categoryDao() }
    single<SelfHostDeletedNoteDao> { get<AppDatabase>().selfHostDeletedNoteDao() }
    single<com.ben.ember.data.local.room.ChatSessionDao> { get<AppDatabase>().chatSessionDao() }
    single<com.ben.ember.data.local.room.SelfHostDeletedApiConfigDao> { get<AppDatabase>().selfHostDeletedApiConfigDao() }

    // SQLDelight
    single<SqlDriver> { DatabaseDriverFactory(androidContext()).createDriver() }
    single { EmberDatabase(get()) }

    // AI
    single { com.ben.ember.domain.ai.LocalAiEngine(aiSettingsRepository = get()) }
    single {
        RagRepository(
            database = get(),
            localAiEngine = get(),
            externalAiEngine = get(),
            aiSettingsRepository = get()
        )
    }
    single<com.ben.ember.domain.ai.external.SecureAiKeyStorage> { com.ben.ember.domain.ai.external.SecureAiKeyStorage(androidContext()) }
    single { com.ben.ember.domain.ai.models.LocalModelUploadManager(androidContext()) }
    single { com.ben.ember.domain.ai.models.ModelDownloadScheduler(androidContext()) }
    worker {
        com.ben.ember.domain.ai.models.ModelDownloadWorker(
            appContext = get(),
            workerParams = get(),
            modelDownloadManager = get()
        )
    }
    viewModel {
        RagViewModel(
            ragRepository = get(),
            aiSettingsRepository = get(),
            chatSessionRepository = get(),
            modelDownloadScheduler = get(),
            reindexAllNotesUseCase = get(),
            localModelUploadManager = get()
        )
    }

    // Self-hosted WebDAV sync
    single<KeyDerivationManager> { Pbkdf2KeyDerivationManager() }
    single<SecureSyncKeyStorage> { SecureSyncKeyStorage(androidContext()) }
    single { SelfHostSyncScheduler(androidContext(), get(), get()) }
    worker {
        SelfHostSyncWorker(
            appContext = get(),
            workerParams = get(),
            selfHostSyncEngine = get()
        )
    }

    // Sync
    single<SyncEncryptionManager> { AesGcmEncryptionManager() }
    single<com.ben.ember.core.security.SyncHmacSigner> { com.ben.ember.core.security.HmacSha256Signer() }
    single<SyncDiscoveryManager> { AndroidDiscoveryManager(androidContext()) }
    single<com.ben.ember.domain.sync.SyncClient> { com.ben.ember.domain.sync.SyncClient(get(), get(), get()) }
    single<SyncRepository> { SyncRepositoryImpl(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SyncViewModel(get(), get(), get(), get(), get()) }

    // Automatic backups
    single { com.ben.ember.domain.util.AndroidBackupExporter(androidContext()) }
    worker {
        BackupWorker(
            appContext = get(),
            workerParams = get(),
            backupRepository = get(),
            settingsManager = get(),
            backupExporter = get(),
            backupNotifier = get(),
            backupScheduler = get()
        )
    }
    single { BackupScheduler(context = get(), settingsManager = get()) }
    single { BackupNotifier(context = get()) }
    single<BackupRescheduler> { AndroidBackupRescheduler(backupScheduler = get()) }
}