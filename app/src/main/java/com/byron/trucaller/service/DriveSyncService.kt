package com.byron.trucaller.service

import android.content.Context
import android.util.Log
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.CallerIdEntry
import com.byron.trucaller.data.model.Contact
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DriveSyncService(private val context: Context) {

    companion object {
        private const val TAG = "DriveSyncService"
    }

    private val driveManager = GoogleDriveManager(context)
    private val gson = Gson()

    private val container get() = (context.applicationContext as TruCallerApplication).container

    /**
     * Upload all contacts from local DB to Google Drive
     */
    suspend fun uploadContacts(): Boolean = withContext(Dispatchers.IO) {
        try {
            val allContacts = container.contactRepository.getAllContacts().first()
            driveManager.uploadData(GoogleDriveManager.CONTACTS_FILE, allContacts)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload contacts to Google Drive", e)
            false
        }
    }

    /**
     * Download contacts from Google Drive and merge into local DB.
     * New contacts are inserted; existing contacts are updated when the Drive version is newer.
     * Returns total count of inserted + updated contacts.
     */
    suspend fun downloadAndMergeContacts(): Int = withContext(Dispatchers.IO) {
        try {
            val json = driveManager.downloadRawJson(GoogleDriveManager.CONTACTS_FILE)
            if (json == null) {
                Log.d(TAG, "No contacts file found on Google Drive")
                return@withContext 0
            }
            val type = object : TypeToken<List<Contact>>() {}.type
            val driveContacts: List<Contact> = gson.fromJson(json, type)
            if (driveContacts == null) {
                Log.w(TAG, "Failed to parse contacts JSON from Google Drive")
                return@withContext 0
            }

            var inserted = 0
            var updated = 0
            driveContacts.forEach { driveContact ->
                try {
                    val existing = container.contactRepository.getContactByPhone(driveContact.phoneNumber)
                    if (existing == null) {
                        container.contactRepository.insertContact(driveContact)
                        inserted++
                    } else if (driveContact.syncedAt > existing.syncedAt) {
                        // Drive version is newer — update local with Drive data, preserving local-only fields
                        container.contactRepository.updateContact(
                            existing.copy(
                                name = driveContact.name,
                                email = driveContact.email ?: existing.email,
                                syncedAt = driveContact.syncedAt,
                                isBackedUp = true
                            )
                        )
                        updated++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to merge Drive contact: ${driveContact.phoneNumber}", e)
                }
            }
            Log.d(TAG, "Drive contacts merge complete: $inserted inserted, $updated updated")
            inserted + updated
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download and merge contacts from Google Drive", e)
            0
        }
    }

    /**
     * Upload caller ID entries to Drive
     */
    suspend fun uploadCallerIdEntries(): Boolean = withContext(Dispatchers.IO) {
        try {
            val entries = container.callerIdRepository.getAllEntries().first()
            driveManager.uploadData(GoogleDriveManager.CALLER_ID_FILE, entries)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload caller ID entries to Google Drive", e)
            false
        }
    }

    /**
     * Download caller ID entries from Drive and merge.
     * New entries are inserted; existing entries are updated when the Drive version is newer.
     * Returns total count of inserted + updated entries.
     */
    suspend fun downloadAndMergeCallerIdEntries(): Int = withContext(Dispatchers.IO) {
        try {
            val json = driveManager.downloadRawJson(GoogleDriveManager.CALLER_ID_FILE)
            if (json == null) {
                Log.d(TAG, "No caller ID file found on Google Drive")
                return@withContext 0
            }
            val type = object : TypeToken<List<CallerIdEntry>>() {}.type
            val driveEntries: List<CallerIdEntry> = gson.fromJson(json, type)
            if (driveEntries == null) {
                Log.w(TAG, "Failed to parse caller ID JSON from Google Drive")
                return@withContext 0
            }

            var inserted = 0
            var updated = 0
            driveEntries.forEach { driveEntry ->
                try {
                    val existing = container.callerIdRepository.getByPhone(driveEntry.phoneNumber)
                    if (existing == null) {
                        container.callerIdRepository.insertEntry(driveEntry)
                        inserted++
                    } else if (driveEntry.lastUpdated > existing.lastUpdated) {
                        // Drive version is newer — update local entry
                        container.callerIdRepository.updateEntry(
                            existing.copy(
                                name = driveEntry.name,
                                spamScore = driveEntry.spamScore,
                                reportCount = driveEntry.reportCount,
                                category = driveEntry.category,
                                lastUpdated = driveEntry.lastUpdated
                            )
                        )
                        updated++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to merge Drive caller ID entry: ${driveEntry.phoneNumber}", e)
                }
            }
            Log.d(TAG, "Drive caller ID merge complete: $inserted inserted, $updated updated")
            inserted + updated
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download and merge caller ID entries from Google Drive", e)
            0
        }
    }

    /**
     * Full sync: upload local -> download remote -> merge
     */
    suspend fun fullSync(): SyncResult = withContext(Dispatchers.IO) {
        val uploadOk = uploadContacts()
        val uploadCallerOk = uploadCallerIdEntries()
        val newContacts = downloadAndMergeContacts()
        val newEntries = downloadAndMergeCallerIdEntries()
        SyncResult(
            contactsUploaded = uploadOk,
            callerIdUploaded = uploadCallerOk,
            newContactsDownloaded = newContacts,
            newCallerIdDownloaded = newEntries,
            timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        )
    }

    fun isSignedIn(): Boolean = driveManager.isSignedIn()
    fun getSignInIntent() = driveManager.getSignInIntent()
}

data class SyncResult(
    val contactsUploaded: Boolean,
    val callerIdUploaded: Boolean,
    val newContactsDownloaded: Int,
    val newCallerIdDownloaded: Int,
    val timestamp: String
)
