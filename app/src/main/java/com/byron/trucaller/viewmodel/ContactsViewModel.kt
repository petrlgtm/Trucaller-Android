package com.byron.trucaller.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.BlockedNumber
import com.byron.trucaller.data.model.Contact
import com.byron.trucaller.data.preferences.UserPreferences
import com.byron.trucaller.data.repository.BackendMappers
import com.byron.trucaller.data.repository.BlockedNumberRepository
import com.byron.trucaller.data.repository.CallerIdRepository
import com.byron.trucaller.data.repository.ContactRepository
import com.byron.trucaller.service.ApiClient
import com.byron.trucaller.service.DriveSyncService
import android.net.Uri
import com.byron.trucaller.util.copyImageToInternal
import com.byron.trucaller.util.readPhoneContacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ContactsViewModel(
    application: Application,
    private val contactRepository: ContactRepository,
    private val preferences: UserPreferences,
    private val blockedNumberRepository: BlockedNumberRepository,
    private val callerIdRepository: CallerIdRepository
) : AndroidViewModel(application) {

    val autoBackup: Flow<Boolean> = preferences.autoBackup
    private val driveSyncService = DriveSyncService(application)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private var syncJob: Job? = null

    fun getContactsByUser(userId: String): Flow<List<Contact>> = contactRepository.getContactsByUser(userId)
    fun getAllContacts(): Flow<List<Contact>> = contactRepository.getAllContacts()
    fun getContactCountByUser(userId: String): Flow<Int> = contactRepository.getContactCountByUser(userId)

    fun getFavouritesByUser(userId: String): Flow<List<Contact>> = contactRepository.getFavouritesByUser(userId)
    fun getFavouritesBySegment(userId: String, segment: String): Flow<List<Contact>> = contactRepository.getFavouritesBySegment(userId, segment)
    fun getSegmentsByUser(userId: String): Flow<List<String>> = contactRepository.getSegmentsByUser(userId)

    fun toggleFavourite(contact: Contact, segment: String?) {
        viewModelScope.launch {
            val newFav = !contact.isFavourite
            contactRepository.updateFavourite(contact.id, newFav, if (newFav) segment else null)
            _syncMessage.value = if (newFav) "${contact.name} added to favourites" else "${contact.name} removed from favourites"
        }
    }

    fun removeFromFavourites(contact: Contact) {
        viewModelScope.launch {
            contactRepository.updateFavourite(contact.id, false, null)
            _syncMessage.value = "${contact.name} removed from favourites"
        }
    }

    fun updateFavouriteSegment(contactId: String, segment: String?) {
        viewModelScope.launch {
            contactRepository.updateFavourite(contactId, true, segment)
        }
    }

    fun updateNote(contactId: String, note: String?) {
        viewModelScope.launch {
            val trimmed = note?.trim()?.ifEmpty { null }
            contactRepository.updateNote(contactId, trimmed)
            _syncMessage.value = if (trimmed != null) "Note saved" else "Note removed"
        }
    }

    fun updateContactPhoto(contactId: String, uri: Uri) {
        viewModelScope.launch {
            try {
                val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    copyImageToInternal(getApplication(), uri, "contact_$contactId")
                }
                contactRepository.updateContactPhoto(contactId, file.absolutePath)
                _syncMessage.value = "Photo updated"
            } catch (_: Exception) {
                _syncMessage.value = "Failed to save photo"
            }
        }
    }

    fun removeContactPhoto(contactId: String) {
        viewModelScope.launch {
            val contact = contactRepository.getContactById(contactId)
            contact?.photoUri?.let { path -> java.io.File(path).delete() }
            contactRepository.updateContactPhoto(contactId, null)
            _syncMessage.value = "Photo removed"
        }
    }

    fun setAutoBackup(enabled: Boolean, userId: String) {
        viewModelScope.launch {
            preferences.setAutoBackup(enabled)
            if (enabled) {
                contactRepository.updateAllBackupStatus(userId, true)
            }
        }
    }

    fun updateContact(contact: Contact) {
        viewModelScope.launch {
            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
            contactRepository.updateContact(contact.copy(syncedAt = now))
            _syncMessage.value = "${contact.name} updated"
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            contactRepository.deleteContact(contact)
            _syncMessage.value = "${contact.name} deleted"
        }
    }

    fun blockContact(contact: Contact, userId: String) {
        viewModelScope.launch {
            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
            val blocked = BlockedNumber(
                id = "blk-${System.currentTimeMillis()}",
                userId = userId,
                phoneNumber = contact.phoneNumber,
                name = contact.name,
                reason = "Blocked from contacts",
                blockedAt = now
            )
            blockedNumberRepository.blockNumber(blocked)
            _syncMessage.value = "${contact.name} has been blocked"
        }
    }

    fun unblockContact(phoneNumber: String, userId: String, name: String) {
        viewModelScope.launch {
            blockedNumberRepository.unblockNumber(userId, phoneNumber)
            _syncMessage.value = "$name has been unblocked"
        }
    }

    suspend fun isContactBlocked(userId: String, phoneNumber: String): Boolean {
        return blockedNumberRepository.isBlocked(userId, phoneNumber)
    }

    fun syncContacts(userId: String) {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _isSyncing.value = true
            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

            // Step 1: Read contacts from the phone's contact book and import into Room DB
            val importedCount = withContext(Dispatchers.IO) {
                val phoneContacts = readPhoneContacts(getApplication())
                var count = 0
                phoneContacts.forEach { pc ->
                    val existing = contactRepository.getContactByPhone(pc.phoneNumber)
                    if (existing == null) {
                        contactRepository.insertContact(
                            Contact(
                                id = "cnt-${userId}-${System.nanoTime()}",
                                userId = userId,
                                name = pc.name,
                                phoneNumber = pc.phoneNumber,
                                email = pc.email,
                                syncedAt = now,
                                isBackedUp = true
                            )
                        )
                        count++
                    } else if (existing.userId == userId) {
                        // Update name/email if changed in phone contacts
                        if (existing.name != pc.name || (pc.email != null && existing.email != pc.email)) {
                            contactRepository.updateContact(
                                existing.copy(
                                    name = pc.name,
                                    email = pc.email ?: existing.email,
                                    syncedAt = now,
                                    isBackedUp = true
                                )
                            )
                        }
                    } else {
                        // Contact exists under a different userId (e.g. re-registration).
                        // Claim it for the current user.
                        contactRepository.updateContact(
                            existing.copy(
                                userId = userId,
                                name = pc.name,
                                email = pc.email ?: existing.email,
                                syncedAt = now,
                                isBackedUp = true
                            )
                        )
                        count++
                    }
                }
                count
            }

            // Step 2: Update all user's contacts' sync timestamp via batch
            contactRepository.markAllSynced(userId, now)

            // Step 3: Upload contacts to backend MongoDB
            // Re-fetch after updates to include newly imported contacts
            val list = contactRepository.getContactsByUser(userId).firstOrNull() ?: emptyList()
            var uploadFailed = false
            try {
                val contactMaps = list.map { c ->
                    mapOf(
                        "name" to c.name,
                        "phoneNumber" to c.phoneNumber,
                        "email" to (c.email ?: "")
                    )
                }
                withContext(Dispatchers.IO) {
                    ApiClient.uploadContacts(contactMaps)
                }
            } catch (e: Exception) {
                android.util.Log.w("ContactSync", "Upload contacts failed", e)
                uploadFailed = true
            }

            // Step 3b: Upload caller IDs to backend MongoDB
            var callerIdCount = 0
            try {
                val callerIds = withContext(Dispatchers.IO) {
                    callerIdRepository.getAllEntriesOnce()
                }
                callerIdCount = callerIds.size
                if (callerIds.isNotEmpty()) {
                    val callerIdMaps = callerIds.map { entry ->
                        mapOf(
                            "phoneNumber" to entry.phoneNumber,
                            "name" to entry.name,
                            "spamScore" to entry.spamScore,
                            "reportCount" to entry.reportCount,
                            "category" to entry.category.name
                        )
                    }
                    withContext(Dispatchers.IO) {
                        ApiClient.uploadCallerIds(callerIdMaps)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ContactSync", "Upload caller IDs failed", e)
                uploadFailed = true
            }

            // Step 3c: Download contacts from backend and merge (incremental via last sync timestamp)
            var downloadedContacts = 0
            var updatedContacts = 0
            try {
                val lastSyncMs = preferences.lastContactSyncTimestamp.first()
                val sinceParam = if (lastSyncMs > 0L) {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date(lastSyncMs))
                } else null
                val result = withContext(Dispatchers.IO) { ApiClient.syncContacts(since = sinceParam) }
                if (result.success && result.data != null) {
                    result.data.forEach { map ->
                        val remote = BackendMappers.mapToContact(map, userId)
                        if (remote.phoneNumber.isNotBlank()) {
                            val existing = contactRepository.getContactByPhone(remote.phoneNumber)
                            if (existing == null) {
                                contactRepository.insertContact(remote)
                                downloadedContacts++
                            } else if (existing.userId == userId) {
                                val remoteTime = remote.syncedAt
                                val localTime = existing.syncedAt
                                if (remoteTime > localTime || existing.name != remote.name || existing.email != remote.email) {
                                    contactRepository.updateContact(
                                        existing.copy(
                                            name = remote.name,
                                            email = remote.email ?: existing.email,
                                            syncedAt = now,
                                            isBackedUp = true
                                        )
                                    )
                                    updatedContacts++
                                }
                            }
                        }
                    }
                    // Only update timestamp on successful download
                    preferences.setLastContactSyncTimestamp(System.currentTimeMillis())
                }
            } catch (e: Exception) {
                android.util.Log.w("ContactSync", "Download contacts failed", e)
                // Don't update timestamp — retry will re-fetch on next sync
            }

            // Step 3d: Bi-directional sync of blocked numbers with backend
            var downloadedBlocked = 0
            try {
                downloadedBlocked = blockedNumberRepository.syncBlockedNumbers(userId)
            } catch (e: Exception) {
                android.util.Log.w("ContactSync", "Blocked numbers sync failed", e)
            }

            // Step 3e: Download spam/caller-ID data from backend and merge
            var downloadedSpam = 0
            try {
                val result = withContext(Dispatchers.IO) { ApiClient.getSpamNumbers() }
                if (result.success && result.data != null) {
                    result.data.forEach { map ->
                        val remote = BackendMappers.mapToCallerIdEntry(map)
                        if (remote.phoneNumber.isNotBlank()) {
                            val existing = callerIdRepository.getByPhone(remote.phoneNumber)
                            if (existing == null) {
                                callerIdRepository.insertEntry(remote)
                                downloadedSpam++
                            } else if (remote.spamScore > existing.spamScore) {
                                callerIdRepository.updateEntry(
                                    existing.copy(
                                        spamScore = remote.spamScore,
                                        reportCount = remote.reportCount,
                                        category = remote.category,
                                        lastUpdated = remote.lastUpdated
                                    )
                                )
                                downloadedSpam++
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ContactSync", "Spam numbers sync failed", e)
            }

            val downloadSummary = listOfNotNull(
                if (downloadedContacts > 0) "$downloadedContacts new contacts" else null,
                if (updatedContacts > 0) "$updatedContacts updated contacts" else null,
                if (downloadedBlocked > 0) "$downloadedBlocked blocked" else null,
                if (downloadedSpam > 0) "$downloadedSpam spam entries" else null
            ).joinToString(", ")
            val downloadPart = if (downloadSummary.isNotEmpty()) " Downloaded $downloadSummary." else ""

            // Step 4: Sync to Google Drive if signed in
            val failNote = if (uploadFailed) " (some uploads failed — will retry)" else ""
            if (driveSyncService.isSignedIn()) {
                try { driveSyncService.fullSync() } catch (_: Exception) { }
            }
            _isSyncing.value = false

            // Only show popup when there's something new to report
            val hasNewContent = importedCount > 0 || downloadSummary.isNotEmpty() || uploadFailed
            if (hasNewContent) {
                val syncType = if (driveSyncService.isSignedIn()) "synced" else "backed up"
                _syncMessage.value = "Imported $importedCount new contacts. " +
                        "${list.size} contacts & $callerIdCount caller IDs $syncType.$downloadPart$failNote"
            }
        }
    }

    fun syncToGoogleDrive() {
        viewModelScope.launch {
            _isSyncing.value = true
            if (driveSyncService.isSignedIn()) {
                val result = driveSyncService.fullSync()
                _isSyncing.value = false
                _syncMessage.value = "Cloud sync complete. Downloaded ${result.newContactsDownloaded} new contacts, " +
                        "${result.newCallerIdDownloaded} new caller IDs."
            } else {
                _isSyncing.value = false
                _syncMessage.value = "Please sign in to Google first."
            }
        }
    }

    fun isDriveSignedIn(): Boolean = driveSyncService.isSignedIn()
    fun getDriveSignInIntent(): Intent = driveSyncService.getSignInIntent()

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruCallerApplication
                ContactsViewModel(
                    app,
                    app.container.contactRepository,
                    app.container.userPreferences,
                    app.container.blockedNumberRepository,
                    app.container.callerIdRepository
                )
            }
        }
    }
}
