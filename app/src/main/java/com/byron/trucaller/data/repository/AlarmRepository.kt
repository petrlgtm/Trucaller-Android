package com.byron.trucaller.data.repository

import com.byron.trucaller.data.dao.AlarmLogDao
import com.byron.trucaller.data.model.AlarmLog
import kotlinx.coroutines.flow.Flow

class AlarmRepository(private val alarmLogDao: AlarmLogDao) {
    fun getAllLogs(): Flow<List<AlarmLog>> = alarmLogDao.getAll()
    fun getLogsByDevice(deviceId: String): Flow<List<AlarmLog>> = alarmLogDao.getByDeviceId(deviceId)
    fun getPendingCount(): Flow<Int> = alarmLogDao.getPendingCount()
    fun getLogCount(): Flow<Int> = alarmLogDao.countFlow()

    suspend fun insertLog(alarmLog: AlarmLog) = alarmLogDao.insert(alarmLog)
    suspend fun updateResult(id: String, result: String, notes: String) = alarmLogDao.updateResult(id, result, notes)
}
