package com.example.trucaller.service

import android.content.Context
import com.example.trucaller.TruCallerApplication
import com.example.trucaller.data.model.CallerIdEntry
import com.example.trucaller.data.model.Contact
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DriveSyncService(private val context: Context) {

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
            e.printStackTrace()
            false
        }
    }

    /**
     * Download contacts from Google Drive and merge into local DB.
     * New contacts (by ID) are inserted; existing are updated.
     */
    suspend fun downloadAndMergeContacts(): Int = withContext(Dispatchers.IO) {
        try {
            val json = driveManager.downloadRawJson(GoogleDriveManager.CONTACTS_FILE)
                ?: return@withContext 0
            val type = object : TypeToken<List<Contact>>() {}.type
            val driveContacts: List<Contact> = gson.fromJson(json, type) ?: return@withContext 0

            var count = 0
            driveContacts.forEach { contact ->
                val existing = container.contactRepository.getContactByPhone(contact.phoneNumber)
                if (existing == null) {
                    container.contactRepository.insertContact(contact)
                    count++
                }
            }
            count
        } catch (e: Exception) {
            e.printStackTrace()
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
            e.printStackTrace()
            false
        }
    }

    /**
     * Download caller ID entries from Drive and merge
     */
    suspend fun downloadAndMergeCallerIdEntries(): Int = withContext(Dispatchers.IO) {
        try {
            val json = driveManager.downloadRawJson(GoogleDriveManager.CALLER_ID_FILE)
                ?: return@withContext 0
            val type = object : TypeToken<List<CallerIdEntry>>() {}.type
            val driveEntries: List<CallerIdEntry> = gson.fromJson(json, type) ?: return@withContext 0

            var count = 0
            driveEntries.forEach { entry ->
                val existing = container.callerIdRepository.getByPhone(entry.phoneNumber)
                if (existing == null) {
                    container.callerIdRepository.insertEntry(entry)
                    count++
                }
            }
            count
        } catch (e: Exception) {
            e.printStackTrace()
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
