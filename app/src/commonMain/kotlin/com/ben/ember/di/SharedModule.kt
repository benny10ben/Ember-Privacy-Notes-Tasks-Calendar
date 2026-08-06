package com.ben.ember.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import com.ben.ember.domain.repository.NoteRepository
import com.ben.ember.domain.repository.NoteRepositoryImpl
import com.ben.ember.domain.ai.NoteIndexer
import com.ben.ember.domain.selfhost.sync.ForegroundSyncPoller
import com.ben.ember.domain.selfhost.sync.SelfHostSyncEngine
import com.ben.ember.domain.selfhost.webdav.WebDavSyncClient
import com.ben.ember.domain.util.HeuristicTaskExtractor
import com.ben.ember.domain.util.TaskExtractor
import com.ben.ember.presentation.settings.selfhost.SelfHostSetupViewModel
import com.ben.ember.presentation.mobile.daily.DailyEditorViewModel
import com.ben.ember.presentation.search.SearchViewModel
import com.ben.ember.presentation.trash.TrashViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

val sharedModule = module {

    single<CoroutineScope>(named("AppScope")) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single {
        NoteIndexer(
            database = get(),
            aiEngine = get()
        )
    }

    single { com.ben.ember.domain.ai.models.ModelDownloadManager() }

    single {
        com.ben.ember.domain.ai.ReindexAllNotesUseCase(
            noteRepository = get()
        )
    }

    single<NoteRepository> {
        NoteRepositoryImpl(
            noteDao = get(),
            folderDao = get(),
            tagDao = get(),
            blockDao = get(),
            noteIndexer = get(),
            calendarTaskDao = get(),
            calendarEventExceptionDao = get(),
            imageBlockDao = get(),
            documentBlockDao = get(),
            bookmarkBlockDao = get(),
            databaseTemplateDao = get(),
            categoryDao = get(),
            selfHostDeletedNoteDao = get()
        )
    }

    single {
        com.ben.ember.domain.template.DefaultTemplateSeeder(repository = get())
    }

    single {
        com.ben.ember.domain.media.LocalMediaGarbageCollector(
            noteRepository = get(),
            mediaStorageHelper = get()
        )
    }

    single<com.ben.ember.domain.ai.external.AiSettingsRepository> {
        com.ben.ember.domain.ai.external.AiSettingsRepositoryImpl(
            settingsManager = get(),
            secureAiKeyStorage = get(),
            selfHostDeletedApiConfigDao = get()
        )
    }

    single {
        com.ben.ember.domain.ai.external.ExternalAiEngine(
            aiSettingsRepository = get()
        )
    }

    single<com.ben.ember.domain.ai.chat.ChatSessionRepository> {
        com.ben.ember.domain.ai.chat.ChatSessionRepositoryImpl(
            chatSessionDao = get()
        )
    }

    single<com.ben.ember.domain.repository.BackupRepository> {
        com.ben.ember.domain.repository.BackupRepositoryImpl(
            noteDao = get(),
            folderDao = get(),
            tagDao = get(),
            blockDao = get(),
            calendarTaskDao = get(),
            imageBlockDao = get(),
            documentBlockDao = get(),
            bookmarkBlockDao = get()
        )
    }

    viewModel {
        com.ben.ember.presentation.settings.SettingsViewModel(
            backupRepository = get(),
            noteRepository = get(),
            settingsManager = get(),
            backupRescheduler = get()
        )
    }

    viewModel {
        _root_ide_package_.com.ben.ember.presentation.mobile.home.HomeViewModel(
            repository = get(),
            settingsManager = get(),
            reminderScheduler = get(),
            taskExtractor = get(),
            voiceRecognizer = get(),
            templateSeeder = get(),
            localMediaGarbageCollector = get()
        )
    }
    viewModel {
        _root_ide_package_.com.ben.ember.presentation.mobile.home.overview.tasks.TasksViewModel(
            repository = get(),
            reminderScheduler = get()
        )
    }
    viewModel {
        _root_ide_package_.com.ben.ember.presentation.mobile.home.overview.images.ImagesViewModel(
            repository = get(),
            mediaStorageHelper = get()
        )
    }
    viewModel {
        _root_ide_package_.com.ben.ember.presentation.mobile.home.overview.documents.DocumentsViewModel(
            repository = get(),
            mediaStorageHelper = get()
        )
    }
    viewModel {
        _root_ide_package_.com.ben.ember.presentation.mobile.home.overview.bookmarks.BookmarksViewModel(
            repository = get()
        )
    }
    viewModel {
        com.ben.ember.presentation.share.ShareViewModel(
            repository = get(),
            mediaStorageHelper = get(),
            appScope = get(named("AppScope"))
        )
    }
    viewModel {
        _root_ide_package_.com.ben.ember.presentation.mobile.home.note.NoteEditorViewModel(
            repository = get(),
            mediaStorageHelper = get(),
            reminderScheduler = get(),
            audioRecorder = get(),
            appScope = get(named("AppScope"))
        )
    }
    viewModel {
        DailyEditorViewModel(
            repository = get(),
            mediaStorageHelper = get(),
            reminderScheduler = get(),
            audioRecorder = get(),
            appScope = get(named("AppScope"))
        )
    }
    viewModel { TrashViewModel(repository = get()) }
    viewModel { SearchViewModel(repository = get()) }
    viewModel {
        _root_ide_package_.com.ben.ember.presentation.calendar.CalendarViewModel(
            repository = get(),
            reminderScheduler = get(),
            settingsManager = get()
        )
    }
    single<TaskExtractor> { HeuristicTaskExtractor() }

    single { com.ben.ember.domain.sync.SyncPairingState(settingsManager = get()) }

    single {
        WebDavSyncClient(
            secureSyncKeyStorage = get(),
            syncEncryptionManager = get()
        )
    }

    single {
        SelfHostSyncEngine(
            webDavSyncClient = get(),
            noteDao = get(),
            blockDao = get(),
            folderDao = get(),
            tagDao = get(),
            categoryDao = get(),
            settingsManager = get(),
            mediaStorageHelper = get(),
            noteRepository = get(),
            selfHostDeletedNoteDao = get(),
            chatSessionDao = get(),
            selfHostDeletedApiConfigDao = get(),
            aiSettingsRepository = get(),
            database = get()
        )
    }
    single {
        com.ben.ember.domain.sync.MediaRetryCoordinator(
            syncRepository = get(),
            selfHostSyncEngine = get()
        )
    }

    single {
        ForegroundSyncPoller(
            webDavSyncClient = get(),
            selfHostSyncEngine = get(),
            settingsManager = get()
        )
    }

    viewModel {
        SelfHostSetupViewModel(
            webDavSyncClient = get(),
            secureSyncKeyStorage = get(),
            keyDerivationManager = get(),
            selfHostSyncEngine = get(),
            selfHostSyncScheduler = get(),
            settingsManager = get(),
            foregroundSyncPoller = get()
        )
    }
}