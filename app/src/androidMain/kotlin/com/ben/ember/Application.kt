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

class EmberApplication : Application() {
    companion object {
        @Volatile
        var isReady = false
    }

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
        }

        CoroutineScope(Dispatchers.Default).launch {
            try {
                getKoin().get<LocalAiEngine>().warmUpGenerator()
            } catch (e: Exception) {
                // Silent — if pre-warm fails, first query just pays the load cost
            }
        }
    }
}