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

        // Defer non-critical initializations off the main thread
        CoroutineScope(Dispatchers.IO).launch {
            container.seedDatabaseIfEmpty()
        }

        // Defer notification channel creation — still synchronous but low-cost,
        // moved to a background post so it does not block Activity.onCreate()
        CoroutineScope(Dispatchers.Default).launch {
            NotificationChannelManager.createNotificationChannels(this@TruCallerApplication)
        }
    }
}
