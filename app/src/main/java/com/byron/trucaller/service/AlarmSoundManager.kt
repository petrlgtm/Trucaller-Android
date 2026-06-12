package com.byron.trucaller.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object AlarmSoundManager {
    private val _alarmActive = MutableStateFlow(false)
    val alarmActiveFlow: StateFlow<Boolean> = _alarmActive.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var alarmJob: Job? = null
    private var previousVolume: Int = 0
    private var audioManager: AudioManager? = null

    /**
     * Starts the alarm (looping siren at max volume + vibration).
     *
     * [durationMs] of `0` (the default) means the alarm rings **until it is
     * explicitly silenced** via [stopAlarm] — used by the remote "find my phone"
     * command, which must keep sounding until the owner sends STOP_ALARM. Pass a
     * positive value for a self-limiting alarm (e.g. geofence breach).
     */
    fun triggerAlarm(context: Context, durationMs: Long = 0) {
        stopAlarm()
        _alarmActive.value = true

        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager = am
        previousVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        am.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Vibrate
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }

        // Auto-stop only when a positive duration is given; durationMs == 0 means
        // ring until stopAlarm() is called (remote find-my-phone alarm).
        if (durationMs > 0) {
            alarmJob = CoroutineScope(Dispatchers.Main).launch {
                delay(durationMs)
                stopAlarm()
            }
        }
    }

    fun stopAlarm() {
        _alarmActive.value = false
        alarmJob?.cancel()
        alarmJob = null

        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null

        // Restore previous volume
        audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, previousVolume, 0)
        audioManager = null
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true
}
