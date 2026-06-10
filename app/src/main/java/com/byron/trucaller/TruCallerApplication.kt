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
import kotlinx.coroutines.flow.first
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
        ApiClient.init(this)

        // Persist rotated tokens — the backend deletes the old refresh token on every
        // refresh, so losing the new pair on process death would force a re-login.
        ApiClient.onTokensRefreshed = { accessToken, refreshToken ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    container.userPreferences.setAuthToken(accessToken)
                    refreshToken?.let { container.userPreferences.setRefreshToken(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist refreshed tokens", e)
                }
            }
        }

        // Root detection disabled to ensure app accessibility
        isDeviceRooted = false

        // Defer non-critical initializations off the main thread
        CoroutineScope(Dispatchers.IO).launch {
            // Restore persisted tokens so background work (spam sync, SMS receiver,
            // FCM handlers) is authenticated even before any UI is opened.
            try {
                val prefs = container.userPreferences
                prefs.authToken.first()?.let { ApiClient.setAuthToken(it) }
                prefs.refreshToken.first()?.let { ApiClient.setRefreshToken(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore persisted session tokens", e)
            }
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
