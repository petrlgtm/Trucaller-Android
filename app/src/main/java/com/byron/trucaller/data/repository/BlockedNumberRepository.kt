package com.byron.trucaller.data.repository

import com.byron.trucaller.data.dao.BlockedNumberDao
import com.byron.trucaller.data.model.BlockedNumber
import com.byron.trucaller.util.PhoneUtils
import kotlinx.coroutines.flow.Flow

class BlockedNumberRepository(private val blockedNumberDao: BlockedNumberDao) {
    fun getBlockedByUser(userId: String): Flow<List<BlockedNumber>> = blockedNumberDao.getByUserId(userId)
    fun getBlockedCount(userId: String): Flow<Int> = blockedNumberDao.getCountByUser(userId)

    suspend fun blockNumber(blockedNumber: BlockedNumber) {
        val normalized = blockedNumber.copy(
            phoneNumber = PhoneUtils.normalizePhone(blockedNumber.phoneNumber)
        )
        blockedNumberDao.insert(normalized)
    }

    suspend fun unblockNumber(userId: String, phone: String) {
        blockedNumberDao.unblock(userId, PhoneUtils.normalizePhone(phone))
    }

    suspend fun isBlocked(userId: String, phone: String): Boolean {
        return blockedNumberDao.isBlocked(userId, PhoneUtils.normalizePhone(phone))
    }
}
