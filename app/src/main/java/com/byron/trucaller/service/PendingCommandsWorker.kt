package com.byron.trucaller.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.byron.trucaller.TruCallerApplication
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Polling fallback for remote security commands (alarm, lock, wipe, locate).
 *
 * FCM is the primary transport, but when push delivery is unavailable
 * (no credentials on the server, token revoked, Play Services issues) queued
 * commands stay PENDING on the backend. This worker fetches and executes them:
 * periodically every 15 minutes, plus a one-shot pass on app startup.
 */
class PendingCommandsWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? TruCallerApplication ?: return Result.failure()

        return try {
            val userId = app.container.userPreferences.loggedInUserId.first()
                ?: return Result.success() // not logged in — nothing to poll

            val device = app.container.deviceRepository.getFirstDeviceByUser(userId)
                ?: return Result.success() // device not registered yet

            val result = ApiClient.getPendingAlarmCommands(device.deviceId)
            if (!result.success) {
                Log.w(TAG, "Pending-commands poll failed: ${result.error}")
                return Result.retry()
            }

            val commands = result.data.orEmpty()
            if (commands.isNotEmpty()) {
                Log.i(TAG, "Executing ${commands.size} queued remote command(s)")
            }
            for (command in commands) {
                val action = command["type"]?.toString() ?: continue
                val logId = command["logId"]?.toString()
                RemoteCommandExecutor.execute(applicationContext, action, logId)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Pending-commands poll crashed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "PendingCommandsWorker"
        private const val PERIODIC_WORK_NAME = "pending_commands_poll"
        private const val STARTUP_WORK_NAME = "pending_commands_startup"

        /** Schedules the 15-minute periodic poll plus an immediate startup pass. */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodic = PeriodicWorkRequestBuilder<PendingCommandsWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )

            // Pick up queued commands as soon as the app starts instead of
            // waiting for the first periodic slot.
            val startup = OneTimeWorkRequestBuilder<PendingCommandsWorker>()
                .setConstraints(constraints)
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                STARTUP_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                startup
            )
            Log.d(TAG, "Pending-commands polling scheduled (15 min interval + startup pass)")
        }
    }
}
