package com.byron.trucaller.data.repository

import com.byron.trucaller.data.dao.StolenReportDao
import com.byron.trucaller.data.model.ReportStatus
import com.byron.trucaller.data.model.StolenReport
import kotlinx.coroutines.flow.Flow

class StolenReportRepository(private val stolenReportDao: StolenReportDao) {
    fun getAllReports(): Flow<List<StolenReport>> = stolenReportDao.getAll()
    fun getReportsByDevice(deviceId: String): Flow<List<StolenReport>> = stolenReportDao.getByDeviceId(deviceId)
    fun getReportsByUser(userId: String): Flow<List<StolenReport>> = stolenReportDao.getByUserId(userId)
    fun getReportCount(): Flow<Int> = stolenReportDao.countFlow()

    suspend fun getReportById(id: String): StolenReport? = stolenReportDao.getById(id)
    suspend fun insertReport(report: StolenReport) = stolenReportDao.insert(report)
    suspend fun updateReport(report: StolenReport) = stolenReportDao.update(report)
    suspend fun updateReportStatus(id: String, status: ReportStatus) = stolenReportDao.updateStatus(id, status)
}
