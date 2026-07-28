package com.ben.inly.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import com.ben.inly.domain.repository.NoteRepository
import com.ben.inly.domain.repository.NoteRepositoryImpl
import com.ben.inly.domain.ai.NoteIndexer
import com.ben.inly.domain.selfhost.sync.ForegroundSyncPoller
import com.ben.inly.domain.selfhost.sync.SelfHostSyncEngine
import com.ben.inly.domain.selfhost.webdav.WebDavSyncClient
import com.ben.inly.domain.util.HeuristicTaskExtractor
import com.ben.inly.domain.util.TaskExtractor
import com.ben.inly.presentation.settings.selfhost.SelfHostSetupViewModel
import com.ben.inly.presentation.mobile.daily.DailyEditorViewModel
import com.ben.inly.presentation.search.SearchViewModel
import com.ben.inly.presentation.trash.TrashViewModel
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

    single<NoteRepository> {
        NoteRepositoryImpl(
            noteDao = get(),
            folderDao = get(),
            tagDao = get(),
            blockDao = get(),
            noteIndexer = get(),
            calendarTaskDao = get(),
            imageBlockDao = get(),
            documentBlockDao = get(),
            bookmarkBlockDao = get(),
            databaseTemplateDao = get(),
            categoryDao = get(),
            selfHostDeletedNoteDao = get()
        )
    }

    single {
        com.ben.inly.domain.template.DefaultTemplateSeeder(repository = get())
    }

    single<com.ben.inly.domain.repository.BackupRepository> {
        com.ben.inly.domain.repository.BackupRepositoryImpl(
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
        com.ben.inly.presentation.settings.SettingsViewModel(
            backupRepository = get(),
            noteRepository = get(),
            settingsManager = get(),
            backupRescheduler = get()
        )
    }

    viewModel {
        _root_ide_package_.com.ben.inly.presentation.mobile.home.HomeViewModel(
            repository = get(),
            settingsManager = get(),
            reminderScheduler = get(),
            taskExtractor = get(),
            voiceRecognizer = get(),
            templateSeeder = get()
        )
    }
    viewModel {
        _root_ide_package_.com.ben.inly.presentation.mobile.home.overview.reminders.RemindersViewModel(
            repository = get(),
            reminderScheduler = get()
        )
    }
    viewModel {
        _root_ide_package_.com.ben.inly.presentation.mobile.home.overview.images.ImagesViewModel(
            repository = get(),
            mediaStorageHelper = get()
        )
    }
    viewModel {
        _root_ide_package_.com.ben.inly.presentation.mobile.home.overview.documents.DocumentsViewModel(
            repository = get(),
            mediaStorageHelper = get()
        )
    }
    viewModel {
        _root_ide_package_.com.ben.inly.presentation.mobile.home.overview.bookmarks.BookmarksViewModel(
            repository = get()
        )
    }
    viewModel {
        _root_ide_package_.com.ben.inly.presentation.mobile.home.note.NoteEditorViewModel(
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
        _root_ide_package_.com.ben.inly.presentation.calendar.CalendarViewModel(
            repository = get(),
            reminderScheduler = get(),
            settingsManager = get()
        )
    }
    single<TaskExtractor> { HeuristicTaskExtractor() }

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
            localMediaReader = get(),
            noteRepository = get(),
            selfHostDeletedNoteDao = get()
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