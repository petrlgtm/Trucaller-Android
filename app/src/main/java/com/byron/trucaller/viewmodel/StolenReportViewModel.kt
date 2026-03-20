package com.byron.trucaller.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.DeviceStatus
import com.byron.trucaller.data.model.LastKnownLocation
import com.byron.trucaller.data.model.ReportStatus
import com.byron.trucaller.data.model.StolenReport
import com.byron.trucaller.data.repository.DeviceRepository
import com.byron.trucaller.data.repository.StolenReportRepository
import com.byron.trucaller.data.repository.UserRepository
import com.byron.trucaller.util.hashPassword
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.util.Log
import com.byron.trucaller.service.ApiClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StolenReportViewModel(
    application: Application,
    private val stolenReportRepository: StolenReportRepository,
    private val deviceRepository: DeviceRepository,
    private val userRepository: UserRepository
) : AndroidViewModel(application) {

    val allReports: Flow<List<StolenReport>> = stolenReportRepository.getAllReports()
    val reportCount: Flow<Int> = stolenReportRepository.getReportCount()

    private val _reportError = MutableStateFlow<String?>(null)
    val reportError: StateFlow<String?> = _reportError.asStateFlow()

    /** ID of the report currently being updated (drives button loading spinners). */
    private val _updatingReportId = MutableStateFlow<String?>(null)
    val updatingReportId: StateFlow<String?> = _updatingReportId.asStateFlow()

    /** Transient message shown as a snackbar after a status change. */
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearReportError() { _reportError.value = null }
    fun clearStatusMessage() { _statusMessage.value = null }

    fun getReportsByDevice(deviceId: String): Flow<List<StolenReport>> =
        stolenReportRepository.getReportsByDevice(deviceId)

    fun getReportsByUser(userId: String): Flow<List<StolenReport>> =
        stolenReportRepository.getReportsByUser(userId)

    fun reportStolen(userId: String, deviceId: String, description: String, pin: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            // Verify PIN against user's stored security PIN
            val user = userRepository.getUserById(userId)
            if (user == null) {
                _reportError.value = "User not found"
                return@launch
            }
            if (user.securityPin == null) {
                _reportError.value = "Security PIN not set. Please set a PIN in Security settings first."
                return@launch
            }
            if (user.securityPin != hashPassword(pin)) {
                _reportError.value = "Incorrect security PIN"
                return@launch
            }

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
            try {
                // Update device status FIRST, then insert report.
                // If either fails, roll back the other to maintain consistency.
                deviceRepository.updateDeviceStatus(deviceId, DeviceStatus.STOLEN)
                try {
                    stolenReportRepository.insertReport(report)
                } catch (e: Exception) {
                    // Report insert failed — roll back device status
                    deviceRepository.updateDeviceStatus(deviceId, device.status)
                    _reportError.value = "Failed to create stolen report: ${e.message}"
                    return@launch
                }

                // Best-effort sync to backend — failure is logged but does not block the user
                try {
                    val reportData = mutableMapOf<String, Any>(
                        "reportId" to report.id,
                        "deviceId" to deviceId,
                        "reportedBy" to userId,
                        "reportedAt" to now,
                        "status" to report.status.name,
                        "description" to description,
                        "pinVerified" to true,
                        "lastKnownIp" to (report.lastKnownIp ?: "")
                    )
                    if (location.latitude != 0.0 || location.longitude != 0.0) {
                        reportData["latitude"] = location.latitude
                        reportData["longitude"] = location.longitude
                        reportData["city"] = location.city
                    }
                    ApiClient.reportStolen(reportData)
                } catch (e: Exception) {
                    Log.e("StolenReportVM", "Backend sync failed for stolen report ${report.id}", e)
                }

                _reportError.value = null
                onSuccess()
            } catch (e: Exception) {
                _reportError.value = "Failed to report device as stolen: ${e.message}"
            }
        }
    }

    fun updateReportStatus(reportId: String, newStatus: ReportStatus) {
        viewModelScope.launch {
            _updatingReportId.value = reportId
            try {
                // 1. Update locally in Room
                stolenReportRepository.updateReportStatus(reportId, newStatus)

                // 2. Sync status change to backend (fire-and-forget — failures are logged)
                try {
                    val result = ApiClient.updateStolenReportStatus(reportId, newStatus.name)
                    if (result.success) {
                        Log.d("StolenReportVM", "Status synced to backend: $reportId -> $newStatus")
                    } else {
                        Log.w("StolenReportVM", "Backend status sync failed: ${result.error}")
                    }
                } catch (e: Exception) {
                    Log.e("StolenReportVM", "Backend status sync error for $reportId", e)
                }

                // 3. If resolving, also trigger device recovery on backend
                if (newStatus == ReportStatus.RESOLVED) {
                    try {
                        val report = stolenReportRepository.getReportById(reportId)
                        if (report != null) {
                            deviceRepository.updateDeviceStatus(report.deviceId, DeviceStatus.ACTIVE)
                            val recoveryResult = ApiClient.recoverDevice(report.deviceId)
                            if (recoveryResult.success) {
                                Log.d("StolenReportVM", "Device recovery synced for ${report.deviceId}")
                            } else {
                                Log.w("StolenReportVM", "Backend device recovery failed: ${recoveryResult.error}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("StolenReportVM", "Device recovery error for report $reportId", e)
                    }
                }

                _statusMessage.value = "Report ${newStatus.name.lowercase().replaceFirstChar { it.uppercase() }}"
            } catch (e: Exception) {
                Log.e("StolenReportVM", "Failed to update report status for $reportId", e)
                _statusMessage.value = "Failed to update status: ${e.message}"
            } finally {
                _updatingReportId.value = null
            }
        }
    }

    /**
     * Admin-only: update a stolen report's status without ownership check.
     * Uses the admin endpoint PUT /api/admin/stolen-reports/{id}/status.
     * When resolving, the backend automatically sets the device status to ACTIVE.
     */
    fun adminUpdateReportStatus(reportId: String, newStatus: ReportStatus) {
        viewModelScope.launch {
            _updatingReportId.value = reportId
            try {
                // 1. Update locally in Room
                stolenReportRepository.updateReportStatus(reportId, newStatus)

                // 2. Sync via admin endpoint (no ownership check on backend)
                try {
                    val result = ApiClient.updateAdminStolenReportStatus(reportId, newStatus.name)
                    if (result.success) {
                        Log.d("StolenReportVM", "Admin status synced: $reportId -> $newStatus")
                    } else {
                        Log.w("StolenReportVM", "Admin status sync failed: ${result.error}")
                    }
                } catch (e: Exception) {
                    Log.e("StolenReportVM", "Admin status sync error for $reportId", e)
                }

                // 3. If resolving, update device status locally as well
                if (newStatus == ReportStatus.RESOLVED) {
                    try {
                        val report = stolenReportRepository.getReportById(reportId)
                        if (report != null) {
                            deviceRepository.updateDeviceStatus(report.deviceId, DeviceStatus.ACTIVE)
                        }
                    } catch (e: Exception) {
                        Log.e("StolenReportVM", "Local device recovery error for report $reportId", e)
                    }
                }

                _statusMessage.value = "Report ${newStatus.name.lowercase().replaceFirstChar { it.uppercase() }}"
            } catch (e: Exception) {
                Log.e("StolenReportVM", "Failed to admin-update report status for $reportId", e)
                _statusMessage.value = "Failed to update status: ${e.message}"
            } finally {
                _updatingReportId.value = null
            }
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

            // Best-effort sync to backend — update device status and resolve stolen reports
            try {
                val result = ApiClient.recoverDevice(deviceId)
                if (result.success) {
                    Log.d("StolenReportVM", "Device recovery synced to backend for $deviceId")
                } else {
                    Log.w("StolenReportVM", "Backend recovery sync failed: ${result.error}")
                }
            } catch (e: Exception) {
                Log.e("StolenReportVM", "Backend recovery sync failed for device $deviceId", e)
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
                    app.container.deviceRepository,
                    app.container.userRepository
                )
            }
        }
    }
}
