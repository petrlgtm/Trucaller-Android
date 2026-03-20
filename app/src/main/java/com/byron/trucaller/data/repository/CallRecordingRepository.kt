package com.byron.trucaller.data.repository

import com.byron.trucaller.data.dao.CallRecordingDao
import com.byron.trucaller.data.model.CallRecording
import kotlinx.coroutines.flow.Flow

class CallRecordingRepository(
    private val callRecordingDao: CallRecordingDao
) {
    fun getByUserId(userId: String): Flow<List<CallRecording>> =
        callRecordingDao.getByUserId(userId)

    suspend fun getById(id: String): CallRecording? =
        callRecordingDao.getById(id)

    fun getByPhoneNumber(userId: String, phoneNumber: String): Flow<List<CallRecording>> =
        callRecordingDao.getByPhoneNumber(userId, phoneNumber)

    fun getStarred(userId: String): Flow<List<CallRecording>> =
        callRecordingDao.getStarred(userId)

    suspend fun insert(recording: CallRecording) =
        callRecordingDao.insert(recording)

    suspend fun update(recording: CallRecording) =
        callRecordingDao.update(recording)

    suspend fun updateStarred(id: String, isStarred: Boolean) =
        callRecordingDao.updateStarred(id, isStarred)

    suspend fun updateDurationAndSize(id: String, duration: Long, fileSize: Long) =
        callRecordingDao.updateDurationAndSize(id, duration, fileSize)

    suspend fun delete(recording: CallRecording) =
        callRecordingDao.delete(recording)

    suspend fun deleteById(id: String) =
        callRecordingDao.deleteById(id)

    fun getCountByUser(userId: String): Flow<Int> =
        callRecordingDao.getCountByUser(userId)
}
