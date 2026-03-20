package com.byron.trucaller.data.repository

import com.byron.trucaller.data.dao.BlockingScheduleDao
import com.byron.trucaller.data.model.BlockingSchedule
import kotlinx.coroutines.flow.Flow

class BlockingScheduleRepository(private val blockingScheduleDao: BlockingScheduleDao) {

    fun getSchedulesByUser(userId: String): Flow<List<BlockingSchedule>> =
        blockingScheduleDao.getByUserId(userId)

    suspend fun getActiveSchedulesByUser(userId: String): List<BlockingSchedule> =
        blockingScheduleDao.getActiveByUserId(userId)

    suspend fun getScheduleById(id: String): BlockingSchedule? =
        blockingScheduleDao.getById(id)

    suspend fun insertSchedule(schedule: BlockingSchedule) =
        blockingScheduleDao.insert(schedule)

    suspend fun updateSchedule(schedule: BlockingSchedule) =
        blockingScheduleDao.update(schedule)

    suspend fun toggleScheduleActive(id: String, isActive: Boolean) =
        blockingScheduleDao.updateActive(id, isActive)

    suspend fun deleteSchedule(schedule: BlockingSchedule) =
        blockingScheduleDao.delete(schedule)

    suspend fun deleteScheduleById(id: String) =
        blockingScheduleDao.deleteById(id)

    fun getScheduleCount(userId: String): Flow<Int> =
        blockingScheduleDao.getCountByUser(userId)
}
