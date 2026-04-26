package com.byron.trucaller.viewmodel

import android.app.Application
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.byron.trucaller.TruCallerApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CallRecording(
    val id: String,
    val filePath: String,
    val fileName: String,
    val phoneNumber: String,
    val contactName: String?,
    val direction: RecordingDirection,
    val durationMs: Long,
    val fileSize: Long,
    val timestamp: Long,
    val isStarred: Boolean = false
)

enum class RecordingDirection { INCOMING, OUTGOING }

enum class RecordingFilter { ALL, INCOMING, OUTGOING, STARRED }

data class StorageInfo(
    val totalRecordingsSize: Long = 0L,
    val recordingCount: Int = 0,
    val deviceFreeSpace: Long = 0L,
    val deviceTotalSpace: Long = 0L
)

data class PlaybackState(
    val recordingId: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f
)

class CallRecordingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _recordings = MutableStateFlow<List<CallRecording>>(emptyList())
    val recordings: StateFlow<List<CallRecording>> = _recordings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedFilter = MutableStateFlow(RecordingFilter.ALL)
    val selectedFilter: StateFlow<RecordingFilter> = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _storageInfo = MutableStateFlow(StorageInfo())
    val storageInfo: StateFlow<StorageInfo> = _storageInfo.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _pendingDelete = MutableStateFlow<CallRecording?>(null)
    val pendingDelete: StateFlow<CallRecording?> = _pendingDelete.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val recordingsDir: File
        get() {
            val dir = File(
                getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                "call_recordings"
            )
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    init {
        loadRecordings()
    }

    private val starIndexFile: File
        get() = File(recordingsDir, ".starred")

    private fun loadStarredSet(): Set<String> {
        return try {
            if (starIndexFile.exists()) starIndexFile.readLines().toHashSet() else emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun saveStarredSet(starred: Set<String>) {
        try {
            starIndexFile.writeText(starred.joinToString("\n"))
        } catch (_: Exception) { }
    }

    fun loadRecordings() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val (files, starred) = withContext(Dispatchers.IO) {
                    val dir = recordingsDir
                    val f = dir.listFiles()?.filter {
                        it.isFile && (it.extension in listOf("mp3", "m4a", "aac", "wav", "ogg", "3gp", "amr"))
                    }?.sortedByDescending { it.lastModified() } ?: emptyList()
                    Pair(f, loadStarredSet())
                }

                val recordings = withContext(Dispatchers.IO) {
                    files.mapIndexed { index, file ->
                        parseRecordingFile(file, index, starred)
                    }
                }

                _recordings.value = recordings
                updateStorageInfo()
            } catch (e: Exception) {
                _actionMessage.value = "Failed to load recordings: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun parseRecordingFile(file: File, index: Int, starred: Set<String>): CallRecording {
        // Parse file name pattern: direction_number_timestamp.ext
        // e.g. "incoming_+254712345678_1711234567890.m4a"
        val nameParts = file.nameWithoutExtension.split("_", limit = 3)
        val direction = when (nameParts.getOrNull(0)?.lowercase()) {
            "incoming", "in" -> RecordingDirection.INCOMING
            "outgoing", "out" -> RecordingDirection.OUTGOING
            else -> RecordingDirection.INCOMING
        }
        val phoneNumber = nameParts.getOrNull(1) ?: "Unknown"
        val timestamp = nameParts.getOrNull(2)?.toLongOrNull() ?: file.lastModified()

        // Use MediaMetadataRetriever — lightweight, no full prepare/decode needed
        val durationMs = try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            dur
        } catch (_: Exception) {
            0L
        }

        return CallRecording(
            id = "rec_${file.name.hashCode()}_$index",
            filePath = file.absolutePath,
            fileName = file.name,
            phoneNumber = phoneNumber,
            contactName = null,
            direction = direction,
            durationMs = durationMs,
            fileSize = file.length(),
            timestamp = timestamp,
            isStarred = file.name in starred
        )
    }

    private fun updateStorageInfo() {
        val recs = _recordings.value
        val totalSize = recs.sumOf { it.fileSize }
        val statPath = recordingsDir.takeIf { it.exists() }?.absolutePath
            ?: getApplication<Application>().filesDir.absolutePath
        val stat = StatFs(statPath)
        _storageInfo.value = StorageInfo(
            totalRecordingsSize = totalSize,
            recordingCount = recs.size,
            deviceFreeSpace = stat.availableBytes,
            deviceTotalSpace = stat.totalBytes
        )
    }

    fun setFilter(filter: RecordingFilter) {
        _selectedFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getFilteredRecordings(): List<CallRecording> {
        val all = _recordings.value
        val query = _searchQuery.value.lowercase()

        val filtered = when (_selectedFilter.value) {
            RecordingFilter.ALL -> all
            RecordingFilter.INCOMING -> all.filter { it.direction == RecordingDirection.INCOMING }
            RecordingFilter.OUTGOING -> all.filter { it.direction == RecordingDirection.OUTGOING }
            RecordingFilter.STARRED -> all.filter { it.isStarred }
        }

        return if (query.isBlank()) {
            filtered
        } else {
            filtered.filter { rec ->
                (rec.contactName?.lowercase()?.contains(query) == true) ||
                    rec.phoneNumber.contains(query) ||
                    rec.fileName.lowercase().contains(query)
            }
        }
    }

    // -- Playback --

    fun togglePlayback(recording: CallRecording) {
        val current = _playbackState.value
        if (current.recordingId == recording.id && current.isPlaying) {
            pausePlayback()
        } else if (current.recordingId == recording.id && !current.isPlaying) {
            resumePlayback()
        } else {
            startPlayback(recording)
        }
    }

    private fun startPlayback(recording: CallRecording) {
        stopPlayback()
        val player = MediaPlayer()
        try {
            player.setDataSource(recording.filePath)
            player.prepare()
            player.setOnCompletionListener {
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    currentPositionMs = it.duration.toLong()
                )
                progressJob?.cancel()
            }
            player.start()
            mediaPlayer = player
            val duration = player.duration.toLong()
            _playbackState.value = PlaybackState(
                recordingId = recording.id,
                isPlaying = true,
                currentPositionMs = 0L,
                totalDurationMs = duration,
                playbackSpeed = _playbackState.value.playbackSpeed
            )
            applyPlaybackSpeed()
            startProgressTracking()
        } catch (e: Exception) {
            player.release()
            _actionMessage.value = "Playback error: ${e.message}"
        }
    }

    private fun pausePlayback() {
        mediaPlayer?.pause()
        progressJob?.cancel()
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }

    private fun resumePlayback() {
        mediaPlayer?.start()
        _playbackState.value = _playbackState.value.copy(isPlaying = true)
        startProgressTracking()
    }

    fun stopPlayback() {
        progressJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        _playbackState.value = PlaybackState()
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
        applyPlaybackSpeed()
    }

    private fun applyPlaybackSpeed() {
        mediaPlayer?.let { player ->
            try {
                val params = player.playbackParams
                params.speed = _playbackState.value.playbackSpeed
                player.playbackParams = params
            } catch (_: Exception) {
                // Playback speed not supported on this device
            }
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                val pos = mediaPlayer?.currentPosition?.toLong() ?: break
                _playbackState.value = _playbackState.value.copy(currentPositionMs = pos)
                delay(200L)
            }
        }
    }

    // -- Star/Delete --

    fun toggleStar(recording: CallRecording) {
        val newStarred = !recording.isStarred
        _recordings.value = _recordings.value.map {
            if (it.id == recording.id) it.copy(isStarred = newStarred) else it
        }
        viewModelScope.launch(Dispatchers.IO) {
            val current = loadStarredSet().toMutableSet()
            if (newStarred) current.add(recording.fileName) else current.remove(recording.fileName)
            saveStarredSet(current)
        }
        _actionMessage.value = if (newStarred) "Recording starred" else "Star removed"
    }

    fun markForDeletion(recording: CallRecording) {
        _pendingDelete.value = recording
        // Temporarily remove from list
        _recordings.value = _recordings.value.filter { it.id != recording.id }
        _actionMessage.value = "Recording deleted"
    }

    fun undoDelete() {
        val deleted = _pendingDelete.value ?: return
        _pendingDelete.value = null
        // Restore to list in correct position by timestamp
        val current = _recordings.value.toMutableList()
        val insertIndex = current.indexOfFirst { it.timestamp < deleted.timestamp }
        if (insertIndex >= 0) {
            current.add(insertIndex, deleted)
        } else {
            current.add(deleted)
        }
        _recordings.value = current
        _actionMessage.value = "Delete undone"
    }

    fun confirmDelete() {
        val recording = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                File(recording.filePath).delete()
                withContext(Dispatchers.Main) {
                    updateStorageInfo()
                }
            } catch (_: Exception) {
                // File already removed from list
            }
        }
    }

    fun deleteOldRecordings(olderThanDays: Int) {
        val cutoff = System.currentTimeMillis() - (olderThanDays.toLong() * 24 * 60 * 60 * 1000)
        val toDelete = _recordings.value.filter { it.timestamp < cutoff }
        if (toDelete.isEmpty()) {
            _actionMessage.value = "No recordings older than $olderThanDays days"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            toDelete.forEach { rec ->
                try {
                    File(rec.filePath).delete()
                } catch (_: Exception) { }
            }
            withContext(Dispatchers.Main) {
                _recordings.value = _recordings.value.filter { it.timestamp >= cutoff }
                updateStorageInfo()
                _actionMessage.value = "${toDelete.size} old recordings deleted"
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruCallerApplication
                CallRecordingsViewModel(app)
            }
        }

        fun formatDuration(ms: Long): String {
            val totalSec = ms / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return "%d:%02d".format(min, sec)
        }

        fun formatFileSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
                bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
                else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
            }
        }

        fun formatTimestamp(timestamp: Long): String {
            val sdf = SimpleDateFormat("h:mm a", Locale.US)
            return sdf.format(Date(timestamp))
        }

        fun formatDateHeader(timestamp: Long): String {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val todayStart = cal.timeInMillis
            val yesterdayStart = todayStart - 24 * 60 * 60 * 1000L

            return when {
                timestamp >= todayStart -> "Today"
                timestamp >= yesterdayStart -> "Yesterday"
                else -> SimpleDateFormat("EEEE, MMM d", Locale.US).format(Date(timestamp))
            }
        }
    }
}
