package com.ben.inly.di

import com.ben.inly.core.security.AesGcmEncryptionManager
import com.ben.inly.core.security.SyncEncryptionManager
import com.ben.inly.data.local.prefs.DesktopSettingsManager
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
import com.ben.inly.data.worker.DesktopBackupRescheduler
import com.ben.inly.data.worker.BackupRescheduler
import com.ben.inly.database.DatabaseDriverFactory
import com.ben.inly.domain.ai.LocalAiEngine
import com.ben.inly.domain.ai.RagRepository
import com.ben.inly.domain.selfhost.crypto.KeyDerivationManager
import com.ben.inly.domain.selfhost.crypto.Pbkdf2KeyDerivationManager
import com.ben.inly.domain.selfhost.crypto.SecureSyncKeyStorage
import com.ben.inly.domain.selfhost.sync.SelfHostSyncScheduler
import com.ben.inly.domain.sync.SyncRepository
import com.ben.inly.domain.util.AudioRecorder
import com.ben.inly.domain.util.DesktopAudioRecorder
import com.ben.inly.domain.util.DesktopImageDownloader
import com.ben.inly.domain.util.DesktopMediaStorageHelper
import com.ben.inly.domain.util.DesktopVoiceRecognizer
import com.ben.inly.domain.util.ImageDownloader
import com.ben.inly.domain.util.MediaStorageHelper
import com.ben.inly.domain.util.VoiceRecognizer
import com.ben.inly.presentation.rag.RagViewModel
import com.ben.inly.presentation.reminders.DesktopReminderScheduler
import com.ben.inly.presentation.reminders.ReminderScheduler
import com.ben.inly.presentation.sync.SyncViewModel
import com.ben.inly.domain.sync.discovery.DesktopDiscoveryManager
import com.ben.inly.domain.sync.discovery.SyncDiscoveryManager
import com.inly.database.InlyDatabase
import org.koin.dsl.module

val desktopModule = module {

    // Room
    single<AppDatabase> {
        val builder = com.ben.inly.data.local.room.getDatabaseBuilder()
        builder.fallbackToDestructiveMigration(dropAllTables = true)
        com.ben.inly.data.local.room.getRoomDatabase(builder)
    }
    single<NoteDao> { get<AppDatabase>().noteDao() }
    single<FolderDao> { get<AppDatabase>().folderDao() }
    single<TagDao> { get<AppDatabase>().tagDao() }
    single<BlockDao> { get<AppDatabase>().blockDao() }
    single<CalendarTaskDao> { get<AppDatabase>().calendarTaskDao() }
    single<com.ben.inly.data.local.room.CalendarEventExceptionDao> { get<AppDatabase>().calendarEventExceptionDao() }
    single<ImageBlockDao> { get<AppDatabase>().imageBlockDao() }
    single<DocumentBlockDao> { get<AppDatabase>().documentBlockDao() }
    single<BookmarkBlockDao> { get<AppDatabase>().bookmarkBlockDao() }
    single<DatabaseTemplateDao> { get<AppDatabase>().databaseTemplateDao() }
    single<CategoryDao> { get<AppDatabase>().categoryDao() }
    single<SelfHostDeletedNoteDao> { get<AppDatabase>().selfHostDeletedNoteDao() }
    single<com.ben.inly.data.local.room.ChatSessionDao> { get<AppDatabase>().chatSessionDao() }
    single<com.ben.inly.data.local.room.SelfHostDeletedApiConfigDao> { get<AppDatabase>().selfHostDeletedApiConfigDao() }
    single<VoiceRecognizer> { DesktopVoiceRecognizer() }

    // SQLDelight
    single { DatabaseDriverFactory().createDriver() }
    single { InlyDatabase(get()) }

    // AI
    single { LocalAiEngine(aiSettingsRepository = get()) }
    single { RagRepository(get(), get(), get(), get()) }
    single<com.ben.inly.domain.ai.external.SecureAiKeyStorage> { com.ben.inly.domain.ai.external.SecureAiKeyStorage() }
    single { com.ben.inly.domain.ai.models.LocalModelUploadManager() }
    single { com.ben.inly.domain.ai.models.ModelDownloadScheduler(modelDownloadManager = get()) }
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
    single<com.ben.inly.core.security.SyncHmacSigner> { com.ben.inly.core.security.HmacSha256Signer() }
    single<SyncDiscoveryManager> { DesktopDiscoveryManager() }
    single<com.ben.inly.domain.sync.SyncClient> { com.ben.inly.domain.sync.SyncClient(get(), get(), get()) }
    single<SyncRepository> { SyncRepositoryImpl(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { SyncViewModel(get(), get(), get(), get(), get()) }

    // Automatic Backup
    single<BackupRescheduler> { DesktopBackupRescheduler() }
}