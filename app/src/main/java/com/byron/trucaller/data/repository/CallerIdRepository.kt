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
     * Downloads the latest spam signatures from the backend and updates local cache.
     */
    suspend fun syncSpamDatabase() {
        try {
            val apiResult = withTimeoutOrNull(30_000L) {
                ApiClient.getLatestSpamSignatures()
            }
            if (apiResult != null && apiResult.success && apiResult.data != null) {
                val entries = (apiResult.data as? List<*>)?.mapNotNull { item ->
                    @Suppress("UNCHECKED_CAST")
                    val map = item as? Map<String, Any> ?: return@mapNotNull null
                    BackendMappers.mapLookupToCallerIdEntry(map)
                }
                if (!entries.isNullOrEmpty()) {
                    callerIdDao.insertAll(entries)
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Fast local-only lookup scoped to [currentUserId].
     *
     * Contact and alias results are only returned if they belong to the
     * requesting user — preventing cross-user data leakage. Pass `null`
     * to skip the contact/alias tier entirely (e.g. unauthenticated contexts).
     */
    suspend fun lookupNumberLocal(rawQuery: String, currentUserId: String? = null): LookupResult {
        val fullPhone = PhoneUtils.normalizePhone(rawQuery)

        val callerIdEntry = callerIdDao.getByPhone(fullPhone)
        if (callerIdEntry != null) {
            return LookupResult(callerIdEntry = callerIdEntry, contactMatch = null, source = "caller_id_db")
        }

        val registeredUser = userDao.getByPhone(fullPhone)
        if (registeredUser != null) {
            return LookupResult(
                callerIdEntry = CallerIdEntry(
                    id = "user-${registeredUser.id}",
                    phoneNumber = registeredUser.phoneNumber,
                    name = registeredUser.fullName,
                    spamScore = 0, reportCount = 0,
                    category = SpamCategory.SAFE,
                    lastUpdated = registeredUser.lastLogin ?: registeredUser.createdAt
                ),
                contactMatch = null, source = "registered_user"
            )
        }

        // Contact and alias tiers are scoped to the requesting user only.
        if (currentUserId != null) {
            val contact = contactDao.getByPhoneAndUser(fullPhone, currentUserId)
            if (contact != null) {
                return LookupResult(
                    callerIdEntry = CallerIdEntry(
                        id = "contact-${contact.id}",
                        phoneNumber = contact.phoneNumber,
                        name = contact.name,
                        spamScore = 0, reportCount = 0,
                        category = SpamCategory.SAFE,
                        lastUpdated = contact.syncedAt
                    ),
                    contactMatch = contact, source = "contacts"
                )
            }

            val aliases = contactAliasDao.getAliasesByPhoneOnce(fullPhone)
            val userAliases = aliases.filter { it.userId == currentUserId }
            if (userAliases.isNotEmpty()) {
                val sourcePriority = listOf("user", "contact_book", "whatsapp", "caller_id")
                val bestAlias = userAliases.sortedBy { alias ->
                    val idx = sourcePriority.indexOf(alias.source)
                    if (idx == -1) sourcePriority.size else idx
                }.first()
                return LookupResult(
                    callerIdEntry = CallerIdEntry(
                        id = "alias-${bestAlias.id}",
                        phoneNumber = bestAlias.phoneNumber,
                        name = bestAlias.name,
                        spamScore = 0, reportCount = 0,
                        category = SpamCategory.SAFE,
                        lastUpdated = bestAlias.addedAt
                    ),
                    contactMatch = null, source = "alias"
                )
            }
        }

        return LookupResult(callerIdEntry = null, contactMatch = null, source = "not_found")
    }

    /**
     * Full lookup with backend API fallback, scoped to [currentUserId].
     *
     * Contact results are restricted to contacts owned by [currentUserId] to
     * prevent one user from seeing another user's contact names.
     */
    suspend fun lookupNumber(rawQuery: String, currentUserId: String? = null): LookupResult {
        val fullPhone = PhoneUtils.normalizePhone(rawQuery)

        // 1. Community caller-ID database (shared, non-personal)
        val callerIdEntry = callerIdDao.getByPhone(fullPhone)
        if (callerIdEntry != null) {
            return LookupResult(callerIdEntry = callerIdEntry, contactMatch = null, source = "caller_id_db")
        }

        // 2. Registered users (public profile data only — name is self-submitted)
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

        // 3. Requesting user's own contacts (scoped — never cross-user)
        if (currentUserId != null) {
            val contact = contactDao.getByPhoneAndUser(fullPhone, currentUserId)
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
                    source = "contacts"
                )
            }

            // 4. Aliases owned by this user only
            val aliases = contactAliasDao.getAliasesByPhoneOnce(fullPhone)
            val userAliases = aliases.filter { it.userId == currentUserId }
            if (userAliases.isNotEmpty()) {
                val sourcePriority = listOf("user", "contact_book", "whatsapp", "caller_id")
                val bestAlias = userAliases.sortedBy { alias ->
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
        }

        // 5. Backend API fallback — community-sourced caller ID only
        try {
            val apiResult = withTimeoutOrNull(5_000L) {
                ApiClient.lookupCallerId(fullPhone)
            }
            if (apiResult != null && apiResult.success && apiResult.data != null) {
                val entry = BackendMappers.mapLookupToCallerIdEntry(apiResult.data)
                if (entry != null) {
                    callerIdDao.insert(entry)
                    return LookupResult(callerIdEntry = entry, contactMatch = null, source = "backend_api")
                }
            }
        } catch (_: Exception) { }

        return LookupResult(callerIdEntry = null, contactMatch = null, source = "not_found")
    }
}

data class LookupResult(
    val callerIdEntry: CallerIdEntry?,
    val contactMatch: Contact?,
    val source: String
)
