package com.byron.trucaller

import android.app.Application
import android.util.Log
import com.byron.trucaller.di.AppContainer
import com.byron.trucaller.service.ApiClient
import com.byron.trucaller.service.NotificationChannelManager
import com.byron.trucaller.service.SpamSyncWorker
import com.byron.trucaller.util.SecurityUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.byron.trucaller.BuildConfig

class TruCallerApplication : Application() {
    lateinit var container: AppContainer

    /** True if root indicators were detected at startup. */
    var isDeviceRooted: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Ensure the HTTP client talks to the environment we built for.
        ApiClient.setBaseUrl(BuildConfig.API_BASE_URL)

        // Root detection disabled to ensure app accessibility
        isDeviceRooted = false

        // Defer non-critical initializations off the main thread
        CoroutineScope(Dispatchers.IO).launch {
            container.seedDatabaseIfEmpty()
            SpamSyncWorker.schedule(this@TruCallerApplication)
        }

        // Defer notification channel creation — still synchronous but low-cost,
        // moved to a background post so it does not block Activity.onCreate()
        CoroutineScope(Dispatchers.Default).launch {
            NotificationChannelManager.createNotificationChannels(this@TruCallerApplication)
        }
    }

    companion object {
        private const val TAG = "TruCallerApp"
    }
}
