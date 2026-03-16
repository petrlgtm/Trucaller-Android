package com.byron.trucaller.data.repository

import com.byron.trucaller.data.dao.DeviceDao
import com.byron.trucaller.data.dao.IpLogDao
import com.byron.trucaller.data.model.Device
import com.byron.trucaller.data.model.DeviceStatus
import com.byron.trucaller.data.model.IpLog
import kotlinx.coroutines.flow.Flow

class DeviceRepository(
    private val deviceDao: DeviceDao,
    private val ipLogDao: IpLogDao
) {
    fun getAllDevices(): Flow<List<Device>> = deviceDao.getAll()
    fun getDevicesByUser(userId: String): Flow<List<Device>> = deviceDao.getByUserId(userId)
    fun getDeviceCount(): Flow<Int> = deviceDao.countFlow()

    suspend fun getDeviceById(id: String): Device? = deviceDao.getById(id)
    suspend fun getFirstDeviceByUser(userId: String): Device? = deviceDao.getFirstByUserId(userId)
    fun observeFirstDeviceByUser(userId: String): Flow<Device?> = deviceDao.observeFirstByUserId(userId)
    suspend fun insertDevice(device: Device) = deviceDao.insert(device)
    suspend fun updateDevice(device: Device) = deviceDao.update(device)
    suspend fun updateDeviceStatus(deviceId: String, status: DeviceStatus) = deviceDao.updateStatus(deviceId, status)

    fun getIpLogsByDevice(deviceId: String): Flow<List<IpLog>> = ipLogDao.getByDeviceId(deviceId)
    fun getAllIpLogs(): Flow<List<IpLog>> = ipLogDao.getAll()
    suspend fun insertIpLog(ipLog: IpLog) = ipLogDao.insert(ipLog)

    suspend fun updateFcmToken(deviceId: String, token: String) = deviceDao.updateFcmToken(deviceId, token)
    suspend fun getActiveDeviceByUser(userId: String): Device? = deviceDao.getActiveDeviceByUser(userId)
}
