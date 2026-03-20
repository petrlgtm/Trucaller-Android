package com.byron.trucaller

import android.app.Application
import com.byron.trucaller.di.AppContainer
import com.byron.trucaller.service.NotificationChannelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TruCallerApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannelManager.createNotificationChannels(this)
        CoroutineScope(Dispatchers.IO).launch {
            container.seedDatabaseIfEmpty()
        }
    }
}
