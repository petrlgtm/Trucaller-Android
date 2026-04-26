package com.byron.trucaller.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.byron.trucaller.TruCallerApplication
import java.util.concurrent.TimeUnit

/**
 * Background worker that periodically syncs the local spam database
 * with the latest signatures from the backend.
 */
class SpamSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? TruCallerApplication ?: return Result.failure()
        val callerIdRepo = app.container.callerIdRepository
        val smsRepo = app.container.smsRepository

        Log.d(TAG, "Starting periodic spam database sync...")

        return try {
            // 1. Sync Caller ID / Spam lookups
            callerIdRepo.syncSpamDatabase()
            
            // 2. Sync SMS spam reports
            smsRepo.syncUnsyncedReports()

            Log.i(TAG, "Spam database sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Spam database sync failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SpamSyncWorker"
        private const val WORK_NAME = "periodic_spam_sync"

        /**
         * Schedules a periodic sync every 24 hours.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SpamSyncWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Periodic spam sync scheduled (24h interval)")
        }
    }
}
