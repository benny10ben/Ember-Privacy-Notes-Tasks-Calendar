package com.ben.inly.di

import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.cash.sqldelight.db.SqlDriver
import com.ben.inly.core.security.AesGcmEncryptionManager
import com.ben.inly.core.security.EncryptionManager
import com.ben.inly.core.security.SyncEncryptionManager
import com.ben.inly.data.local.prefs.AndroidSettingsManager
import com.ben.inly.data.local.prefs.SettingsManager
import com.ben.inly.data.local.room.AppDatabase
import com.ben.inly.data.local.room.BlockDao
import com.ben.inly.data.local.room.BookmarkBlockDao
import com.ben.inly.data.local.room.CalendarTaskDao
import com.ben.inly.data.local.room.CategoryDao
import com.ben.inly.data.local.room.DatabaseTemplateDao
import com.ben.inly.data.local.room.DocumentBlockDao
import com.ben.inly.data.local.room.FolderDao
import com.ben.inly.data.local.room.ImageBlockDao
import com.ben.inly.data.local.room.NoteDao
import com.ben.inly.data.local.room.SelfHostDeletedNoteDao
import com.ben.inly.data.local.room.TagDao
import com.ben.inly.domain.sync.SyncRepositoryImpl
import com.ben.inly.data.worker.AndroidBackupRescheduler
import com.ben.inly.data.worker.BackupNotifier
import com.ben.inly.data.worker.BackupScheduler
import com.ben.inly.data.worker.BackupWorker
import com.ben.inly.data.worker.BackupRescheduler
import com.ben.inly.database.DatabaseDriverFactory
import com.ben.inly.domain.ai.RagRepository
import com.ben.inly.domain.selfhost.crypto.KeyDerivationManager
import com.ben.inly.domain.selfhost.crypto.Pbkdf2KeyDerivationManager
import com.ben.inly.domain.selfhost.crypto.SecureSyncKeyStorage
import com.ben.inly.domain.selfhost.sync.SelfHostSyncScheduler
import com.ben.inly.domain.selfhost.sync.SelfHostSyncWorker
import com.ben.inly.domain.sync.SyncRepository
import com.ben.inly.domain.util.AndroidAudioRecorder
import com.ben.inly.domain.util.AndroidMediaStorageHelper
import com.ben.inly.domain.util.AudioRecorder
import com.ben.inly.domain.util.MediaStorageHelper
import com.ben.inly.domain.util.NativeVoiceRecognizer
import com.ben.inly.domain.util.VoiceRecognizer
import com.ben.inly.presentation.rag.RagViewModel
import com.ben.inly.presentation.reminders.AndroidReminderScheduler
import com.ben.inly.presentation.reminders.ReminderScheduler
import com.ben.inly.presentation.sync.SyncViewModel
import com.ben.inly.domain.sync.discovery.AndroidDiscoveryManager
import com.ben.inly.domain.sync.discovery.SyncDiscoveryManager
import com.inly.database.InlyDatabase
import net.sqlcipher.database.SupportFactory
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val androidModule = module {

    // Platform implementations
    single<MediaStorageHelper> { AndroidMediaStorageHelper(androidContext()) }
    single<VoiceRecognizer> { NativeVoiceRecognizer(androidContext()) }
    single<ReminderScheduler> { AndroidReminderScheduler(androidContext()) }
    single<AudioRecorder> { AndroidAudioRecorder(androidContext()) }

    single<SharedPreferences> {
        val masterKey = MasterKey.Builder(androidContext())
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            androidContext(),
            "inly_settings_prefs",
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

        val builder = com.ben.inly.data.local.room.getDatabaseBuilder(androidContext())
        builder
            .openHelperFactory(supportFactory)
            .fallbackToDestructiveMigration(dropAllTables = true)

        com.ben.inly.data.local.room.getRoomDatabase(builder)
    }

    single<NoteDao> { get<AppDatabase>().noteDao() }
    single<FolderDao> { get<AppDatabase>().folderDao() }
    single<TagDao> { get<AppDatabase>().tagDao() }
    single<BlockDao> { get<AppDatabase>().blockDao() }
    single<CalendarTaskDao> { get<AppDatabase>().calendarTaskDao() }
    single<ImageBlockDao> { get<AppDatabase>().imageBlockDao() }
    single<DocumentBlockDao> { get<AppDatabase>().documentBlockDao() }
    single<BookmarkBlockDao> { get<AppDatabase>().bookmarkBlockDao() }
    single<DatabaseTemplateDao> { get<AppDatabase>().databaseTemplateDao() }
    single<CategoryDao> { get<AppDatabase>().categoryDao() }
    single<SelfHostDeletedNoteDao> { get<AppDatabase>().selfHostDeletedNoteDao() }

    // SQLDelight
    single<SqlDriver> { DatabaseDriverFactory(androidContext()).createDriver() }
    single { InlyDatabase(get()) }

    // AI
    single { com.ben.inly.domain.ai.LocalAiEngine() }
    single { RagRepository(database = get(), aiEngine = get()) }
    viewModel { RagViewModel(ragRepository = get()) }

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
    single<com.ben.inly.core.security.SyncHmacSigner> { com.ben.inly.core.security.HmacSha256Signer() }
    single<SyncDiscoveryManager> { AndroidDiscoveryManager(androidContext()) }
    single<com.ben.inly.domain.sync.SyncClient> { com.ben.inly.domain.sync.SyncClient(get(), get(), get()) }
    single<SyncRepository> { SyncRepositoryImpl(get(), get(), get(), get(), get()) }
    viewModel { SyncViewModel(get(), get(), get(), get(), get()) }

    // Automatic backups
    single { com.ben.inly.domain.util.AndroidBackupExporter(androidContext()) }
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