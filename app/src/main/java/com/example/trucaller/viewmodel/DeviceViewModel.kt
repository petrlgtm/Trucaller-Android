package com.example.trucaller.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.trucaller.TruCallerApplication
import com.example.trucaller.data.model.Device
import com.example.trucaller.data.model.DeviceStatus
import com.example.trucaller.data.model.IpLog
import com.example.trucaller.data.repository.DeviceRepository
import com.example.trucaller.data.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository,
    private val userRepository: UserRepository
) : AndroidViewModel(application) {

    val allDevices: Flow<List<Device>> = deviceRepository.getAllDevices()
    val deviceCount: Flow<Int> = deviceRepository.getDeviceCount()
    val userCount: Flow<Int> = userRepository.getUserCount()

    private val _userDevice = MutableStateFlow<Device?>(null)
    val userDevice: StateFlow<Device?> = _userDevice.asStateFlow()

    fun loadUserDevice(userId: String) {
        viewModelScope.launch {
            _userDevice.value = deviceRepository.getFirstDeviceByUser(userId)
        }
    }

    fun getDevicesByUser(userId: String): Flow<List<Device>> = deviceRepository.getDevicesByUser(userId)
    fun getIpLogs(deviceId: String): Flow<List<IpLog>> = deviceRepository.getIpLogsByDevice(deviceId)
    fun getAllIpLogs(): Flow<List<IpLog>> = deviceRepository.getAllIpLogs()

    suspend fun getDeviceById(id: String): Device? = deviceRepository.getDeviceById(id)

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
                DeviceViewModel(app, app.container.deviceRepository, app.container.userRepository)
            }
        }
    }
}
