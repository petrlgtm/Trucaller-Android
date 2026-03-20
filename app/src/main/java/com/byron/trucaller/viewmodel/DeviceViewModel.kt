package com.byron.trucaller.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.util.Log
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.Device
import com.byron.trucaller.data.model.DeviceStatus
import com.byron.trucaller.data.model.IpLog
import com.byron.trucaller.data.repository.DeviceRepository
import com.byron.trucaller.data.repository.UserRepository
import com.byron.trucaller.service.ApiClient
import com.byron.trucaller.service.DeviceRegistrationService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DeviceViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository,
    private val userRepository: UserRepository,
    private val deviceRegistrationService: DeviceRegistrationService
) : AndroidViewModel(application) {

    val allDevices: Flow<List<Device>> = deviceRepository.getAllDevices()
    val deviceCount: Flow<Int> = deviceRepository.getDeviceCount()
    val userCount: Flow<Int> = userRepository.getUserCount()

    private val _userDevice = MutableStateFlow<Device?>(null)
    val userDevice: StateFlow<Device?> = _userDevice.asStateFlow()

    /** True while an admin status update is in progress. */
    private val _statusUpdating = MutableStateFlow(false)
    val statusUpdating: StateFlow<Boolean> = _statusUpdating.asStateFlow()

    /** Transient message shown after admin status change. */
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearStatusMessage() { _statusMessage.value = null }

    private var deviceObserverJob: Job? = null

    fun loadUserDevice(userId: String) {
        deviceObserverJob?.cancel()
        deviceObserverJob = viewModelScope.launch {
            deviceRepository.observeFirstDeviceByUser(userId).collectLatest { device ->
                _userDevice.value = device
            }
        }
    }

    fun getDevicesByUser(userId: String): Flow<List<Device>> = deviceRepository.getDevicesByUser(userId)
    fun getIpLogs(deviceId: String): Flow<List<IpLog>> = deviceRepository.getIpLogsByDevice(deviceId)
    fun getAllIpLogs(): Flow<List<IpLog>> = deviceRepository.getAllIpLogs()

    suspend fun getDeviceById(id: String): Device? = deviceRepository.getDeviceById(id)

    /**
     * Re-registers the device with fresh location data.
     * Call after location permission is granted to update from "Unknown".
     */
    fun refreshDeviceLocation(userId: String) {
        viewModelScope.launch {
            try {
                deviceRegistrationService.registerOrUpdateDevice(userId)
                loadUserDevice(userId)
            } catch (_: Exception) { }
        }
    }

    fun updateDeviceStatus(deviceId: String, status: DeviceStatus) {
        viewModelScope.launch {
            deviceRepository.updateDeviceStatus(deviceId, status)
            val current = _userDevice.value
            if (current?.id == deviceId) {
                _userDevice.value = current.copy(status = status)
            }
        }
    }

    /**
     * Admin-only: update device status with backend sync and audit logging.
     *
     * @param onStolenPrompt callback invoked when changing to STOLEN so the UI
     *   can prompt the admin to optionally create a stolen report.
     * @param onAutoResolved callback invoked when pending stolen reports are
     *   auto-resolved (transition away from STOLEN to ACTIVE).
     */
    fun adminUpdateDeviceStatus(
        deviceId: String,
        newStatus: DeviceStatus,
        adminId: String,
        adminName: String,
        onStolenPrompt: (() -> Unit)? = null,
        onAutoResolved: ((count: Int) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _statusUpdating.value = true
            try {
                val oldDevice = deviceRepository.getDeviceById(deviceId)
                val oldStatus = oldDevice?.status

                // 1. Update Room locally
                deviceRepository.updateDeviceStatus(deviceId, newStatus)

                // 2. Best-effort sync to backend
                try {
                    val result = ApiClient.adminUpdateDeviceStatus(
                        deviceId = deviceId,
                        status = newStatus.name,
                        changedBy = adminId,
                        changedByName = adminName
                    )
                    if (result.success) {
                        Log.d(TAG, "Admin status change synced: $deviceId -> $newStatus by $adminName")
                    } else {
                        Log.w(TAG, "Backend sync failed for admin status change: ${result.error}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Backend sync error for admin status change on $deviceId", e)
                }

                Log.i(TAG, "ADMIN_STATUS_CHANGE: device=$deviceId " +
                        "from=${oldStatus?.name ?: "UNKNOWN"} to=${newStatus.name} " +
                        "by=$adminName ($adminId)")

                // 3. If changing to STOLEN, prompt admin for optional stolen report
                if (newStatus == DeviceStatus.STOLEN && oldStatus != DeviceStatus.STOLEN) {
                    onStolenPrompt?.invoke()
                }

                // 4. If transitioning from STOLEN to ACTIVE, auto-resolve pending stolen reports
                if (oldStatus == DeviceStatus.STOLEN && newStatus == DeviceStatus.ACTIVE) {
                    onAutoResolved?.invoke(0) // signal the UI; actual resolution handled by StolenReportViewModel
                }

                _statusMessage.value = "Status changed to ${newStatus.name}"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update device status for $deviceId", e)
                _statusMessage.value = "Failed to update status: ${e.message}"
            } finally {
                _statusUpdating.value = false
            }
        }
    }

    companion object {
        private const val TAG = "DeviceViewModel"
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruCallerApplication
                val deviceRegService = DeviceRegistrationService(app, app.container.deviceRepository)
                DeviceViewModel(app, app.container.deviceRepository, app.container.userRepository, deviceRegService)
            }
        }
    }
}
