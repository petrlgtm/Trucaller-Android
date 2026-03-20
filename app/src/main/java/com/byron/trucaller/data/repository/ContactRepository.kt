package com.byron.trucaller.data.repository

import com.byron.trucaller.data.dao.ContactDao
import com.byron.trucaller.data.model.Contact
import kotlinx.coroutines.flow.Flow

class ContactRepository(private val contactDao: ContactDao) {
    fun getContactsByUser(userId: String): Flow<List<Contact>> = contactDao.getByUserId(userId)
    fun getAllContacts(): Flow<List<Contact>> = contactDao.getAllContacts()
    fun getContactCountByUser(userId: String): Flow<Int> = contactDao.getCountByUser(userId)
    fun getTotalContactCount(): Flow<Int> = contactDao.getTotalCount()

    suspend fun insertContact(contact: Contact) = contactDao.insert(contact)
    suspend fun insertContacts(contacts: List<Contact>) = contactDao.insertAll(contacts)
    suspend fun updateContact(contact: Contact) = contactDao.update(contact)
    suspend fun updateAllBackupStatus(userId: String, backed: Boolean) = contactDao.updateAllBackupStatus(userId, backed)
    suspend fun markAllSynced(userId: String, syncedAt: String) = contactDao.markAllSynced(userId, syncedAt)
    suspend fun getContactByPhone(phone: String): Contact? = contactDao.getByPhone(phone)
    suspend fun searchContacts(query: String): List<Contact> = contactDao.search(query)
    suspend fun deleteContact(contact: Contact) = contactDao.delete(contact)
    suspend fun getContactById(id: String): Contact? = contactDao.getById(id)

    fun getFavouritesByUser(userId: String): Flow<List<Contact>> = contactDao.getFavouritesByUser(userId)
    fun getFavouritesBySegment(userId: String, segment: String): Flow<List<Contact>> = contactDao.getFavouritesBySegment(userId, segment)
    fun getSegmentsByUser(userId: String): Flow<List<String>> = contactDao.getSegmentsByUser(userId)
    suspend fun updateFavourite(id: String, isFavourite: Boolean, segment: String?) = contactDao.updateFavourite(id, isFavourite, segment)
    suspend fun updateNote(id: String, note: String?) = contactDao.updateNote(id, note)
    suspend fun updateContactPhoto(id: String, photoUri: String?) = contactDao.updatePhoto(id, photoUri)
}
