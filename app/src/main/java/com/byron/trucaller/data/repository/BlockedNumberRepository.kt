package com.byron.trucaller.data.repository

import com.byron.trucaller.data.dao.BlockedNumberDao
import com.byron.trucaller.data.model.BlockedNumber
import com.byron.trucaller.service.ApiClient
import com.byron.trucaller.util.PhoneUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

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

    /**
     * Bi-directional sync of blocked numbers with the backend.
     *
     * 1. Uploads all local blocked numbers to the backend (fire-and-forget on errors).
     * 2. Downloads server-side blocked numbers and inserts any missing locally,
     *    deduplicating by normalized phone number.
     */
    suspend fun syncBlockedNumbers(userId: String): Int {
        var downloaded = 0

        // Upload local blocked numbers to backend
        val localBlocked = blockedNumberDao.getByUserId(userId).first()
        localBlocked.forEach { blocked ->
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.blockNumber(
                        mapOf(
                            "phoneNumber" to blocked.phoneNumber,
                            "name" to blocked.name,
                            "reason" to blocked.reason
                        )
                    )
                }
            } catch (_: Exception) { /* fire-and-forget */ }
        }

        // Download server-side blocked numbers and merge locally
        try {
            val result = withContext(Dispatchers.IO) { ApiClient.getBlockedNumbers() }
            if (result.success && result.data != null) {
                // Build a set of already-known normalized phone numbers for dedup
                val localPhones = localBlocked.map { PhoneUtils.normalizePhone(it.phoneNumber) }.toSet()

                result.data.forEach { map ->
                    val remote = BackendMappers.mapToBlockedNumber(map)
                    val normalizedPhone = PhoneUtils.normalizePhone(remote.phoneNumber)
                    if (normalizedPhone.isNotBlank() && normalizedPhone !in localPhones) {
                        blockNumber(remote.copy(userId = userId, phoneNumber = normalizedPhone))
                        downloaded++
                    }
                }
            }
        } catch (_: Exception) { }

        return downloaded
    }
}
