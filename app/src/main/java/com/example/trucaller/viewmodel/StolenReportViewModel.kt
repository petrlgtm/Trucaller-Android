package com.example.trucaller.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.trucaller.TruCallerApplication
import com.example.trucaller.data.model.DeviceStatus
import com.example.trucaller.data.model.LastKnownLocation
import com.example.trucaller.data.model.ReportStatus
import com.example.trucaller.data.model.StolenReport
import com.example.trucaller.data.repository.DeviceRepository
import com.example.trucaller.data.repository.StolenReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StolenReportViewModel(
    application: Application,
    private val stolenReportRepository: StolenReportRepository,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    val allReports: Flow<List<StolenReport>> = stolenReportRepository.getAllReports()
    val reportCount: Flow<Int> = stolenReportRepository.getReportCount()

    fun getReportsByDevice(deviceId: String): Flow<List<StolenReport>> =
        stolenReportRepository.getReportsByDevice(deviceId)

    fun getReportsByUser(userId: String): Flow<List<StolenReport>> =
        stolenReportRepository.getReportsByUser(userId)

    fun reportStolen(userId: String, deviceId: String, description: String, pin: String) {
        viewModelScope.launch {
            val device = deviceRepository.getDeviceById(deviceId) ?: return@launch
            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

            // Get real location from latest IP log
            val ipLogs = deviceRepository.getIpLogsByDevice(deviceId).first()
            val latestIp = ipLogs.maxByOrNull { it.timestamp }
            val location = if (latestIp != null) {
                LastKnownLocation(latestIp.latitude, latestIp.longitude, latestIp.city)
            } else {
                LastKnownLocation(0.0, 0.0, "Unknown")
            }

            val report = StolenReport(
                id = "sr-${System.currentTimeMillis()}",
                deviceId = deviceId,
                reportedBy = userId,
                reportedAt = now,
                status = ReportStatus.PENDING,
                description = description,
                pinVerified = true,
                lastKnownIp = latestIp?.ipAddress ?: device.lastIp,
                lastKnownLocation = location
            )
            stolenReportRepository.insertReport(report)
            deviceRepository.updateDeviceStatus(deviceId, DeviceStatus.STOLEN)
        }
    }

    fun updateReportStatus(reportId: String, newStatus: ReportStatus) {
        viewModelScope.launch {
            stolenReportRepository.updateReportStatus(reportId, newStatus)
        }
    }

    fun markDeviceRecovered(deviceId: String) {
        viewModelScope.launch {
            deviceRepository.updateDeviceStatus(deviceId, DeviceStatus.ACTIVE)
            // Resolve all reports for this device
            val reports = stolenReportRepository.getReportsByDevice(deviceId).first()
            reports.filter { it.status != ReportStatus.RESOLVED }.forEach { report ->
                stolenReportRepository.updateReportStatus(report.id, ReportStatus.RESOLVED)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruCallerApplication
                StolenReportViewModel(
                    app,
                    app.container.stolenReportRepository,
                    app.container.deviceRepository
                )
            }
        }
    }
}
