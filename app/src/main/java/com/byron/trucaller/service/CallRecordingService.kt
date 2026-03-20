package com.byron.trucaller.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.byron.trucaller.MainActivity
import com.byron.trucaller.R
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.CallDirection
import com.byron.trucaller.data.model.CallRecording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Foreground service that records phone calls using [MediaRecorder].
 *
 * Recording source preference:
 * 1. [MediaRecorder.AudioSource.VOICE_CALL] — captures both sides on supported devices
 * 2. [MediaRecorder.AudioSource.MIC] — fallback when VOICE_CALL is unavailable
 *
 * Recordings are stored in the app's private files directory under `recordings/`.
 * A persistent notification is shown while recording is in progress.
 */
class CallRecordingService : Service() {

    companion object {
        private const val TAG = "CallRecordingService"
        private const val NOTIFICATION_ID = 700_001
        private const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        private const val EXTRA_CALL_DIRECTION = "extra_call_direction"
        private const val RECORDINGS_DIR = "recordings"

        fun start(context: Context, phoneNumber: String, callDirection: CallDirection) {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_CALL_DIRECTION, callDirection.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CallRecordingService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingId: String? = null
    private var recordingStartTime: Long = 0L
    private var isRecording = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val phoneNumber = intent?.getStringExtra(EXTRA_PHONE_NUMBER)
        val directionStr = intent?.getStringExtra(EXTRA_CALL_DIRECTION)

        if (phoneNumber.isNullOrBlank() || directionStr == null) {
            Log.w(TAG, "Missing phone number or direction, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }

        val callDirection = try {
            CallDirection.valueOf(directionStr)
        } catch (_: IllegalArgumentException) {
            CallDirection.INCOMING
        }

        // Start foreground immediately to avoid ANR
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(phoneNumber),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(phoneNumber))
        }

        if (!isRecording) {
            startRecording(phoneNumber, callDirection)
        }

        return START_NOT_STICKY
    }

    private fun startRecording(phoneNumber: String, callDirection: CallDirection) {
        val app = applicationContext as? TruCallerApplication
        if (app == null) {
            Log.e(TAG, "Failed to get TruCallerApplication")
            stopSelf()
            return
        }

        val id = UUID.randomUUID().toString()
        recordingId = id

        // Ensure recordings directory exists
        val recordingsDir = File(filesDir, RECORDINGS_DIR)
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs()
        }

        val timestamp = System.currentTimeMillis()
        val fileName = "rec_${timestamp}_${phoneNumber.replace(Regex("[^0-9+]"), "")}.m4a"
        val outputFile = File(recordingsDir, fileName)
        recordingFile = outputFile

        // Attempt to play a consent beep if enabled in preferences
        serviceScope.launch {
            val prefs = app.container.userPreferences
            val beepEnabled = prefs.recordingPlayBeep.firstOrNull() ?: true
            if (beepEnabled) {
                playConsentBeep()
            }
        }

        // Initialize and start MediaRecorder
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            // Try VOICE_CALL first, fallback to MIC
            val audioSource = try {
                recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                MediaRecorder.AudioSource.VOICE_CALL
            } catch (e: Exception) {
                Log.w(TAG, "VOICE_CALL source unavailable, falling back to MIC", e)
                recorder.reset()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Need a new recorder after reset on some devices
                }
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                MediaRecorder.AudioSource.MIC
            }

            Log.d(TAG, "Using audio source: ${if (audioSource == MediaRecorder.AudioSource.VOICE_CALL) "VOICE_CALL" else "MIC"}")

            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128_000)
            recorder.setAudioSamplingRate(44_100)
            recorder.setOutputFile(outputFile.absolutePath)
            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            recordingStartTime = timestamp
            isRecording = true

            Log.d(TAG, "Recording started: $fileName (direction=$callDirection)")

            // Insert the recording entry into the database
            serviceScope.launch {
                val userId = prefs(app).loggedInUserId.firstOrNull() ?: "unknown"
                val recording = CallRecording(
                    id = id,
                    userId = userId,
                    phoneNumber = phoneNumber,
                    contactName = null,
                    callDirection = callDirection,
                    startTime = timestamp,
                    duration = 0L,
                    filePath = outputFile.absolutePath,
                    fileSize = 0L,
                    isStarred = false,
                    createdAt = timestamp
                )
                try {
                    app.container.callRecordingRepository.insert(recording)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to insert recording entry", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            recorder.release()
            mediaRecorder = null
            isRecording = false
            stopSelf()
        }
    }

    private fun prefs(app: TruCallerApplication) = app.container.userPreferences

    private fun stopRecording() {
        if (!isRecording) return

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            Log.d(TAG, "Recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            mediaRecorder?.release()
        } finally {
            mediaRecorder = null
            isRecording = false
        }

        // Update the database entry with duration and file size
        val id = recordingId ?: return
        val file = recordingFile ?: return
        val duration = System.currentTimeMillis() - recordingStartTime
        val fileSize = if (file.exists()) file.length() else 0L

        val app = applicationContext as? TruCallerApplication ?: return
        // Use runBlocking to ensure the DB update completes before onDestroy cancels the scope
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            try {
                app.container.callRecordingRepository.updateDurationAndSize(id, duration, fileSize)
                Log.d(TAG, "Recording entry updated: duration=${duration}ms, size=${fileSize}B")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update recording entry", e)
            }
        }
    }

    private fun playConsentBeep() {
        try {
            val toneGenerator = android.media.ToneGenerator(
                android.media.AudioManager.STREAM_VOICE_CALL,
                50 // volume (0-100)
            )
            toneGenerator.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 200)
            Thread.sleep(250)
            toneGenerator.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play consent beep", e)
        }
    }

    private fun buildNotification(phoneNumber: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationChannelManager.GENERAL_CHANNEL)
            .setSmallIcon(R.drawable.ic_recording)
            .setContentTitle("Recording call")
            .setContentText("Call with $phoneNumber is being recorded")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        stopRecording()
        serviceScope.cancel()
        super.onDestroy()
    }
}
