package com.ben.ember

import android.app.Application
import android.content.SharedPreferences
import com.ben.ember.data.local.room.AppDatabase
import com.ben.ember.di.androidModule
import com.ben.ember.di.sharedModule
import com.ben.ember.domain.ai.LocalAiEngine
import com.ben.ember.domain.selfhost.sync.SelfHostSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import java.util.concurrent.atomic.AtomicBoolean

class EmberApplication : Application() {
    companion object {
        @Volatile
        var isReady = false
    }

    private val hasStartedAiWarmUp = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@EmberApplication)
            workManagerFactory()
            modules(sharedModule, androidModule)
        }

        getKoin().get<SelfHostSyncScheduler>()

        CoroutineScope(Dispatchers.IO).launch {
            getKoin().get<AppDatabase>()
            getKoin().get<SharedPreferences>()
            isReady = true
            getKoin().get<com.ben.ember.presentation.widget.note.NoteWidgetCoordinator>().start()
            getKoin().get<com.ben.ember.presentation.widget.tasks.TasksWidgetCoordinator>().start()
            getKoin().get<com.ben.ember.presentation.widget.notelist.NoteListWidgetCoordinator>().start()
            getKoin().get<com.ben.ember.presentation.widget.noteshortcut.NoteShortcutWidgetCoordinator>().start()
            getKoin().get<com.ben.ember.presentation.widget.todaytasks.TodayTasksWidgetCoordinator>().start()
            getKoin().get<com.ben.ember.presentation.widget.calendar.CalendarWidgetCoordinator>().start()
        }

    }

    fun warmUpAiEngineOnce() {
        if (!hasStartedAiWarmUp.compareAndSet(false, true)) return

        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (getKoin().get<com.ben.ember.data.local.prefs.SettingsManager>().isAiFeaturesDisabled()) return@launch
                getKoin().get<LocalAiEngine>().warmUpGenerator()
            } catch (e: Exception) {
                // Silent — if pre-warm fails, first query just pays the load cost
            }
        }
    }
}