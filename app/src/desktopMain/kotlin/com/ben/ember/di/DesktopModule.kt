package com.ben.ember.di

import com.ben.ember.core.security.AesGcmEncryptionManager
import com.ben.ember.core.security.SyncEncryptionManager
import com.ben.ember.data.local.prefs.DesktopSettingsManager
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
import com.ben.ember.data.worker.DesktopBackupRescheduler
import com.ben.ember.data.worker.BackupRescheduler
import com.ben.ember.database.DatabaseDriverFactory
import com.ben.ember.domain.ai.LocalAiEngine
import com.ben.ember.domain.ai.RagRepository
import com.ben.ember.domain.selfhost.crypto.KeyDerivationManager
import com.ben.ember.domain.selfhost.crypto.Pbkdf2KeyDerivationManager
import com.ben.ember.domain.selfhost.crypto.SecureSyncKeyStorage
import com.ben.ember.domain.selfhost.sync.SelfHostSyncScheduler
import com.ben.ember.domain.sync.SyncRepository
import com.ben.ember.domain.util.AudioRecorder
import com.ben.ember.domain.util.DesktopAudioRecorder
import com.ben.ember.domain.util.DesktopImageDownloader
import com.ben.ember.domain.util.DesktopMediaStorageHelper
import com.ben.ember.domain.util.DesktopVoiceRecognizer
import com.ben.ember.domain.util.ImageDownloader
import com.ben.ember.domain.util.MediaStorageHelper
import com.ben.ember.domain.util.VoiceRecognizer
import com.ben.ember.presentation.rag.RagViewModel
import com.ben.ember.presentation.reminders.DesktopReminderScheduler
import com.ben.ember.presentation.reminders.ReminderScheduler
import com.ben.ember.presentation.sync.SyncViewModel
import com.ben.ember.domain.sync.discovery.DesktopDiscoveryManager
import com.ben.ember.domain.sync.discovery.SyncDiscoveryManager
import com.ember.database.EmberDatabase
import org.koin.dsl.module

val desktopModule = module {

    // Room
    single<AppDatabase> {
        val builder = com.ben.ember.data.local.room.getDatabaseBuilder()
        builder.fallbackToDestructiveMigration(dropAllTables = true)
        com.ben.ember.data.local.room.getRoomDatabase(builder)
    }
    single<NoteDao> { get<AppDatabase>().noteDao() }
    single<FolderDao> { get<AppDatabase>().folderDao() }
    single<TagDao> { get<AppDatabase>().tagDao() }
    single<BlockDao> { get<AppDatabase>().blockDao() }
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
    single<VoiceRecognizer> { DesktopVoiceRecognizer() }

    // SQLDelight
    single { DatabaseDriverFactory().createDriver() }
    single { EmberDatabase(get()) }

    // AI
    single { LocalAiEngine(aiSettingsRepository = get()) }
    single { RagRepository(get(), get(), get(), get()) }
    single<com.ben.ember.domain.ai.external.SecureAiKeyStorage> { com.ben.ember.domain.ai.external.SecureAiKeyStorage() }
    single { com.ben.ember.domain.ai.models.LocalModelUploadManager() }
    single { com.ben.ember.domain.ai.models.ModelDownloadScheduler(modelDownloadManager = get()) }
    factory { RagViewModel(get(), get(), get(), get(), get(), get()) }

    // Platform implementations
    single<SettingsManager> { DesktopSettingsManager() }
    single<ReminderScheduler> { DesktopReminderScheduler() }
    single<MediaStorageHelper> { DesktopMediaStorageHelper() }
    single<ImageDownloader> { DesktopImageDownloader() }
    single<AudioRecorder> { DesktopAudioRecorder() }

    // Self-hosted WebDAV sync
    single<KeyDerivationManager> { Pbkdf2KeyDerivationManager() }
    single<SecureSyncKeyStorage> { SecureSyncKeyStorage() }
    single { SelfHostSyncScheduler(selfHostSyncEngine = get()) }

    // Sync
    single<SyncEncryptionManager> { AesGcmEncryptionManager() }
    single<com.ben.ember.core.security.SyncHmacSigner> { com.ben.ember.core.security.HmacSha256Signer() }
    single<SyncDiscoveryManager> { DesktopDiscoveryManager() }
    single<com.ben.ember.domain.sync.SyncClient> { com.ben.ember.domain.sync.SyncClient(get(), get(), get()) }
    single<SyncRepository> { SyncRepositoryImpl(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { SyncViewModel(get(), get(), get(), get(), get()) }

    // Automatic Backup
    single<BackupRescheduler> { DesktopBackupRescheduler() }
}