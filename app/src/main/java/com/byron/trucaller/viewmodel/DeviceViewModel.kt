package com.byron.trucaller.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.Device
import com.byron.trucaller.data.model.DeviceStatus
import com.byron.trucaller.data.model.IpLog
import com.byron.trucaller.data.repository.DeviceRepository
import com.byron.trucaller.data.repository.UserRepository
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruCallerApplication
                val deviceRegService = DeviceRegistrationService(app, app.container.deviceRepository)
                DeviceViewModel(app, app.container.deviceRepository, app.container.userRepository, deviceRegService)
            }
        }
    }
}
