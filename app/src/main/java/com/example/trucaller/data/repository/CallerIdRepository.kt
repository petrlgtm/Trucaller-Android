package com.example.trucaller.data.repository

import com.example.trucaller.data.dao.CallerIdDao
import com.example.trucaller.data.dao.ContactDao
import com.example.trucaller.data.dao.UserDao
import com.example.trucaller.data.model.CallerIdEntry
import com.example.trucaller.data.model.Contact
import com.example.trucaller.data.model.SpamCategory
import kotlinx.coroutines.flow.Flow

class CallerIdRepository(
    private val callerIdDao: CallerIdDao,
    private val contactDao: ContactDao,
    private val userDao: UserDao
) {
    fun getAllEntries(): Flow<List<CallerIdEntry>> = callerIdDao.getAll()
    fun searchEntries(query: String): Flow<List<CallerIdEntry>> = callerIdDao.search(query)
    fun getEntryCount(): Flow<Int> = callerIdDao.countFlow()

    suspend fun getByPhone(phone: String): CallerIdEntry? = callerIdDao.getByPhone(phone)
    suspend fun getById(id: String): CallerIdEntry? = callerIdDao.getById(id)
    suspend fun insertEntry(entry: CallerIdEntry) = callerIdDao.insert(entry)
    suspend fun updateEntry(entry: CallerIdEntry) = callerIdDao.update(entry)
    suspend fun deleteEntry(entry: CallerIdEntry) = callerIdDao.delete(entry)

    /**
     * Central drive lookup: search caller ID DB first, then search ALL contacts
     * across all users. If a phone number belongs to any user in the system,
     * return their name.
     */
    suspend fun lookupNumber(rawQuery: String): LookupResult {
        var query = rawQuery.replace(" ", "").replace("-", "")
        if (query.startsWith("+256")) query = query.substring(4)
        else if (query.startsWith("256")) query = query.substring(3)
        else if (query.startsWith("0")) query = query.substring(1)

        val fullPhone = "+256$query"

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

        return LookupResult(callerIdEntry = null, contactMatch = null, source = "not_found")
    }
}

data class LookupResult(
    val callerIdEntry: CallerIdEntry?,
    val contactMatch: Contact?,
    val source: String
)
