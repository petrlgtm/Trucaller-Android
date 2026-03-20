package com.byron.trucaller.data.repository

import com.byron.trucaller.data.dao.CallerIdDao
import com.byron.trucaller.data.dao.ContactAliasDao
import com.byron.trucaller.data.dao.ContactDao
import com.byron.trucaller.data.dao.UserDao
import com.byron.trucaller.data.model.CallerIdEntry
import com.byron.trucaller.data.model.Contact
import com.byron.trucaller.data.model.SpamCategory
import com.byron.trucaller.service.ApiClient
import com.byron.trucaller.util.PhoneUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull

class CallerIdRepository(
    private val callerIdDao: CallerIdDao,
    private val contactDao: ContactDao,
    private val userDao: UserDao,
    private val contactAliasDao: ContactAliasDao
) {
    fun getAllEntries(): Flow<List<CallerIdEntry>> = callerIdDao.getAll()
    fun searchEntries(query: String): Flow<List<CallerIdEntry>> = callerIdDao.search(query)
    fun getEntryCount(): Flow<Int> = callerIdDao.countFlow()

    suspend fun getAllEntriesOnce(): List<CallerIdEntry> = callerIdDao.getAllOnce()
    suspend fun getByPhone(phone: String): CallerIdEntry? = callerIdDao.getByPhone(phone)
    suspend fun getById(id: String): CallerIdEntry? = callerIdDao.getById(id)
    suspend fun insertEntry(entry: CallerIdEntry) = callerIdDao.insert(entry)
    suspend fun updateEntry(entry: CallerIdEntry) = callerIdDao.update(entry)
    suspend fun deleteEntry(entry: CallerIdEntry) = callerIdDao.delete(entry)
    suspend fun deleteEntriesByIds(ids: List<String>) = callerIdDao.deleteByIds(ids)
    suspend fun updateCategoryByIds(ids: List<String>, category: SpamCategory, lastUpdated: String) =
        callerIdDao.updateCategoryByIds(ids, category, lastUpdated)
    suspend fun getEntriesByIds(ids: List<String>): List<CallerIdEntry> = callerIdDao.getByIds(ids)

    /**
     * Central drive lookup: search caller ID DB first, then search ALL contacts
     * across all users. If a phone number belongs to any user in the system,
     * return their name.
     */
    suspend fun lookupNumber(rawQuery: String): LookupResult {
        val fullPhone = PhoneUtils.normalizePhone(rawQuery)

        // 1. Check caller ID database
        val callerIdEntry = callerIdDao.getByPhone(fullPhone)
        if (callerIdEntry != null) {
            return LookupResult(callerIdEntry = callerIdEntry, contactMatch = null, source = "caller_id_db")
        }

        // 2. Check registered users - if caller is a registered user, show their true name
        val registeredUser = userDao.getByPhone(fullPhone)
        if (registeredUser != null) {
            return LookupResult(
                callerIdEntry = CallerIdEntry(
                    id = "user-${registeredUser.id}",
                    phoneNumber = registeredUser.phoneNumber,
                    name = registeredUser.fullName,
                    spamScore = 0,
                    reportCount = 0,
                    category = SpamCategory.SAFE,
                    lastUpdated = registeredUser.lastLogin ?: registeredUser.createdAt
                ),
                contactMatch = null,
                source = "registered_user"
            )
        }

        // 3. Check central contacts drive - if ANY user has this number in contacts, we know who it is
        val contact = contactDao.getByPhone(fullPhone)
        if (contact != null) {
            return LookupResult(
                callerIdEntry = CallerIdEntry(
                    id = "contact-${contact.id}",
                    phoneNumber = contact.phoneNumber,
                    name = contact.name,
                    spamScore = 0,
                    reportCount = 0,
                    category = SpamCategory.SAFE,
                    lastUpdated = contact.syncedAt
                ),
                contactMatch = contact,
                source = "central_drive"
            )
        }

        // 4. Check contact aliases — user-assigned or imported alternate names for this number
        val aliases = contactAliasDao.getAliasesByPhoneOnce(fullPhone)
        if (aliases.isNotEmpty()) {
            val sourcePriority = listOf("user", "contact_book", "whatsapp", "caller_id")
            val bestAlias = aliases.sortedBy { alias ->
                val idx = sourcePriority.indexOf(alias.source)
                if (idx == -1) sourcePriority.size else idx
            }.first()
            return LookupResult(
                callerIdEntry = CallerIdEntry(
                    id = "alias-${bestAlias.id}",
                    phoneNumber = bestAlias.phoneNumber,
                    name = bestAlias.name,
                    spamScore = 0,
                    reportCount = 0,
                    category = SpamCategory.SAFE,
                    lastUpdated = bestAlias.addedAt
                ),
                contactMatch = null,
                source = "alias"
            )
        }

        // 5. Backend API fallback — query the server for community-sourced caller ID
        try {
            val apiResult = withTimeoutOrNull(5_000L) {
                ApiClient.lookupCallerId(fullPhone)
            }
            if (apiResult != null && apiResult.success && apiResult.data != null) {
                val entry = BackendMappers.mapLookupToCallerIdEntry(apiResult.data)
                if (entry != null) {
                    callerIdDao.insert(entry) // Cache locally for future lookups
                    return LookupResult(callerIdEntry = entry, contactMatch = null, source = "backend_api")
                }
            }
        } catch (_: Exception) {
            // Network error or other failure — fall through to not_found gracefully
        }

        return LookupResult(callerIdEntry = null, contactMatch = null, source = "not_found")
    }
}

data class LookupResult(
    val callerIdEntry: CallerIdEntry?,
    val contactMatch: Contact?,
    val source: String
)
