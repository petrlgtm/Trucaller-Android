package com.example.trucaller.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.trucaller.TruCallerApplication
import com.example.trucaller.data.model.BlockedNumber
import com.example.trucaller.data.model.Contact
import com.example.trucaller.data.preferences.UserPreferences
import com.example.trucaller.data.repository.BlockedNumberRepository
import com.example.trucaller.data.repository.ContactRepository
import com.example.trucaller.service.DriveSyncService
import com.example.trucaller.util.readPhoneContacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ContactsViewModel(
    application: Application,
    private val contactRepository: ContactRepository,
    private val preferences: UserPreferences,
    private val blockedNumberRepository: BlockedNumberRepository
) : AndroidViewModel(application) {

    val autoBackup: Flow<Boolean> = preferences.autoBackup
    private val driveSyncService = DriveSyncService(application)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    fun getContactsByUser(userId: String): Flow<List<Contact>> = contactRepository.getContactsByUser(userId)
    fun getAllContacts(): Flow<List<Contact>> = contactRepository.getAllContacts()
    fun getContactCountByUser(userId: String): Flow<Int> = contactRepository.getContactCountByUser(userId)

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
        viewModelScope.launch {
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
                                syncedAt = now,
                                isBackedUp = true
                            )
                        )
                        count++
                    } else if (existing.userId == userId) {
                        // Update name if changed in phone contacts
                        if (existing.name != pc.name) {
                            contactRepository.updateContact(
                                existing.copy(name = pc.name, syncedAt = now, isBackedUp = true)
                            )
                        }
                    }
                }
                count
            }

            // Step 2: Update all user's contacts' sync timestamp
            val contacts = contactRepository.getContactsByUser(userId)
            contacts.collect { list ->
                list.forEach { contact ->
                    contactRepository.updateContact(
                        contact.copy(syncedAt = now, isBackedUp = true)
                    )
                }

                // Step 3: Sync to Google Drive if signed in
                if (driveSyncService.isSignedIn()) {
                    val result = driveSyncService.fullSync()
                    _isSyncing.value = false
                    _syncMessage.value = "Imported $importedCount new phone contacts. " +
                            "Synced ${list.size} total to Google Drive."
                } else {
                    _isSyncing.value = false
                    _syncMessage.value = "Imported $importedCount new phone contacts. " +
                            "${list.size} contacts saved locally. Sign in to Google for cloud backup."
                }
                return@collect
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
                    app.container.blockedNumberRepository
                )
            }
        }
    }
}
